package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.MissionShots;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionShotsTest {
    private static final Optional<Mission> MISSION = Optional.of(Mission.of(List.of(
            Waypoint.at(0, 70, 0).withAction(Waypoint.Action.RECORD_START),
            Waypoint.at(0, 70, -20).withAction(Waypoint.Action.PHOTO),
            Waypoint.at(20, 70, -20),
            Waypoint.at(20, 70, 0).withAction(Waypoint.Action.RECORD_STOP))));

    @Test
    void actionsFireOnceOnArrival() {
        MissionShots shots = new MissionShots();
        List<Waypoint.Action> all = new ArrayList<>();
        all.addAll(shots.update(MISSION, NavLogic.MissionState.RUNNING, 0, 1, 0.0, 70.0, 3.0));
        all.addAll(shots.update(MISSION, NavLogic.MissionState.RUNNING, 0, 1, 0.0, 70.0, 0.5));
        all.addAll(shots.update(MISSION, NavLogic.MissionState.RUNNING, 0, 1, 0.0, 70.0, 0.2));
        assertEquals(List.of(Waypoint.Action.RECORD_START), all);
        all.clear();
        all.addAll(shots.update(MISSION, NavLogic.MissionState.RUNNING, 1, 1, 0.0, 70.0, -10.0));
        all.addAll(shots.update(MISSION, NavLogic.MissionState.RUNNING, 1, 1, 0.0, 70.0, -19.0));
        all.addAll(shots.update(MISSION, NavLogic.MissionState.RUNNING, 2, 1, 5.0, 70.0, -20.0));
        assertEquals(List.of(Waypoint.Action.PHOTO), all);
    }

    @Test
    void missedArrivalFiresWhenTheIndexMovesOn() {
        MissionShots shots = new MissionShots();
        assertTrue(shots.update(MISSION, NavLogic.MissionState.RUNNING, 1, 3, 0.0, 70.0, -5.0).isEmpty());
        assertEquals(List.of(Waypoint.Action.PHOTO), shots.update(MISSION, NavLogic.MissionState.RUNNING, 2, 3, 10.0, 70.0, -20.0));
    }

    @Test
    void doneFiresTheLastActionAndLateJoinersStayQuiet() {
        MissionShots shots = new MissionShots();
        shots.update(MISSION, NavLogic.MissionState.RUNNING, 3, 2, 20.0, 70.0, -10.0);
        assertEquals(List.of(Waypoint.Action.RECORD_STOP), shots.update(MISSION, NavLogic.MissionState.DONE, 3, 2, 20.0, 70.0, -3.0));
        assertTrue(shots.update(MISSION, NavLogic.MissionState.DONE, 3, 2, 20.0, 70.0, 0.0).isEmpty());
        MissionShots late = new MissionShots();
        assertTrue(late.update(MISSION, NavLogic.MissionState.DONE, 3, 2, 20.0, 70.0, 0.0).isEmpty());
    }

    @Test
    void newEpochStartsOverAndLoopsRepeat() {
        MissionShots shots = new MissionShots();
        assertEquals(1, shots.update(MISSION, NavLogic.MissionState.RUNNING, 0, 1, 0.0, 70.0, 0.0).size());
        assertEquals(1, shots.update(MISSION, NavLogic.MissionState.RUNNING, 0, 2, 0.0, 70.0, 0.0).size(), "neue Epoche");
        shots.update(MISSION, NavLogic.MissionState.RUNNING, 3, 2, 20.0, 70.0, 0.0);
        List<Waypoint.Action> lap = shots.update(MISSION, NavLogic.MissionState.RUNNING, 0, 2, 0.0, 70.0, 0.0);
        assertEquals(List.of(Waypoint.Action.RECORD_START), lap, "Schleife: Punkt 4 schon ausgelöst, Punkt 1 erneut");
        assertEquals(1, shots.laps());
        assertTrue(shots.update(MISSION, NavLogic.MissionState.IDLE, 0, 2, 0.0, 70.0, 0.0).isEmpty());
        assertTrue(shots.update(Optional.empty(), NavLogic.MissionState.RUNNING, 0, 2, 0.0, 70.0, 0.0).isEmpty());
    }
}
