package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.propwash.api.drone.AutonomyEnd;

import java.util.Objects;
import java.util.Optional;

public final class EndDecision {
    public record Situation(AutonomyEnd reason, AutonomyPlan.Kind kind, boolean missionLoaded, NavLogic.MissionState flightState, int flightIndex,
                            boolean replacedByUs, MissionStatus current) {
        public Situation {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(kind, "kind");
            flightState = Objects.requireNonNullElse(flightState, NavLogic.MissionState.IDLE);
            current = Objects.requireNonNullElse(current, MissionStatus.IDLE);
        }
    }

    public record Outcome(MissionStatus status, boolean clearSmart, Optional<AutonomyStatus> message) {
    }

    private EndDecision() {
    }

    public static Outcome decide(Situation s) {
        if (s.replacedByUs()) {
            return new Outcome(s.current(), false, Optional.empty());
        }
        MissionStatus next = s.missionLoaded() ? missionStatus(s) : s.current();
        if (s.kind() == AutonomyPlan.Kind.HOME && s.reason() == AutonomyEnd.STOPPED) {
            return new Outcome(next, true, Optional.empty());
        }
        Optional<AutonomyStatus> message = s.reason() == AutonomyEnd.LANDED && s.flightState() == NavLogic.MissionState.DONE
                ? Optional.of(AutonomyStatus.MISSION_DONE) : Optional.of(AutonomyStatus.ended(s.reason()));
        return new Outcome(next, true, message);
    }

    static MissionStatus missionStatus(Situation s) {
        MissionStatus current = s.current();
        int index = Math.max(0, s.flightIndex());
        NavLogic.MissionState state = s.flightState();
        if (state == NavLogic.MissionState.DONE) {
            return current.with(NavLogic.MissionState.DONE, index);
        }
        if (state == NavLogic.MissionState.IDLE) {
            return current;
        }
        if (s.reason() == AutonomyEnd.PILOT) {
            return current.with(NavLogic.MissionState.PAUSED, index);
        }
        return current.with(NavLogic.MissionState.ABORTED, index);
    }
}
