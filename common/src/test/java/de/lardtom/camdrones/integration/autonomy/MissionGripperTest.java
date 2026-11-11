package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.logic.nav.NavLogic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionGripperTest {
    private static final NavLogic.MissionState RUNNING = NavLogic.MissionState.RUNNING;

    private static List<Long> grabs(MissionGripper gripper, NavLogic.MissionState state, boolean loaded, long from, long to) {
        List<Long> out = new ArrayList<>();
        for (long tick = from; tick < to; tick++) {
            if (gripper.renew(state, loaded, tick)) {
                out.add(tick);
            }
        }
        return out;
    }

    @Test
    void anEmptyGripperIsReadyEverySecondOfARunningMission() {
        assertEquals(20, MissionGripper.RENEW_TICKS);
        assertTrue(MissionGripper.RENEW_TICKS * 4 < MissionGripper.GRAB_WINDOW_TICKS, "der Griff aus der Propwash-API bleibt 5 s offen");
        MissionGripper gripper = new MissionGripper();
        assertEquals(List.of(100L, 120L, 140L, 160L, 180L), grabs(gripper, RUNNING, false, 100, 200));
    }

    @Test
    void withCargoNoNewGrabAndAfterTheReleaseRightAway() {
        MissionGripper gripper = new MissionGripper();
        assertTrue(gripper.renew(RUNNING, false, 0));
        assertEquals(List.of(), grabs(gripper, RUNNING, true, 1, 300), "solange Fracht hängt");
        assertTrue(gripper.renew(RUNNING, false, 300), "nach dem Auslösen sofort wieder bereit");
        assertFalse(gripper.renew(RUNNING, false, 301));
    }

    @Test
    void onlyWhileTheMissionRuns() {
        for (NavLogic.MissionState state : NavLogic.MissionState.values()) {
            MissionGripper gripper = new MissionGripper();
            List<Long> seen = grabs(gripper, state, false, 0, 100);
            if (state == RUNNING) {
                assertEquals(5, seen.size());
            } else {
                assertEquals(List.of(), seen, state + ": kein Dauergriff ohne laufende Mission");
            }
        }
        MissionGripper resumed = new MissionGripper();
        assertTrue(resumed.renew(RUNNING, false, 0));
        assertFalse(resumed.renew(NavLogic.MissionState.PAUSED, false, 30));
        assertTrue(resumed.renew(RUNNING, false, 31), "nach der Pause sofort wieder bereit");
        assertFalse(resumed.renew(null, false, 60));
    }

    @Test
    void anOpenGrabClosesWhenTheMissionStopsRunning() {
        MissionGripper done = new MissionGripper();
        assertTrue(done.renew(RUNNING, false, 0));
        assertFalse(done.close(RUNNING, false, 10));
        assertFalse(done.renew(NavLogic.MissionState.DONE, false, 50));
        assertTrue(done.close(NavLogic.MissionState.DONE, false, 50), "kein Griff mehr nach dem Missionsende");
        assertFalse(done.close(NavLogic.MissionState.DONE, false, 51), "nur einmal");
        MissionGripper expired = new MissionGripper();
        assertTrue(expired.renew(RUNNING, false, 0));
        assertFalse(expired.close(NavLogic.MissionState.DONE, false, MissionGripper.GRAB_WINDOW_TICKS), "Griff schon abgelaufen");
        MissionGripper loaded = new MissionGripper();
        assertTrue(loaded.renew(RUNNING, false, 0));
        assertFalse(loaded.renew(RUNNING, true, 5));
        assertFalse(loaded.close(NavLogic.MissionState.DONE, true, 6), "Fracht bleibt am Greifer");
        assertFalse(loaded.close(NavLogic.MissionState.DONE, false, 7), "nach dem Aufnehmen ist der Griff schon zu");
        assertFalse(new MissionGripper().close(NavLogic.MissionState.IDLE, false, 0), "ohne Griff nichts zu schließen");
    }
}
