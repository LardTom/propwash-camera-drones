package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.autonomy.client.PilotRelay;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PilotRelayTest {
    private static final UUID DRONE = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final Mission FOUR = Mission.of(List.of(Waypoint.at(0, 70, 0), Waypoint.at(10, 70, 0), Waypoint.at(20, 70, 0),
            Waypoint.at(30, 70, 0)));

    private static int presses(PilotRelay relay, CamControl control) {
        int count = 0;
        for (int i = 0; i < 10; i++) {
            if (relay.pulse(control)) {
                count++;
            }
        }
        return count;
    }

    @Test
    void firstObservationOnlyLearnsTheEpoch() {
        PilotRelay relay = new PilotRelay();
        relay.observe(DRONE, new MissionStatus(NavLogic.MissionState.RUNNING, 1, 5), NavLogic.MissionState.IDLE);
        assertFalse(relay.pending(), "alte Befehle werden nicht nachgespielt");
        assertEquals(5, relay.epoch());
        assertTrue(relay.seen());
    }

    @Test
    void newEpochWithRunningPressesMissionStartOnce() {
        PilotRelay relay = new PilotRelay();
        relay.observe(DRONE, new MissionStatus(NavLogic.MissionState.IDLE, 0, 1), NavLogic.MissionState.IDLE);
        relay.observe(DRONE, new MissionStatus(NavLogic.MissionState.RUNNING, 0, 2), NavLogic.MissionState.IDLE);
        assertEquals(PilotRelay.PULSE_UPDATES, presses(relay, CamControl.MISSION_START));
        assertEquals(0, presses(relay, CamControl.PAUSE));
        relay.observe(DRONE, new MissionStatus(NavLogic.MissionState.RUNNING, 0, 2), NavLogic.MissionState.RUNNING);
        assertFalse(relay.pending(), "gleiche Epoche ändert nichts");
    }

    @Test
    void pauseStopAndAbortPressPauseOnlyWhileTheLocalMissionRuns() {
        PilotRelay relay = new PilotRelay();
        relay.observe(DRONE, new MissionStatus(NavLogic.MissionState.RUNNING, 1, 1), NavLogic.MissionState.RUNNING);
        relay.observe(DRONE, new MissionStatus(NavLogic.MissionState.PAUSED, 1, 2), NavLogic.MissionState.RUNNING);
        assertEquals(PilotRelay.PULSE_UPDATES, presses(relay, CamControl.PAUSE));
        relay.observe(DRONE, new MissionStatus(NavLogic.MissionState.ABORTED, 1, 3), NavLogic.MissionState.PAUSED);
        assertFalse(relay.pending());
    }

    @Test
    void anotherDroneStartsOver() {
        PilotRelay relay = new PilotRelay();
        relay.observe(DRONE, new MissionStatus(NavLogic.MissionState.IDLE, 0, 1), NavLogic.MissionState.IDLE);
        relay.observe(UUID.randomUUID(), new MissionStatus(NavLogic.MissionState.RUNNING, 0, 7), NavLogic.MissionState.IDLE);
        assertFalse(relay.pending());
        assertEquals(7, relay.epoch());
    }

    @Test
    void pausedMissionContinuesFromItsWaypointAndReportsAbsoluteIndices() {
        PilotRelay relay = new PilotRelay();
        Mission trimmed = relay.mission(Optional.of(FOUR), new MissionStatus(NavLogic.MissionState.PAUSED, 2, 0)).orElseThrow();
        assertEquals(2, trimmed.size());
        assertEquals(20.0, trimmed.waypoint(0).x());
        assertEquals(2, relay.offset());
        assertEquals(3, relay.absolute(1));
        assertEquals(4, relay.mission(Optional.of(FOUR), new MissionStatus(NavLogic.MissionState.DONE, 2, 0)).orElseThrow().size());
        assertEquals(0, relay.offset());
        assertEquals(4, relay.mission(Optional.of(FOUR.withLoop(true)), new MissionStatus(NavLogic.MissionState.PAUSED, 2, 0)).orElseThrow()
                .size(), "Schleifen bleiben ganz");
        assertTrue(relay.mission(Optional.empty(), MissionStatus.IDLE).isEmpty());
    }

    @Test
    void functionChangesAreReportedOnce() {
        PilotRelay relay = new PilotRelay();
        assertTrue(relay.changed(NavLogic.Function.FOLLOW).isEmpty(), "ohne Drohne nichts");
        relay.observe(DRONE, MissionStatus.IDLE, NavLogic.MissionState.IDLE);
        assertEquals(SmartState.Function.NONE, relay.changed(NavLogic.Function.NONE).orElseThrow());
        assertTrue(relay.changed(NavLogic.Function.MISSION).isEmpty(), "Mission zählt als keine Funktion");
        assertEquals(SmartState.Function.FOLLOW, relay.changed(NavLogic.Function.FOLLOW).orElseThrow());
        assertTrue(relay.changed(NavLogic.Function.FOLLOW).isEmpty());
        assertEquals(SmartState.Function.POI, PilotRelay.function(NavLogic.Function.ORBIT));
        assertEquals(SmartState.Function.HOLD, PilotRelay.function(NavLogic.Function.HOLD));
        assertEquals(SmartState.Function.COURSE_LOCK, PilotRelay.function(NavLogic.Function.COURSE_LOCK));
        assertEquals(SmartState.Function.HOLD, PilotRelay.function(NavLogic.Function.AUTO_SHOT));
    }
}
