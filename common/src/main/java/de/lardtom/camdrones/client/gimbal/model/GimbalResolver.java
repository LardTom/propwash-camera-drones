package de.lardtom.camdrones.client.gimbal.model;

import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.logic.gimbal.GimbalAim;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.WaypointFollower;

import java.util.Objects;
import java.util.Optional;

public final class GimbalResolver {
    public enum Source {
        MANUAL,
        MISSION,
        POINT_OF_INTEREST,
        FOLLOW
    }

    public enum Smart {
        NONE,
        HOLD,
        FOLLOW,
        POI,
        COURSE_LOCK
    }

    public record Point(double x, double y, double z) {
    }

    public record Scene(GimbalLimits limits, double x, double y, double z, double heading, Optional<Mission> mission,
                        NavLogic.MissionState missionState, int missionIndex, Smart smart, Optional<Point> pointOfInterest,
                        Optional<Point> followTarget) {
        public Scene {
            limits = Objects.requireNonNullElse(limits, GimbalLimits.DEFAULT);
            mission = Objects.requireNonNullElse(mission, Optional.empty());
            missionState = Objects.requireNonNullElse(missionState, NavLogic.MissionState.IDLE);
            smart = Objects.requireNonNullElse(smart, Smart.NONE);
            pointOfInterest = Objects.requireNonNullElse(pointOfInterest, Optional.empty());
            followTarget = Objects.requireNonNullElse(followTarget, Optional.empty());
        }

        public static Scene free(GimbalLimits limits, double x, double y, double z, double heading) {
            return new Scene(limits, x, y, z, heading, Optional.empty(), NavLogic.MissionState.IDLE, 0, Smart.NONE, Optional.empty(),
                    Optional.empty());
        }

        public Scene manualOnly() {
            return free(this.limits, this.x, this.y, this.z, this.heading);
        }
    }

    public record Aim(Source source, GimbalSetting.Mode mode, double tilt, double pan, double heading, double zoom) {
        public boolean automatic() {
            return this.source != Source.MANUAL;
        }

        public boolean locked() {
            return this.mode == GimbalSetting.Mode.LOCK;
        }

        public boolean fpv() {
            return this.mode == GimbalSetting.Mode.FPV;
        }

        public Aim withTilt(double value) {
            return new Aim(this.source, this.mode, value, this.pan, this.heading, this.zoom);
        }
    }

    private GimbalResolver() {
    }

    public static Aim resolve(Scene scene, GimbalSetting manual) {
        GimbalSetting setting = Objects.requireNonNullElse(manual, GimbalSetting.CENTER);
        Optional<Aim> auto = automatic(scene, setting.zoom());
        return auto.orElseGet(() -> manual(scene, setting));
    }

    public static boolean manualControl(Scene scene) {
        return automatic(scene, 0.0).isEmpty();
    }

    public static Optional<GimbalSetting> automaticSetting(Scene scene, double zoom) {
        return automatic(scene, zoom).map(aim -> new GimbalSetting(GimbalSetting.Mode.LOCK, aim.tilt(), 0.0, aim.heading(), zoom));
    }

    static Optional<Aim> automatic(Scene scene, double zoom) {
        if (scene.missionState() == NavLogic.MissionState.RUNNING && scene.mission().isPresent() && !scene.mission().get().isEmpty()) {
            Mission mission = scene.mission().get();
            int index = Math.clamp(scene.missionIndex(), 0, mission.size() - 1);
            Waypoint waypoint = mission.waypoint(index);
            switch (waypoint.heading()) {
                case POI -> {
                    if (waypoint.poi() < mission.pointsOfInterest().size()) {
                        PointOfInterest p = mission.pointsOfInterest().get(waypoint.poi());
                        return Optional.of(aimAt(scene, Source.MISSION, p.x(), p.y(), p.z(), zoom));
                    }
                    return Optional.of(missionTilt(scene, mission, index, zoom));
                }
                case FREE -> {
                    return Optional.empty();
                }
                default -> {
                    return Optional.of(missionTilt(scene, mission, index, zoom));
                }
            }
        }
        return switch (scene.smart()) {
            case POI -> scene.pointOfInterest().map(p -> aimAt(scene, Source.POINT_OF_INTEREST, p.x(), p.y(), p.z(), zoom));
            case FOLLOW -> scene.followTarget().map(p -> aimAt(scene, Source.FOLLOW, p.x(), p.y(), p.z(), zoom));
            default -> Optional.empty();
        };
    }

    private static Aim missionTilt(Scene scene, Mission mission, int index, double zoom) {
        double tilt = scene.limits().clampTilt(WaypointFollower.tiltAt(mission, index, scene.x(), scene.y(), scene.z()));
        return new Aim(Source.MISSION, GimbalSetting.Mode.FOLLOW, tilt, 0.0, Compass.normalize(scene.heading()), zoom);
    }

    static Aim aimAt(Scene scene, Source source, double tx, double ty, double tz, double zoom) {
        double dx = tx - scene.x();
        double dy = ty - scene.y();
        double dz = tz - scene.z();
        double tilt = scene.limits().clampTilt(GimbalAim.tiltTo(dx, dy, dz));
        if (scene.limits().fullPan() && Math.hypot(dx, dz) > 1.0E-6) {
            double yaw = Compass.headingTo(dx, dz);
            return new Aim(source, GimbalSetting.Mode.LOCK, tilt, Compass.wrap180(yaw - scene.heading()), yaw, zoom);
        }
        return new Aim(source, GimbalSetting.Mode.FOLLOW, tilt, 0.0, Compass.normalize(scene.heading()), zoom);
    }

    static Aim manual(Scene scene, GimbalSetting setting) {
        GimbalLimits limits = scene.limits();
        double tilt = limits.clampTilt(setting.tilt());
        double pan = limits.fullPan() ? setting.pan() : 0.0;
        if (setting.fpv()) {
            return new Aim(Source.MANUAL, GimbalSetting.Mode.FPV, tilt, 0.0, Compass.normalize(scene.heading()), setting.zoom());
        }
        if (setting.locked()) {
            return new Aim(Source.MANUAL, GimbalSetting.Mode.LOCK, tilt, Compass.wrap180(setting.heading() - scene.heading()),
                    setting.heading(), setting.zoom());
        }
        return new Aim(Source.MANUAL, GimbalSetting.Mode.FOLLOW, tilt, pan, Compass.normalize(scene.heading() + pan), setting.zoom());
    }
}
