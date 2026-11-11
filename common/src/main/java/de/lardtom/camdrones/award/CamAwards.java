package de.lardtom.camdrones.award;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.crew.CrewDrones;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.net.VideoRecorded;
import de.lardtom.camdrones.planner.PlannerOwners;
import de.lardtom.camdrones.planner.model.RateLimiter;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CamAwards {
    public static final String ROOT = "root";
    public static final String CREW_FLIGHT = "crew_flight";
    public static final String VIDEO = "video";
    public static final String MISSION = "mission";
    public static final List<String> AWARDED = List.of(CREW_FLIGHT, VIDEO, MISSION);
    public static final List<String> ALL = List.of(ROOT, CREW_FLIGHT, VIDEO, MISSION);
    public static final String CRITERION = "done";
    public static final double VIDEO_RATE = 1.0;
    public static final double VIDEO_BURST = 3.0;
    private static final RateLimiter VIDEOS = new RateLimiter(VIDEO_RATE, VIDEO_BURST);

    private CamAwards() {
    }

    public static boolean award(@Nullable ServerPlayer player, String path) {
        if (player == null) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        AdvancementHolder holder = server == null ? null : server.getAdvancements().get(CamDrones.id(path));
        return holder != null && player.getAdvancements().award(holder, CRITERION);
    }

    public static void crewFlight(Entity drone, ServerPlayer operator) {
        if (drone == null || operator == null) {
            return;
        }
        Optional<DroneView> view;
        try {
            view = PropwashApi.drones().view(drone);
        } catch (RuntimeException e) {
            return;
        }
        Optional<UUID> pilot = view.flatMap(DroneView::pilot);
        boolean armed = view.map(DroneView::armed).orElse(false);
        if (!AwardRules.crewFlight(pilot, operator.getUUID(), armed, airborne(drone))) {
            return;
        }
        award(operator, CREW_FLIGHT);
        award(player(drone, pilot.get()), CREW_FLIGHT);
    }

    public static boolean airborne(Entity drone) {
        return drone.level().noCollision(drone, drone.getBoundingBox().expandTowards(0.0, -AwardRules.AIR_GAP, 0.0));
    }

    public static void missionStatus(Entity drone, MissionStatus previous, MissionStatus next) {
        if (drone == null || previous == null || next == null || !AwardRules.missionCompleted(previous.state(), next.state())) {
            return;
        }
        UUID owner = PlannerOwners.owner(drone);
        if (owner != null && !PlannerOwners.NO_OWNER.equals(owner)) {
            award(player(drone, owner), MISSION);
        }
        CrewDrones.pilot(drone).filter(pilot -> !pilot.equals(owner)).ifPresent(pilot -> award(player(drone, pilot), MISSION));
    }

    public static void onVideo(VideoRecorded.Report report, ServerPlayer player) {
        if (!VIDEOS.tryAcquire(player.getUUID(), Util.getMillis())) {
            return;
        }
        Entity drone = player.level().getEntity(report.drone());
        if (drone != null && !drone.isRemoved() && CrewDrones.hasGimbal(drone)) {
            award(player, VIDEO);
        }
    }

    private static @Nullable ServerPlayer player(Entity drone, UUID id) {
        MinecraftServer server = drone.level().getServer();
        return server == null || id == null ? null : server.getPlayerList().getPlayer(id);
    }
}
