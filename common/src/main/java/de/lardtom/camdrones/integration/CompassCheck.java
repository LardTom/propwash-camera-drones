package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.propwash.api.flight.FlightState;

public final class CompassCheck {
    private static volatile double testDisturbance;

    private CompassCheck() {
    }

    public static double errorDeg(FlightState state) {
        double error = state.compassErrorDeg();
        double extra = testDisturbance;
        if (extra == 0.0) {
            return Double.isFinite(error) ? error : 0.0;
        }
        return Compass.wrap180((Double.isFinite(error) ? error : 0.0) + extra);
    }

    public static boolean healthy(FlightState state) {
        if (testDisturbance == 0.0) {
            return state.compassHealthy();
        }
        return Math.abs(errorDeg(state)) < FlightState.COMPASS_DISTURBED_DEG;
    }

    public static void testDisturbance(double degrees) {
        testDisturbance = Double.isFinite(degrees) ? degrees : 0.0;
    }

    public static double testDisturbance() {
        return testDisturbance;
    }
}
