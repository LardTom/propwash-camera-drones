package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.estimate.MissionClock;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.WaypointFollower;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionTimerTest {
    private static final double TICK = 0.05;
    private static final double MAX_STEP = 1.0;
    private static final double ARRIVAL_STEP = 2.5;
    private static final double MAX_PER_SECOND = 2.0;
    private static final double SHARE = 0.15;
    private static final double SLACK = 2.5;

    interface Timer {
        double seconds(FlightClass flightClass, Mission mission, NavLogic.MissionState state, int index, long tick, double x, double y, double z,
                       boolean grounded);
    }

    static Timer timer() {
        MissionClock clock = new MissionClock();
        return (flightClass, mission, state, index, tick, x, y, z, grounded) -> clock.update(flightClass, mission, state, index, tick * TICK, x, y, z,
                grounded);
    }

    record Sample(long tick, NavLogic.MissionState state, double seconds) {
    }

    record Run(List<Sample> samples, long arrival, long pausedFrom, long pausedTo) {
        double actual(Sample s) {
            long end = this.arrival;
            if (this.pausedFrom >= 0 && this.pausedTo >= 0 && s.tick() < this.pausedTo) {
                end -= this.pausedTo - Math.max(s.tick(), this.pausedFrom);
            }
            return Math.max(0.0, (end - s.tick()) * TICK);
        }
    }

    static Mission varied(Mission.End end) {
        List<Waypoint> list = new ArrayList<>();
        list.add(Waypoint.at(0.0, 24.0, -6.0).withSpeed(3.0));
        list.add(Waypoint.at(0.0, 24.0, -46.0).withSpeed(10.0));
        list.add(Waypoint.at(5.0, 27.0, -46.0).withSpeed(2.0).withWait(2.0));
        list.add(Waypoint.at(30.0, 22.0, -30.0).withSpeed(6.0));
        list.add(Waypoint.at(30.0, 22.0, -27.0));
        list.add(Waypoint.at(55.0, 25.0, 0.0).withSpeed(12.0));
        return Mission.of(list).withEnd(end).withBaseSpeed(5.0);
    }

    static Mission zigzag() {
        List<Waypoint> list = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            list.add(Waypoint.at(7.0 * (i + 1) + (i % 3) * 4.0, 28.0 + (i % 3) * 2.0, i % 2 == 0 ? -10.0 : 10.0).withSpeed(3.0 + (i % 4) * 2.5));
        }
        return Mission.of(list).withSmooth(true).withEnd(Mission.End.HOVER).withBaseSpeed(6.0);
    }

    static Run fly(FlightClass flightClass, Mission mission, double startY, double windX, double windZ, double gust, double pauseAt, double pauseFor,
                   Timer timer) {
        NavLogic logic = new NavLogic(flightClass);
        logic.startMission(mission, 0);
        PointMass drone = new PointMass(flightClass).at(0.0, startY, 0.0, 0.0).wind(windX, windZ, gust);
        int last = mission.size() - 1;
        List<Sample> samples = new ArrayList<>();
        long[] clock = {-1L, Long.MIN_VALUE, -1L, -1L};
        drone.run(logic::update, 240.0, d -> {
            long now = (long) Math.floor(d.time / TICK + 1.0E-6);
            if (now == clock[0]) {
                return;
            }
            clock[0] = now;
            if (pauseAt >= 0.0 && clock[2] < 0 && now * TICK >= pauseAt) {
                logic.pauseMission();
                clock[2] = now;
            } else if (clock[2] >= 0 && clock[3] < 0 && (now - clock[2]) * TICK >= pauseFor) {
                assertTrue(logic.resumeMission());
                clock[3] = now;
            }
            WaypointFollower follower = logic.follower();
            WaypointFollower.Phase phase = follower == null ? null : follower.phase();
            if (clock[1] == Long.MIN_VALUE && follower != null && (follower.index() == last && phase == WaypointFollower.Phase.WAIT
                    || phase == WaypointFollower.Phase.RETURN || phase == WaypointFollower.Phase.LAND || phase == WaypointFollower.Phase.DONE)) {
                clock[1] = now;
            }
            NavLogic.MissionState state = logic.missionState();
            double seconds = timer.seconds(flightClass, mission, state, logic.missionIndex(), now, d.x, d.y, d.z, d.grounded());
            samples.add(new Sample(now, state, seconds));
        });
        assertTrue(clock[1] != Long.MIN_VALUE, "letzter Wegpunkt nicht erreicht");
        return new Run(samples, clock[1], clock[2], clock[3]);
    }

    static String check(String name, Run run) {
        List<String> problems = new ArrayList<>();
        double worst = 0.0;
        double worstAt = 0.0;
        Sample previous = null;
        List<Sample> window = new ArrayList<>();
        for (Sample s : run.samples()) {
            boolean active = s.state() == NavLogic.MissionState.RUNNING || s.state() == NavLogic.MissionState.PAUSED;
            if (!active) {
                previous = null;
                continue;
            }
            if (!Double.isFinite(s.seconds())) {
                problems.add("kein Timer bei " + fmt(s.tick() * TICK));
                break;
            }
            boolean paused = run.pausedFrom() >= 0 && s.tick() >= run.pausedFrom() && (run.pausedTo() < 0 || s.tick() < run.pausedTo());
            double actual = run.actual(s);
            if (s.tick() < run.arrival()) {
                double deviation = s.seconds() - actual;
                if (Math.abs(deviation) > SLACK + SHARE * actual && Math.abs(deviation) - SHARE * actual > Math.abs(worst) - SHARE * worstAt) {
                    worst = deviation;
                    worstAt = actual;
                }
            } else if (s.tick() > run.arrival() && s.seconds() != 0.0) {
                problems.add(String.format(Locale.ROOT, "nach der Ankunft %s s statt 0:00", fmt(s.seconds())));
                break;
            }
            if (previous != null) {
                double drop = previous.seconds() - s.seconds();
                if (drop < -1.0E-9) {
                    problems.add(String.format(Locale.ROOT, "steigt bei %s s von %s auf %s s", fmt(s.tick() * TICK), fmt(previous.seconds()), fmt(s.seconds())));
                    break;
                }
                if (drop > (s.seconds() == 0.0 ? ARRIVAL_STEP : MAX_STEP)) {
                    problems.add(String.format(Locale.ROOT, "springt bei %s s um %s s", fmt(s.tick() * TICK), fmt(drop)));
                    break;
                }
                if (paused && drop > 0.0 && s.tick() > run.pausedFrom()) {
                    problems.add("läuft in der Pause weiter bei " + fmt(s.tick() * TICK));
                    break;
                }
            }
            window.add(s);
            while (window.size() > 1 && s.tick() - window.getFirst().tick() > Math.round(1.0 / TICK)) {
                window.removeFirst();
            }
            Sample secondAgo = window.getFirst();
            if (s.tick() - secondAgo.tick() == Math.round(1.0 / TICK) && s.seconds() > 0.0 && secondAgo.seconds() - s.seconds() > MAX_PER_SECOND + 1.0E-9) {
                problems.add(String.format(Locale.ROOT, "zählt bei %s s %s s in einer Sekunde", fmt(s.tick() * TICK), fmt(secondAgo.seconds() - s.seconds())));
                break;
            }
            previous = s;
        }
        if (worst != 0.0) {
            problems.add(String.format(Locale.ROOT, "weicht bei %s s Restzeit um %+.2f s ab (Grenze 2,5 s + 15 %%)", fmt(worstAt), worst));
        }
        return problems.isEmpty() ? "" : name + ": " + String.join("; ", problems);
    }

    static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    static void verify(String name, FlightClass flightClass, Mission mission, double startY, double windX, double windZ, double gust, double pauseAt,
                       double pauseFor) {
        Run run = fly(flightClass, mission, startY, windX, windZ, gust, pauseAt, pauseFor, timer());
        assertEquals("", check(name, run));
    }

    @Test
    void sharpLegsOfDifferentLengthAndSpeedEndingAtTheStart() {
        verify("Tour, eckig, zum Start", FlightClass.TOUR, varied(Mission.End.START), 20.0, 0.0, 0.0, 0.0, -1.0, 0.0);
    }

    @Test
    void sharpLegsInWind() {
        verify("Tour, eckig, Wind", FlightClass.TOUR, varied(Mission.End.START), 20.0, 3.0, 1.5, 1.0, -1.0, 0.0);
    }

    @Test
    void cineFromTheGroundWithLanding() {
        verify("Kino, vom Boden, landen", FlightClass.CINE, varied(Mission.End.LAND), 0.0, 0.0, 0.0, 0.0, -1.0, 0.0);
    }

    @Test
    void smoothZigzagWithChangingSpeeds() {
        verify("Tour, weich, Zickzack", FlightClass.TOUR, zigzag(), 20.0, 0.0, 0.0, 0.0, -1.0, 0.0);
    }

    @Test
    void compactSmoothInWind() {
        verify("Compact, weich, Wind", FlightClass.COMPACT, zigzag(), 20.0, -2.0, 2.0, 1.0, -1.0, 0.0);
    }

    @Test
    void pauseInFlightHoldsTheTimer() {
        verify("Tour, Pause 5 s", FlightClass.TOUR, varied(Mission.End.START), 20.0, 0.0, 0.0, 0.0, 15.0, 5.0);
    }

    private static final Mission LINE = Mission.of(List.of(Waypoint.at(0.0, 20.0, -20.0), Waypoint.at(0.0, 20.0, -40.0).withWait(4.0),
            Waypoint.at(0.0, 20.0, -60.0))).withEnd(Mission.End.START).withBaseSpeed(5.0);

    @Test
    void showsZeroFromTheLastWaypointThroughTheFlightBack() {
        MissionClock clock = new MissionClock();
        double before = clock.update(FlightClass.TOUR, LINE, NavLogic.MissionState.RUNNING, 2, 0.0, 0.0, 20.0, -50.0, false);
        assertTrue(before > 0.0 && before < 10.0, "Restzeit 10 m vor dem Ziel: " + before);
        assertEquals(0.0, clock.update(FlightClass.TOUR, LINE, NavLogic.MissionState.RUNNING, 2, 4.0, 0.0, 20.0, -59.7, false));
        for (int i = 1; i <= 100; i++) {
            assertEquals(0.0, clock.update(FlightClass.TOUR, LINE, NavLogic.MissionState.RUNNING, 2, 4.0 + i * TICK, 0.0, 20.0, -59.7 + i * 0.4, false),
                    "Rückflug nach " + i + " Ticks");
        }
    }

    @Test
    void waitingCountsDownInRealSeconds() {
        MissionClock clock = new MissionClock();
        double first = clock.update(FlightClass.TOUR, LINE, NavLogic.MissionState.RUNNING, 1, 0.0, 0.0, 20.0, -40.0, false);
        for (int i = 1; i <= 60; i++) {
            double now = clock.update(FlightClass.TOUR, LINE, NavLogic.MissionState.RUNNING, 1, i * TICK, 0.0, 20.0, -40.0, false);
            assertEquals(first - i * TICK, now, 1.0E-9, "Warten nach " + i + " Ticks");
        }
    }

    @Test
    void pauseHoldsTheTimer() {
        MissionClock clock = new MissionClock();
        double running = clock.update(FlightClass.TOUR, LINE, NavLogic.MissionState.RUNNING, 0, 0.0, 0.0, 20.0, -5.0, false);
        for (int i = 1; i <= 100; i++) {
            assertEquals(running, clock.update(FlightClass.TOUR, LINE, NavLogic.MissionState.PAUSED, 0, i * TICK, 0.0, 20.0, -5.0, false), 1.0E-9);
        }
    }

    @Test
    void correctionsAreGentleAndNeverCountUp() {
        MissionClock clock = new MissionClock();
        double previous = clock.update(FlightClass.TOUR, LINE, NavLogic.MissionState.RUNNING, 0, 0.0, 0.0, 20.0, -10.0, false);
        double z = -10.0;
        for (int i = 1; i <= 200; i++) {
            if (i == 20) {
                z = 15.0;
            }
            double now = clock.update(FlightClass.TOUR, LINE, NavLogic.MissionState.RUNNING, 0, i * TICK, 0.0, 20.0, z, false);
            assertTrue(now <= previous + 1.0E-9, "steigt nach " + i + " Ticks: " + previous + " auf " + now);
            assertTrue(previous - now <= MissionClock.MAX_RATE * TICK + 1.0E-9, "springt nach " + i + " Ticks: " + previous + " auf " + now);
            previous = now;
        }
    }

    @Test
    void newPlanStartsFromTheNewEstimate() {
        MissionClock clock = new MissionClock();
        double first = clock.update(FlightClass.TOUR, LINE, NavLogic.MissionState.RUNNING, 0, 0.0, 0.0, 20.0, -5.0, false);
        Mission shorter = LINE.withWaypoints(LINE.waypoints().subList(0, 1));
        double second = clock.update(FlightClass.TOUR, shorter, NavLogic.MissionState.RUNNING, 0, TICK, 0.0, 20.0, -5.0, false);
        assertTrue(second < first - 5.0, first + " auf " + second);
    }

    @Test
    void noTimerForLoopsIdleOrDoneMissions() {
        MissionClock clock = new MissionClock();
        assertTrue(Double.isNaN(clock.update(FlightClass.TOUR, LINE.withLoop(true), NavLogic.MissionState.RUNNING, 0, 0.0, 0.0, 20.0, 0.0, false)));
        assertTrue(Double.isNaN(clock.update(FlightClass.TOUR, LINE, NavLogic.MissionState.IDLE, 0, 0.0, 0.0, 20.0, 0.0, false)));
        assertTrue(Double.isNaN(clock.update(FlightClass.TOUR, LINE, NavLogic.MissionState.DONE, 2, 0.0, 0.0, 20.0, 0.0, false)));
        assertTrue(Double.isNaN(clock.update(null, LINE, NavLogic.MissionState.RUNNING, 0, 0.0, 0.0, 20.0, 0.0, false)));
        assertTrue(Double.isNaN(clock.update(FlightClass.TOUR, LINE, NavLogic.MissionState.RUNNING, 3, 0.0, 0.0, 20.0, 0.0, false)));
        assertTrue(Double.isNaN(clock.update(FlightClass.TOUR, Mission.empty(), NavLogic.MissionState.RUNNING, 0, 0.0, 0.0, 20.0, 0.0, false)));
    }
}
