package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.propwash.api.drone.AutonomyOptions;

import java.time.Duration;
import java.util.OptionalDouble;

public final class FlightOptions {
    public static final double HOLD_TAKEOFF = 5.0;
    public static final double ORBIT_TAKEOFF = 10.0;
    public static final double MISSION_FALLBACK_TAKEOFF = 10.0;

    private FlightOptions() {
    }

    public static OptionalDouble takeoff(boolean airborne, AutonomyPlan plan, double droneY) {
        if (airborne || plan.kind() == AutonomyPlan.Kind.LAND) {
            return OptionalDouble.empty();
        }
        double metres = switch (plan.kind()) {
            case MISSION -> plan.mission().map(mission -> missionTakeoff(mission, plan.startIndex(), droneY)).orElse(MISSION_FALLBACK_TAKEOFF);
            case FOLLOW -> FollowController.HEIGHT;
            case ORBIT -> ORBIT_TAKEOFF;
            default -> HOLD_TAKEOFF;
        };
        return OptionalDouble.of(clamp(metres));
    }

    static double missionTakeoff(Mission mission, int index, double droneY) {
        if (mission.isEmpty() || !Double.isFinite(droneY)) {
            return MISSION_FALLBACK_TAKEOFF;
        }
        double height = mission.waypoint(Math.clamp(index, 0, mission.size() - 1)).y() - droneY;
        return Double.isFinite(height) ? height : MISSION_FALLBACK_TAKEOFF;
    }

    public static double clamp(double metres) {
        return Math.clamp(metres, AutonomyOptions.MIN_TAKEOFF_METRES, AutonomyOptions.MAX_TAKEOFF_METRES);
    }

    public static AutonomyOptions of(AutonomyPlan plan, OptionalDouble takeoff) {
        return of(plan, takeoff, CamWorldRules.DEFAULT_AUTONOMY_MINUTES);
    }

    public static AutonomyOptions of(AutonomyPlan plan, OptionalDouble takeoff, int maxMinutes) {
        AutonomyOptions options = AutonomyOptions.of(plan.id()).label(plan.label()).maxDuration(maxDuration(maxMinutes));
        return takeoff.isPresent() ? options.takeoff(clamp(takeoff.getAsDouble())) : options;
    }

    public static Duration maxDuration(int minutes) {
        return Duration.ofMinutes(Math.clamp(minutes, CamWorldRules.MIN_AUTONOMY_MINUTES, CamWorldRules.MAX_AUTONOMY_MINUTES));
    }
}
