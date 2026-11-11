package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.AttiMode;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttiFallbackTest {
    @Test
    void withoutGpsTheDroneHoldsOnlyTheHeightAndDriftsWithTheWind() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0).wind(4.0, 0.0, 0.0);
        drone.sticks(0, 0, 0, 0.5);
        drone.run(logic::update, 10.0);
        assertEquals("N", drone.out.label());
        double heldX = drone.x;
        drone.gps = false;
        double[] height = {0.0};
        drone.run(logic::update, 10.0, d -> {
            assertEquals(AttiMode.LABEL, d.out.label());
            assertTrue(d.out.has(NavOutput.Warning.NO_GPS));
            assertTrue(d.out.hasSticks());
            assertEquals(NavOutput.Horizontal.NONE, d.out.horizontal());
            height[0] = Math.max(height[0], Math.abs(d.y - 30.0));
        });
        assertTrue(logic.atti());
        assertTrue(drone.x - heldX > 10.0, "driftet nicht mit dem Wind: " + (drone.x - heldX));
        assertTrue(height[0] < 0.5, "Höhe nicht gehalten: " + height[0]);
    }

    @Test
    void fixMustBeStableBeforeLeavingAtti() {
        NavLogic logic = new NavLogic(FlightClass.COMPACT);
        PointMass drone = new PointMass(FlightClass.COMPACT).at(0, 30, 0, 0).wind(3.0, 0.0, 0.0);
        drone.sticks(0, 0, 0, 0.5);
        drone.gps = false;
        drone.run(logic::update, 3.0);
        drone.gps = true;
        drone.run(logic::update, 1.0);
        assertEquals(AttiMode.LABEL, drone.out.label());
        drone.gps = false;
        drone.run(logic::update, 0.5);
        drone.gps = true;
        drone.run(logic::update, AttiMode.REGAIN_SECONDS - 0.1);
        assertEquals(AttiMode.LABEL, drone.out.label());
        drone.run(logic::update, 0.2);
        assertEquals("N", drone.out.label());
        drone.run(logic::update, 10.0);
        double x = drone.x;
        drone.run(logic::update, 10.0);
        assertTrue(Math.abs(drone.x - x) < 0.3, "hält nach GPS-Rückkehr nicht");
    }

    @Test
    void tooFewSatellitesCountAsNoFix() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0);
        drone.satellites = AttiMode.MIN_SATELLITES - 1;
        drone.run(logic::update, 0.1);
        assertTrue(logic.atti());
        assertEquals(AttiMode.LABEL, drone.out.label());
    }

    @Test
    void missionPausesInAttiAndHoldsWhenTheFixReturns() {
        Mission mission = Mission.of(List.of(Waypoint.at(0, 30, -100), Waypoint.at(50, 30, -100)));
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.startMission(mission, 0);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0).wind(2.0, 0.0, 0.0);
        drone.run(logic::update, 5.0);
        assertEquals(NavLogic.MissionState.RUNNING, logic.missionState());
        drone.gps = false;
        drone.run(logic::update, 5.0);
        assertEquals(NavLogic.MissionState.PAUSED, logic.missionState());
        assertTrue(drone.out.has(NavOutput.Warning.PAUSED));
        assertEquals(0, logic.missionIndex());
        drone.gps = true;
        drone.run(logic::update, 12.0);
        assertFalse(logic.atti());
        assertEquals(NavLogic.Function.HOLD, logic.function());
        assertEquals("HOLD", drone.out.label());
        assertTrue(drone.speed() < 0.3);
        assertTrue(logic.resumeMission());
        drone.run(logic::update, 60.0);
        assertEquals(NavLogic.MissionState.DONE, logic.missionState());
    }

    @Test
    void sticksInAttiTiltGently() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0);
        drone.gps = false;
        drone.sticks(1.0, -1.0, 0.5, 0.5);
        drone.run(logic::update, 0.1);
        assertEquals(AttiMode.MAX_ANGLE / 60.0, drone.out.stickRoll(), 1.0E-9);
        assertEquals(-AttiMode.MAX_ANGLE / 60.0, drone.out.stickPitch(), 1.0E-9);
        assertTrue(drone.out.yawRateTarget() > 0.0);
    }

    @Test
    void disturbedCompassSwitchesToAttiWithItsOwnWarning() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0).wind(3.0, 0.0, 0.0);
        drone.sticks(0, 0, 0, 0.5);
        drone.run(logic::update, 10.0);
        assertEquals("N", drone.out.label());
        double heldX = drone.x;
        drone.compassError = 40.0;
        double[] height = {0.0};
        drone.run(logic::update, 8.0, d -> {
            assertEquals(AttiMode.LABEL, d.out.label());
            assertTrue(d.out.has(NavOutput.Warning.COMPASS));
            assertFalse(d.out.has(NavOutput.Warning.NO_GPS));
            assertTrue(d.out.hasSticks());
            height[0] = Math.max(height[0], Math.abs(d.y - 30.0));
        });
        assertTrue(logic.atti());
        assertEquals(NavOutput.Warning.COMPASS, logic.attiCause());
        assertTrue(drone.x - heldX > 5.0, "driftet bei Kompassfehler nicht mit dem Wind: " + (drone.x - heldX));
        assertTrue(height[0] < 0.5, "Höhe nicht gehalten: " + height[0]);
    }

    @Test
    void compassBelowTheLimitKeepsGps() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0);
        drone.compassError = 14.5;
        drone.run(logic::update, 3.0);
        assertFalse(logic.atti());
        assertFalse(drone.out.has(NavOutput.Warning.COMPASS));
        drone.compassError = -15.5;
        drone.run(logic::update, 0.1);
        assertTrue(logic.atti());
        assertTrue(drone.out.has(NavOutput.Warning.COMPASS));
    }

    @Test
    void compassMustStayHealthyBeforeLeavingAtti() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0).wind(2.0, 0.0, 0.0);
        drone.sticks(0, 0, 0, 0.5);
        drone.compassError = 30.0;
        drone.run(logic::update, 2.0);
        drone.compassError = 3.0;
        drone.run(logic::update, AttiMode.REGAIN_SECONDS - 0.1);
        assertEquals(AttiMode.LABEL, drone.out.label());
        assertTrue(drone.out.has(NavOutput.Warning.COMPASS), "Grund der ATTI-Zeit fehlt bis zur Rückkehr");
        drone.run(logic::update, 0.2);
        assertEquals("N", drone.out.label());
        assertFalse(drone.out.has(NavOutput.Warning.COMPASS));
        drone.run(logic::update, 8.0);
        double x = drone.x;
        drone.run(logic::update, 8.0);
        assertTrue(Math.abs(drone.x - x) < 0.3, "hält nach Rückkehr des Kompasses nicht");
    }

    @Test
    void gpsAndCompassBothLostWarnBoth() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0);
        drone.gps = false;
        drone.compassError = 90.0;
        drone.run(logic::update, 0.5);
        assertTrue(drone.out.has(NavOutput.Warning.NO_GPS));
        assertTrue(drone.out.has(NavOutput.Warning.COMPASS));
        assertEquals(NavOutput.Warning.NO_GPS, logic.attiCause());
    }
}
