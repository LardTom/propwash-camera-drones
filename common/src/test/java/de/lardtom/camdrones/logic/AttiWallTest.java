package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.nav.AttiMode;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttiWallTest {
    private static final double FACE = -20.0;
    private static final double HEIGHT = 3.0;

    private static boolean sensingOff(NavOutput out) {
        return out.has(NavOutput.Warning.OBSTACLE_OFF);
    }

    private static PointMass towardsTheWall(FlightClass flightClass, NavLogic logic) {
        PointMass drone = new PointMass(flightClass).at(0, HEIGHT, 0, 0);
        drone.boxes.add(new PointMass.Box(-30, 0, FACE - 1.0, 30, 40, FACE));
        drone.sticks(0, 1.0, 0, 0.5);
        drone.run(logic::update, 20.0);
        assertFalse(drone.crashed);
        assertTrue(drone.speed() < 0.3, "steht nicht vor der Wand: " + drone.speed());
        return drone;
    }

    private static double gap(PointMass drone) {
        return drone.z - FACE;
    }

    @Test
    void whenTheGpsDropsWhileTheStickStillPointsAtTheWallTheDroneStaysPut() {
        List<String> problems = new ArrayList<>();
        for (FlightClass flightClass : List.of(FlightClass.TOUR, FlightClass.CINE)) {
            for (double wind : new double[] {0.0, 3.0}) {
                NavLogic logic = new NavLogic(flightClass);
                PointMass drone = towardsTheWall(flightClass, logic);
                double before = gap(drone);
                drone.wind(0.0, -wind, 0.0);
                drone.gps = false;
                double[] closest = {before};
                boolean[] warned = {true};
                drone.run(logic::update, 15.0, d -> {
                    closest[0] = Math.min(closest[0], gap(d));
                    warned[0] &= AttiMode.LABEL.equals(d.out.label()) && sensingOff(d.out) && d.out.has(NavOutput.Warning.OBSTACLE_STOP);
                });
                String line = String.format(Locale.ROOT, "%s Wind %.0f m/s zur Wand: beim GPS-Verlust %.2f m, nächster Abstand %.2f m, am Ende %.2f m%s%s",
                        flightClass.key(), wind, before, closest[0], gap(drone), drone.crashed ? ", Aufprall" : "",
                        warned[0] ? "" : ", Warnungen " + drone.out.warnings());
                if (drone.crashed || closest[0] < ObstacleGuard.STOP_DISTANCE - 0.3 || before - closest[0] > 0.5 || !warned[0]) {
                    problems.add(line);
                }
            }
        }
        assertTrue(problems.isEmpty(), "ATTI vor der Wand mit Knüppel zur Wand: " + problems);
    }

    @Test
    void onceTheStickReturnsToTheCentreAttiFliesFreely() {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        PointMass drone = towardsTheWall(FlightClass.TOUR, logic);
        drone.gps = false;
        drone.run(logic::update, 3.0);
        drone.sticks(0, 0, 0, 0.5);
        drone.run(logic::update, 0.5);
        assertTrue(logic.atti());
        assertTrue(sensingOff(drone.out), "ATTI ohne Hinderniserkennung meldet die Warnung: " + drone.out.warnings());
        assertFalse(drone.out.has(NavOutput.Warning.OBSTACLE_STOP), "nach der Mitte kein Halt mehr");
        double before = gap(drone);
        drone.sticks(0, 1.0, 0, 0.5);
        drone.run(logic::update, 3.0);
        assertTrue(drone.crashed || gap(drone) < before - 0.5, "freies ATTI fliegt zur Wand: " + before + " -> " + gap(drone));
    }

    @Test
    void aStickAwayFromTheWallAtTheMomentOfTheLossLeavesAttiFree() {
        NavLogic logic = new NavLogic(FlightClass.CINE);
        PointMass drone = towardsTheWall(FlightClass.CINE, logic);
        drone.sticks(0, 0, 0, 0.5);
        drone.run(logic::update, 3.0);
        drone.gps = false;
        drone.run(logic::update, 0.5);
        double before = gap(drone);
        drone.sticks(0, 1.0, 0, 0.5);
        drone.run(logic::update, 3.0);
        assertFalse(drone.out.has(NavOutput.Warning.OBSTACLE_STOP));
        assertTrue(drone.crashed || gap(drone) < before - 0.5, "ohne Knüppel zur Wand beim Verlust gilt freies ATTI: " + before + " -> " + gap(drone));
    }

    @Test
    void theWarningNeedsObstacleSensorsInUse() {
        NavLogic compact = new NavLogic(FlightClass.COMPACT);
        PointMass small = new PointMass(FlightClass.COMPACT).at(0, 10, 0, 0);
        small.gps = false;
        small.run(compact::update, 0.5);
        assertTrue(compact.atti());
        assertFalse(sensingOff(small.out), "die Kompakt-Drohne hat keine Hinderniserkennung: " + small.out.warnings());

        NavLogic tour = new NavLogic(FlightClass.TOUR);
        PointMass open = new PointMass(FlightClass.TOUR).at(0, 10, 0, 0);
        open.sticks(0, 0, 0, 0.5);
        open.gps = false;
        open.run(tour::update, 0.5);
        assertTrue(sensingOff(open.out), "ATTI mit Sensoren meldet die Warnung: " + open.out.warnings());
        assertFalse(open.out.has(NavOutput.Warning.OBSTACLE_STOP));
        open.gps = true;
        open.run(tour::update, AttiMode.REGAIN_SECONDS + 0.5);
        assertFalse(tour.atti());
        assertFalse(sensingOff(open.out));
        assertEquals("N", open.out.label());
    }
}
