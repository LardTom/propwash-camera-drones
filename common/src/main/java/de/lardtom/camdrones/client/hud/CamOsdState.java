package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record CamOsdState(boolean cameraDrone, boolean piloted, Optional<FlightClass> flightClass, String label, boolean atti,
                          Set<NavOutput.Warning> warnings, NavLogic.MissionState missionState, int missionIndex, int missionSize,
                          double missionSecondsLeft, double gimbalTilt, Optional<NavInput.Sensor> obstacleSensor, double obstacleDistance,
                          double flightSecondsLeft, boolean highWind, boolean operator, boolean cameraView, String hint,
                          boolean gimbalStop) {
    public static final CamOsdState NONE = new CamOsdState(false, false, Optional.empty(), "", false, Set.of(), NavLogic.MissionState.IDLE, 0,
            0, Double.NaN, Double.NaN, Optional.empty(), Double.NaN, Double.NaN, false, false, false, "", false);

    public CamOsdState {
        flightClass = Objects.requireNonNullElse(flightClass, Optional.empty());
        label = label == null ? "" : label;
        warnings = warnings == null || warnings.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(warnings));
        missionState = Objects.requireNonNullElse(missionState, NavLogic.MissionState.IDLE);
        missionIndex = Math.max(0, missionIndex);
        missionSize = Math.max(0, missionSize);
        obstacleSensor = Objects.requireNonNullElse(obstacleSensor, Optional.empty());
        hint = hint == null ? "" : hint;
    }

    public boolean warns(NavOutput.Warning warning) {
        return this.warnings.contains(warning);
    }

    public boolean missionActive() {
        return this.missionState == NavLogic.MissionState.RUNNING || this.missionState == NavLogic.MissionState.PAUSED;
    }

    public double gimbalMaxTilt() {
        return this.flightClass.map(FlightClass::gimbalMaxTilt).orElse(FlightClass.TOUR.gimbalMaxTilt());
    }
}
