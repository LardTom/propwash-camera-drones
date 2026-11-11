package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.NavTarget;
import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.flight.SensorDirection;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutonomyAssistTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-00000000beef");

    private final FakeServerFlight.Recorder recorder = new FakeServerFlight.Recorder();
    private final FakeServerFlight.Environment environment = new FakeServerFlight.Environment();
    private final AtomicLong clock = new AtomicLong(1_000L);

    private AutonomyAssist assist(AutonomyPlan plan, boolean groundStart) {
        return new AutonomyAssist(FlightClass.TOUR, EnumSet.allOf(SensorDirection.class), plan, groundStart, ObstacleGuard.Mode.STOP,
                NavTarget.at(0.0, 64.0, 0.0), this.environment, this.recorder, this.clock::get);
    }

    private static Mission square(Mission.End end) {
        return Mission.of(List.of(Waypoint.at(8.0, 74.0, 0.0), Waypoint.at(8.0, 74.0, 8.0), Waypoint.at(0.0, 74.0, 8.0))).withEnd(end);
    }

    @Test
    void missionPlanStartsAtTheGivenWaypointWithoutPilot() {
        AutonomyAssist assist = this.assist(AutonomyPlan.mission(square(Mission.End.HOVER), 1), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.step(assist);
        assertEquals(1, this.recorder.started);
        assertEquals(NavLogic.Function.MISSION, assist.nav().logic().function());
        assertEquals(NavLogic.MissionState.RUNNING, assist.missionState());
        assertEquals(1, assist.missionIndex());
        assertTrue(this.recorder.events.contains("RUNNING 1"), this.recorder.events.toString());
        assertFalse(assist.nav().piloted());
        assertEquals(this.clock.get(), assist.lastUpdate());
    }

    @Test
    void missionFlownToTheEndHoversWhenStartedInTheAir() {
        AutonomyAssist assist = this.assist(AutonomyPlan.mission(square(Mission.End.START), 0), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(0.0, 74.0, 0.0);
        assertTrue(sim.runUntil(assist, 240.0, () -> assist.missionState() == NavLogic.MissionState.DONE), "Mission fertig");
        assertEquals(1, this.recorder.done);
        sim.run(assist, 5.0);
        assertFalse(assist.landing());
        assertEquals(0, sim.landCalls);
        assertFalse(sim.state.grounded);
    }

    @Test
    void missionStartedFromTheGroundLandsAtTheStartAfterReturning() {
        AutonomyAssist assist = this.assist(AutonomyPlan.mission(square(Mission.End.START), 0), true);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(0.0, 74.0, 0.0);
        assertTrue(sim.runUntil(assist, 240.0, () -> assist.missionState() == NavLogic.MissionState.DONE), "Mission fertig");
        assertTrue(sim.runUntil(assist, 60.0, () -> sim.state.grounded), "gelandet");
        assertTrue(assist.landing());
        assertTrue(sim.landCalls > 0);
        assertTrue(Math.hypot(sim.state.x, sim.state.z) < 1.5, "am Start gelandet: " + sim.state.x + " " + sim.state.z);
    }

    @Test
    void missionWithLandingEndAsksPropwashToLand() {
        AutonomyAssist assist = this.assist(AutonomyPlan.mission(square(Mission.End.LAND), 0), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(0.0, 74.0, 0.0);
        assertTrue(sim.runUntil(assist, 300.0, () -> sim.landCalls > 0), "Landung angefordert");
        assertEquals(NavLogic.MissionState.DONE, assist.missionState(), "alle Punkte abgeflogen, die Landung ist nur noch das Ende");
        assertEquals(EndDecision.decide(new EndDecision.Situation(AutonomyEnd.LANDED, AutonomyPlan.Kind.MISSION, true,
                assist.missionState(), assist.missionIndex(), false, MissionStatus.IDLE
                        .with(NavLogic.MissionState.RUNNING, assist.missionIndex()))).status().state(), NavLogic.MissionState.DONE);
    }

    @Test
    void pausedMissionHoldsAndResumesWhereItStopped() {
        AutonomyAssist assist = this.assist(AutonomyPlan.pausedMission(square(Mission.End.HOVER), 2), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.step(assist);
        assertEquals(NavLogic.MissionState.PAUSED, assist.missionState());
        assertEquals(NavLogic.Function.HOLD, assist.nav().logic().function());
        assertEquals(2, assist.missionIndex());
        assist.control(AutonomyAssist.Control.RESUME);
        sim.step(assist);
        assertEquals(NavLogic.MissionState.RUNNING, assist.missionState());
        assertEquals(2, assist.missionIndex());
        assist.control(AutonomyAssist.Control.PAUSE);
        sim.step(assist);
        assertEquals(NavLogic.MissionState.PAUSED, assist.missionState());
        assertEquals("HOLD", sim.out.label);
    }

    @Test
    void holdCommandPausesTheMissionAndKeepsItLoaded() {
        AutonomyAssist assist = this.assist(AutonomyPlan.mission(square(Mission.End.HOVER), 0), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.run(assist, 1.0);
        assist.control(AutonomyAssist.Control.HOLD);
        sim.step(assist);
        assertEquals(NavLogic.Function.HOLD, assist.nav().logic().function());
        assertEquals(NavLogic.MissionState.PAUSED, assist.missionState());
        assertTrue(assist.missionLoaded());
    }

    @Test
    void holdPlanHoversInPlaceAndNeverLands() {
        AutonomyAssist assist = this.assist(AutonomyPlan.hold(), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(3.0, 80.0, -2.0);
        sim.run(assist, 20.0);
        assertEquals(NavLogic.Function.HOLD, assist.nav().logic().function());
        assertEquals("HOLD", sim.out.label);
        assertEquals(0, sim.landCalls);
        assertTrue(Math.hypot(sim.state.x - 3.0, sim.state.z + 2.0) < 0.5);
        assertTrue(Math.abs(sim.state.y - 80.0) < 0.5);
        assertFalse(assist.missionLoaded());
        assertTrue(this.recorder.states.isEmpty() || this.recorder.states.stream().allMatch(s -> s == NavLogic.MissionState.IDLE));
    }

    @Test
    void homeExitHandsOverToPropwashsReturnFlightWithoutDropping() {
        AutonomyPlan plan = ExitDecision.decide(new ExitDecision.Situation(true, true, true, Optional.empty(), MissionStatus.IDLE, SmartState.NONE,
                DroneSettings.ExitBehaviour.HOME, TARGET, false, FlightClass.TOUR, 20.0, 84.0, 0.0))
                .orElseThrow(() -> new AssertionError("Heim liefert keinen Flug, die Drohne fiele"));
        AutonomyAssist assist = this.assist(plan, false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(20.0, 84.0, 0.0);
        sim.state.vy = -0.6;
        sim.step(assist);
        assertFalse(sim.out.returnHome, "erst ruhig schweben, dann heim");
        assertEquals("HOME", sim.out.label);
        assertEquals(20.0, sim.out.holdX, 1.0E-9, "hält bis zur Übergabe die Stelle");
        assertEquals(0.0, sim.out.holdZ, 1.0E-9);
        double lowest = sim.state.y;
        double requestedAt = Double.NaN;
        for (int i = 0; i < 200 && Double.isNaN(requestedAt); i++) {
            sim.step(assist);
            lowest = Math.min(lowest, sim.state.y);
            if (sim.out.returnHome) {
                requestedAt = sim.state.time;
            }
        }
        assertTrue(requestedAt >= AutonomyAssist.HOME_SETTLE_MIN_SECONDS && requestedAt <= AutonomyAssist.HOME_SETTLE_MAX_SECONDS + 0.1,
                "Heimflug angefordert nach " + requestedAt + " s");
        assertFalse(sim.out.land);
        assertEquals(84.0 + AutonomyAssist.HOME_CLIMB, sim.out.altitude, 1.0E-9, "steigt vor dem Heimflug etwas");
        assertTrue(Math.abs(sim.state.y - 84.0 - AutonomyAssist.HOME_CLIMB) <= AutonomyAssist.HOME_SETTLE_HEIGHT, "Höhe beim Heimflug: " + sim.state.y);
        assertTrue(84.0 - lowest < 0.3, "sackt nicht ab: " + lowest);
        assertEquals(1, this.recorder.started);
    }

    @Test
    void homeIsRequestedAtTheLatestAfterTheSettleTime() {
        AutonomyAssist assist = this.assist(AutonomyPlan.home(), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(0.0, 80.0, 0.0);
        sim.step(assist);
        sim.state.time += AutonomyAssist.HOME_SETTLE_MAX_SECONDS;
        sim.state.vy = 3.0;
        sim.step(assist);
        assertTrue(sim.out.returnHome);
    }

    @Test
    void missionStartedOnALandingPadLandsOnItsCentre() {
        NavTarget pad = NavTarget.at(0.5, 64.125, 0.5);
        this.environment.pad = pad;
        AutonomyAssist assist = this.assist(AutonomyPlan.mission(square(Mission.End.START), 0), true);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.terrain.block(0, 0, pad.y());
        sim.state.at(0.9, pad.y(), 0.85);
        sim.state.grounded = true;
        assertTrue(sim.runUntil(assist, 240.0, () -> assist.missionState() == NavLogic.MissionState.DONE), "Mission fertig");
        assertTrue(sim.runUntil(assist, 60.0, () -> sim.state.grounded), "gelandet");
        assertEquals(pad, assist.pad().orElseThrow());
        assertTrue(assist.padReleased());
        double off = Math.hypot(sim.state.x - pad.x(), sim.state.z - pad.z());
        assertTrue(off <= 0.1, "setzt " + off + " m neben der Mitte des Landeplatzes auf");
        assertEquals(pad.y(), sim.state.y, 1.0E-9, "auf der Oberseite des Landeplatzes");
    }

    @Test
    void landingWaitsAboveThePadUntilItIsCentred() {
        NavTarget pad = NavTarget.at(5.5, 64.125, 5.5);
        this.environment.pad = pad;
        AutonomyAssist assist = this.assist(AutonomyPlan.land(), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.terrain.block(5, 5, pad.y());
        sim.state.at(6.3, 70.0, 5.1);
        sim.step(assist);
        assertFalse(sim.out.land, "erst über die Mitte, dann landen");
        assertEquals(5.5, sim.out.holdX, 1.0E-9);
        assertEquals(5.5, sim.out.holdZ, 1.0E-9);
        assertEquals(0.0, sim.out.climb, 1.0E-9);
        assertEquals("LAND", sim.out.label);
        assertTrue(sim.runUntil(assist, 30.0, () -> sim.out.land), "Landung nach dem Ausrichten");
        assertEquals(1, this.environment.padCalls, "Landeplatz nur einmal je Landung gesucht");
        assertTrue(Math.abs(sim.state.y - 70.0) < 0.3, "hält beim Ausrichten die Höhe: " + sim.state.y);
        assertTrue(Math.hypot(sim.state.x - pad.x(), sim.state.z - pad.z()) <= AutonomyAssist.PAD_ALIGN_RADIUS + 1.0E-9);
        assertTrue(sim.runUntil(assist, 30.0, () -> sim.state.grounded));
        assertTrue(Math.hypot(sim.state.x - pad.x(), sim.state.z - pad.z()) <= 0.1);
    }

    @Test
    void missionEndingWithLandingOverAPadLandsOnIt() {
        NavTarget pad = NavTarget.at(0.5, 64.125, 8.5);
        this.environment.pad = pad;
        AutonomyAssist assist = this.assist(AutonomyPlan.mission(square(Mission.End.LAND), 0), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.terrain.block(0, 8, pad.y());
        sim.state.at(0.0, 74.0, 0.0);
        assertTrue(sim.runUntil(assist, 300.0, () -> sim.state.grounded), "gelandet");
        assertTrue(Math.hypot(sim.state.x - pad.x(), sim.state.z - pad.z()) <= 0.1, "auf dem Landeplatz: " + sim.state.x + " " + sim.state.z);
    }

    @Test
    void padBesideUnevenGroundIsLandedOnOnceTheDroneIsCentred() {
        NavTarget pad = NavTarget.at(0.5, 64.125, 0.5);
        this.environment.pad = pad;
        AutonomyAssist assist = this.assist(AutonomyPlan.land(), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.terrain.block(0, 0, pad.y()).uneven(1, 0, 64.5).uneven(1, 1, 64.5).uneven(1, -1, 64.5);
        sim.state.at(1.3, 70.0, 0.6);
        assertTrue(sim.runUntil(assist, 60.0, () -> sim.state.grounded), "gelandet");
        double off = Math.hypot(sim.state.x - pad.x(), sim.state.z - pad.z());
        assertTrue(off <= 0.1, "setzt " + off + " m neben der Mitte des Landeplatzes auf");
        assertEquals(pad.y(), sim.state.y, 1.0E-9, "auf der Oberseite des Landeplatzes");
    }

    @Test
    void landingOverUnevenGroundEndsOnTheLevelSpotPropwashPicks() {
        AutonomyAssist assist = this.assist(AutonomyPlan.land(), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.terrain.uneven(0, 0, 64.5);
        sim.state.at(0.5, 70.0, 0.5);
        assertTrue(sim.runUntil(assist, 60.0, () -> sim.state.grounded), "gelandet");
        double off = Math.hypot(sim.state.x - 0.5, sim.state.z - 0.5);
        assertTrue(off >= 1.5 && off <= 8.5, "Propwash weicht der unebenen Stelle aus, setzt " + off + " m daneben auf");
        assertEquals(64.0, sim.state.y, 1.0E-9, "auf ebenem Boden");
        assertEquals(1, sim.landCalls, "Propwash fliegt die Landung nach land() allein");
    }

    @Test
    void withoutPadTheDroneLandsWhereItIs() {
        AutonomyAssist assist = this.assist(AutonomyPlan.land(), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(6.3, 70.0, 5.1);
        sim.step(assist);
        assertTrue(sim.out.land);
        assertTrue(assist.pad().isEmpty());
        assertEquals(1, this.environment.padCalls);
        sim.step(assist);
        assertEquals(1, this.environment.padCalls, "Landeplatz nur einmal je Landung gesucht");
    }

    @Test
    void padAlignmentGivesUpAfterTheTimeLimit() {
        NavTarget pad = NavTarget.at(5.5, 64.125, 5.5);
        this.environment.pad = pad;
        AutonomyAssist assist = this.assist(AutonomyPlan.land(), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(6.3, 70.0, 5.1);
        sim.step(assist);
        assertFalse(sim.out.land);
        sim.state.time += AutonomyAssist.PAD_ALIGN_SECONDS;
        sim.state.vx = 2.0;
        sim.step(assist);
        assertTrue(sim.out.land, "landet nach " + AutonomyAssist.PAD_ALIGN_SECONDS + " s auch ohne genaue Mitte");
    }

    @Test
    void afterLandTheAssistNoLongerSteersAndPropwashLandsWhereItWasCalled() {
        NavTarget pad = NavTarget.at(5.5, 64.125, 5.5);
        this.environment.pad = pad;
        AutonomyAssist assist = this.assist(AutonomyPlan.land(), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.terrain.block(5, 5, pad.y());
        sim.state.at(6.3, 70.0, 5.1);
        sim.step(assist);
        sim.state.time += AutonomyAssist.PAD_ALIGN_SECONDS;
        sim.step(assist);
        assertTrue(sim.out.land, "landet nach " + AutonomyAssist.PAD_ALIGN_SECONDS + " s ohne genaue Mitte");
        double x = sim.state.x;
        double z = sim.state.z;
        assertTrue(sim.runUntil(assist, 30.0, () -> sim.state.grounded), "gelandet");
        double drift = Math.hypot(sim.state.x - x, sim.state.z - z);
        assertTrue(drift <= 0.3, "setzt " + drift + " m neben dem Punkt von land() auf");
        assertEquals(1, sim.landCalls);
    }

    @Test
    void landPlanLandsOnTheSpot() {
        AutonomyAssist assist = this.assist(AutonomyPlan.land(), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(5.0, 70.0, 5.0);
        assertTrue(sim.runUntil(assist, 30.0, () -> sim.state.grounded));
        assertEquals("LAND", sim.out.label);
        assertTrue(sim.out.land);
        assertTrue(Math.hypot(sim.state.x - 5.0, sim.state.z - 5.0) < 0.5);
    }

    @Test
    void stopAbortsTheMissionAndLands() {
        AutonomyAssist assist = this.assist(AutonomyPlan.mission(square(Mission.End.HOVER), 0), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.run(assist, 2.0);
        assist.control(AutonomyAssist.Control.STOP);
        sim.step(assist);
        assertEquals(NavLogic.MissionState.ABORTED, assist.missionState());
        assertTrue(assist.landing());
        assertTrue(sim.out.land);
        assertTrue(this.recorder.states.contains(NavLogic.MissionState.ABORTED));
    }

    @Test
    void stopOnAHoldFlightBecomesALanding() {
        AutonomyAssist assist = this.assist(AutonomyPlan.hold(), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.step(assist);
        assist.control(AutonomyAssist.Control.STOP);
        sim.step(assist);
        assertEquals(AutonomyPlan.Kind.LAND, assist.plan().kind());
        assertTrue(sim.out.land);
    }

    @Test
    void followUsesTheEnvironmentTargetAndHoldsWhenItIsLost() {
        AutonomyAssist assist = this.assist(AutonomyPlan.follow(TARGET, FollowController.Style.BEHIND), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        this.environment.targets.put(TARGET, new NavTarget(20.0, 64.0, 0.0, 0.0, 0.0, 0.0));
        sim.run(assist, 15.0);
        assertEquals(NavLogic.Function.FOLLOW, assist.nav().logic().function());
        assertTrue(this.environment.followCalls > 0);
        double distance = Math.hypot(sim.state.x - 20.0, sim.state.z);
        assertTrue(distance > FollowController.MIN_DISTANCE && distance < FollowController.DISTANCE + 3.0, "Abstand " + distance);
        this.environment.targets.clear();
        sim.run(assist, AutonomyAssist.TARGET_LOST_SECONDS + 0.5);
        assertEquals(1, this.recorder.lost);
        assertEquals(AutonomyPlan.Kind.HOLD, assist.plan().kind());
        sim.step(assist);
        assertEquals(NavLogic.Function.HOLD, assist.nav().logic().function());
    }

    @Test
    void orbitCirclesTheGivenCenter() {
        AutonomyAssist assist = this.assist(AutonomyPlan.orbit(0.0, 64.0, 0.0, 10.0, 3.0, true), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(10.0, 74.0, 0.0);
        sim.run(assist, 20.0);
        assertEquals(NavLogic.Function.ORBIT, assist.nav().logic().function());
        double radius = Math.hypot(sim.state.x, sim.state.z);
        assertTrue(Math.abs(radius - 10.0) < 2.0, "Radius " + radius);
        assertTrue(Math.abs(sim.state.y - 74.0) < 1.0, "Höhe " + sim.state.y);
    }

    @Test
    void replanSwitchesTheFunctionWithoutANewFlight() {
        AutonomyAssist assist = this.assist(AutonomyPlan.hold(), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.step(assist);
        assist.replan(AutonomyPlan.mission(square(Mission.End.HOVER), 2));
        assertEquals(AutonomyPlan.Kind.MISSION, assist.plan().kind());
        sim.step(assist);
        assertEquals(NavLogic.Function.MISSION, assist.nav().logic().function());
        assertEquals(2, assist.missionIndex());
        assertEquals(1, this.recorder.started);
    }

    @Test
    void resetKeepsTheMissionProgress() {
        AutonomyAssist assist = this.assist(AutonomyPlan.mission(square(Mission.End.HOVER), 0), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.state.at(0.0, 74.0, 0.0);
        assertTrue(sim.runUntil(assist, 120.0, () -> assist.missionIndex() >= 1));
        int index = assist.missionIndex();
        assist.reset();
        sim.step(assist);
        assertEquals(NavLogic.MissionState.RUNNING, assist.missionState());
        assertEquals(index, assist.missionIndex());
    }

    @Test
    void limitsComeFromTheWorldOfTheDrone() {
        this.environment.maxAltitude = 40;
        this.environment.maxDistance = 300;
        AutonomyAssist assist = this.assist(AutonomyPlan.hold(), false);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.step(assist);
        assertEquals(40, assist.nav().maxAltitude());
        assertEquals(300, assist.nav().maxDistance());
        assertEquals(64.0, assist.nav().home().orElseThrow().y());
        assertTrue(assist.sensorRange() > 0.0);
    }
}
