package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavInput;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

final class StateBuilder {
    boolean cameraDrone = true;
    boolean piloted = true;
    FlightClass flightClass = FlightClass.TOUR;
    String label = "N";
    boolean atti;
    Set<NavOutput.Warning> warnings = EnumSet.noneOf(NavOutput.Warning.class);
    NavLogic.MissionState missionState = NavLogic.MissionState.IDLE;
    int missionIndex;
    int missionSize;
    double missionSecondsLeft = Double.NaN;
    double gimbalTilt = Double.NaN;
    NavInput.Sensor obstacleSensor;
    double obstacleDistance = Double.NaN;
    double flightSecondsLeft = Double.NaN;
    boolean highWind;
    boolean operator;
    boolean cameraView;
    String hint = "";
    boolean gimbalStop;

    static StateBuilder camera() {
        return new StateBuilder();
    }

    StateBuilder warn(NavOutput.Warning... values) {
        for (NavOutput.Warning value : values) {
            this.warnings.add(value);
        }
        return this;
    }

    StateBuilder obstacle(NavInput.Sensor sensor, double distance) {
        this.obstacleSensor = sensor;
        this.obstacleDistance = distance;
        return this;
    }

    StateBuilder mission(NavLogic.MissionState state, int index, int size, double secondsLeft) {
        this.missionState = state;
        this.missionIndex = index;
        this.missionSize = size;
        this.missionSecondsLeft = secondsLeft;
        return this;
    }

    StateBuilder everything() {
        this.label = "WP 50/50";
        this.atti = true;
        this.warnings = EnumSet.allOf(NavOutput.Warning.class);
        this.mission(NavLogic.MissionState.PAUSED, 49, 50, 359999.0);
        this.gimbalTilt = -90.0;
        this.obstacle(NavInput.Sensor.RIGHT, 7.99);
        this.flightSecondsLeft = 359999.0;
        this.highWind = true;
        this.operator = true;
        this.hint = "ATTI";
        this.gimbalStop = true;
        return this;
    }

    CamOsdState build() {
        return new CamOsdState(this.cameraDrone, this.piloted, Optional.ofNullable(this.flightClass), this.label, this.atti, this.warnings,
                this.missionState, this.missionIndex, this.missionSize, this.missionSecondsLeft, this.gimbalTilt,
                Optional.ofNullable(this.obstacleSensor), this.obstacleDistance, this.flightSecondsLeft, this.highWind, this.operator,
                this.cameraView, this.hint, this.gimbalStop);
    }
}
