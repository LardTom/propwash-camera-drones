package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionCodec;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.TaskTracker;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.mission.WaypointTasks;
import de.lardtom.camdrones.logic.nav.NavLogic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointGripperTest {
    private static final NavLogic.MissionState RUNNING = NavLogic.MissionState.RUNNING;

    private static Mission line(int count) {
        List<Waypoint> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(Waypoint.at(i * 10.0, 80.0, 0.0));
        }
        return Mission.of(list);
    }

    private static Mission set(Mission mission, int index, Waypoint.Accessory accessory, Waypoint.Repeat mode, int ticks) {
        return mission.replace(index, mission.waypoint(index).withAccessory(accessory, mode, ticks));
    }

    private static Mission release(Mission mission, int index) {
        return set(mission, index, Waypoint.Accessory.GRIPPER_RELEASE, Waypoint.Repeat.ONCE, 0);
    }

    private static List<Integer> accessories(Mission m) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < m.size(); i++) {
            out.add(WaypointTasks.accessoryAt(m, i));
        }
        return out;
    }

    private static List<Boolean> releases(Mission m) {
        List<Boolean> out = new ArrayList<>();
        for (int i = 0; i < m.size(); i++) {
            out.add(WaypointTasks.due(m, i).release());
        }
        return out;
    }

    @Test
    void releaseIsANewValueOfTheAccessoryListAfterTheOldOnes() {
        Waypoint.Accessory[] values = Waypoint.Accessory.values();
        assertEquals(List.of("NONE", "ACTION_1", "ACTION_2", "ACTION_3", "ACTION_4", "END_REPEAT", "GRIPPER_RELEASE"),
                List.of(values).stream().map(Enum::name).toList(), "die Zahlen der alten Werte bleiben im Missionsformat gleich");
        assertEquals(0, Waypoint.Accessory.GRIPPER_RELEASE.number(), "keine Zubehör-Aktion 1 bis 4");
        assertEquals(Waypoint.Accessory.NONE, Waypoint.Accessory.of(6));
        Waypoint w = Waypoint.at(0.0, 70.0, 0.0);
        assertFalse(w.withAccessory(Waypoint.Accessory.GRIPPER_RELEASE, true).repeat(), "Greifer auslösen wiederholt sich nicht");
        assertEquals(0, w.withAccessory(Waypoint.Accessory.GRIPPER_RELEASE, Waypoint.Repeat.EVERY, 20).everyTicks(), "und läuft nicht im Takt");
        assertTrue(w.withAccessory(Waypoint.Accessory.GRIPPER_RELEASE, false).hasTasks());
        assertThrows(IllegalArgumentException.class, () -> new Waypoint(0, 800, 0, 0, 0, 0, Waypoint.Heading.COURSE, 0, Waypoint.Action.NONE, 0,
                Waypoint.Light.NONE, Waypoint.Accessory.GRIPPER_RELEASE, true));
    }

    @Test
    void releaseIsDueOnlyAtItsOwnWaypoint() {
        Mission m = release(line(4), 2);
        assertEquals(List.of(false, false, true, false), releases(m));
        WaypointTasks.Due due = WaypointTasks.due(m, 2);
        assertTrue(due.any());
        assertEquals(0, due.accessory());
        assertFalse(due.repeated());
        assertFalse(due.timed());
        assertEquals(1, WaypointTasks.all(m).size());
        assertFalse(due.withoutAccessory().accessory() > 0);
        assertFalse(WaypointTasks.due(m, 1).any());
        assertFalse(new WaypointTasks.Due(0, Waypoint.Light.ON, 1, false).release());
    }

    @Test
    void releaseEndsARepeatLikeAnyOtherAccessorySetting() {
        Mission m = set(line(6), 1, Waypoint.Accessory.ACTION_1, Waypoint.Repeat.EACH, 0);
        m = release(m, 3);
        assertEquals(List.of(0, 1, 1, 0, 0, 0), accessories(m));
        assertEquals(List.of(false, false, false, true, false, false), releases(m));
        assertEquals(1, WaypointTasks.repeatSource(m, 2));
        assertEquals(-1, WaypointTasks.repeatSource(m, 3));
        assertEquals(-1, WaypointTasks.repeatSource(m, 4));
    }

    @Test
    void releaseEndsATimedRange() {
        Mission m = set(line(5), 0, Waypoint.Accessory.ACTION_2, Waypoint.Repeat.EVERY, 10);
        m = release(m, 2);
        assertTrue(WaypointTasks.timedAt(m, 1).isPresent());
        assertTrue(WaypointTasks.timedAt(m, 2).isEmpty());
        assertTrue(WaypointTasks.timedAt(m, 3).isEmpty());
        assertEquals(-1, WaypointTasks.timedSource(m, 3));
        TaskTracker tracker = new TaskTracker();
        long tick = 0;
        tracker.update(m, RUNNING, 0, 0.0, 80.0, 0.0, tick);
        assertTrue(tracker.timer().isPresent());
        tracker.update(m, RUNNING, 1, 10.0, 80.0, 0.0, ++tick);
        tracker.update(m, RUNNING, 2, 15.0, 80.0, 0.0, ++tick);
        List<WaypointTasks.Due> at = tracker.update(m, RUNNING, 2, 20.0, 80.0, 0.0, ++tick);
        assertEquals(1, at.size());
        assertTrue(at.getFirst().release());
        assertTrue(tracker.timer().isEmpty(), "der Takt endet am Punkt mit „Greifer auslösen“");
    }

    @Test
    void trackerReleasesOncePerPassAndAtTheEnd() {
        Mission m = release(line(3), 1);
        TaskTracker tracker = new TaskTracker();
        assertTrue(tracker.update(m, RUNNING, 0, 0.0, 80.0, 0.0).isEmpty());
        assertTrue(tracker.update(m, RUNNING, 1, 5.0, 80.0, 0.0).isEmpty());
        List<WaypointTasks.Due> arrived = tracker.update(m, RUNNING, 1, 10.0, 80.0, 0.0);
        assertEquals(1, arrived.size());
        assertTrue(arrived.getFirst().release());
        assertEquals(1, arrived.getFirst().index());
        assertTrue(tracker.update(m, RUNNING, 1, 10.1, 80.0, 0.0).isEmpty(), "einmal je Durchgang");
        assertTrue(tracker.update(m, NavLogic.MissionState.PAUSED, 1, 10.0, 80.0, 0.0).isEmpty());
        assertTrue(tracker.update(m, RUNNING, 1, 10.0, 80.0, 0.0).isEmpty(), "Fortsetzen löst nicht erneut aus");
        Mission last = release(line(3), 2);
        TaskTracker end = new TaskTracker();
        end.update(last, RUNNING, 2, 15.0, 80.0, 0.0);
        List<WaypointTasks.Due> done = end.update(last, NavLogic.MissionState.DONE, 2, 20.0, 80.0, 0.0);
        assertEquals(1, done.size());
        assertTrue(done.getFirst().release());
    }

    @Test
    void missionsWithReleaseUseTheFourthFormatAndOlderOnesStayByteIdentical() {
        assertEquals(4, MissionLimits.FORMAT_VERSION);
        assertEquals(4, MissionLimits.GRIPPER_FORMAT_VERSION);
        assertEquals(3, MissionLimits.EVERY_FORMAT_VERSION);
        Mission plain = line(3);
        Mission tasks = set(plain, 1, Waypoint.Accessory.ACTION_1, Waypoint.Repeat.EACH, 0);
        Mission timed = set(plain, 1, Waypoint.Accessory.ACTION_1, Waypoint.Repeat.EVERY, 20);
        assertEquals(MissionLimits.FIRST_FORMAT_VERSION, MissionCodec.encode(plain)[0]);
        assertEquals(MissionLimits.TASK_FORMAT_VERSION, MissionCodec.encode(tasks)[0]);
        assertEquals(MissionLimits.EVERY_FORMAT_VERSION, MissionCodec.encode(timed)[0]);
        Mission gripper = release(timed, 2);
        int[] data = MissionCodec.encode(gripper);
        assertEquals(MissionLimits.GRIPPER_FORMAT_VERSION, data[0]);
        assertEquals(MissionLimits.packedInts(3, 0), data.length, "gleiche Größe wie bisher");
        Mission back = MissionCodec.decode(data).mission().orElseThrow();
        assertEquals(gripper, back);
        assertEquals(Waypoint.Accessory.GRIPPER_RELEASE, back.waypoint(2).accessory());
        assertArrayEquals(data, MissionCodec.encode(back));
        int[] timedData = MissionCodec.encode(timed);
        assertArrayEquals(timedData, MissionCodec.encode(MissionCodec.decode(timedData).mission().orElseThrow()), "Format 3 lädt weiter");
        assertEquals(MissionLimits.GRIPPER_FORMAT_VERSION, MissionCodec.encode(release(plain, 0))[0]);
    }

    @Test
    void releaseInAnOlderFormatIsRejected() {
        int[] data = MissionCodec.encode(release(line(2), 1));
        for (int version : new int[]{MissionLimits.TASK_FORMAT_VERSION, MissionLimits.EVERY_FORMAT_VERSION}) {
            int[] older = data.clone();
            older[0] = version;
            assertEquals(MissionCodec.Status.CORRUPT, MissionCodec.decode(older).status(), "Format " + version + " kennt „Greifer auslösen“ nicht");
        }
        int[] future = data.clone();
        future[0] = MissionLimits.FORMAT_VERSION + 1;
        assertEquals(MissionCodec.Status.UNKNOWN_VERSION, MissionCodec.decode(future).status());
        int[] plain = MissionCodec.encode(line(2));
        plain[0] = MissionLimits.GRIPPER_FORMAT_VERSION;
        assertEquals(line(2), MissionCodec.decode(plain).mission().orElseThrow(), "Format 4 ohne Greifer lädt");
    }
}
