package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.estimate.MissionClock;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionTimerReplayTest {
    private static final double TICK = 0.05;
    private static final double TOLERANCE = 1.0;
    private static final long SETTLE_TICKS = 10L;
    private static final double CLIMB_TOLERANCE = 1.3;
    private static final double UNEXPECTED_CLIMB_TOLERANCE = 1.8;
    private static final double MOVED = 0.25;
    private static final long AFTER_CLIMB_TICKS = 30L;

    record Flight(long arrival, Mission mission, List<double[]> points) {
    }

    static Flight load(String name, boolean smooth, Mission.End end) throws IOException {
        long arrival = -1L;
        List<Waypoint> waypoints = new ArrayList<>();
        List<double[]> points = new ArrayList<>();
        try (InputStream in = Objects.requireNonNull(MissionTimerReplayTest.class.getResourceAsStream("/mission-timer/" + name), name);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] c = line.split(",");
                switch (c[0]) {
                    case "a" -> arrival = Long.parseLong(c[1]);
                    case "w" -> {
                        Waypoint w = Waypoint.at(Double.parseDouble(c[1]), Double.parseDouble(c[2]), Double.parseDouble(c[3]))
                                .withSpeed(Double.parseDouble(c[4]));
                        double wait = Double.parseDouble(c[5]);
                        waypoints.add(wait > 0.0 ? w.withWait(wait) : w);
                    }
                    case "p" -> points.add(new double[]{Double.parseDouble(c[1]), Double.parseDouble(c[2]), Double.parseDouble(c[3]),
                            Double.parseDouble(c[4]), Double.parseDouble(c[5])});
                    default -> throw new IllegalStateException("Zeile " + line);
                }
            }
        }
        return new Flight(arrival, Mission.of(waypoints).withSmooth(smooth).withEnd(end), points);
    }

    static void replay(String name, FlightClass flightClass, boolean smooth, Mission.End end) throws IOException {
        replay(name, flightClass, smooth, end, TOLERANCE);
    }

    static long climbEnd(Flight flight) {
        double[] start = flight.points().getFirst();
        for (double[] p : flight.points()) {
            if (p[1] > 0 || Math.hypot(p[2] - start[2], p[4] - start[4]) > MOVED) {
                return (long) p[0];
            }
        }
        return flight.arrival();
    }

    static void replay(String name, FlightClass flightClass, boolean smooth, Mission.End end, double climbTolerance) throws IOException {
        Flight flight = load(name, smooth, end);
        long settled = climbEnd(flight) + AFTER_CLIMB_TICKS;
        MissionClock clock = new MissionClock();
        double worstClimb = 0.0;
        double worst = 0.0;
        double previous = Double.NaN;
        long previousTick = 0L;
        for (int k = 0; k < flight.points().size(); k++) {
            double[] p = flight.points().get(k);
            long tick = (long) p[0];
            double shown = clock.update(flightClass, flight.mission(), NavLogic.MissionState.RUNNING, (int) p[1], tick * TICK, p[2], p[3], p[4], k == 0);
            assertTrue(Double.isFinite(shown), name + ": kein Timer bei Tick " + tick);
            if (!Double.isNaN(previous)) {
                assertTrue(shown <= previous + 1.0E-9, name + ": steigt bei Tick " + tick + " von " + previous + " auf " + shown);
                assertTrue(previous - shown <= MissionClock.MAX_RATE * Math.max(0L, tick - previousTick) * TICK + 1.0E-9 || shown == 0.0,
                        name + ": springt bei Tick " + tick + " von " + previous + " auf " + shown);
            }
            previous = shown;
            previousTick = tick;
            if (tick < flight.arrival()) {
                double deviation = shown - (flight.arrival() - tick) * TICK;
                if (tick < settled) {
                    worstClimb = Math.abs(deviation) > Math.abs(worstClimb) ? deviation : worstClimb;
                } else {
                    worst = Math.abs(deviation) > Math.abs(worst) ? deviation : worst;
                }
            } else {
                assertTrue(shown < 1.0, name + ": nach der Ankunft " + shown + " s statt 0:00 bei Tick " + tick);
                if (tick >= flight.arrival() + SETTLE_TICKS) {
                    assertEquals(0.0, shown, name + ": nach der Ankunft bei Tick " + tick);
                }
            }
        }
        assertTrue(Math.abs(worstClimb) <= climbTolerance, String.format(Locale.ROOT, "%s: weicht bis 1,5 s nach dem Aufstieg um %+.2f s von der echten Restzeit ab",
                name, worstClimb));
        assertTrue(Math.abs(worst) <= TOLERANCE, String.format(Locale.ROOT, "%s: weicht um %+.2f s von der echten Restzeit ab", name, worst));
    }

    @Test
    void cineSharpLegsEndingAtTheStartFollowTheRealFlight() throws IOException {
        replay("cine-sharp-start.csv", FlightClass.CINE, false, Mission.End.START);
    }

    @Test
    void tourSmoothLegsFollowTheRealFlight() throws IOException {
        replay("tour-smooth-hover.csv", FlightClass.TOUR, true, Mission.End.HOVER);
    }

    @Test
    void cineSharpElevenPointsEndingAtTheStartFollowTheRealFlight() throws IOException {
        replay("cine-sharp-eleven-start.csv", FlightClass.CINE, false, Mission.End.START);
    }

    @Test
    void cineSharpElevenPointsFollowTheRealFlightOnNeoForge() throws IOException {
        replay("cine-sharp-eleven-start-neoforge.csv", FlightClass.CINE, false, Mission.End.START);
    }

    @Test
    void compactSmoothLegsEndingWithLandingFollowTheRealFlight() throws IOException {
        replay("compact-smooth-land.csv", FlightClass.COMPACT, true, Mission.End.LAND);
    }

    @Test
    void clientTimeStepsBackWithoutRestartingTheTimer() throws IOException {
        replay("compact-smooth-land-time-back.csv", FlightClass.COMPACT, true, Mission.End.LAND);
    }

    @Test
    void cineSmoothFastLegsFollowTheRealFlight() throws IOException {
        replay("cine-smooth-hover.csv", FlightClass.CINE, true, Mission.End.HOVER);
    }

    @Test
    void cineSmoothFastLegsFollowTheRealFlightOnNeoForge() throws IOException {
        replay("cine-smooth-hover-neoforge.csv", FlightClass.CINE, true, Mission.End.HOVER);
    }

    @Test
    void cineSharpLegsFollowTheRealFlightOnNeoForge() throws IOException {
        replay("cine-sharp-start-neoforge.csv", FlightClass.CINE, false, Mission.End.START);
    }

    @Test
    void tourSmoothLegsFollowTheRealFlightOnNeoForge() throws IOException {
        replay("tour-smooth-hover-neoforge.csv", FlightClass.TOUR, true, Mission.End.HOVER);
    }

    @Test
    void compactClimbOfTwentyMetresFromTheGroundFollowsTheRealFlight() throws IOException {
        replay("compact-smooth-climb20-start.csv", FlightClass.COMPACT, true, Mission.End.START);
    }

    @Test
    void compactClimbOfTwentyFiveMetresFromTheGroundFollowsTheRealFlightOnNeoForge() throws IOException {
        replay("compact-smooth-climb25-land-neoforge.csv", FlightClass.COMPACT, true, Mission.End.LAND);
    }

    @Test
    void tourClimbOfTwentyFourMetresFromTheGroundFollowsTheRealFlightOnNeoForge() throws IOException {
        replay("tour-smooth-climb24-land-neoforge.csv", FlightClass.TOUR, true, Mission.End.LAND);
    }

    @Test
    void tourClimbOfThirtyMetresFromTheGroundFollowsTheRealFlight() throws IOException {
        replay("tour-sharp-climb30-hover.csv", FlightClass.TOUR, false, Mission.End.HOVER);
    }

    @Test
    void tourClimbOfThirtyMetresFromTheGroundFollowsTheRealFlightOnNeoForge() throws IOException {
        replay("tour-sharp-climb30-hover-neoforge.csv", FlightClass.TOUR, false, Mission.End.HOVER);
    }

    @Test
    void compactClimbOfSixteenMetresFromTheGroundFollowsTheRealFlight() throws IOException {
        replay("compact-sharp-climb16-hover.csv", FlightClass.COMPACT, false, Mission.End.HOVER);
    }

    @Test
    void tourClimbOfEighteenMetresFromTheGroundFollowsTheRealFlightOnNeoForge() throws IOException {
        replay("tour-sharp-climb18-start-neoforge.csv", FlightClass.TOUR, false, Mission.End.START);
    }

    @Test
    void compactSharpClimbOfTwentyMetresEndingAtTheFirstPassFollowsTheRealFlight() throws IOException {
        replay("compact-sharp-climb20-hover.csv", FlightClass.COMPACT, false, Mission.End.HOVER);
    }

    @Test
    void compactSmoothClimbOfTwentyOneMetresFollowsTheRealFlight() throws IOException {
        replay("compact-smooth-climb21-land.csv", FlightClass.COMPACT, true, Mission.End.LAND, CLIMB_TOLERANCE);
    }

    @Test
    void compactSmoothClimbOfTwentyOneMetresFollowsTheRealFlightOnNeoForge() throws IOException {
        replay("compact-smooth-climb21-land-neoforge.csv", FlightClass.COMPACT, true, Mission.End.LAND, UNEXPECTED_CLIMB_TOLERANCE);
    }

    @Test
    void compactSharpClimbOfTwentyMetresStaysCloseWhicheverWayTheClimbEnds() throws IOException {
        replay("compact-sharp-climb20-hover-second.csv", FlightClass.COMPACT, false, Mission.End.HOVER, CLIMB_TOLERANCE);
        replay("compact-sharp-climb20-hover-neoforge.csv", FlightClass.COMPACT, false, Mission.End.HOVER, CLIMB_TOLERANCE);
    }

    @Test
    void compactSmoothClimbOfTwentyMetresWithSecondPassStaysCloseOnNeoForge() throws IOException {
        replay("compact-smooth-climb20-land-neoforge.csv", FlightClass.COMPACT, true, Mission.End.LAND, CLIMB_TOLERANCE);
    }

    @Test
    void compactClimbOfTwentyFiveMetresEndingAtTheFirstPassCatchesUpRightAfterTheClimb() throws IOException {
        replay("compact-smooth-climb25-land-first-pass.csv", FlightClass.COMPACT, true, Mission.End.LAND, UNEXPECTED_CLIMB_TOLERANCE);
    }
}
