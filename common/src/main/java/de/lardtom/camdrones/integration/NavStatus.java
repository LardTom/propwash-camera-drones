package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record NavStatus(FlightClass flightClass, String label, NavLogic.StickMode stickMode, NavLogic.Function function,
                        NavLogic.MissionState missionState, int missionIndex, Set<NavOutput.Warning> warnings, boolean atti,
                        double gimbalTilt, Optional<NavTarget> gimbalTarget, Waypoint.Action action,
                        Optional<NavInput.Sensor> nearestSensor, double nearestDistance) {
    public NavStatus {
        Objects.requireNonNull(flightClass, "flightClass");
        label = label == null ? "" : label;
        Objects.requireNonNull(stickMode, "stickMode");
        Objects.requireNonNull(function, "function");
        Objects.requireNonNull(missionState, "missionState");
        warnings = warnings == null || warnings.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(warnings));
        gimbalTarget = Objects.requireNonNullElse(gimbalTarget, Optional.empty());
        action = Objects.requireNonNullElse(action, Waypoint.Action.NONE);
        nearestSensor = Objects.requireNonNullElse(nearestSensor, Optional.empty());
    }

    public boolean warns(NavOutput.Warning warning) {
        return this.warnings.contains(warning);
    }
}
