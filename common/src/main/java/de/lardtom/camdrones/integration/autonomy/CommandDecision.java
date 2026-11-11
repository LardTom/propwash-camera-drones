package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;

import java.util.Objects;

public final class CommandDecision {
    public enum Action {
        REJECT,
        RELAY,
        START_FLIGHT,
        CONTROL,
        RESET
    }

    public record Situation(MissionCommand.Command command, int startIndex, boolean owner, boolean operator, boolean withinRate,
                            boolean droneFound, boolean cameraDrone, boolean piloted, boolean ourFlight, boolean otherFlight,
                            boolean allowAutonomy, int missionSize, MissionStatus status) {
        public Situation {
            Objects.requireNonNull(command, "command");
            status = Objects.requireNonNullElse(status, MissionStatus.IDLE);
            missionSize = Math.max(0, missionSize);
        }

        public boolean hasMission() {
            return this.missionSize > 0;
        }
    }

    public record Decision(Action action, AutonomyPlan.Kind plan, NavLogic.MissionState state, int index, AutonomyStatus reply) {
        public static Decision reject(AutonomyStatus reply) {
            return new Decision(Action.REJECT, AutonomyPlan.Kind.HOLD, NavLogic.MissionState.IDLE, 0, reply);
        }

        public boolean accepted() {
            return this.action != Action.REJECT;
        }
    }

    private CommandDecision() {
    }

    public static boolean safe(MissionCommand.Command command) {
        return command == MissionCommand.Command.STOP || command == MissionCommand.Command.PAUSE || command == MissionCommand.Command.HOLD;
    }

    public static Decision decide(Situation s) {
        if (!s.withinRate()) {
            return Decision.reject(AutonomyStatus.TOO_FAST);
        }
        if (!s.droneFound()) {
            return Decision.reject(AutonomyStatus.NOT_FOUND);
        }
        if (!s.owner() && !(s.operator() && safe(s.command()))) {
            return Decision.reject(AutonomyStatus.NOT_OWNER);
        }
        if (s.piloted()) {
            return relay(s);
        }
        if (s.otherFlight() && !s.ourFlight()) {
            return Decision.reject(AutonomyStatus.BUSY);
        }
        return s.ourFlight() ? control(s) : idle(s);
    }

    static int startIndex(Situation s) {
        if (s.startIndex() >= 0) {
            return clampIndex(s.startIndex(), s.missionSize());
        }
        return s.status().state() == NavLogic.MissionState.PAUSED ? clampIndex(s.status().index(), s.missionSize()) : 0;
    }

    private static int clampIndex(int index, int size) {
        return size <= 0 ? 0 : Math.clamp(index, 0, size - 1);
    }

    private static Decision relay(Situation s) {
        MissionStatus status = s.status();
        return switch (s.command()) {
            case START -> s.hasMission()
                    ? new Decision(Action.RELAY, AutonomyPlan.Kind.MISSION, NavLogic.MissionState.RUNNING, startIndex(s), AutonomyStatus.RELAYED)
                    : Decision.reject(AutonomyStatus.NO_MISSION);
            case RESUME -> s.hasMission() && status.active()
                    ? new Decision(Action.RELAY, AutonomyPlan.Kind.MISSION, NavLogic.MissionState.RUNNING, clampIndex(status.index(), s.missionSize()),
                    AutonomyStatus.RELAYED)
                    : Decision.reject(AutonomyStatus.NOT_RUNNING);
            case PAUSE -> status.state() == NavLogic.MissionState.RUNNING
                    ? new Decision(Action.RELAY, AutonomyPlan.Kind.MISSION, NavLogic.MissionState.PAUSED, status.index(), AutonomyStatus.RELAYED)
                    : Decision.reject(AutonomyStatus.NOT_RUNNING);
            case STOP -> status.active()
                    ? new Decision(Action.RELAY, AutonomyPlan.Kind.MISSION, NavLogic.MissionState.ABORTED, status.index(), AutonomyStatus.RELAYED)
                    : Decision.reject(AutonomyStatus.NOT_RUNNING);
            case HOLD -> Decision.reject(AutonomyStatus.PILOTED);
        };
    }

