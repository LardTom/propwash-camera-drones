package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.MissionUpload;
import de.lardtom.camdrones.planner.model.RateLimiter;
import de.lardtom.camdrones.planner.model.UploadCheck;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.UUID;

public final class MissionUploads {
    private static final RateLimiter RATE = new RateLimiter(UploadCheck.RATE_PER_SECOND);

    private MissionUploads() {
    }

    public static void handle(MissionUpload.Request request, ServerPlayer player) {
        UploadCheck.Verdict verdict = verdict(request, player);
        if (verdict.result().ok() && verdict.mission().isPresent()) {
            verdict = store(request.serial(), (ServerLevel) player.level(), verdict.mission().get());
        }
        CamNetwork.sendToPlayer(player, new MissionUpload.Reply(request.requestId(), verdict.result(), verdict.waypoint()));
        if (verdict.result().ok()) {
            CamDrones.LOG.info("{}: Mission mit {} Wegpunkten von {} an Drohne {} übertragen", CamDrones.MOD_NAME,
                    verdict.mission().map(Mission::size).orElse(0), player.getGameProfile().name(), request.serial());
        }
    }

    static UploadCheck.Verdict verdict(MissionUpload.Request request, ServerPlayer player) {
        boolean withinRate = RATE.tryAcquire(player.getUUID(), Util.getMillis());
        UUID serial = request.serial();
        boolean linked = !CamDroneData.NO_SERIAL.equals(serial);
        ServerLevel level = (ServerLevel) player.level();
        Optional<Entity> drone = withinRate && linked ? PlannerTablets.findDrone(level, serial) : Optional.empty();
        UploadCheck.Drone info = drone.flatMap(entity -> PropwashApi.drones().view(entity)).map(view -> describe(view, player))
                .orElse(UploadCheck.Drone.MISSING);
        boolean operator = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        UploadCheck.Rules rules = new UploadCheck.Rules(CamWorldRules.maxAltitude(level), CamWorldRules.maxDistance(level));
        return UploadCheck.check(withinRate, request.mission(), linked, info, operator, rules);
    }

    static UploadCheck.Drone describe(DroneView view, ServerPlayer player) {
        boolean self = view.pilot().filter(player.getUUID()::equals).isPresent();
        boolean other = view.pilot().isPresent() && !self;
        Entity entity = view.entity();
        boolean owned = PlannerOwners.owner(entity).equals(player.getUUID());
        return new UploadCheck.Drone(true, owned, other, self, entity.getX(), entity.getY(), entity.getZ());
    }

    static UploadCheck.Verdict store(UUID serial, ServerLevel level, Mission mission) {
        Optional<Entity> drone = PlannerTablets.findDrone(level, serial);
        if (drone.isEmpty() || !CamDroneData.registered()) {
            return UploadCheck.Verdict.of(UploadCheck.Result.UNAVAILABLE);
        }
        try {
            PropwashApi.droneData().set(drone.get(), CamDroneData.mission(), MissionData.of(mission));
        } catch (RuntimeException e) {
            CamDrones.LOG.warn("{}: Mission nicht an der Drohne gespeichert: {}", CamDrones.MOD_NAME, e.toString());
            return UploadCheck.Verdict.of(UploadCheck.Result.UNAVAILABLE);
        }
        return new UploadCheck.Verdict(UploadCheck.Result.OK, UploadCheck.NO_WAYPOINT, Optional.of(mission));
    }
}
