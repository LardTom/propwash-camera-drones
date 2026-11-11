package de.lardtom.camdrones.client.gimbal.model;

import de.lardtom.camdrones.logic.nav.NavLogic;

import java.util.Optional;

public final class SoloMission {
    public enum Command {
        PAUSE,
        RESUME,
        STOP
    }

    private SoloMission() {
    }

    public static Optional<Command> command(OperatorGimbalInput.Event event, NavLogic.MissionState state) {
        if (event == OperatorGimbalInput.Event.ABORT) {
            return Optional.of(Command.STOP);
        }
        if (event != OperatorGimbalInput.Event.MISSION || state == null) {
            return Optional.empty();
        }
        return switch (state) {
            case RUNNING -> Optional.of(Command.PAUSE);
            case PAUSED -> Optional.of(Command.RESUME);
            default -> Optional.empty();
        };
    }
}
