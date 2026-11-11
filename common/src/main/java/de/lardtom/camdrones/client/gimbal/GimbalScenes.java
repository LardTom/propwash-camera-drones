package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.GimbalOptics;
import de.lardtom.camdrones.client.gimbal.model.GimbalResolver;
import de.lardtom.camdrones.client.record.model.CameraAngles;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.CrewDrones;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.part.GimbalTable;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneDataAccess;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public final class GimbalScenes {
    private GimbalScenes() {
    }

    public static GimbalLimits limits(GimbalTable.Spec spec) {
        return GimbalLimits.of(spec.minTiltDeg(), spec.maxTiltDeg(), spec.fullPan());
    }

    public static Optional<GimbalLimits> limits(Entity drone) {
        return CrewDrones.gimbal(drone).map(GimbalScenes::limits);
    }

    public static GimbalOptics.Range optics(Entity drone) {
        Optional<GimbalTable.Spec> spec = CrewDrones.gimbal(drone);
        if (spec.isEmpty()) {
            return GimbalOptics.range(List.of(), false, OptionalInt.empty());
        }
        return GimbalOptics.range(spec.get().builtInFocalMm(), spec.get().lensBay(),
                spec.get().lensBay() ? CrewDrones.lensFocal(drone) : OptionalInt.empty());
    }

    public static double heading(Entity drone) {
        Optional<DroneView> view = PropwashApi.drones().view(drone);
        if (view.isPresent()) {
            Vec3 forward = view.get().forward();
            Vec3 up = view.get().up();
            if (forward.x * forward.x + forward.z * forward.z > 1.0E-8) {
                return GimbalPose.heading(CameraAngles.frame(forward.x, forward.y, forward.z, up.x, up.y, up.z, new Quaternionf()));
            }
        }
        return Compass.normalize(drone.getYRot() + 180.0);
    }

    public static float partialTick() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? 1.0F : minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    public static GimbalResolver.Scene scene(Entity drone, GimbalLimits limits, double heading) {
        return scene(drone, limits, heading, partialTick());
    }

    public static GimbalResolver.Scene scene(Entity drone, GimbalLimits limits, double heading, float partialTick) {
        return shown(drone, plan(drone, limits, heading, partialTick));
    }

    public static GimbalResolver.Scene shown(Entity drone, GimbalResolver.Scene plan) {
        return manual(drone) ? plan.manualOnly() : plan;
    }

    public static boolean manual(Entity drone) {
        return drone != null && (CrewData.get(drone).manual() || GimbalClient.control().steering(drone.getId()));
    }

    public static GimbalResolver.Scene plan(Entity drone, GimbalLimits limits, double heading) {
        return plan(drone, limits, heading, partialTick());
    }

    public static GimbalResolver.Scene plan(Entity drone, GimbalLimits limits, double heading, float partialTick) {
        Optional<Mission> mission = Optional.empty();
        NavLogic.MissionState state = NavLogic.MissionState.IDLE;
        int index = 0;
        GimbalResolver.Smart smart = GimbalResolver.Smart.NONE;
        Optional<GimbalResolver.Point> pointOfInterest = Optional.empty();
        Optional<GimbalResolver.Point> follow = Optional.empty();
        if (CamDroneData.registered()) {
            try {
                DroneDataAccess data = PropwashApi.droneData();
                MissionStatus status = data.get(drone, CamDroneData.missionState());
                if (status != null) {
                    state = status.state();
                    index = status.index();
                    if (state == NavLogic.MissionState.RUNNING) {
                        MissionData packed = data.get(drone, CamDroneData.mission());
                        mission = packed == null ? Optional.empty() : packed.mission();
                    }
                }
                SmartState smartState = data.get(drone, CamDroneData.smart());
                if (smartState != null) {
                    smart = GimbalResolver.Smart.valueOf(smartState.function().name());
                    if (smart == GimbalResolver.Smart.POI) {
                        pointOfInterest = Optional.of(new GimbalResolver.Point(smartState.x(), smartState.y(), smartState.z()));
                    } else if (smart == GimbalResolver.Smart.FOLLOW) {
                        follow = smartState.target().map(id -> drone.level().getEntity(id)).map(target -> center(target, partialTick));
                    }
                }
            } catch (RuntimeException e) {
                mission = Optional.empty();
                state = NavLogic.MissionState.IDLE;
                smart = GimbalResolver.Smart.NONE;
            }
        }
        Vec3 at = drone.getPosition(partialTick);
        return new GimbalResolver.Scene(limits, at.x, at.y, at.z, heading, mission, state, index, smart, pointOfInterest, follow);
    }

    private static GimbalResolver.Point center(Entity entity, float partialTick) {
        Vec3 c = entity.getBoundingBox().getCenter();
        Vec3 now = entity.position();
        Vec3 at = entity.getPosition(partialTick);
        return new GimbalResolver.Point(c.x - now.x + at.x, c.y - now.y + at.y, c.z - now.z + at.z);
    }
}
