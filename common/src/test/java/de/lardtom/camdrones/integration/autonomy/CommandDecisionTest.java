package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandDecisionTest {
    private static final MissionStatus PAUSED_AT_3 = new MissionStatus(NavLogic.MissionState.PAUSED, 3, 4);
    private static final MissionStatus RUNNING_AT_2 = new MissionStatus(NavLogic.MissionState.RUNNING, 2, 4);

    private static Builder situation(MissionCommand.Command command) {
        return new Builder(command);
    }

    private static final class Builder {
        MissionCommand.Command command;
        int startIndex = MissionCommand.AUTO_INDEX;
        boolean owner = true;
        boolean operator;
        boolean withinRate = true;
        boolean found = true;
        boolean camera = true;
        boolean piloted;
        boolean ourFlight;
        boolean otherFlight;
        boolean allow = true;
        int size = 6;
        MissionStatus status = MissionStatus.IDLE;

        Builder(MissionCommand.Command command) {
            this.command = command;
        }

        CommandDecision.Decision decide() {
            return CommandDecision.decide(new CommandDecision.Situation(this.command, this.startIndex, this.owner, this.operator, this.withinRate,
                    this.found, this.camera, this.piloted, this.ourFlight, this.otherFlight, this.allow, this.size, this.status));
        }
    }

    @Test
    void startOnTheGroundStartsAFlightWithoutPilot() {
        CommandDecision.Decision d = situation(MissionCommand.Command.START).decide();
        assertEquals(CommandDecision.Action.START_FLIGHT, d.action());
        assertEquals(AutonomyPlan.Kind.MISSION, d.plan());
        assertEquals(NavLogic.MissionState.RUNNING, d.state());
        assertEquals(0, d.index());
        assertEquals(AutonomyStatus.MISSION_STARTED, d.reply());
    }

    @Test
    void startWithoutIndexContinuesAPausedMission() {
        Builder b = situation(MissionCommand.Command.START);
        b.status = PAUSED_AT_3;
        assertEquals(3, b.decide().index());
        b.startIndex = 1;
        assertEquals(1, b.decide().index());
        b.startIndex = 40;
        assertEquals(5, b.decide().index(), "auf die Mission begrenzt");
    }

    @Test
    void resumeNeedsAnActiveMission() {
        Builder b = situation(MissionCommand.Command.RESUME);
        assertEquals(AutonomyStatus.NOT_RUNNING, b.decide().reply());
        b.status = PAUSED_AT_3;
        CommandDecision.Decision d = b.decide();
        assertEquals(CommandDecision.Action.START_FLIGHT, d.action());
        assertEquals(3, d.index());
        assertEquals(AutonomyStatus.MISSION_RESUMED, d.reply());
    }

    @Test
    void rejectsWithoutMissionRateOwnerOrDrone() {
        Builder b = situation(MissionCommand.Command.START);
        b.size = 0;
        assertEquals(AutonomyStatus.NO_MISSION, b.decide().reply());
        b = situation(MissionCommand.Command.START);
        b.withinRate = false;
        assertEquals(AutonomyStatus.TOO_FAST, b.decide().reply());
        b = situation(MissionCommand.Command.START);
        b.found = false;
        assertEquals(AutonomyStatus.NOT_FOUND, b.decide().reply());
        b = situation(MissionCommand.Command.START);
        b.owner = false;
        assertEquals(AutonomyStatus.NOT_OWNER, b.decide().reply());
        assertFalse(b.decide().accepted());
    }

    @Test
    void operatorsMayOnlyStopPauseAndHoldForeignDrones() {
        for (MissionCommand.Command command : MissionCommand.Command.values()) {
            Builder b = situation(command);
            b.owner = false;
            b.operator = true;
            b.ourFlight = true;
            b.status = RUNNING_AT_2;
            boolean allowed = CommandDecision.safe(command);
            assertEquals(allowed, b.decide().accepted(), command.name());
        }
    }

    @Test
    void worldRuleAndCameraCheckGateNewFlights() {
        Builder b = situation(MissionCommand.Command.START);
        b.allow = false;
        assertEquals(AutonomyStatus.DISABLED, b.decide().reply());
        b = situation(MissionCommand.Command.HOLD);
        b.allow = false;
        assertEquals(AutonomyStatus.DISABLED, b.decide().reply());
        b = situation(MissionCommand.Command.START);
        b.camera = false;
        assertEquals(AutonomyStatus.NOT_CAMERA, b.decide().reply());
    }

    @Test
    void pilotedDronesGetTheCommandRelayedThroughTheMissionState() {
        Builder b = situation(MissionCommand.Command.START);
        b.piloted = true;
        b.ourFlight = true;
        CommandDecision.Decision d = b.decide();
        assertEquals(CommandDecision.Action.RELAY, d.action());
        assertEquals(NavLogic.MissionState.RUNNING, d.state());
        assertEquals(AutonomyStatus.RELAYED, d.reply());
        b = situation(MissionCommand.Command.PAUSE);
        b.piloted = true;
        b.status = RUNNING_AT_2;
        assertEquals(NavLogic.MissionState.PAUSED, b.decide().state());
        b = situation(MissionCommand.Command.STOP);
        b.piloted = true;
        b.status = RUNNING_AT_2;
        assertEquals(NavLogic.MissionState.ABORTED, b.decide().state());
        b = situation(MissionCommand.Command.HOLD);
        b.piloted = true;
        assertEquals(AutonomyStatus.PILOTED, b.decide().reply());
    }

    @Test
    void runningFlightIsControlledInPlace() {
        Builder b = situation(MissionCommand.Command.PAUSE);
        b.ourFlight = true;
        b.status = RUNNING_AT_2;
        CommandDecision.Decision d = b.decide();
        assertEquals(CommandDecision.Action.CONTROL, d.action());
        assertEquals(AutonomyStatus.MISSION_PAUSED, d.reply());
        b.command = MissionCommand.Command.STOP;
        d = b.decide();
        assertEquals(AutonomyPlan.Kind.LAND, d.plan());
        assertEquals(NavLogic.MissionState.ABORTED, d.state());
        assertEquals(AutonomyStatus.LAND_STARTED, d.reply());
        b.command = MissionCommand.Command.HOLD;
        d = b.decide();
        assertEquals(AutonomyPlan.Kind.HOLD, d.plan());
        assertEquals(NavLogic.MissionState.PAUSED, d.state());
        b.command = MissionCommand.Command.START;
        b.startIndex = 4;
        assertEquals(4, b.decide().index());
    }

    @Test
    void anotherFlightWithoutPilotBlocksNewFlights() {
        Builder b = situation(MissionCommand.Command.START);
        b.otherFlight = true;
        assertEquals(AutonomyStatus.BUSY, b.decide().reply());
    }

    @Test
    void stopWithoutFlightOnlyResetsAStaleMission() {
        Builder b = situation(MissionCommand.Command.STOP);
        assertEquals(AutonomyStatus.NOT_RUNNING, b.decide().reply());
        b.status = RUNNING_AT_2;
        CommandDecision.Decision d = b.decide();
        assertEquals(CommandDecision.Action.RESET, d.action());
        assertEquals(NavLogic.MissionState.ABORTED, d.state());
        assertTrue(d.accepted());
        assertEquals(AutonomyStatus.NOT_RUNNING, situation(MissionCommand.Command.PAUSE).decide().reply());
    }

    @Test
    void holdOnTheGroundStartsAnAerialTripod() {
        Builder b = situation(MissionCommand.Command.HOLD);
        b.size = 0;
        CommandDecision.Decision d = b.decide();
        assertEquals(CommandDecision.Action.START_FLIGHT, d.action());
        assertEquals(AutonomyPlan.Kind.HOLD, d.plan());
        assertEquals(AutonomyStatus.HOLD_STARTED, d.reply());
    }
}
