package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.WaypointFollower;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ExitDecision {
    public record Situation(boolean allowAutonomy, boolean cameraDrone, boolean airborne, Optional<Mission> mission, MissionStatus status,
                            SmartState smart, DroneSettings.ExitBehaviour exit, UUID pilot, boolean followTargetAllowed, FlightClass flightClass,
                            double x, double y, double z) {
        public Situation {
            mission = Objects.requireNonNullElse(mission, Optional.<Mission>empty()).filter(m -> !m.isEmpty());
            status = Objects.requireNonNullElse(status, MissionStatus.IDLE);
            smart = Objects.requireNonNullElse(smart, SmartState.NONE);
            exit = Objects.requireNonNullElse(exit, DroneSettings.ExitBehaviour.HOVER);
            Objects.requireNonNull(pilot, "pilot");
            flightClass = Objects.requireNonNullElse(flightClass, FlightClass.TOUR);
        }
    }

    private ExitDecision() {
    }

    public static Optional<AutonomyPlan> decide(Situation s) {
        if (!s.allowAutonomy() || !s.cameraDrone() || !s.airborne()) {
            return Optional.empty();
        }
        if (s.mission().isPresent() && s.status().active()) {
            Mission mission = s.mission().get();
            int reported = Math.clamp(s.status().index(), 0, mission.size() - 1);
            if (s.status().state() == NavLogic.MissionState.RUNNING) {
                return Optional.of(AutonomyPlan.mission(mission, WaypointFollower.resumeIndex(mission, reported, s.x(), s.y(), s.z())));
            }
            return Optional.of(AutonomyPlan.pausedMission(mission, reported));
        }
        SmartState smart = s.smart();
        switch (smart.function()) {
            case FOLLOW -> {
                if (smart.target().isPresent() && s.followTargetAllowed()) {
                    return Optional.of(AutonomyPlan.follow(smart.target().get(), smart.followStyle()));
                }
                return Optional.of(AutonomyPlan.hold());
            }
            case POI -> {
                double speed = smart.speed() > 0.0 ? smart.speed() : s.flightClass().cineSpeed();
                return Optional.of(AutonomyPlan.orbit(smart.x(), smart.y(), smart.z(), smart.radius(), speed, smart.clockwise()));
            }
            case HOLD, COURSE_LOCK, AUTO_SHOT -> {
                return Optional.of(AutonomyPlan.hold());
            }
            default -> {
            }
        }
        return switch (s.exit()) {
            case HOVER -> Optional.of(AutonomyPlan.hold());
            case LAND -> Optional.of(AutonomyPlan.land());
            case HOME -> Optional.of(AutonomyPlan.home());
        };
    }
}
