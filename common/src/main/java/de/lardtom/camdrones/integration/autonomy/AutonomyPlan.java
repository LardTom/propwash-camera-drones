package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.OrbitController;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AutonomyPlan(Kind kind, Optional<Mission> mission, int startIndex, boolean paused, Optional<UUID> target,
                           FollowController.Style style, double x, double y, double z, double radius, double speed, boolean clockwise,
                           AutoShots.Shot shot) {
    public enum Kind {
        MISSION("mission", "MISSION"),
        HOLD("hold", "HOLD"),
        FOLLOW("follow", "FOLLOW"),
        ORBIT("poi", "POI"),
        LAND("land", "LAND"),
        AUTO_SHOT("auto_shot", AutoShots.LABEL),
        HOME("home", "HOME");

        private final String path;
        private final String label;
        private final Identifier id;

        Kind(String path, String label) {
            this.path = path;
            this.label = label;
            this.id = CamDrones.id(path);
        }

        public String path() {
            return this.path;
        }

        public String label() {
            return this.label;
        }

        public Identifier id() {
            return this.id;
        }

        public static Optional<Kind> byId(Identifier id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return Optional.of(kind);
                }
            }
            return Optional.empty();
        }
    }

    public AutonomyPlan {
        Objects.requireNonNull(kind, "kind");
        mission = Objects.requireNonNullElse(mission, Optional.<Mission>empty()).filter(m -> !m.isEmpty());
        if (kind == Kind.MISSION && mission.isEmpty()) {
            throw new IllegalArgumentException("Mission fehlt");
        }
        startIndex = mission.isPresent() ? Math.clamp(startIndex, 0, mission.get().size() - 1) : 0;
        target = Objects.requireNonNullElse(target, Optional.<UUID>empty());
        if (kind == Kind.FOLLOW && target.isEmpty()) {
            throw new IllegalArgumentException("Ziel fehlt");
        }
        style = Objects.requireNonNullElse(style, FollowController.Style.BEHIND);
        x = finite(x);
        y = finite(y);
        z = finite(z);
        radius = Math.clamp(finite(radius), 0.0, OrbitController.MAX_RADIUS);
        speed = Math.max(0.0, finite(speed));
        shot = Objects.requireNonNullElse(shot, AutoShots.Shot.RETREAT);
    }

    public AutonomyPlan(Kind kind, Optional<Mission> mission, int startIndex, boolean paused, Optional<UUID> target, FollowController.Style style,
                        double x, double y, double z, double radius, double speed, boolean clockwise) {
        this(kind, mission, startIndex, paused, target, style, x, y, z, radius, speed, clockwise, AutoShots.Shot.RETREAT);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    public static AutonomyPlan mission(Mission mission, int index) {
        return new AutonomyPlan(Kind.MISSION, Optional.of(mission), index, false, Optional.empty(), FollowController.Style.BEHIND, 0.0, 0.0, 0.0,
                0.0, 0.0, true);
    }

    public static AutonomyPlan pausedMission(Mission mission, int index) {
        return new AutonomyPlan(Kind.MISSION, Optional.of(mission), index, true, Optional.empty(), FollowController.Style.BEHIND, 0.0, 0.0, 0.0,
                0.0, 0.0, true);
    }

    public static AutonomyPlan hold() {
        return simple(Kind.HOLD);
    }

    public static AutonomyPlan land() {
        return simple(Kind.LAND);
    }

    public static AutonomyPlan home() {
        return simple(Kind.HOME);
    }

    private static AutonomyPlan simple(Kind kind) {
        return new AutonomyPlan(kind, Optional.empty(), 0, false, Optional.empty(), FollowController.Style.BEHIND, 0.0, 0.0, 0.0, 0.0, 0.0, true);
    }

    public static AutonomyPlan follow(UUID target, FollowController.Style style) {
        return new AutonomyPlan(Kind.FOLLOW, Optional.empty(), 0, false, Optional.of(target), style, 0.0, 0.0, 0.0, 0.0, 0.0, true);
    }

    public static AutonomyPlan orbit(double x, double y, double z, double radius, double speed, boolean clockwise) {
        return new AutonomyPlan(Kind.ORBIT, Optional.empty(), 0, false, Optional.empty(), FollowController.Style.BEHIND, x, y, z, radius, speed,
                clockwise);
    }

    public static AutonomyPlan autoShot(AutoShots.Shot shot, double subjectX, double subjectY, double subjectZ) {
        return new AutonomyPlan(Kind.AUTO_SHOT, Optional.empty(), 0, false, Optional.empty(), FollowController.Style.BEHIND, subjectX, subjectY,
                subjectZ, 0.0, 0.0, true, shot);
    }

    public static AutonomyPlan autoShotAhead(AutoShots.Shot shot, double x, double z, double heading, double subjectGroundY) {
        return autoShot(shot, x + Compass.forwardX(heading) * AutoShots.SUBJECT_DISTANCE, subjectGroundY,
                z + Compass.forwardZ(heading) * AutoShots.SUBJECT_DISTANCE);
    }

    public AutonomyPlan withStartIndex(int index) {
        return new AutonomyPlan(this.kind, this.mission, index, this.paused, this.target, this.style, this.x, this.y, this.z, this.radius,
                this.speed, this.clockwise, this.shot);
    }

    public AutonomyPlan withPaused(boolean value) {
        return new AutonomyPlan(this.kind, this.mission, this.startIndex, value, this.target, this.style, this.x, this.y, this.z, this.radius,
                this.speed, this.clockwise, this.shot);
    }

    public Identifier id() {
        return this.kind.id();
    }

    public String label() {
        return this.kind.label();
    }

    public AutonomyStatus startedStatus() {
        return this.kind == Kind.MISSION && this.paused ? AutonomyStatus.MISSION_PAUSED : AutonomyStatus.started(this.kind);
    }
}
