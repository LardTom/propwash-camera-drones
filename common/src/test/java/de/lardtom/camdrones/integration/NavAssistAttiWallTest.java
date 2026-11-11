package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NavAssistAttiWallTest {
    private static final double STAND = 5.0;
    private static final double PUSH = 15.0;

    private static boolean warns(NavAssistWallTopTest.Flight flight, NavOutput.Warning warning) {
        return flight.assist.status().map(s -> s.warns(warning)).orElse(false);
    }

    private static NavAssistWallTopTest.Flight stand(FlightClass flightClass, double height) {
        NavAssistWallTopTest.Flight flight = new NavAssistWallTopTest.Flight(flightClass, NavLogic.StickMode.NORMAL, height, true).fly(20.0 + STAND);
        assertTrue(!flight.crashed && !flight.over && Math.abs(flight.speed) < 0.3, flightClass.key() + " " + height + " m steht nicht vor der Wand");
        return flight;
    }

    @Test
    void fullStickAfterTheGpsDropsInFrontOfAHighWallDoesNotHitIt() {
        List<String> problems = new ArrayList<>();
        List<String> all = new ArrayList<>();
        for (FlightClass flightClass : List.of(FlightClass.TOUR, FlightClass.CINE)) {
            for (double height : new double[] {1.0, 3.0}) {
                NavAssistWallTopTest.Flight flight = stand(flightClass, height);
                double before = flight.gap();
                flight.state.gpsFix = false;
                flight.closest = before;
                boolean warned = true;
                int steps = (int) Math.round(PUSH / NavAssistWallTopTest.DT);
                for (int i = 0; i < steps && !flight.crashed; i++) {
                    flight.step(1.0);
                    if (i > 10) {
                        warned &= flight.assist.logic().atti() && warns(flight, NavOutput.Warning.OBSTACLE_OFF) && warns(flight, NavOutput.Warning.OBSTACLE_STOP);
                    }
                }
                String line = String.format(Locale.ROOT, "%s %.1f m: beim GPS-Verlust %.2f m vor der Wand, danach nächster Abstand %.2f m, nach %.0f s Vollgas %.2f m%s%s",
                        flightClass.key(), height, before, flight.closest, PUSH, flight.gap(), flight.crashed ? ", Aufprall" : "",
                        warned ? "" : ", Warnungen " + flight.assist.status().map(NavStatus::warnings).orElse(null));
                all.add(line);
                if (flight.crashed || flight.closest < ObstacleGuard.STOP_DISTANCE || !warned) {
                    problems.add(line);
                }
            }
        }
        assertTrue(problems.isEmpty(), "Vollgas gegen die Wand nach GPS-Verlust: " + problems + " (alle: " + all + ")");
    }

    @Test
    void whenTheGpsDropsOnTheWayTowardsTheWallTheDroneStillStopsInFrontOfIt() {
        List<String> problems = new ArrayList<>();
        for (FlightClass flightClass : List.of(FlightClass.TOUR, FlightClass.CINE)) {
            for (double at : new double[] {12.0, 8.0, 5.0}) {
                NavAssistWallTopTest.Flight flight = new NavAssistWallTopTest.Flight(flightClass, NavLogic.StickMode.NORMAL, 3.0, true);
                while (flight.gap() > at && !flight.crashed && flight.state.time < 30.0) {
                    flight.step(1.0);
                }
                double speed = flight.speed;
                flight.state.gpsFix = false;
                flight.fly(PUSH);
                String line = String.format(Locale.ROOT, "%s GPS-Verlust %.1f m vor der Wand mit %.2f m/s: nächster Abstand %.2f m, am Ende %.2f m, %.2f m/s%s",
                        flightClass.key(), at, speed, flight.closest, flight.gap(), flight.speed, flight.crashed ? ", Aufprall" : "");
                if (flight.crashed || flight.closest < 1.0 || Math.abs(flight.speed) > 0.3) {
                    problems.add(line);
                }
            }
        }
        assertTrue(problems.isEmpty(), "GPS-Verlust im Anflug: " + problems);
    }

    @Test
    void afterTheStickWasCentredAttiFliesWithoutObstacleBrake() {
        NavAssistWallTopTest.Flight flight = stand(FlightClass.CINE, 1.0);
        flight.state.gpsFix = false;
        for (int i = 0; i < 300; i++) {
            flight.step(1.0);
        }
        for (int i = 0; i < 50; i++) {
            flight.step(0.0);
        }
        assertTrue(flight.assist.logic().atti());
        assertTrue(!warns(flight, NavOutput.Warning.OBSTACLE_STOP), "nach der Mitte kein Halt mehr");
        double before = flight.gap();
        flight.fly(3.0);
        assertTrue(flight.crashed || flight.gap() < before - 0.5, "freies ATTI: " + before + " -> " + flight.gap());
    }
}
