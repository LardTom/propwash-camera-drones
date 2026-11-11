package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.propwash.api.flight.SensorDirection;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavAssistGroundRayTest {
    private static final double DT = 0.01;
    private static final double TILT = 35.0;
    private static final double HEIGHT = 6.0;

    private static double command(FlightClass flightClass, double forward) {
        FakeFlight.State state = new FakeFlight.State().at(0.0, 66.0, 0.0);
        state.heightAboveGround = HEIGHT;
        state.noseDown = TILT;
        state.obstacle(SensorDirection.FORWARD, forward);
        state.obstacle(SensorDirection.DOWN, HEIGHT / Math.cos(Math.toRadians(TILT)));
        state.stickPitch = 1.0;
        NavAssist assist = new NavAssist(flightClass, EnumSet.allOf(SensorDirection.class), true, NavContext.NONE);
        for (int i = 0; i < 50; i++) {
            state.step(DT);
            assist.update(state, new FakeFlight.Output());
        }
        return Math.hypot(assist.control().commandX(), assist.control().commandZ());
    }

    @Test
    void theGroundSeenByTheTiltedForwardSensorIsNoObstacle() {
        double ground = HEIGHT / Math.sin(Math.toRadians(TILT));
        double level = Math.cos(Math.toRadians(TILT));
        double free = ObstacleGuard.commandLimit(Double.POSITIVE_INFINITY, ObstacleGuard.SENSOR_RANGE * level);
        double cine = command(FlightClass.CINE, ground);
        assertEquals(free, cine, 1.0E-6, "Kino im Modus N bei 35° Neigung in 6 m Höhe");
        assertTrue(cine > ObstacleGuard.commandLimit(ground * level) + 1.0, "Boden wie ein Hindernis behandelt: " + cine + " m/s");
        double tour = command(FlightClass.TOUR, ground);
        assertEquals(Math.min(FlightClass.TOUR.normalSpeed(), free), tour, 1.0E-6, "Tour");
    }

    @Test
    void aWallInFrontOfTheGroundStillSlowsTheDrone() {
        double wall = 5.0;
        double cine = command(FlightClass.CINE, wall);
        double horizontal = wall * Math.cos(Math.toRadians(TILT));
        assertTrue(cine <= ObstacleGuard.SLOW_GAIN * (horizontal - ObstacleGuard.STOP_DISTANCE) + 1.0E-9, "vor der Wand " + cine + " m/s");
    }
}
