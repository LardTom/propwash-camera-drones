package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExitAndEndDecisionTest {
    private static final UUID PILOT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FRIEND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Mission LINE = Mission.of(List.of(Waypoint.at(0.0, 80.0, 0.0), Waypoint.at(20.0, 80.0, 0.0), Waypoint.at(40.0, 80.0, 0.0),
            Waypoint.at(60.0, 80.0, 0.0)));

    private static ExitDecision.Situation exit(boolean allow, boolean airborne, Optional<Mission> mission, MissionStatus status, SmartState smart,
                                               DroneSettings.ExitBehaviour behaviour, boolean followAllowed, double x) {
        return new ExitDecision.Situation(allow, true, airborne, mission, status, smart, behaviour, PILOT, followAllowed, FlightClass.TOUR, x, 80.0,
                0.0);
    }

    @Test
    void runningMissionContinuesFromTheNearestSegmentAfterTheReportedWaypoint() {
        MissionStatus status = new MissionStatus(NavLogic.MissionState.RUNNING, 1, 7);
        AutonomyPlan plan = ExitDecision.decide(exit(true, true, Optional.of(LINE), status, SmartState.NONE, DroneSettings.ExitBehaviour.HOME,
                false, 45.0)).orElseThrow();
        assertEquals(AutonomyPlan.Kind.MISSION, plan.kind());
        assertFalse(plan.paused());
        assertEquals(3, plan.startIndex());
        plan = ExitDecision.decide(exit(true, true, Optional.of(LINE), status, SmartState.NONE, DroneSettings.ExitBehaviour.HOME, false, 5.0))
                .orElseThrow();
        assertEquals(1, plan.startIndex(), "nie vor den gemeldeten Wegpunkt zurück");
    }

    @Test
    void pausedMissionStaysPausedWithoutPilot() {
        MissionStatus status = new MissionStatus(NavLogic.MissionState.PAUSED, 2, 1);
        AutonomyPlan plan = ExitDecision.decide(exit(true, true, Optional.of(LINE), status, SmartState.NONE, DroneSettings.ExitBehaviour.LAND,
                false, 0.0)).orElseThrow();
        assertTrue(plan.paused());
        assertEquals(2, plan.startIndex());
        assertEquals(AutonomyStatus.MISSION_PAUSED, plan.startedStatus());
    }

    @Test
    void smartFunctionsContinueOrFallBackToHold() {
        AutonomyPlan follow = ExitDecision.decide(exit(true, true, Optional.empty(), MissionStatus.IDLE,
                SmartState.follow(PILOT, FollowController.Style.PARALLEL), DroneSettings.ExitBehaviour.HOME, true, 0.0)).orElseThrow();
        assertEquals(AutonomyPlan.Kind.FOLLOW, follow.kind());
        assertEquals(PILOT, follow.target().orElseThrow());
        assertEquals(FollowController.Style.PARALLEL, follow.style());
        AutonomyPlan denied = ExitDecision.decide(exit(true, true, Optional.empty(), MissionStatus.IDLE,
                SmartState.follow(FRIEND, FollowController.Style.BEHIND), DroneSettings.ExitBehaviour.HOME, false, 0.0)).orElseThrow();
        assertEquals(AutonomyPlan.Kind.HOLD, denied.kind());
        AutonomyPlan orbit = ExitDecision.decide(exit(true, true, Optional.empty(), MissionStatus.IDLE,
                SmartState.pointOfInterest(5.0, 64.0, 5.0, 12.0, 0.0, false), DroneSettings.ExitBehaviour.HOME, false, 0.0)).orElseThrow();
        assertEquals(AutonomyPlan.Kind.ORBIT, orbit.kind());
        assertEquals(12.0, orbit.radius());
        assertEquals(FlightClass.TOUR.cineSpeed(), orbit.speed());
        assertFalse(orbit.clockwise());
        assertEquals(AutonomyPlan.Kind.HOLD, ExitDecision.decide(exit(true, true, Optional.empty(), MissionStatus.IDLE,
                SmartState.courseLock(90.0), DroneSettings.ExitBehaviour.HOME, false, 0.0)).orElseThrow().kind());
    }

    @Test
    void settingDecidesWithoutMissionOrFunction() {
        assertEquals(AutonomyPlan.Kind.HOLD, ExitDecision.decide(exit(true, true, Optional.of(LINE), MissionStatus.IDLE, SmartState.NONE,
                DroneSettings.ExitBehaviour.HOVER, false, 0.0)).orElseThrow().kind());
        assertEquals(AutonomyPlan.Kind.LAND, ExitDecision.decide(exit(true, true, Optional.empty(), MissionStatus.IDLE, SmartState.NONE,
                DroneSettings.ExitBehaviour.LAND, false, 0.0)).orElseThrow().kind());
        AutonomyPlan home = ExitDecision.decide(exit(true, true, Optional.empty(), MissionStatus.IDLE, SmartState.NONE,
                DroneSettings.ExitBehaviour.HOME, false, 0.0)).orElseThrow(() -> new AssertionError("Heim übernimmt nahtlos, bevor Propwash heimfliegt"));
        assertEquals("HOME", home.label());
        assertTrue(home.mission().isEmpty());
    }

    @Test
    void propwashKeepsItsBehaviourWhenTheRuleIsOffOrTheDroneIsGrounded() {
        MissionStatus running = new MissionStatus(NavLogic.MissionState.RUNNING, 0, 0);
        assertTrue(ExitDecision.decide(exit(false, true, Optional.of(LINE), running, SmartState.NONE, DroneSettings.ExitBehaviour.HOVER, false,
                0.0)).isEmpty());
        assertTrue(ExitDecision.decide(exit(true, false, Optional.of(LINE), running, SmartState.NONE, DroneSettings.ExitBehaviour.HOVER, false,
                0.0)).isEmpty());
        assertTrue(ExitDecision.decide(new ExitDecision.Situation(true, false, true, Optional.of(LINE), running, SmartState.NONE,
                DroneSettings.ExitBehaviour.HOVER, PILOT, false, FlightClass.TOUR, 0.0, 80.0, 0.0)).isEmpty(), "keine Kamera-Drohne");
    }

    private static EndDecision.Outcome end(AutonomyEnd reason, NavLogic.MissionState state, boolean replaced) {
        return EndDecision.decide(new EndDecision.Situation(reason, AutonomyPlan.Kind.MISSION, true, state, 4, replaced,
                new MissionStatus(NavLogic.MissionState.RUNNING, 3, 9)));
    }

    @Test
    void everyEndReasonGetsAMessageForTheOwner() {
        for (AutonomyEnd reason : AutonomyEnd.values()) {
            EndDecision.Outcome outcome = end(reason, NavLogic.MissionState.RUNNING, false);
            assertEquals(AutonomyStatus.ended(reason), outcome.message().orElseThrow(), reason.name());
            assertTrue(outcome.clearSmart());
            assertEquals(9, outcome.status().epoch(), "Epoche bleibt");
        }
    }

    @Test
    void pilotTakeoverPausesAndOtherEndsAbortTheMission() {
        assertEquals(NavLogic.MissionState.PAUSED, end(AutonomyEnd.PILOT, NavLogic.MissionState.RUNNING, false).status().state());
        assertEquals(4, end(AutonomyEnd.PILOT, NavLogic.MissionState.RUNNING, false).status().index());
        for (AutonomyEnd reason : AutonomyEnd.values()) {
            if (reason != AutonomyEnd.PILOT) {
                assertEquals(NavLogic.MissionState.ABORTED, end(reason, NavLogic.MissionState.RUNNING, false).status().state(), reason.name());
            }
        }
    }

    @Test
    void landingAfterACompletedMissionReportsDone() {
        EndDecision.Outcome outcome = end(AutonomyEnd.LANDED, NavLogic.MissionState.DONE, false);
        assertEquals(NavLogic.MissionState.DONE, outcome.status().state());
        assertEquals(AutonomyStatus.MISSION_DONE, outcome.message().orElseThrow());
        assertEquals(NavLogic.MissionState.ABORTED, end(AutonomyEnd.LANDED, NavLogic.MissionState.ABORTED, false).status().state());
    }

    @Test
    void returnFlightHandOverLeavesTheMessageToPropwash() {
        EndDecision.Outcome handover = EndDecision.decide(new EndDecision.Situation(AutonomyEnd.STOPPED, AutonomyPlan.Kind.HOME, false,
                NavLogic.MissionState.IDLE, 0, false, MissionStatus.IDLE));
        assertTrue(handover.message().isEmpty(), "Propwash meldet seinen Heimflug selbst");
        assertTrue(handover.clearSmart());
        EndDecision.Outcome pilot = EndDecision.decide(new EndDecision.Situation(AutonomyEnd.PILOT, AutonomyPlan.Kind.HOME, false,
                NavLogic.MissionState.IDLE, 0, false, MissionStatus.IDLE));
        assertEquals(AutonomyStatus.ENDED_PILOT, pilot.message().orElseThrow());
        assertEquals(AutonomyStatus.HOME_STARTED, AutonomyPlan.home().startedStatus());
        assertFalse(AutonomyPlan.home().startedStatus().error());
    }

    @Test
    void ownReplacementChangesNothing() {
        EndDecision.Outcome outcome = end(AutonomyEnd.REPLACED, NavLogic.MissionState.RUNNING, true);
        assertTrue(outcome.message().isEmpty());
        assertFalse(outcome.clearSmart());
        assertEquals(NavLogic.MissionState.RUNNING, outcome.status().state());
    }

    @Test
    void flightsWithoutMissionLeaveTheMissionStateAlone() {
        MissionStatus paused = new MissionStatus(NavLogic.MissionState.PAUSED, 2, 3);
        EndDecision.Outcome outcome = EndDecision.decide(new EndDecision.Situation(AutonomyEnd.BATTERY, AutonomyPlan.Kind.HOLD, false,
                NavLogic.MissionState.IDLE, 0, false, paused));
        assertEquals(paused, outcome.status());
        assertEquals(AutonomyStatus.ENDED_BATTERY, outcome.message().orElseThrow());
    }
}
