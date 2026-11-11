package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.HorizontalControl;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.camdrones.logic.nav.SportMode;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.SensorDirection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavAssistAngleLimitTest {
    static final double[] LIMITS = {10.0, 20.0, 30.0, 45.0, 60.0, 85.0};

    private static NavAssist assist(NavLogic.StickMode mode) {
        NavAssist assist = new NavAssist(FlightClass.CINE, EnumSet.allOf(SensorDirection.class), true, NavContext.NONE);
        assist.logic().stickMode(mode);
        return assist;
    }

    @Test
    void theAssistFliesWithTheAngleLimitOfTheFlightState() {
        for (double limit : new double[] {10.0, 30.0, 45.0, 60.0, 85.0}) {
            FakeFlight.State state = new FakeFlight.State();
            state.angleLimit = limit;
            NavAssist assist = assist(NavLogic.StickMode.NORMAL);
            assist.update(state, new FakeFlight.Output());
            assertEquals(limit, assist.input().angleLimit(), 1.0E-12, "Winkelgrenze " + limit + "°");
        }
        NavAssist assist = assist(NavLogic.StickMode.NORMAL);
        assist.update(new FakeFlight.State(), new FakeFlight.Output());
        assertEquals(FlightState.DEFAULT_ANGLE_LIMIT_DEG, assist.input().angleLimit(), 1.0E-12, "ohne Tune");
    }

    @Test
    void sportFliesItsOwnTiltOrTheSmallerAngleLimit() {
        for (double limit : new double[] {20.0, 30.0, 45.0, 60.0, 85.0}) {
            FakeFlight.State state = new FakeFlight.State().flies(NavLogic.StickMode.SPORT);
            state.angleLimit = limit;
            state.stickPitch = 1.0;
            FakeFlight.Output out = new FakeFlight.Output();
            assist(NavLogic.StickMode.SPORT).update(state, out);
            assertTrue(out.sticks, "Sport gibt Knüppel aus");
            assertEquals(Math.min(SportMode.MAX_ANGLE, limit), Math.clamp(out.pitch, -1.0, 1.0) * limit, 1.0E-6, "Winkelgrenze " + limit + "°");
        }
    }

    @Test
    void theAssistCommandsTheSmallerTiltOfItsOwnLimitAndTheAngleLimit() {
        for (double limit : new double[] {10.0, 20.0, 30.0, 45.0, 60.0, 85.0}) {
            FakeFlight.State state = new FakeFlight.State();
            state.angleLimit = limit;
            state.heightAboveGround = 30.0;
            state.stickPitch = 1.0;
            NavAssist assist = assist(NavLogic.StickMode.NORMAL);
            double commanded = 0.0;
            for (int i = 0; i < 20; i++) {
                FakeFlight.Output out = new FakeFlight.Output();
                assist.update(state, out);
                assertTrue(out.sticks, "Winkelgrenze " + limit + "°: keine Knüppel");
                commanded = Math.max(commanded, Math.abs(out.pitch) * limit);
                state.step(0.05);
            }
            assertEquals(Math.min(HorizontalControl.MAX_TILT, limit), commanded, 1.0E-6, "Winkelgrenze " + limit + "°");
        }
    }

    @Test
    void theFlownTiltReachesTheSmallerOfItsOwnLimitAndTheAngleLimit() {
        List<String> problems = new ArrayList<>();
        List<String> all = new ArrayList<>();
        for (double limit : LIMITS) {
            NavAssistWallStopTest.Flight flight = new NavAssistWallStopTest.Flight(FlightClass.CINE, NavLogic.StickMode.NORMAL, 30.0, 1000.0)
                    .angleLimit(limit);
            double tilt = 0.0;
            for (int i = 0; i < 800; i++) {
                flight.step();
                tilt = Math.max(tilt, Math.abs(flight.tilt));
            }
            double allowed = Math.min(HorizontalControl.MAX_TILT, limit);
            String line = String.format(Locale.ROOT, "%.0f°: neigt höchstens %.2f° von %.0f°", limit, tilt, allowed);
            all.add(line);
            if (tilt < allowed - 3.0 || tilt > allowed + 3.0) {
                problems.add(line);
            }
        }
        assertTrue(problems.isEmpty(), "Neigung: " + problems + " (alle: " + all + ")");
    }

    @Test
    void fullStickTowardsAWallStopsAtLeastTheStopDistanceAwayWithEveryAngleLimit() {
        List<String> problems = new ArrayList<>();
        List<String> all = new ArrayList<>();
        for (double limit : LIMITS) {
            for (FlightClass flightClass : List.of(FlightClass.TOUR, FlightClass.CINE)) {
                for (NavLogic.StickMode mode : List.of(NavLogic.StickMode.NORMAL, NavLogic.StickMode.CINE)) {
                    for (double height : new double[] {1.5, 3.0, 6.0, 10.0}) {
                        NavAssistWallStopTest.Flight flight = new NavAssistWallStopTest.Flight(flightClass, mode, height, NavAssistWallStopTest.WALL)
                                .angleLimit(limit).fly(20.0);
                        String line = String.format(Locale.ROOT, "%.0f° %s %s in %.1f m: nächster Abstand %.2f m, Spitzentempo %.2f m/s, Tempo am Ende %.2f m/s%s",
                                limit, flightClass.key(), mode, height, flight.closest, flight.peak, flight.speed, flight.crashed ? ", Aufprall" : "");
                        all.add(line);
                        if (flight.crashed || flight.closest < ObstacleGuard.STOP_DISTANCE || Math.abs(flight.speed) > 0.3) {
                            problems.add(line);
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "zu nah an der Wand: " + problems + " (alle: " + all + ")");
    }

    @Test
    void withAChangingHeightTheDroneStillStopsInFrontOfTheWallWithEveryAngleLimit() {
        List<String> problems = new ArrayList<>();
        List<String> all = new ArrayList<>();
        for (double limit : LIMITS) {
            for (FlightClass flightClass : List.of(FlightClass.TOUR, FlightClass.CINE)) {
                for (double height : new double[] {3.0, 6.0, 10.0, NavAssistWallTopTest.WALL_TOP - 1.0}) {
                    for (boolean probes : new boolean[] {true, false}) {
                        NavAssistWallTopTest.Flight flight = new NavAssistWallTopTest.Flight(flightClass, NavLogic.StickMode.NORMAL, height, probes)
                                .angleLimit(limit).fly(20.0);
                        String line = String.format(Locale.ROOT, "%.0f° %s N in %.1f m%s: nächster Abstand %.2f m, Spitzentempo %.2f m/s, Tempo am Ende %.2f m/s, "
                                        + "gestiegen %.2f m%s%s", limit, flightClass.key(), height, probes ? "" : " ohne Strahlen der Welt", flight.closest,
                                flight.peak, flight.speed, flight.rise(), flight.crashed ? ", Aufprall" : "", flight.over ? ", über die Wand" : "");
                        all.add(line);
                        if (flight.crashed || flight.over || flight.closest < ObstacleGuard.STOP_DISTANCE || Math.abs(flight.speed) > 0.3) {
                            problems.add(line);
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "zu nah an der Wand: " + problems + " (alle: " + all + ")");
    }
}
