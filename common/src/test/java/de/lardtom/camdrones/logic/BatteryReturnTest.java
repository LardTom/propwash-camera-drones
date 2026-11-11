package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatteryReturnTest {
    static final double LOW = 0.15;
    static final double CRITICAL = 0.05;
    static final double FULL = 0.5;

    private static boolean warnsBattery(NavOutput out) {
        return out.warnings().stream().anyMatch(w -> w.name().contains("BATTERY"));
    }

    private static int[] fly(NavLogic logic, PointMass drone, double seconds) {
        int[] seen = {0, 0, 0};
        drone.run(logic::update, seconds, d -> {
            seen[0] += d.out.returnHome() ? 1 : 0;
            seen[1] += warnsBattery(d.out) ? 1 : 0;
            seen[2] += d.out.landing() ? 1 : 0;
        });
        return seen;
    }

    @Test
    void lowBatteryInTheAirAsksForTheReturnOnceAndKeepsWarning() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0).sticks(0.0, 0.0, 0.0, 0.5);
        drone.in.battery(LOW);
        int[] seen = fly(logic, drone, 10.0);
        assertEquals(1, seen[0], "Rückkehr bei 15 % angefordert");
        assertEquals(1000, seen[1], "Warnung in jedem Schritt");
        assertEquals(0, seen[2], "bei 15 % noch keine Landung");
    }

    @Test
    void onTheGroundItOnlyWarnsAndAsksAfterTakeOff() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 0, 0, 0).sticks(0.0, 0.0, 0.0, 0.5);
        drone.in.battery(LOW);
        int[] ground = fly(logic, drone, 2.0);
        assertEquals(0, ground[0], "am Boden keine Rückkehr");
        assertTrue(ground[1] > 0, "am Boden aber die Warnung");
        drone.sticks(0.0, 0.0, 0.0, 1.0);
        int[] climb = fly(logic, drone, 3.0);
        assertTrue(drone.y > 2.0, "steigt: " + drone.y);
        assertEquals(1, climb[0], "nach dem Abheben genau eine Rückkehr");
    }

    @Test
    void anotherBatteryArmsTheReturnAgain() {
        NavLogic logic = new NavLogic(FlightClass.COMPACT);
        PointMass drone = new PointMass(FlightClass.COMPACT).at(0, 30, 0, 0).sticks(0.0, 0.0, 0.0, 0.5);
        drone.in.battery(LOW);
        assertEquals(1, fly(logic, drone, 2.0)[0]);
        drone.in.battery(FULL);
        int[] full = fly(logic, drone, 2.0);
        assertEquals(0, full[0]);
        assertEquals(0, full[1], "voller Akku warnt nicht");
        drone.in.battery(LOW);
        assertEquals(1, fly(logic, drone, 2.0)[0], "neuer Akku, wieder knapp: neue Rückkehr");
    }

    @Test
    void flightsWithoutPilotLeaveTheBatteryToPropwash() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 30, 0, 0);
        drone.in.battery(CRITICAL);
        int[] seen = fly(logic, drone, 5.0);
        assertEquals(0, seen[0]);
        assertEquals(0, seen[2]);
        assertEquals(30.0, drone.y, 0.3, "hält die Höhe");
    }

    @Test
    void criticalBatteryLandsWhereItIsEvenWithThrottleUp() {
        for (FlightClass c : FlightClass.values()) {
            NavLogic logic = new NavLogic(c);
            PointMass drone = new PointMass(c).at(0, 20, 0, 0).sticks(0.0, 0.0, 0.0, 1.0);
            drone.in.battery(CRITICAL);
            double[] fastest = {0.0, Double.NEGATIVE_INFINITY};
            String[] label = {null};
            drone.run(logic::update, 20.0, d -> {
                fastest[0] = Math.max(fastest[0], -d.vy);
                fastest[1] = Math.max(fastest[1], d.y);
                label[0] = d.out.label();
            });
            assertTrue(drone.y < 0.05, c + ": nicht gelandet, Höhe " + drone.y);
            assertTrue(fastest[0] <= c.descendSpeed() + 0.3, c + ": sinkt mit " + fastest[0] + " m/s");
            assertTrue(fastest[0] > 1.0, c + ": sinkt zu langsam (" + fastest[0] + " m/s)");
            assertTrue(fastest[1] <= 20.05, c + ": steigt trotz kritischem Akku auf " + fastest[1] + " m");
            assertEquals("LAND", label[0], c.toString());
        }
    }

    @Test
    void criticalBatteryPausesAMissionAndStillMovesWithTheSticks() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0, 20, 0, 0).sticks(0.0, 0.0, 0.0, 0.5);
        logic.startMission(Mission.of(List.of(Waypoint.at(80.0, 20.0, 0.0), Waypoint.at(80.0, 20.0, 80.0))), 0);
        drone.in.battery(CRITICAL);
        int[] seen = fly(logic, drone, 1.0);
        assertTrue(seen[2] > 0, "landet");
        assertEquals(NavLogic.MissionState.PAUSED, logic.missionState());
        assertNotEquals(NavLogic.Function.MISSION, logic.function());
        double x = drone.x;
        drone.sticks(0.0, 0.0, 0.0, 0.5);
        fly(logic, drone, 2.0);
        assertTrue(Math.abs(drone.x - x) < 2.0, "fliegt nicht weiter zum Wegpunkt: " + (drone.x - x) + " m");
        drone.sticks(0.0, 1.0, 0.0, 0.5);
        double z = drone.z;
        double xBefore = drone.x;
        fly(logic, drone, 2.0);
        assertTrue(Math.hypot(drone.x - xBefore, drone.z - z) > 2.0, "lässt sich beim Landen waagrecht steuern");
        assertFalse(logic.function() == NavLogic.Function.MISSION);
    }
}
