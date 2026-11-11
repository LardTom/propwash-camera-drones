package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.HeightLimit;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeightLimitTest {
    private static final double HOME_Y = 64.0;

    @Test
    void fullThrottleStopsAtTheCeiling() {
        NavLogic logic = new NavLogic(FlightClass.CINE);
        logic.heightLimit(new HeightLimit(0, HOME_Y, 0, HeightLimit.DEFAULT_MAX_ALTITUDE, 0));
        PointMass drone = new PointMass(FlightClass.CINE).at(0, HOME_Y + 100, 0, 0);
        drone.sticks(0, 0, 0, 1.0);
        double[] top = {0.0};
        boolean[] warned = {false};
        drone.run(logic::update, 20.0, d -> {
            top[0] = Math.max(top[0], d.y);
            warned[0] |= d.out.has(NavOutput.Warning.ALTITUDE_LIMIT);
        });
        assertTrue(top[0] <= HOME_Y + 120.0 + 0.3, "über der Grenze: " + (top[0] - HOME_Y));
        assertTrue(top[0] >= HOME_Y + 119.0, "erreicht die Grenze nicht: " + (top[0] - HOME_Y));
        assertTrue(warned[0]);
        drone.sticks(0, 0, 0, 0.0);
        drone.run(logic::update, 3.0);
        assertTrue(drone.y < HOME_Y + 110.0, "sinkt nicht");
    }

    @Test
    void zeroMeansOff() {
        HeightLimit off = new HeightLimit(0, HOME_Y, 0, 0, 0);
        assertFalse(off.active());
        assertEquals(Double.POSITIVE_INFINITY, off.ceiling(), 0.0);
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.heightLimit(off);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, HOME_Y + 115, 0, 0);
        drone.sticks(0, 0, 0, 1.0);
        drone.run(logic::update, 5.0);
        assertTrue(drone.y > HOME_Y + 125.0);
        assertFalse(drone.out.has(NavOutput.Warning.ALTITUDE_LIMIT));
    }

    @Test
    void aboveTheCeilingNoClimbButNoForcedDescent() {
        HeightLimit limit = new HeightLimit(0, HOME_Y, 0, 50, 0);
        NavInput in = new NavInput().position(0, HOME_Y + 60, 0);
        NavOutput out = new NavOutput();
        out.climb(3.0);
        limit.apply(in, out);
        assertEquals(0.0, out.climbRate(), 1.0E-12);
        assertTrue(out.has(NavOutput.Warning.ALTITUDE_LIMIT));
        out.reset();
        out.holdAltitude(HOME_Y + 80);
        limit.apply(in, out);
        assertEquals(HOME_Y + 60, out.altitudeTarget(), 1.0E-12);
        out.reset();
        out.climb(-2.0);
        limit.apply(in, out);
        assertEquals(-2.0, out.climbRate(), 1.0E-12);
        out.reset();
        in.position(0, HOME_Y + 10, 0);
        out.holdAltitude(HOME_Y + 80);
        limit.apply(in, out);
        assertEquals(HOME_Y + 50, out.altitudeTarget(), 1.0E-12);
    }

    @Test
    void missionAboveTheCeilingFliesAtTheCeiling() {
        Mission mission = Mission.of(List.of(Waypoint.at(0, HOME_Y + 140, -40), Waypoint.at(40, HOME_Y + 140, -40)));
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.heightLimit(new HeightLimit(0, HOME_Y, 0, HeightLimit.DEFAULT_MAX_ALTITUDE, 0));
        logic.startMission(mission, 0);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, HOME_Y + 110, 0, 0);
        double[] top = {0.0};
        drone.run(logic::update, 40.0, d -> top[0] = Math.max(top[0], d.y));
        assertTrue(top[0] <= HOME_Y + 120.3, "Mission über der Grenze: " + (top[0] - HOME_Y));
    }

    @Test
    void distanceLimitStopsOutwardFlight() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.heightLimit(new HeightLimit(0, HOME_Y, 0, 0, 100));
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, HOME_Y + 20, -60, 0);
        drone.sticks(0, 1.0, 0, 0.5);
        double[] far = {0.0};
        boolean[] warned = {false};
        drone.run(logic::update, 30.0, d -> {
            far[0] = Math.max(far[0], Math.hypot(d.x, d.z));
            warned[0] |= d.out.has(NavOutput.Warning.DISTANCE_LIMIT);
        });
        assertTrue(far[0] <= 101.0, "zu weit: " + far[0]);
        assertTrue(far[0] >= 98.5, "bleibt zu früh stehen: " + far[0]);
        assertTrue(warned[0]);
        drone.sticks(0, -1.0, 0, 0.5);
        drone.run(logic::update, 3.0);
        assertTrue(Math.hypot(drone.x, drone.z) < 95.0, "kommt nicht zurück");
        NavOutput out = new NavOutput();
        out.holdPosition(0, -150);
        new HeightLimit(0, HOME_Y, 0, 0, 100).apply(new NavInput().position(0, HOME_Y, -90), out);
        assertEquals(-100.0, out.targetZ(), 1.0E-9);
    }
}
