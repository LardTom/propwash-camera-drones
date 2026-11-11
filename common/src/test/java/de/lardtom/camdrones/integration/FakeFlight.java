package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.flight.StabilizationMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class FakeFlight {
    private FakeFlight() {
    }

    static final class State implements FlightState {
        double time;
        double x;
        double y = 64.0;
        double z;
        double vx;
        double vy;
        double vz;
        double estimatedX = Double.NaN;
        double estimatedY = Double.NaN;
        double estimatedZ = Double.NaN;
        double estimatedVx = Double.NaN;
        double estimatedVy = Double.NaN;
        double estimatedVz = Double.NaN;
        boolean gpsFix = true;
        boolean positionValid = true;
        int satellites = 14;
        double compassError;
        double heading;
        double noseDown;
        double pitch;
        double roll;
        double heightAboveGround = 10.0;
        boolean armed = true;
        boolean grounded;
        double battery = 0.9;
        double stickRoll;
        double stickPitch;
        double stickYaw;
        double stickThrottle = 0.5;
        double angleLimit = FlightState.DEFAULT_ANGLE_LIMIT_DEG;
        StabilizationMode pilotMode = StabilizationMode.ANGLE;
        final double[] obstacles = new double[SensorDirection.values().length];
        final Set<String> switches = new HashSet<>();

        State() {
            Arrays.fill(this.obstacles, Double.POSITIVE_INFINITY);
        }

        State at(double px, double py, double pz) {
            this.x = px;
            this.y = py;
            this.z = pz;
            return this;
        }

        State on(CamControl control) {
            this.switches.add(control.idString());
            return this;
        }

        State off(CamControl control) {
            this.switches.remove(control.idString());
            return this;
        }

        State mode(StabilizationMode mode) {
            this.pilotMode = mode;
            return this;
        }

        State flies(NavLogic.StickMode mode) {
            return this.mode(switch (mode) {
                case NORMAL -> StabilizationMode.ANGLE;
                case CINE -> StabilizationMode.HORIZON;
                case SPORT -> StabilizationMode.ACRO;
            });
        }

        State obstacle(SensorDirection direction, double metres) {
            this.obstacles[direction.ordinal()] = metres;
            return this;
        }

        State step(double seconds) {
            this.time += seconds;
            return this;
        }

        @Override
        public double time() {
            return this.time;
        }

        @Override
        public double massKg() {
            return 0.72;
        }

        @Override
        public double gravity() {
            return 9.81;
        }

        @Override
        public double x() {
            return this.x;
        }

        @Override
        public double y() {
            return this.y;
        }

        @Override
        public double z() {
            return this.z;
        }

        @Override
        public double vx() {
            return this.vx;
        }

        @Override
        public double vy() {
            return this.vy;
        }

        @Override
        public double vz() {
            return this.vz;
        }

        @Override
        public double estimatedX() {
            return this.estimatedX;
        }

        @Override
        public double estimatedY() {
            return this.estimatedY;
        }

        @Override
        public double estimatedZ() {
            return this.estimatedZ;
        }

        @Override
        public double estimatedVx() {
            return this.estimatedVx;
        }

        @Override
        public double estimatedVy() {
            return this.estimatedVy;
        }

        @Override
        public double estimatedVz() {
            return this.estimatedVz;
        }

        @Override
        public boolean positionValid() {
            return this.gpsFix && this.positionValid;
        }

        @Override
        public boolean gpsFix() {
            return this.gpsFix;
        }

        @Override
        public int satellites() {
            return this.satellites;
        }

        @Override
        public double headingDeg() {
            return this.heading;
        }

        @Override
        public double compassHeadingDeg() {
            return this.heading + this.compassError;
        }

        @Override
        public double pitchDeg() {
            return this.pitch;
        }

        @Override
        public double rollDeg() {
            return this.roll;
        }

        @Override
        public double rollRateDps() {
            return 0.0;
        }

        @Override
        public double pitchRateDps() {
            return 0.0;
        }

        @Override
        public double yawRateDps() {
            return 0.0;
        }

        @Override
        public double forwardX() {
            return Math.sin(Math.toRadians(this.heading)) * Math.cos(Math.toRadians(this.noseDown));
        }

        @Override
        public double forwardY() {
            return -Math.sin(Math.toRadians(this.noseDown));
        }

        @Override
        public double forwardZ() {
            return -Math.cos(Math.toRadians(this.heading)) * Math.cos(Math.toRadians(this.noseDown));
        }

        @Override
        public double upX() {
            return Math.sin(Math.toRadians(this.heading)) * Math.sin(Math.toRadians(this.noseDown));
        }

        @Override
        public double upY() {
            return Math.cos(Math.toRadians(this.noseDown));
        }

        @Override
        public double upZ() {
            return -Math.cos(Math.toRadians(this.heading)) * Math.sin(Math.toRadians(this.noseDown));
        }

        @Override
        public double rightX() {
            return Math.cos(Math.toRadians(this.heading));
        }

        @Override
        public double rightY() {
            return 0.0;
        }

        @Override
        public double rightZ() {
            return Math.sin(Math.toRadians(this.heading));
        }

        @Override
        public double heightAboveGround() {
            return this.heightAboveGround;
        }

        @Override
        public boolean armed() {
            return this.armed;
        }

        @Override
        public boolean grounded() {
            return this.grounded;
        }

        @Override
        public boolean failsafe() {
            return false;
        }

        @Override
        public double hoverThrottle() {
            return 0.35;
        }

        @Override
        public double batteryCharge() {
            return this.battery;
        }

        @Override
        public double stickRoll() {
            return this.stickRoll;
        }

        @Override
        public double stickPitch() {
            return this.stickPitch;
        }

        @Override
        public double stickYaw() {
            return this.stickYaw;
        }

        @Override
        public double stickThrottle() {
            return this.stickThrottle;
        }

        @Override
        public double angleLimitDeg() {
            return this.angleLimit;
        }

        @Override
        public StabilizationMode pilotMode() {
            return this.pilotMode;
        }

        @Override
        public boolean action(int number) {
            return false;
        }

        @Override
        public double obstacleDistance(SensorDirection direction) {
            return this.obstacles[direction.ordinal()];
        }

        @Override
        public boolean addonSwitch(String id) {
            return this.switches.contains(id);
        }
    }

    static final class Output implements AssistOutput {
        final List<String> calls = new ArrayList<>();
        boolean sticks;
        double roll;
        double pitch;
        double yaw;
        double throttle;
        StabilizationMode mode;
        double altitude = Double.NaN;
        double climb = Double.NaN;
        double holdX = Double.NaN;
        double holdZ = Double.NaN;
        double velocityX = Double.NaN;
        double velocityZ = Double.NaN;
        double maxSpeed = Double.NaN;
        double yawRate = Double.NaN;
        double heading = Double.NaN;
        String label;
        boolean returnHome;
        boolean land;

        private static String f(double value) {
            return String.format(Locale.ROOT, "%.4f", value);
        }

        @Override
        public void sticks(double roll, double pitch, double yaw, double throttle) {
            this.sticks = true;
            this.roll = roll;
            this.pitch = pitch;
            this.yaw = yaw;
            this.throttle = throttle;
            this.calls.add("sticks " + f(roll) + " " + f(pitch) + " " + f(yaw) + " " + f(throttle));
        }

        @Override
        public void mode(StabilizationMode mode) {
            this.mode = mode;
            this.calls.add("mode " + mode);
        }

        @Override
        public void holdAltitude(double y) {
            this.altitude = y;
            this.calls.add("holdAltitude " + f(y));
        }

        @Override
        public void climb(double metresPerSecond) {
            this.climb = metresPerSecond;
            this.calls.add("climb " + f(metresPerSecond));
        }

        @Override
        public void holdPosition(double x, double z) {
            this.holdX = x;
            this.holdZ = z;
            this.calls.add("holdPosition " + f(x) + " " + f(z));
        }

        @Override
        public void velocity(double vx, double vz) {
            this.velocityX = vx;
            this.velocityZ = vz;
            this.calls.add("velocity " + f(vx) + " " + f(vz));
        }

        @Override
        public void maxSpeed(double metresPerSecond) {
            this.maxSpeed = metresPerSecond;
            this.calls.add("maxSpeed " + f(metresPerSecond));
        }

        @Override
        public void yawRate(double degreesPerSecond) {
            this.yawRate = degreesPerSecond;
            this.calls.add("yawRate " + f(degreesPerSecond));
        }

        @Override
        public void heading(double compassDegrees) {
            this.heading = compassDegrees;
            this.calls.add("heading " + f(compassDegrees));
        }

        @Override
        public void label(String text) {
            this.label = text;
            this.calls.add("label " + text);
        }

        @Override
        public void requestReturnHome() {
            this.returnHome = true;
            this.calls.add("returnHome");
        }

        @Override
        public void land() {
            this.land = true;
            this.calls.add("land");
        }

        boolean horizontal() {
            return !Double.isNaN(this.holdX) || !Double.isNaN(this.velocityX);
        }

        boolean vertical() {
            return !Double.isNaN(this.altitude) || !Double.isNaN(this.climb);
        }
    }
}
