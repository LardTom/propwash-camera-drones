package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.ExitSettingUpload;
import de.lardtom.camdrones.planner.model.ExitSetting;
import de.lardtom.camdrones.planner.model.RateLimiter;
import de.lardtom.camdrones.planner.model.UploadCheck;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ExitSettings {
    private static final RateLimiter RATE = new RateLimiter(ExitSetting.RATE_PER_SECOND, ExitSetting.RATE_BURST);

    private ExitSettings() {
    }

    public static void handle(ExitSettingUpload.Request request, ServerPlayer player) {
        boolean withinRate = RATE.tryAcquire(player.getUUID(), Util.getMillis());
        UUID serial = request.serial();
        boolean linked = !CamDroneData.NO_SERIAL.equals(serial);
        ServerLevel level = (ServerLevel) player.level();
        Optional<Entity> drone = withinRate && linked ? PlannerTablets.findDrone(level, serial) : Optional.empty();
        UploadCheck.Drone info = drone.flatMap(entity -> PropwashApi.drones().view(entity)).map(view -> MissionUploads.describe(view, player))
                .orElse(UploadCheck.Drone.MISSING);
        boolean operator = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        ExitSetting.Result result = ExitSetting.check(withinRate, request.exit(), linked, info, operator);
        Optional<ExitSetting> wanted = ExitSetting.byId(request.exit());
        if (result.ok() && drone.isPresent() && wanted.isPresent()) {
            result = store(drone.get(), wanted.get());
        }
        Optional<ExitSetting> now = drone.flatMap(ExitSettings::read);
        CamNetwork.sendToPlayer(player, new ExitSettingUpload.Reply(request.requestId(), result, now.map(Enum::ordinal).orElse(-1)));
        if (result.ok()) {
            CamDrones.LOG.info("{}: Verhalten beim Verlassen von Drohne {} auf {} gestellt ({})", CamDrones.MOD_NAME, serial,
                    wanted.map(ExitSetting::key).orElse("?"), player.getGameProfile().name());
        }
    }

    public static ExitSetting.Result store(Entity drone, ExitSetting value) {
        if (!CamDroneData.registered()) {
            return ExitSetting.Result.UNAVAILABLE;
        }
        try {
            DroneSettings current = Objects.requireNonNullElse(PropwashApi.droneData().get(drone, CamDroneData.settings()), DroneSettings.DEFAULT);
            PropwashApi.droneData().set(drone, CamDroneData.settings(), current.withExit(behaviour(value)));
        } catch (RuntimeException e) {
            CamDrones.LOG.warn("{}: Verhalten beim Verlassen nicht an der Drohne gespeichert: {}", CamDrones.MOD_NAME, e.toString());
            return ExitSetting.Result.UNAVAILABLE;
        }
        return ExitSetting.Result.OK;
    }

    public static Optional<ExitSetting> read(Entity drone) {
        if (drone == null || !CamDroneData.registered()) {
            return Optional.empty();
        }
        try {
            DroneSettings settings = PropwashApi.droneData().get(drone, CamDroneData.settings());
            return Optional.of(of(settings == null ? DroneSettings.DEFAULT.exit() : settings.exit()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static DroneSettings.ExitBehaviour behaviour(ExitSetting value) {
        return switch (value) {
            case HOVER -> DroneSettings.ExitBehaviour.HOVER;
            case HOME -> DroneSettings.ExitBehaviour.HOME;
            case LAND -> DroneSettings.ExitBehaviour.LAND;
        };
    }

    public static ExitSetting of(DroneSettings.ExitBehaviour value) {
        return switch (value) {
            case HOVER -> ExitSetting.HOVER;
            case HOME -> ExitSetting.HOME;
            case LAND -> ExitSetting.LAND;
        };
    }
}