    private static Decision control(Situation s) {
        MissionStatus status = s.status();
        return switch (s.command()) {
            case START -> s.hasMission()
                    ? new Decision(Action.CONTROL, AutonomyPlan.Kind.MISSION, NavLogic.MissionState.RUNNING, startIndex(s), AutonomyStatus.MISSION_STARTED)
                    : Decision.reject(AutonomyStatus.NO_MISSION);
            case RESUME -> {
                if (!s.hasMission()) {
                    yield Decision.reject(AutonomyStatus.NO_MISSION);
                }
                if (!status.active()) {
                    yield Decision.reject(AutonomyStatus.NOT_RUNNING);
                }
                yield new Decision(Action.CONTROL, AutonomyPlan.Kind.MISSION, NavLogic.MissionState.RUNNING, clampIndex(status.index(), s.missionSize()),
                        AutonomyStatus.MISSION_RESUMED);
            }
            case PAUSE -> status.state() == NavLogic.MissionState.RUNNING
                    ? new Decision(Action.CONTROL, AutonomyPlan.Kind.MISSION, NavLogic.MissionState.PAUSED, status.index(), AutonomyStatus.MISSION_PAUSED)
                    : Decision.reject(AutonomyStatus.NOT_RUNNING);
            case STOP -> new Decision(Action.CONTROL, AutonomyPlan.Kind.LAND, status.active() ? NavLogic.MissionState.ABORTED : status.state(),
                    status.index(), AutonomyStatus.LAND_STARTED);
            case HOLD -> new Decision(Action.CONTROL, AutonomyPlan.Kind.HOLD, status.state() == NavLogic.MissionState.RUNNING
                    ? NavLogic.MissionState.PAUSED : status.state(), status.index(), AutonomyStatus.HOLD_STARTED);
        };
    }

    private static Decision idle(Situation s) {
        MissionStatus status = s.status();
        return switch (s.command()) {
            case START, RESUME -> {
                if (!s.hasMission()) {
                    yield Decision.reject(AutonomyStatus.NO_MISSION);
                }
                if (s.command() == MissionCommand.Command.RESUME && !status.active()) {
                    yield Decision.reject(AutonomyStatus.NOT_RUNNING);
                }
                Decision gate = gate(s);
                if (gate != null) {
                    yield gate;
                }
                int index = s.command() == MissionCommand.Command.RESUME ? clampIndex(status.index(), s.missionSize()) : startIndex(s);
                yield new Decision(Action.START_FLIGHT, AutonomyPlan.Kind.MISSION, NavLogic.MissionState.RUNNING, index,
                        s.command() == MissionCommand.Command.RESUME ? AutonomyStatus.MISSION_RESUMED : AutonomyStatus.MISSION_STARTED);
            }
            case HOLD -> {
                Decision gate = gate(s);
                yield gate != null ? gate : new Decision(Action.START_FLIGHT, AutonomyPlan.Kind.HOLD, status.state() == NavLogic.MissionState.RUNNING
                        ? NavLogic.MissionState.PAUSED : status.state(), status.index(), AutonomyStatus.HOLD_STARTED);
            }
            case PAUSE -> Decision.reject(AutonomyStatus.NOT_RUNNING);
            case STOP -> status.active()
                    ? new Decision(Action.RESET, AutonomyPlan.Kind.MISSION, NavLogic.MissionState.ABORTED, status.index(), AutonomyStatus.MISSION_STOPPED)
                    : Decision.reject(AutonomyStatus.NOT_RUNNING);
        };
    }

    private static Decision gate(Situation s) {
        if (!s.allowAutonomy()) {
            return Decision.reject(AutonomyStatus.DISABLED);
        }
        if (!s.cameraDrone()) {
            return Decision.reject(AutonomyStatus.NOT_CAMERA);
        }
        return null;
    }
}
