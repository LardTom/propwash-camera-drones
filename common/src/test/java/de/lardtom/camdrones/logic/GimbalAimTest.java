package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.gimbal.GimbalAim;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.OrbitController;
import de.lardtom.camdrones.logic.nav.WaypointFollower;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GimbalAimTest {
    @Test
    void tiltAndPanTowardsAPoint() {
        GimbalAim.Aim ahead = GimbalAim.aim(FlightClass.TOUR, 0, 20, 0, 0, 0, 0, -20);
        assertEquals(-45.0, ahead.tilt(), 1.0E-9);
        assertEquals(0.0, ahead.pan(), 1.0E-9);
        assertEquals(0.0, ahead.yaw(), 1.0E-9);
        assertTrue(ahead.reachable());
        GimbalAim.Aim right = GimbalAim.aim(FlightClass.CINE, 0, 20, 0, 0, 30, 20, 0);
        assertEquals(0.0, right.tilt(), 1.0E-9);
        assertEquals(90.0, right.pan(), 1.0E-9);
        assertEquals(90.0, right.yaw(), 1.0E-9);
        assertTrue(right.reachable() && !right.needsYaw());
        GimbalAim.Aim compactRight = GimbalAim.aim(FlightClass.COMPACT, 0, 20, 0, 0, 30, 20, 0);
        assertEquals(0.0, compactRight.pan(), 1.0E-9);
        assertTrue(compactRight.needsYaw() && !compactRight.reachable());
        GimbalAim.Aim behind = GimbalAim.aim(FlightClass.CINE, 0, 20, 0, 90, -10, 10, 0);
        assertEquals(180.0, Math.abs(behind.pan()), 1.0E-9);
        assertEquals(270.0, behind.yaw(), 1.0E-9);
    }

    @Test
    void tiltLimitsPerClass() {
        GimbalAim.Aim down = GimbalAim.aim(FlightClass.TOUR, 5, 30, 5, 123, 5, 0, 5);
        assertEquals(-90.0, down.tilt(), 1.0E-9);
        assertTrue(down.reachable());
        GimbalAim.Aim upCompact = GimbalAim.aim(FlightClass.COMPACT, 0, 0, 0, 0, 0, 10, -10);
        assertEquals(30.0, upCompact.tilt(), 1.0E-9);
        assertFalse(upCompact.reachable());
        GimbalAim.Aim upCine = GimbalAim.aim(FlightClass.CINE, 0, 0, 0, 0, 0, 10, -10);
        assertEquals(45.0, upCine.tilt(), 1.0E-9);
        assertTrue(upCine.reachable());
        assertEquals(60.0, GimbalAim.clampTilt(FlightClass.CINE, 80.0), 1.0E-9);
        assertEquals(-90.0, GimbalAim.clampTilt(FlightClass.COMPACT, -120.0), 1.0E-9);
        assertEquals(90.0, GimbalAim.tiltTo(0, 5, 0), 1.0E-9);
    }

    @Test
    void smoothingAndRecenter() {
        assertEquals(9.0, GimbalAim.approach(0.0, 45.0, 90.0, 0.1), 1.0E-9);
        assertEquals(45.0, GimbalAim.approach(44.0, 45.0, 90.0, 0.1), 1.0E-9);
        assertEquals(-45.0, GimbalAim.interpolate(0.0, -90.0, 0.5), 1.0E-9);
        assertEquals(-90.0, GimbalAim.interpolate(0.0, -90.0, 2.0), 1.0E-9);
        assertEquals(0.0, GimbalAim.recenter(-35.0), 1.0E-9);
        assertEquals(-90.0, GimbalAim.recenter(0.0), 1.0E-9);
    }

    @Test
    void orbitFollowAndMissionAimAtTheirTargets() {
        PointMass orbitDrone = new PointMass(FlightClass.TOUR).at(0, 40, -20, 180);
        NavLogic orbit = new NavLogic(FlightClass.TOUR);
        orbit.orbit(OrbitController.around(FlightClass.TOUR, orbitDrone.input(), 0, 20, 0, 0, 4.0, true));
        orbitDrone.run(orbit::update, 30.0);
        GimbalAim.Aim poi = GimbalAim.aim(FlightClass.TOUR, orbitDrone.x, orbitDrone.y, orbitDrone.z, orbitDrone.heading,
                orbitDrone.out.gimbalX(), orbitDrone.out.gimbalY(), orbitDrone.out.gimbalZ());
        assertEquals(-45.0, poi.tilt(), 1.5);
        assertEquals(0.0, poi.pan(), 1.0E-9);
        assertTrue(Math.abs(Compass.wrap180(poi.yaw() - orbitDrone.heading)) < 3.0);
        PointMass followDrone = new PointMass(FlightClass.CINE).at(0, 69, 8, 0);
        followDrone.target(0, 64, 0, 0, 0, -3.0);
        followDrone.input();
        NavLogic follow = new NavLogic(FlightClass.CINE);
        follow.follow(FollowController.Style.BEHIND);
        followDrone.run(follow::update, 20.0);
        assertEquals(followDrone.targetZ, followDrone.out.gimbalZ(), 0.05);
        GimbalAim.Aim target = GimbalAim.aim(FlightClass.CINE, followDrone.x, followDrone.y, followDrone.z, followDrone.heading,
                followDrone.out.gimbalX(), followDrone.out.gimbalY(), followDrone.out.gimbalZ());
        assertEquals(Math.toDegrees(Math.atan2(-5.0, 8.0)), target.tilt(), 2.0);
        assertTrue(Math.abs(target.pan()) < 5.0);
        Mission mission = Mission.of(List.of(Waypoint.at(0, 30, -30).facing(0).withWait(3)), List.of(PointOfInterest.at(0, 0, -60, 0)));
        assertEquals(-45.0, WaypointFollower.tiltAt(mission, 0, 0, 30, -30), 1.0E-9);
    }
}
