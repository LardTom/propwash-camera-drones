package de.lardtom.camdrones.planner.model;

import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;

import java.util.List;
import java.util.OptionalInt;

public final class WaypointPlacement {
    public static final double MARKER_RADIUS = 0.75;
    public static final double MARKER_REACH = 64.0;
    public static final double MAX_Y = MissionLimits.MAX_Y_DM / 10.0;
    public static final double MIN_Y = MissionLimits.MIN_Y_DM / 10.0;

    private WaypointPlacement() {
    }

    public static Waypoint aboveBlock(int blockX, double topY, int blockZ, int groundHeight) {
        double y = clampY(topY + TabletState.clampGroundHeight(groundHeight));
        return Waypoint.at(blockX + 0.5, y, blockZ + 0.5);
    }

    public static Waypoint inFlight(double x, double y, double z, double headingDegrees, double gimbalTilt) {
        int tilt = (int) Math.round(Double.isFinite(gimbalTilt) ? gimbalTilt : 0.0);
        tilt = Math.clamp(tilt, MissionLimits.MIN_TILT, MissionLimits.MAX_TILT);
        double heading = Double.isFinite(headingDegrees) ? headingDegrees : 0.0;
        return Waypoint.at(x, clampY(y), z).withHeading(Waypoint.Heading.FIXED, heading).withTilt(tilt);
    }

    public static double flightTilt(double gimbalTilt, double commandedTilt) {
        if (Double.isFinite(gimbalTilt)) {
            return gimbalTilt;
        }
        return Double.isFinite(commandedTilt) ? commandedTilt : 0.0;
    }

    public static Waypoint recorded(double x, double y, double z) {
        return Waypoint.at(x, clampY(y), z);
    }

    public static double clampY(double y) {
        return Math.clamp(y, MIN_Y, MAX_Y);
    }

    public static int clampTilt(int tilt, FlightClass flightClass) {
        int max = flightClass == null ? MissionLimits.MAX_TILT : (int) Math.round(flightClass.gimbalMaxTilt());
        return Math.clamp(tilt, MissionLimits.MIN_TILT, Math.min(max, MissionLimits.MAX_TILT));
    }

    public static OptionalInt pick(List<Waypoint> waypoints, double eyeX, double eyeY, double eyeZ, double dirX, double dirY, double dirZ,
                                   double reach, double radius) {
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (!(length > 1.0E-9) || waypoints == null) {
            return OptionalInt.empty();
        }
        double ux = dirX / length;
        double uy = dirY / length;
        double uz = dirZ / length;
        int best = -1;
        double bestT = Double.POSITIVE_INFINITY;
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint w = waypoints.get(i);
            double ox = w.x() - eyeX;
            double oy = w.y() - eyeY;
            double oz = w.z() - eyeZ;
            double t = ox * ux + oy * uy + oz * uz;
            if (t < 0.0 || t > reach) {
                continue;
            }
            double cx = ox - t * ux;
            double cy = oy - t * uy;
            double cz = oz - t * uz;
            if (cx * cx + cy * cy + cz * cz <= radius * radius && t < bestT) {
                bestT = t;
                best = i;
            }
        }
        return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
    }
}
