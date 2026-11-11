package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.TaskTracker;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.mission.WaypointTasks;
import de.lardtom.camdrones.logic.nav.NavLogic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointTasksTest {
    private static final NavLogic.MissionState RUNNING = NavLogic.MissionState.RUNNING;

    private static Mission line(int count) {
        List<Waypoint> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(Waypoint.at(i * 10.0, 80.0, 0.0));
        }
        return Mission.of(list);
    }

    private static Mission with(Mission mission, int index, Waypoint waypoint) {
        return mission.replace(index, waypoint);
    }

    private static List<Integer> indices(List<WaypointTasks.Due> due) {
        return due.stream().map(WaypointTasks.Due::index).toList();
    }

    @Test
    void repeatRunsFromItsWaypointToTheEnd() {
        Mission m = line(6);
        m = with(m, 2, m.waypoint(2).withAccessory(Waypoint.Accessory.ACTION_1, true));
        assertEquals(List.of(0, 0, 1, 1, 1, 1), accessories(m));
        assertFalse(WaypointTasks.due(m, 2).repeated());
        assertTrue(WaypointTasks.due(m, 3).repeated());
        assertEquals(2, WaypointTasks.repeatSource(m, 5));
        assertEquals(-1, WaypointTasks.repeatSource(m, 2));
        assertEquals(-1, WaypointTasks.repeatSource(m, 1));
    }

    @Test
    void endRepeatAndALaterActionStopTheRepeat() {
        Mission m = line(8);
        m = with(m, 1, m.waypoint(1).withAccessory(Waypoint.Accessory.ACTION_2, true));
        m = with(m, 3, m.waypoint(3).withAccessory(Waypoint.Accessory.END_REPEAT, false));
        m = with(m, 5, m.waypoint(5).withAccessory(Waypoint.Accessory.ACTION_4, false));
        assertEquals(List.of(0, 2, 2, 0, 0, 4, 0, 0), accessories(m));
        assertEquals(-1, WaypointTasks.repeatSource(m, 4));
        assertEquals(-1, WaypointTasks.repeatSource(m, 6));
    }

    @Test
    void onlyAccessoryActionsCanRepeat() {
        Waypoint base = Waypoint.at(0.0, 80.0, 0.0);
        assertFalse(base.withAccessory(Waypoint.Accessory.NONE, true).repeat());
        assertFalse(base.withAccessory(Waypoint.Accessory.END_REPEAT, true).repeat());
        assertTrue(base.withAccessory(Waypoint.Accessory.ACTION_3, true).repeat());
        assertThrows(IllegalArgumentException.class, () -> new Waypoint(0, 800, 0, 0, 0, 0, Waypoint.Heading.COURSE, 0, Waypoint.Action.NONE, 0,
                Waypoint.Light.NONE, Waypoint.Accessory.NONE, true));
        assertEquals(3, Waypoint.Accessory.ACTION_3.number());
        assertEquals(0, Waypoint.Accessory.END_REPEAT.number());
        assertEquals(Waypoint.Accessory.ACTION_4, Waypoint.Accessory.of(4));
        assertEquals(Waypoint.Accessory.NONE, Waypoint.Accessory.of(5));
        assertTrue(Waypoint.Light.RAINBOW.mode());
        assertFalse(Waypoint.Light.OFF.mode());
    }

    @Test
    void editingKeepsTheTasks() {
        Waypoint w = Waypoint.at(1.0, 70.0, 2.0).withLight(Waypoint.Light.BLINK).withAccessory(Waypoint.Accessory.ACTION_2, true);
        Waypoint edited = w.withSpeed(6.0).withWait(2.0).withTilt(-20).withHeading(Waypoint.Heading.FIXED, 90.0).withPosition(3.0, 71.0, 4.0)
                .withAction(Waypoint.Action.PHOTO).withHeight(720).withYaw(-15).facing(1);
        assertEquals(Waypoint.Light.BLINK, edited.light());
        assertEquals(Waypoint.Accessory.ACTION_2, edited.accessory());
        assertTrue(edited.repeat());
        assertTrue(edited.hasTasks());
        assertFalse(edited.withoutTasks().hasTasks());
        assertEquals(edited.speedDmS(), edited.withoutTasks().speedDmS());
    }

    @Test
    void trackerFiresOnArrivalOncePerPass() {
        Mission m = line(4);
        m = with(m, 1, m.waypoint(1).withLight(Waypoint.Light.ON));
        m = with(m, 2, m.waypoint(2).withAccessory(Waypoint.Accessory.ACTION_1, true));
        TaskTracker tracker = new TaskTracker();
        assertTrue(tracker.update(m, RUNNING, 0, 0.0, 80.0, 0.0).isEmpty());
        assertTrue(tracker.update(m, RUNNING, 1, 5.0, 80.0, 0.0).isEmpty());
        assertEquals(List.of(1), indices(tracker.update(m, RUNNING, 1, 10.0, 80.0, 0.0)));
        assertTrue(tracker.update(m, RUNNING, 1, 10.2, 80.0, 0.0).isEmpty(), "einmal je Durchgang");
        assertTrue(tracker.update(m, NavLogic.MissionState.PAUSED, 1, 10.0, 80.0, 0.0).isEmpty());
        assertTrue(tracker.update(m, RUNNING, 1, 10.0, 80.0, 0.0).isEmpty(), "Fortsetzen löst nicht erneut aus");
        assertEquals(List.of(2), indices(tracker.update(m, RUNNING, 2, 20.0, 80.0, 1.0)));
        List<WaypointTasks.Due> last = tracker.update(m, RUNNING, 3, 25.0, 80.0, 0.0);
        assertTrue(last.isEmpty());
        List<WaypointTasks.Due> done = tracker.update(m, NavLogic.MissionState.DONE, 3, 30.0, 80.0, 0.0);
        assertEquals(List.of(3), indices(done));
        assertTrue(done.getFirst().repeated());
        assertEquals(1, done.getFirst().accessory());
        assertTrue(tracker.finished());
    }

    @Test
    void smoothCornersFireWhenTheIndexMovesOn() {
        Mission m = line(3);
        m = with(m, 1, m.waypoint(1).withAccessory(Waypoint.Accessory.ACTION_3, false));
        TaskTracker tracker = new TaskTracker();
        tracker.update(m, RUNNING, 1, 6.0, 80.0, 3.0);
        assertEquals(List.of(1), indices(tracker.update(m, RUNNING, 2, 13.0, 80.0, 3.0)));
    }

    @Test
    void loopsFireAgainButRestartsAndJumpsDoNot() {
        Mission m = line(3).withLoop(true);
        m = with(m, 0, m.waypoint(0).withAccessory(Waypoint.Accessory.ACTION_1, false));
        m = with(m, 2, m.waypoint(2).withLight(Waypoint.Light.OFF));
        TaskTracker tracker = new TaskTracker();
        assertEquals(List.of(0), indices(tracker.update(m, RUNNING, 0, 0.0, 80.0, 0.0)));
        tracker.update(m, RUNNING, 1, 5.0, 80.0, 0.0);
        tracker.update(m, RUNNING, 2, 15.0, 80.0, 0.0);
        assertEquals(List.of(2), indices(tracker.update(m, RUNNING, 0, 10.0, 80.0, 0.0)), "Schleife: letzter Punkt beim Wechsel");
        assertEquals(1, tracker.laps());
        assertEquals(List.of(0), indices(tracker.update(m, RUNNING, 0, 0.0, 80.0, 0.0)), "zweite Runde");
        Mission plain = m.withLoop(false);
        TaskTracker restart = new TaskTracker();
        restart.update(plain, RUNNING, 1, 5.0, 80.0, 0.0);
        assertTrue(restart.update(plain, RUNNING, 0, 5.0, 80.0, 0.0).isEmpty(), "Neustart feuert nichts nach");
        assertTrue(restart.update(plain, RUNNING, 2, 5.0, 80.0, 0.0).isEmpty(), "Sprung nach vorn feuert nichts nach");
        assertEquals(0, restart.laps());
    }

    @Test
    void idleAndAbortResetTheTracker() {
        Mission m = line(2);
        m = with(m, 0, m.waypoint(0).withLight(Waypoint.Light.RAINBOW));
        TaskTracker tracker = new TaskTracker();
        assertEquals(1, tracker.update(m, RUNNING, 0, 0.0, 80.0, 0.0).size());
        assertTrue(tracker.update(m, NavLogic.MissionState.ABORTED, 0, 0.0, 80.0, 0.0).isEmpty());
        assertFalse(tracker.fired(0));
        assertEquals(1, tracker.update(m, RUNNING, 0, 0.0, 80.0, 0.0).size());
        assertTrue(tracker.update(null, RUNNING, 0, 0.0, 80.0, 0.0).isEmpty());
        assertTrue(tracker.update(Mission.empty(), RUNNING, 0, 0.0, 80.0, 0.0).isEmpty());
    }

    @Test
    void allListsOnlyWaypointsWithSomethingToDo() {
        Mission m = line(5);
        assertFalse(WaypointTasks.any(m));
        m = with(m, 1, m.waypoint(1).withAccessory(Waypoint.Accessory.ACTION_1, true));
        m = with(m, 3, m.waypoint(3).withAccessory(Waypoint.Accessory.END_REPEAT, false).withLight(Waypoint.Light.ON));
        assertEquals(List.of(1, 2, 3), WaypointTasks.all(m).stream().map(WaypointTasks.Due::index).toList());
        assertEquals(0, WaypointTasks.due(m, 3).accessory());
        assertEquals(Waypoint.Light.ON, WaypointTasks.due(m, 3).light());
        assertFalse(WaypointTasks.due(m, 99).any());
    }

    private static List<Integer> accessories(Mission m) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < m.size(); i++) {
            out.add(WaypointTasks.accessoryAt(m, i));
        }
        return out;
    }
}
