package de.lardtom.camdrones.planner.model;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionCodec;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.Waypoint;

import java.util.Locale;
import java.util.Optional;

public final class UploadCheck {
    public static final double RATE_PER_SECOND = 2.0;
    public static final int NO_WAYPOINT = -1;

    public enum Result {
        OK,
        RATE_LIMITED,
        INVALID,
        UNKNOWN_VERSION,
        EMPTY,
        TOO_LARGE,
        NOT_LINKED,
        NOT_FOUND,
        NOT_OWNER,
        ALTITUDE,
        DISTANCE,
        UNAVAILABLE;

        public boolean ok() {
            return this == OK;
        }

        public String messageKey() {
            return FieldText.PREFIX + "upload." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    public record Verdict(Result result, int waypoint, Optional<Mission> mission) {
        public static Verdict of(Result result) {
            return new Verdict(result, NO_WAYPOINT, Optional.empty());
        }
    }

    public record Drone(boolean found, boolean ownedBySelf, boolean pilotedByOther, boolean pilotedBySelf, double x, double y, double z) {
        public static final Drone MISSING = new Drone(false, false, false, false, 0.0, 0.0, 0.0);
    }

    public record Rules(int maxAltitude, int maxDistance) {
        public Rules {
            maxAltitude = Math.max(0, maxAltitude);
            maxDistance = Math.max(0, maxDistance);
        }
    }

    private UploadCheck() {
    }

    public static Verdict mission(int[] packed) {
        if (packed == null || packed.length == 0) {
            return Verdict.of(Result.EMPTY);
        }
        if (packed.length > PlannerEntry.MAX_INTS) {
            return Verdict.of(Result.TOO_LARGE);
        }
        MissionCodec.Result decoded = MissionCodec.decode(packed);
        if (decoded.status() == MissionCodec.Status.UNKNOWN_VERSION) {
            return Verdict.of(Result.UNKNOWN_VERSION);
        }
        if (!decoded.ok() || decoded.mission().isEmpty()) {
            return Verdict.of(decoded.status() == MissionCodec.Status.EMPTY ? Result.EMPTY : Result.INVALID);
        }
        Mission mission = decoded.mission().get();
        if (mission.isEmpty()) {
            return Verdict.of(Result.EMPTY);
        }
        if (!MissionLimits.fits(mission.size(), mission.pointsOfInterest().size())) {
            return Verdict.of(Result.TOO_LARGE);
        }
        return new Verdict(Result.OK, NO_WAYPOINT, Optional.of(mission));
    }

    public static boolean owner(boolean operator, Drone drone) {
        return operator || drone.pilotedBySelf() || drone.ownedBySelf() && !drone.pilotedByOther();
    }

    public static Verdict check(boolean withinRate, int[] packed, boolean linked, Drone drone, boolean operator, Rules rules) {
        if (!withinRate) {
            return Verdict.of(Result.RATE_LIMITED);
        }
        Verdict decoded = mission(packed);
        if (!decoded.result().ok()) {
            return decoded;
        }
        if (!linked) {
            return Verdict.of(Result.NOT_LINKED);
        }
        if (!drone.found()) {
            return Verdict.of(Result.NOT_FOUND);
        }
        if (!owner(operator, drone)) {
            return Verdict.of(Result.NOT_OWNER);
        }
        Mission mission = decoded.mission().orElseThrow();
        int index = 0;
        for (Waypoint w : mission.waypoints()) {
            if (rules.maxAltitude() > 0 && w.y() - drone.y() > rules.maxAltitude()) {
                return new Verdict(Result.ALTITUDE, index, Optional.empty());
            }
            if (rules.maxDistance() > 0 && Math.hypot(w.x() - drone.x(), w.z() - drone.z()) > rules.maxDistance()) {
                return new Verdict(Result.DISTANCE, index, Optional.empty());
            }
            index++;
        }
        return decoded;
    }
}
