package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.nav.NavLogic;

import java.util.Optional;

public final class ProgressCheck {
    private ProgressCheck() {
    }

    public static Optional<MissionStatus> apply(boolean senderIsPilot, boolean withinRate, MissionStatus current, NavLogic.MissionState state,
                                                int index, int epoch, int missionSize) {
        if (!senderIsPilot || !withinRate || current == null || state == null || epoch != current.epoch()) {
            return Optional.empty();
        }
        if (missionSize <= 0) {
            return state == NavLogic.MissionState.IDLE ? changed(current, current.with(NavLogic.MissionState.IDLE, 0)) : Optional.empty();
        }
        if (index < 0 || index >= missionSize) {
            return Optional.empty();
        }
        return changed(current, current.with(state, index));
    }

    public static Optional<MissionStatus> fromFlight(boolean missionLoaded, MissionStatus current, NavLogic.MissionState state, int index) {
        if (current == null || state == null) {
            return Optional.empty();
        }
        boolean interrupted = state == NavLogic.MissionState.PAUSED && current.state() == NavLogic.MissionState.RUNNING;
        return missionLoaded || interrupted ? Optional.of(current.with(state, index)) : Optional.empty();
    }

    private static Optional<MissionStatus> changed(MissionStatus current, MissionStatus next) {
        return next.equals(current) ? Optional.empty() : Optional.of(next);
    }
}
