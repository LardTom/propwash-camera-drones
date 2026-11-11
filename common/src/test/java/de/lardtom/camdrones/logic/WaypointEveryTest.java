package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.estimate.FlightTimeEstimate;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionCodec;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.TaskTracker;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.mission.WaypointTasks;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.WaypointFollower;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointEveryTest {
    private static final NavLogic.MissionState RUNNING = NavLogic.MissionState.RUNNING;
    private static final double SPACING = 10.0;
    private static final int STAY_SLACK = 2;

    private record Fire(long tick, int index, int accessory, boolean timed) {
    }

    private static Mission line(int count) {
        List<Waypoint> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(Waypoint.at(i * SPACING, 80.0, 0.0));
        }
        return Mission.of(list);
    }

    private static Mission every(Mission mission, int index, Waypoint.Accessory action, int ticks) {
        return mission.replace(index, mission.waypoint(index).withAccessory(action, Waypoint.Repeat.EVERY, ticks));
    }

    private static Mission set(Mission mission, int index, Waypoint.Accessory action, boolean repeat) {
        return mission.replace(index, mission.waypoint(index).withAccessory(action, repeat));
    }

    private static int target(Mission mission, double x) {
        for (int i = 0; i < mission.size(); i++) {
            if (mission.waypoint(i).x() >= x - 1.0E-9) {
                return i;
            }
        }
        return mission.size() - 1;
    }

    private static void collect(List<Fire> out, long tick, List<WaypointTasks.Due> due) {
        for (WaypointTasks.Due d : due) {
            if (d.accessory() > 0) {
                out.add(new Fire(tick, d.index(), d.accessory(), d.timed()));
            }
        }
    }

    private static List<Fire> fly(Mission mission, TaskTracker tracker, long start, int hoverAt, int hoverTicks) {
        List<Fire> out = new ArrayList<>();
        long tick = start;
        double end = mission.waypoint(mission.size() - 1).x();
        for (double x = 0.0; x <= end + 1.0E-9; x += 1.0) {
            collect(out, tick, tracker.update(mission, RUNNING, target(mission, x), x, 80.0, 0.0, tick));
            tick++;
            if (hoverAt >= 0 && Math.abs(x - mission.waypoint(hoverAt).x()) < 1.0E-9) {
                for (int h = 0; h < hoverTicks; h++) {
                    collect(out, tick, tracker.update(mission, RUNNING, hoverAt, x, 80.0, 0.0, tick));
                    tick++;
                }
            }
        }
        for (int i = 0; i < 30; i++) {
            collect(out, tick, tracker.update(mission, NavLogic.MissionState.DONE, mission.size() - 1, end, 80.0, 0.0, tick));
            tick++;
        }
        return out;
    }

    private static List<Long> ticks(List<Fire> fires) {
        return fires.stream().map(Fire::tick).toList();
    }

    @Test
    void timedActionFiresOnArrivalAndThenEveryXTicksUntilAnotherSetting() {
        Mission m = every(line(6), 1, Waypoint.Accessory.ACTION_1, 5);
        m = set(m, 3, Waypoint.Accessory.END_REPEAT, false);
        List<Fire> fires = fly(m, new TaskTracker(), 0L, -1, 0);
        assertEquals(List.of(9L, 14L, 19L, 24L), ticks(fires), "ab Wegpunkt 2 alle 5 Ticks, bis „Wiederholung aus“ an Wegpunkt 4");
        assertFalse(fires.getFirst().timed(), "der erste Auslöser ist die Ankunft");
        assertTrue(fires.subList(1, fires.size()).stream().allMatch(Fire::timed));
        assertTrue(fires.stream().allMatch(f -> f.index() == 1 && f.accessory() == 1));
    }

    @Test
    void timedActionRunsToTheEndOfTheMissionAndStopsThere() {
        Mission m = every(line(4), 2, Waypoint.Accessory.ACTION_2, 4);
        List<Fire> fires = fly(m, new TaskTracker(), 100L, -1, 0);
        assertEquals(List.of(119L, 123L, 127L), ticks(fires), "bis zum Missionsende, danach nichts mehr");
        assertTrue(fires.stream().allMatch(f -> f.accessory() == 2));
    }

    private record Shot(long tick, WaypointFollower.Phase phase, int index) {
    }

    private record Stay(long from, long to) {
        long length() {
            return this.to - this.from;
        }
    }

    private record Run(List<Shot> shots, List<Stay> stays, long left, boolean done) {
        List<Long> ticks() {
            return this.shots.stream().map(Shot::tick).toList();
        }

        List<Shot> during(Stay stay) {
            return this.shots.stream().filter(s -> s.tick() >= stay.from() && s.tick() < stay.to()).toList();
        }
    }

    private static Mission tour(Mission.End end, double lastWait) {
        return tour(end, lastWait, 20);
    }

    private static Mission tour(Mission.End end, double lastWait, int ticks) {
        Mission m = Mission.of(List.of(Waypoint.at(20.0, 25.0, 0.0), Waypoint.at(20.0, 25.0, 20.0), Waypoint.at(0.0, 25.0, 20.0).withWait(lastWait)))
                .withBaseSpeed(6.0).withEnd(end);
        return every(m, 0, Waypoint.Accessory.ACTION_1, ticks);
    }

    private static Run simulate(Mission mission, boolean grounded) {
        return simulate(mission, grounded, -1, 0);
    }

    private static Run simulate(Mission mission, boolean grounded, int pauseAfter, int pauseTicks) {
        NavLogic logic = new NavLogic(FlightClass.TOUR);
        logic.startMission(mission, 0);
        PointMass drone = new PointMass(FlightClass.TOUR).at(0.0, grounded ? 0.0 : 20.0, 0.0, 90.0).wind(3.0, 0.0, 1.0);
        TaskTracker tracker = new TaskTracker();
        int last = mission.size() - 1;
        List<Shot> shots = new ArrayList<>();
        List<Stay> stays = new ArrayList<>();
        long[] clock = {-1L, Long.MAX_VALUE, -1L, -1L};
        drone.run(logic::update, 150.0, d -> {
            long now = (long) Math.floor(d.time * 20.0 + 1.0E-6);
            if (now == clock[0]) {
                return;
            }
            clock[0] = now;
            if (pauseAfter >= 0 && clock[3] < 0 && clock[2] >= 0 && now - clock[2] >= pauseAfter) {
                logic.pauseMission();
                clock[3] = now;
            } else if (clock[3] >= 0 && now == clock[3] + pauseTicks) {
                assertTrue(logic.resumeMission());
            }
            WaypointFollower follower = logic.follower();
            WaypointFollower.Phase phase = follower == null ? null : follower.phase();
            boolean waiting = phase == WaypointFollower.Phase.WAIT && follower.index() == last;
            if (waiting && clock[2] < 0) {
                clock[2] = now;
            } else if (!waiting && clock[2] >= 0) {
                stays.add(new Stay(clock[2], now));
                clock[2] = -1L;
            }
            if (clock[1] == Long.MAX_VALUE && (phase == WaypointFollower.Phase.RETURN || phase == WaypointFollower.Phase.LAND
                    || phase == WaypointFollower.Phase.DONE)) {
                clock[1] = now;
            }
            for (WaypointTasks.Due due : tracker.update(mission, logic.missionState(), logic.missionIndex(), d.x, d.y, d.z, now)) {
                if (due.accessory() > 0) {
                    shots.add(new Shot(now, phase, follower == null ? -1 : follower.index()));
                }
            }
        });
        return new Run(shots, stays, clock[1], logic.missionState() == NavLogic.MissionState.DONE);
    }

    private static void at(List<Fire> out, TaskTracker tracker, Mission mission, NavLogic.MissionState state, int index, double x, long tick) {
        collect(out, tick, tracker.update(mission, state, index, x, 80.0, 0.0, tick));
    }

    private static Mission lastWaits(double seconds) {
        Mission m = every(line(3), 0, Waypoint.Accessory.ACTION_1, 5);
        return m.replace(2, m.waypoint(2).withWait(seconds));
    }

    private static long toTheLastWaypoint(List<Fire> fires, TaskTracker tracker, Mission m) {
        long tick = 0L;
        for (double x = 0.0; x <= 20.0 + 1.0E-9; x += 1.0) {
            at(fires, tracker, m, RUNNING, target(m, x), x, tick++);
        }
        return tick;
    }

    @Test
    void timedActionEndsAtTheLastWaypointForEveryEnd() {
        for (boolean grounded : new boolean[]{false, true}) {
            for (double wait : new double[]{0.0, 3.0}) {
                Map<Mission.End, Run> runs = new EnumMap<>(Mission.End.class);
                for (Mission.End end : Mission.End.values()) {
                    Run run = simulate(tour(end, wait), grounded);
                    String label = end + (grounded ? " vom Boden" : " in der Luft") + ", Warten " + wait + " s: ";
                    assertTrue(run.done(), label + "Mission nicht beendet");
                    assertTrue(run.left() < Long.MAX_VALUE, label + "letzten Wegpunkt nie verlassen");
                    assertTrue(run.shots().size() >= 5, label + run.shots());
                    assertTrue(run.shots().stream().allMatch(s -> s.tick() < run.left()),
                            label + "Auslöser nach dem letzten Wegpunkt (verlassen in Tick " + run.left() + "): " + run.shots());
                    runs.put(end, run);
                }
                List<Long> hover = runs.get(Mission.End.HOVER).ticks();
                assertEquals(hover, runs.get(Mission.End.START).ticks(), "zum Start wie schweben, Warten " + wait + " s, Boden " + grounded);
                assertEquals(hover, runs.get(Mission.End.LAND).ticks(), "landen wie schweben, Warten " + wait + " s, Boden " + grounded);
                if (wait > 0.0) {
                    assertTrue(runs.get(Mission.End.START).shots().stream().anyMatch(s -> s.phase() == WaypointFollower.Phase.WAIT && s.index() == 2),
                            "beim Warten am letzten Wegpunkt weiter im Takt: " + runs.get(Mission.End.START).shots());
                }
            }
        }
    }

    private static void throughTheStay(String label, Run run, Stay stay, int every) {
        List<Shot> during = run.during(stay);
        assertFalse(during.isEmpty(), label + "kein Auslöser, während die Drohne am letzten Punkt wartet " + stay + ": " + run.shots());
        long gap = stay.to() - during.getLast().tick();
        assertTrue(gap <= every + STAY_SLACK, label + "Takt endet " + (gap - every) + " Ticks vor dem Abflug (Folger wartet " + stay + "): " + during);
        assertTrue(during.size() >= stay.length() / every - 1, label + during.size() + " Auslöser in " + stay.length() + " Ticks: " + during);
        assertTrue(run.shots().stream().allMatch(s -> s.tick() < run.left()), label + "Auslöser nach dem Abflug in Tick " + run.left() + ": " + run.shots());
    }

    @Test
    void timedActionRunsThroughTheWholeVisibleWaitAtTheLastWaypoint() {
        for (Mission.End end : Mission.End.values()) {
            for (boolean grounded : new boolean[]{false, true}) {
                for (double wait : new double[]{3.0, 10.0}) {
                    for (int every : new int[]{5, 20}) {
                        Run run = simulate(tour(end, wait, every), grounded);
                        String label = end + (grounded ? " vom Boden" : " in der Luft") + ", Warten " + wait + " s, alle " + every + " Ticks: ";
                        assertTrue(run.done(), label + "Mission nicht beendet");
                        assertEquals(1, run.stays().size(), label + run.stays());
                        Stay stay = run.stays().getFirst();
                        assertEquals(Math.round(wait * MissionLimits.TICKS_PER_SECOND), stay.length(), 1L, label + stay);
                        throughTheStay(label, run, stay, every);
                    }
                }
            }
        }
    }

    @Test
    void resumeAtTheLastWaypointRunsTheTimerThroughTheWholeNewWait() {
        Map<Mission.End, List<Long>> ticks = new EnumMap<>(Mission.End.class);
        for (Mission.End end : Mission.End.values()) {
            Run run = simulate(tour(end, 4.0, 5), false, 20, 100);
            String label = end + ", 4 s Warten, Pause 5 s nach 1 s Warten: ";
            assertTrue(run.done(), label + "Mission nicht beendet");
            assertEquals(2, run.stays().size(), label + run.stays());
            Stay before = run.stays().get(0);
            Stay after = run.stays().get(1);
            assertEquals(20L, before.length(), 1L, label + before);
            assertTrue(after.from() >= before.to() + 100L, label + run.stays());
            assertEquals(80L, after.length(), 1L, label + "der neue Folger wartet nach der Pause die volle Wartezeit: " + after);
            assertTrue(run.shots().stream().noneMatch(s -> s.phase() == null), label + "Auslöser in der Pause: " + run.shots());
            throughTheStay(label, run, after, 5);
            ticks.put(end, run.ticks());
        }
        assertEquals(ticks.get(Mission.End.HOVER), ticks.get(Mission.End.START), "zum Start wie schweben");
        assertEquals(ticks.get(Mission.End.HOVER), ticks.get(Mission.End.LAND), "landen wie schweben");
    }

    @Test
    void timedActionEndsWhenTheWaitAtTheLastWaypointIsOver() {
        Mission m = lastWaits(2.0);
        TaskTracker tracker = new TaskTracker();
        List<Fire> fires = new ArrayList<>();
        long tick = toTheLastWaypoint(fires, tracker, m);
        for (int h = 0; h < 60; h++) {
            at(fires, tracker, m, RUNNING, 2, 20.0, tick++);
        }
        for (double x = 20.0; x >= 0.0; x -= 1.0) {
            at(fires, tracker, m, RUNNING, 2, x, tick++);
        }
        for (int i = 0; i < 20; i++) {
            at(fires, tracker, m, NavLogic.MissionState.DONE, 2, 0.0, tick++);
        }
        assertEquals(List.of(0L, 5L, 10L, 15L, 20L, 25L, 30L, 35L, 40L, 45L, 50L, 55L), ticks(fires),
                "Ankunft wie der Folger (0,5 m) in Tick 20, 2 s Warten: Schluss in Tick 60, auf dem Rückflug nichts mehr");
    }

    @Test
    void leavingTheLastWaypointEndsTheTimerBeforeTheWaitIsOver() {
        Mission m = lastWaits(10.0);
        TaskTracker tracker = new TaskTracker();
        List<Fire> fires = new ArrayList<>();
        long tick = toTheLastWaypoint(fires, tracker, m);
        for (int h = 0; h < 9; h++) {
            at(fires, tracker, m, RUNNING, 2, 20.0, tick++);
        }
        for (double x = 19.0; x >= 0.0; x -= 1.0) {
            at(fires, tracker, m, RUNNING, 2, x, tick++);
        }
        for (int i = 0; i < 20; i++) {
            at(fires, tracker, m, NavLogic.MissionState.DONE, 2, 0.0, tick++);
        }
        assertEquals(List.of(0L, 5L, 10L, 15L, 20L, 25L, 30L), ticks(fires), "mehr als 3 m vom letzten Punkt weg: Schluss, auch vor Ende der Wartezeit");
    }

    @Test
    void resumeAtTheLastWaypointStartsTheWholeWaitAgain() {
        Mission m = lastWaits(2.0);
        TaskTracker tracker = new TaskTracker();
        List<Fire> fires = new ArrayList<>();
        long tick = toTheLastWaypoint(fires, tracker, m);
        for (; tick <= 30L; tick++) {
            at(fires, tracker, m, RUNNING, 2, 20.0, tick);
        }
        for (; tick <= 130L; tick++) {
            at(fires, tracker, m, NavLogic.MissionState.PAUSED, 2, 20.0, tick);
        }
        for (; tick <= 200L; tick++) {
            at(fires, tracker, m, RUNNING, 2, 20.0, tick);
        }
        assertEquals(List.of(0L, 5L, 10L, 15L, 20L, 25L, 30L, 131L, 136L, 141L, 146L, 151L, 156L, 161L, 166L), ticks(fires),
                "11 Ticks Warten vor der Pause, 100 Ticks Pause, danach wartet der neue Folger die vollen 40 Ticks: Schluss in Tick 171");
    }

    @Test
    void resumeAwayFromTheLastWaypointKeepsTheTimerUntilTheNewWaitIsOver() {
        Mission m = lastWaits(2.0);
        TaskTracker tracker = new TaskTracker();
        List<Fire> fires = new ArrayList<>();
        long tick = toTheLastWaypoint(fires, tracker, m);
        for (; tick <= 30L; tick++) {
            at(fires, tracker, m, RUNNING, 2, 20.0, tick);
        }
        for (; tick <= 60L; tick++) {
            at(fires, tracker, m, NavLogic.MissionState.PAUSED, 2, 26.0, tick);
        }
        for (double x = 25.0; x >= 20.0; x -= 1.0) {
            at(fires, tracker, m, RUNNING, 2, x, tick++);
        }
        for (; tick <= 160L; tick++) {
            at(fires, tracker, m, RUNNING, 2, 20.0, tick);
        }
        assertEquals(List.of(0L, 5L, 10L, 15L, 20L, 25L, 30L, 61L, 66L, 71L, 76L, 81L, 86L, 91L, 96L, 101L), ticks(fires),
                "in der Pause 6 m weggeflogen: zurück zum letzten Punkt weiter im Takt, dort die vollen 40 Ticks ab Tick 66, Schluss in Tick 106");
    }

    @Test
    void timedActionKeepsRunningWhileTheDroneWaitsAtAWaypoint() {
        Mission m = every(line(3), 1, Waypoint.Accessory.ACTION_1, 6);
        m = set(m, 2, Waypoint.Accessory.END_REPEAT, false);
        List<Fire> fires = fly(m, new TaskTracker(), 0L, 1, 20);
        assertEquals(List.of(9L, 15L, 21L, 27L, 33L), ticks(fires), "beim Warten weiter im Takt, keine zweite Ankunft, Schluss an Wegpunkt 3");
    }

    @Test
    void anotherActionOrModeEndsTheTimedRange() {
        Mission m = every(line(5), 1, Waypoint.Accessory.ACTION_1, 4);
        m = set(m, 2, Waypoint.Accessory.ACTION_2, false);
        List<Fire> fires = fly(m, new TaskTracker(), 0L, -1, 0);
        assertEquals(List.of(new Fire(9L, 1, 1, false), new Fire(13L, 1, 1, true), new Fire(17L, 1, 1, true), new Fire(19L, 2, 2, false)), fires);
        Mission each = every(line(5), 1, Waypoint.Accessory.ACTION_1, 4);
        each = set(each, 2, Waypoint.Accessory.ACTION_1, true);
        List<Fire> switched = fly(each, new TaskTracker(), 0L, -1, 0);
        assertEquals(List.of(9L, 13L, 17L, 19L, 29L, 39L), ticks(switched), "ab Wegpunkt 3 an jedem Wegpunkt statt im Takt");
    }

    @Test
    void sameSettingAtALaterWaypointContinuesWithoutAnExtraTrigger() {
        Mission m = every(line(4), 1, Waypoint.Accessory.ACTION_3, 4);
        m = every(m, 2, Waypoint.Accessory.ACTION_3, 4);
        m = set(m, 3, Waypoint.Accessory.END_REPEAT, false);
        List<Fire> fires = fly(m, new TaskTracker(), 0L, -1, 0);
        assertEquals(List.of(9L, 13L, 17L, 21L, 25L), ticks(fires), "gleiche Einstellung läuft weiter, kein Auslöser zur Unzeit");
        assertTrue(WaypointTasks.continues(m, 2));
        assertEquals(0, WaypointTasks.due(m, 2).accessory());
        Mission faster = every(m, 2, Waypoint.Accessory.ACTION_3, 3);
        assertFalse(WaypointTasks.continues(faster, 2));
        assertEquals(List.of(9L, 13L, 17L, 19L, 22L, 25L, 28L), ticks(fly(faster, new TaskTracker(), 0L, -1, 0)), "anderer Takt startet neu");
    }

    @Test
    void neverFasterThanTheChosenInterval() {
        for (int x : new int[]{1, 2, 3, 7, 20}) {
            Mission m = every(line(4), 0, Waypoint.Accessory.ACTION_4, x);
            List<Long> fired = ticks(fly(m, new TaskTracker(), 7L, 1, 13));
            assertTrue(fired.size() >= 2, x + ": " + fired);
            for (int i = 1; i < fired.size(); i++) {
                assertEquals(x, fired.get(i) - fired.get(i - 1), "Abstand bei " + x + " Ticks: " + fired);
            }
        }
    }

    @Test
    void pauseHoldsTheTimerAndResumeNeverFiresFaster() {
        Mission m = every(line(3), 0, Waypoint.Accessory.ACTION_1, 5);
        TaskTracker tracker = new TaskTracker();
        List<Fire> fires = new ArrayList<>();
        collect(fires, 0L, tracker.update(m, RUNNING, 0, 0.0, 80.0, 0.0, 0L));
        collect(fires, 3L, tracker.update(m, RUNNING, 1, 3.0, 80.0, 0.0, 3L));
        for (long t = 4L; t < 30L; t++) {
            collect(fires, t, tracker.update(m, NavLogic.MissionState.PAUSED, 1, 3.0, 80.0, 0.0, t));
        }
        collect(fires, 30L, tracker.update(m, RUNNING, 1, 3.0, 80.0, 0.0, 30L));
        collect(fires, 31L, tracker.update(m, RUNNING, 1, 4.0, 80.0, 0.0, 31L));
        collect(fires, 35L, tracker.update(m, RUNNING, 1, 5.0, 80.0, 0.0, 35L));
        assertEquals(List.of(0L, 30L, 35L), ticks(fires), "keine Auslöser in der Pause, danach wieder im Abstand");
    }

    @Test
    void restartIdleAndAbortStopTheTimer() {
        Mission m = every(line(3), 0, Waypoint.Accessory.ACTION_1, 2);
        TaskTracker tracker = new TaskTracker();
        assertEquals(1, tracker.update(m, RUNNING, 0, 0.0, 80.0, 0.0, 0L).size());
        assertEquals(1, tracker.update(m, RUNNING, 1, 2.0, 80.0, 0.0, 2L).size());
        assertTrue(tracker.update(m, NavLogic.MissionState.ABORTED, 1, 2.0, 80.0, 0.0, 4L).isEmpty());
        assertTrue(tracker.update(m, RUNNING, 1, 5.0, 80.0, 0.0, 6L).isEmpty(), "nach Abbruch kein Takt mehr");
        TaskTracker restart = new TaskTracker();
        restart.update(m, RUNNING, 0, 0.0, 80.0, 0.0, 0L);
        restart.update(m, RUNNING, 1, 5.0, 80.0, 0.0, 1L);
        restart.update(m, RUNNING, 2, 15.0, 80.0, 0.0, 2L);
        assertTrue(restart.update(m, RUNNING, 1, 15.0, 80.0, 0.0, 3L).isEmpty(), "Rücksprung stoppt den Takt");
        assertTrue(restart.update(m, RUNNING, 1, 14.0, 80.0, 0.0, 9L).isEmpty());
    }

    @Test
    void loopsKeepTheTimerAcrossTheWrap() {
        Mission m = every(line(3), 0, Waypoint.Accessory.ACTION_2, 7).withLoop(true);
        TaskTracker tracker = new TaskTracker();
        List<Fire> fires = new ArrayList<>();
        long tick = 0L;
        for (int lap = 0; lap < 2; lap++) {
            for (double x = 0.0; x <= 20.0; x += 1.0) {
                collect(fires, tick, tracker.update(m, RUNNING, target(m, x), x, 80.0, 0.0, tick));
                tick++;
            }
            for (double x = 19.0; x > 0.0; x -= 1.0) {
                collect(fires, tick, tracker.update(m, RUNNING, 0, x, 80.0, 0.0, tick));
                tick++;
            }
        }
        List<Long> fired = ticks(fires);
        assertEquals(0L, fired.getFirst());
        for (int i = 1; i < fired.size(); i++) {
            assertEquals(7L, fired.get(i) - fired.get(i - 1), "Schleife ohne Unterbrechung im Takt: " + fired);
        }
        assertEquals(2, tracker.laps());
    }

    @Test
    void untimedActionsBehaveAsBefore() {
        Mission m = set(line(4), 1, Waypoint.Accessory.ACTION_1, true);
        m = set(m, 3, Waypoint.Accessory.END_REPEAT, false);
        assertEquals(List.of(9L, 59L), ticks(fly(m, new TaskTracker(), 0L, 1, 40)), "an jedem Wegpunkt wie bisher, beim Warten nicht");
        Mission once = set(line(3), 1, Waypoint.Accessory.ACTION_1, false);
        assertEquals(List.of(9L), ticks(fly(once, new TaskTracker(), 0L, 1, 40)), "einmal wie bisher");
    }

    @Test
    void timedWaypointsOnlyWithAnAccessoryActionAndInsideTheLimits() {
        Waypoint base = Waypoint.at(0.0, 80.0, 0.0);
        Waypoint timed = base.withAccessory(Waypoint.Accessory.ACTION_2, Waypoint.Repeat.EVERY, 15);
        assertEquals(Waypoint.Repeat.EVERY, timed.repeatMode());
        assertEquals(15, timed.everyTicks());
        assertFalse(timed.repeat());
        assertEquals(0.75, timed.everySeconds(), 1.0E-9);
        assertEquals(MissionLimits.DEFAULT_EVERY_TICKS, base.withAccessory(Waypoint.Accessory.ACTION_1, Waypoint.Repeat.EVERY, 0).everyTicks());
        assertEquals(MissionLimits.MAX_EVERY_TICKS, timed.withEveryTicks(MissionLimits.MAX_EVERY_TICKS + 500).everyTicks());
        assertEquals(MissionLimits.MIN_EVERY_TICKS, timed.withEveryTicks(-3).everyTicks());
        assertEquals(1, MissionLimits.MIN_EVERY_TICKS);
        assertEquals(1200, MissionLimits.MAX_EVERY_TICKS);
        assertEquals(20, MissionLimits.DEFAULT_EVERY_TICKS);
        assertEquals(Waypoint.Repeat.ONCE, base.withAccessory(Waypoint.Accessory.NONE, Waypoint.Repeat.EVERY, 15).repeatMode());
        assertEquals(0, base.withAccessory(Waypoint.Accessory.END_REPEAT, Waypoint.Repeat.EVERY, 15).everyTicks());
        assertEquals(base, base.withEveryTicks(40), "ohne Takt ändert die Zahl nichts");
        assertEquals(Waypoint.Repeat.EACH, timed.withRepeat(Waypoint.Repeat.EACH).repeatMode());
        assertEquals(0, timed.withRepeat(Waypoint.Repeat.EACH).everyTicks());
        assertTrue(timed.withRepeat(Waypoint.Repeat.EACH).repeat());
        assertEquals(Waypoint.Repeat.ONCE, timed.withRepeat(Waypoint.Repeat.ONCE).repeatMode());
        assertEquals(Waypoint.Repeat.ONCE, base.withAccessory(Waypoint.Accessory.ACTION_1, false).repeatMode());
        Waypoint edited = timed.withSpeed(5.0).withLight(Waypoint.Light.ON).withWait(1.0).withPosition(1.0, 81.0, 2.0).withTilt(-10);
        assertEquals(15, edited.everyTicks(), "andere Felder lassen den Takt stehen");
        assertEquals(0, timed.withoutTasks().everyTicks());
        assertThrows(IllegalArgumentException.class, () -> new Waypoint(0, 800, 0, 0, 0, 0, Waypoint.Heading.COURSE, 0, Waypoint.Action.NONE, 0,
                Waypoint.Light.NONE, Waypoint.Accessory.NONE, false, 20), "Takt ohne Zubehör-Aktion");
        assertThrows(IllegalArgumentException.class, () -> new Waypoint(0, 800, 0, 0, 0, 0, Waypoint.Heading.COURSE, 0, Waypoint.Action.NONE, 0,
                Waypoint.Light.NONE, Waypoint.Accessory.ACTION_1, true, 20), "Takt und „an jedem Wegpunkt“ zugleich");
        assertThrows(IllegalArgumentException.class, () -> new Waypoint(0, 800, 0, 0, 0, 0, Waypoint.Heading.COURSE, 0, Waypoint.Action.NONE, 0,
                Waypoint.Light.NONE, Waypoint.Accessory.ACTION_1, false, MissionLimits.MAX_EVERY_TICKS + 1));
        assertThrows(IllegalArgumentException.class, () -> new Waypoint(0, 800, 0, 0, 0, 0, Waypoint.Heading.COURSE, 0, Waypoint.Action.NONE, 0,
                Waypoint.Light.NONE, Waypoint.Accessory.ACTION_1, false, -1));
    }

    @Test
    void timedSettingsAreSavedAsThirdFormatAndOldMissionsStayByteIdentical() {
        Mission plain = line(3);
        Mission tasks = set(plain, 1, Waypoint.Accessory.ACTION_1, true).withLoop(true);
        Mission timed = every(tasks, 2, Waypoint.Accessory.ACTION_4, MissionLimits.MAX_EVERY_TICKS);
        assertEquals(MissionLimits.FIRST_FORMAT_VERSION, MissionCodec.encode(plain)[0]);
        assertEquals(MissionLimits.TASK_FORMAT_VERSION, MissionCodec.encode(tasks)[0]);
        assertEquals(2, MissionLimits.TASK_FORMAT_VERSION);
        assertEquals(3, MissionLimits.EVERY_FORMAT_VERSION);
        int[] data = MissionCodec.encode(timed);
        assertEquals(MissionLimits.EVERY_FORMAT_VERSION, data[0]);
        assertEquals(MissionLimits.packedInts(3, 0), data.length, "gleiche Größe wie bisher");
        Mission back = MissionCodec.decode(data).mission().orElseThrow();
        assertEquals(timed, back);
        assertEquals(MissionLimits.MAX_EVERY_TICKS, back.waypoint(2).everyTicks());
        assertArrayEquals(data, MissionCodec.encode(back));
        int[] old = MissionCodec.encode(tasks);
        Mission loaded = MissionCodec.decode(old).mission().orElseThrow();
        for (Waypoint w : loaded.waypoints()) {
            assertEquals(0, w.everyTicks());
            assertTrue(w.repeatMode() != Waypoint.Repeat.EVERY);
        }
        assertEquals(Waypoint.Repeat.EACH, loaded.waypoint(1).repeatMode());
        assertArrayEquals(old, MissionCodec.encode(loaded), "Format 2 bleibt Byte für Byte gleich");
    }

    @Test
    void brokenOrOutOfRangeTimedValuesAreRejected() {
        Mission timed = every(line(2), 0, Waypoint.Accessory.ACTION_1, 30);
        int[] data = MissionCodec.encode(timed);
        int look = MissionLimits.HEADER_INTS + 4;
        assertEquals(30, data[look] >>> 16);
        int[] tooSlow = data.clone();
        tooSlow[look] = (data[look] & 0xFFFF) | (MissionLimits.MAX_EVERY_TICKS + 1) << 16;
        assertEquals(MissionCodec.Status.CORRUPT, MissionCodec.decode(tooSlow).status());
        int[] second = data.clone();
        second[0] = MissionLimits.TASK_FORMAT_VERSION;
        assertEquals(MissionCodec.Status.CORRUPT, MissionCodec.decode(second).status(), "Format 2 kennt keinen Takt");
        int[] withoutAction = MissionCodec.encode(line(2));
        withoutAction[0] = MissionLimits.FORMAT_VERSION;
        withoutAction[look] |= 30 << 16;
        assertEquals(MissionCodec.Status.CORRUPT, MissionCodec.decode(withoutAction).status(), "Takt ohne Zubehör-Aktion");
        int[] future = data.clone();
        future[0] = MissionLimits.FORMAT_VERSION + 1;
        assertEquals(MissionCodec.Status.UNKNOWN_VERSION, MissionCodec.decode(future).status());
    }

    @Test
    void previewAndBatteryTimeIgnoreTheTimer() {
        Mission plain = set(line(4), 1, Waypoint.Accessory.ACTION_1, false);
        Mission timed = every(line(4), 1, Waypoint.Accessory.ACTION_1, 4);
        for (FlightClass flightClass : FlightClass.values()) {
            assertEquals(FlightTimeEstimate.mission(flightClass, plain, 0.0, 70.0, 0.0), FlightTimeEstimate.mission(flightClass, timed, 0.0, 70.0, 0.0));
        }
        assertEquals(WaypointTasks.all(plain).size(), WaypointTasks.all(timed).size(), "die Vorschau zählt den Takt als eine Aktion");
    }

    @Test
    void plannerHelpersKnowTheTimedRange() {
        Mission m = every(line(6), 1, Waypoint.Accessory.ACTION_2, 8);
        m = set(m, 4, Waypoint.Accessory.END_REPEAT, false);
        assertEquals(List.of(-1, -1, 1, 1, -1, -1), sources(m));
        assertEquals(8, WaypointTasks.timedAt(m, 3).orElseThrow().ticks());
        assertEquals(2, WaypointTasks.timedAt(m, 3).orElseThrow().accessory());
        assertTrue(WaypointTasks.timedAt(m, 4).isEmpty());
        assertTrue(WaypointTasks.timedAt(m, 0).isEmpty());
        assertEquals(8, WaypointTasks.due(m, 1).everyTicks());
        assertEquals(0, WaypointTasks.due(m, 2).accessory(), "im Takt löst die Ankunft nichts aus");
        assertEquals(-1, WaypointTasks.repeatSource(m, 2));
        assertEquals(0, WaypointTasks.accessoryAt(m, 3));
    }

    private static List<Integer> sources(Mission m) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < m.size(); i++) {
            out.add(WaypointTasks.timedSource(m, i));
        }
        return out;
    }
}
