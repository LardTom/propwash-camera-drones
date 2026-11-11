package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.NavTarget;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.propwash.api.flight.SensorDirection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionInterruptTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-0000000a0013");
    private static final int EPOCH = 7;
    private static final Mission SQUARE = Mission.of(List.of(Waypoint.at(8.0, 74.0, 0.0), Waypoint.at(8.0, 74.0, 8.0), Waypoint.at(0.0, 74.0, 8.0),
            Waypoint.at(0.0, 74.0, 0.0))).withEnd(Mission.End.HOVER);

    private final FakeServerFlight.Environment environment = new FakeServerFlight.Environment();
    private final AtomicLong clock = new AtomicLong(1_000L);

    private static final class DroneData implements AutonomyAssist.Listener {
        MissionStatus status;
        final List<String> events = new ArrayList<>();

        DroneData(MissionStatus status) {
            this.status = status;
        }

        @Override
        public void progress(AutonomyAssist assist, NavLogic.MissionState state, int index) {
            this.events.add(state + " " + index + " " + assist.plan().kind());
            ProgressCheck.fromFlight(assist.missionLoaded(), this.status, state, index).ifPresent(next -> this.status = next);
        }
    }

    private AutonomyAssist assist(AutonomyPlan plan, DroneData data) {
        return new AutonomyAssist(FlightClass.TOUR, EnumSet.allOf(SensorDirection.class), plan, false, ObstacleGuard.Mode.STOP,
                NavTarget.at(0.0, 64.0, 0.0), this.environment, data, this.clock::get);
    }

    private static CommandDecision.Decision ownFlight(MissionCommand.Command command, MissionStatus status) {
        return CommandDecision.decide(new CommandDecision.Situation(command, MissionCommand.AUTO_INDEX, true, false, true, true, true, false,
                true, false, true, SQUARE.size(), status));
    }

    private int interrupt(AutonomyPlan replacement, DroneData data, AutonomyAssist assist, FakeServerFlight.Sim sim) {
        sim.state.at(0.0, 74.0, 0.0);
        assertTrue(sim.runUntil(assist, 120.0, () -> assist.missionIndex() >= 1), "Mission erreicht Wegpunkt 2");
        assertEquals(new MissionStatus(NavLogic.MissionState.RUNNING, assist.missionIndex(), EPOCH), data.status, data.events.toString());
        int index = assist.missionIndex();
        assist.replan(replacement);
        sim.run(assist, 1.0);
        assertEquals(replacement.kind(), assist.plan().kind());
        assertEquals(new MissionStatus(NavLogic.MissionState.PAUSED, index, EPOCH), data.status,
                "Drohnen-Daten nach " + replacement.kind() + ": " + data.events);
        CommandDecision.Decision pause = ownFlight(MissionCommand.Command.PAUSE, data.status);
        assertEquals(CommandDecision.Action.REJECT, pause.action(), "Pause ohne Wirkung darf nicht „Mission pausiert“ melden");
        assertEquals(AutonomyStatus.NOT_RUNNING, pause.reply());
        CommandDecision.Decision resume = ownFlight(MissionCommand.Command.RESUME, data.status);
        assertEquals(CommandDecision.Action.CONTROL, resume.action());
        assertEquals(AutonomyStatus.MISSION_RESUMED, resume.reply());
        assertEquals(index, resume.index());
        CommandDecision.Decision start = ownFlight(MissionCommand.Command.START, data.status);
        assertEquals(CommandDecision.Action.CONTROL, start.action());
        assertEquals(index, start.index(), "▶ Start setzt die Mission am Wegpunkt fort");
        return index;
    }

    @Test
    void automaticFlightDuringAMissionPausesItInTheDroneData() {
        DroneData data = new DroneData(new MissionStatus(NavLogic.MissionState.RUNNING, 0, EPOCH));
        AutonomyAssist assist = this.assist(AutonomyPlan.mission(SQUARE, 0), data);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        int index = this.interrupt(AutonomyPlan.autoShotAhead(AutoShots.Shot.CIRCLE, 0.0, 0.0, 0.0, 64.0), data, assist, sim);
        assist.replan(AutonomyPlan.mission(SQUARE, index));
        sim.run(assist, 1.0);
        assertEquals(NavLogic.Function.MISSION, assist.nav().logic().function());
        assertEquals(new MissionStatus(NavLogic.MissionState.RUNNING, index, EPOCH), data.status, "Fortsetzen: " + data.events);
    }

    @Test
    void pointOfInterestDuringAMissionPausesItInTheDroneData() {
        DroneData data = new DroneData(new MissionStatus(NavLogic.MissionState.RUNNING, 0, EPOCH));
        AutonomyAssist assist = this.assist(AutonomyPlan.mission(SQUARE, 0), data);
        this.interrupt(AutonomyPlan.orbit(4.0, 64.0, 4.0, 10.0, 3.0, true), data, assist, new FakeServerFlight.Sim());
    }

    @Test
    void followDuringAMissionPausesItInTheDroneData() {
        this.environment.targets.put(TARGET, NavTarget.at(20.0, 64.0, 20.0));
        DroneData data = new DroneData(new MissionStatus(NavLogic.MissionState.RUNNING, 0, EPOCH));
        AutonomyAssist assist = this.assist(AutonomyPlan.mission(SQUARE, 0), data);
        this.interrupt(AutonomyPlan.follow(TARGET, FollowController.Style.BEHIND), data, assist, new FakeServerFlight.Sim());
    }

    @Test
    void flightWithoutMissionLeavesTheDroneDataAlone() {
        MissionStatus done = new MissionStatus(NavLogic.MissionState.DONE, 3, EPOCH);
        DroneData data = new DroneData(done);
        AutonomyAssist assist = this.assist(AutonomyPlan.hold(), data);
        FakeServerFlight.Sim sim = new FakeServerFlight.Sim();
        sim.run(assist, 1.0);
        assist.replan(AutonomyPlan.autoShotAhead(AutoShots.Shot.RISE, 0.0, 0.0, 0.0, 64.0));
        sim.run(assist, 1.0);
        assertEquals(done, data.status, data.events.toString());
    }

    @Test
    void progressFromAFlightOnlyPausesAMissionThatWasRunning() {
        MissionStatus running = new MissionStatus(NavLogic.MissionState.RUNNING, 2, EPOCH);
        assertEquals(Optional.of(running.with(NavLogic.MissionState.RUNNING, 3)),
                ProgressCheck.fromFlight(true, running, NavLogic.MissionState.RUNNING, 3));
        assertEquals(Optional.of(running.with(NavLogic.MissionState.PAUSED, 2)),
                ProgressCheck.fromFlight(false, running, NavLogic.MissionState.PAUSED, 2));
        assertEquals(Optional.empty(), ProgressCheck.fromFlight(false, running, NavLogic.MissionState.IDLE, 0));
        assertEquals(Optional.empty(), ProgressCheck.fromFlight(false, running, NavLogic.MissionState.RUNNING, 3));
        assertEquals(Optional.empty(), ProgressCheck.fromFlight(false, MissionStatus.IDLE, NavLogic.MissionState.PAUSED, 0));
        assertEquals(Optional.empty(), ProgressCheck.fromFlight(false, running.with(NavLogic.MissionState.DONE, 3), NavLogic.MissionState.PAUSED, 0));
        assertEquals(Optional.empty(), ProgressCheck.fromFlight(false, running.with(NavLogic.MissionState.PAUSED, 1), NavLogic.MissionState.PAUSED, 2));
        assertEquals(Optional.empty(), ProgressCheck.fromFlight(true, null, NavLogic.MissionState.RUNNING, 0));
    }
}
