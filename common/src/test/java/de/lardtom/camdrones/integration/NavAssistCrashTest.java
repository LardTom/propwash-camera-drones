package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.propwash.api.flight.SensorDirection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NavAssistCrashTest {
    private static final double DT = 0.01;

    static final class Context implements NavContext {
        Mission mission;
        final List<String> progress = new ArrayList<>();
        NavStatus status;

        @Override
        public Optional<Mission> mission() {
            return Optional.ofNullable(this.mission);
        }

        @Override
        public void missionProgress(NavLogic.MissionState state, int index) {
            this.progress.add(state + " " + index);
        }

        @Override
        public void status(NavStatus value) {
            this.status = value;
        }
    }

    private static void run(NavAssist assist, FakeFlight.State state, int steps) {
        for (int i = 0; i < steps; i++) {
            state.step(DT);
            assist.update(state, new FakeFlight.Output());
        }
    }

    private static NavAssist tour(Context context) {
        return new NavAssist(FlightClass.TOUR, EnumSet.allOf(SensorDirection.class), true, context);
    }

    private static void crash(NavAssist assist, FakeFlight.State state) {
        state.vx = 9.5;
        run(assist, state, 5);
        state.vx = 0.0;
        state.armed = false;
        run(assist, state, 1);
    }

    @Test
    void aCrashEndsCourseLockAndItStaysOffAfterArmingAgain() {
        Context context = new Context();
        FakeFlight.State state = new FakeFlight.State().at(0.0, 70.0, 0.0);
        NavAssist assist = tour(context);
        run(assist, state, 2);
        state.on(CamControl.COURSE_LOCK);
        run(assist, state, 2);
        assertEquals(NavLogic.Function.COURSE_LOCK, assist.logic().function());
        crash(assist, state);
        assertEquals(NavLogic.Function.NONE, assist.logic().function(), "Crash beendet Course Lock");
        assertEquals(NavLogic.Function.NONE, context.status.function());
        run(assist, state, 60);
        state.armed = true;
        run(assist, state, 60);
        assertEquals(NavLogic.Function.NONE, assist.logic().function(), "Schalter steht noch, schaltet nach dem Scharfschalten nicht wieder ein");
    }

    @Test
    void aCrashAbortsARunningMission() {
        Context context = new Context();
        context.mission = Mission.of(List.of(Waypoint.at(60.0, 70.0, 0.0), Waypoint.at(60.0, 70.0, 60.0)));
        FakeFlight.State state = new FakeFlight.State().at(0.0, 70.0, 0.0);
        NavAssist assist = tour(context);
        run(assist, state, 2);
        state.on(CamControl.MISSION_START);
        run(assist, state, 5);
        assertEquals(NavLogic.MissionState.RUNNING, assist.logic().missionState());
        crash(assist, state);
        assertEquals(NavLogic.MissionState.ABORTED, assist.logic().missionState());
        assertEquals(NavLogic.Function.NONE, assist.logic().function());
        assertEquals("ABORTED 0", context.progress.getLast());
    }

    @Test
    void disarmingInTheAirCountsAsCrash() {
        Context context = new Context();
        FakeFlight.State state = new FakeFlight.State().at(0.0, 70.0, 0.0).on(CamControl.FOLLOW_ME);
        NavAssist assist = tour(context);
        run(assist, state, 3);
        assertEquals(NavLogic.Function.FOLLOW, assist.logic().function());
        state.armed = false;
        run(assist, state, 1);
        assertEquals(NavLogic.Function.NONE, assist.logic().function());
    }

    @Test
    void landingAndDisarmingKeepsAPausedMission() {
        Context context = new Context();
        context.mission = Mission.of(List.of(Waypoint.at(60.0, 70.0, 0.0), Waypoint.at(60.0, 70.0, 60.0)));
        FakeFlight.State state = new FakeFlight.State().at(0.0, 70.0, 0.0);
        NavAssist assist = tour(context);
        run(assist, state, 2);
        state.on(CamControl.MISSION_START);
        run(assist, state, 5);
        state.off(CamControl.MISSION_START).on(CamControl.PAUSE);
        run(assist, state, 5);
        assertEquals(NavLogic.MissionState.PAUSED, assist.logic().missionState());
        state.off(CamControl.PAUSE);
        state.y = 64.0;
        state.heightAboveGround = 0.0;
        state.grounded = true;
        run(assist, state, 20);
        state.armed = false;
        run(assist, state, 5);
        assertEquals(NavLogic.MissionState.PAUSED, assist.logic().missionState(), "gelandet und entschärft ist kein Crash");
    }

    @Test
    void aCrashOnAFlightWithoutPilotAbortsTheMissionAndHolds() {
        Context context = new Context();
        FakeFlight.State state = new FakeFlight.State().at(0.0, 70.0, 0.0);
        NavAssist assist = new NavAssist(FlightClass.TOUR, EnumSet.allOf(SensorDirection.class), false, context);
        assist.logic().startMission(Mission.of(List.of(Waypoint.at(60.0, 70.0, 0.0), Waypoint.at(60.0, 70.0, 60.0))), 0);
        run(assist, state, 5);
        crash(assist, state);
        assertEquals(NavLogic.MissionState.ABORTED, assist.logic().missionState());
        assertEquals(NavLogic.Function.HOLD, assist.logic().function());
    }
}
