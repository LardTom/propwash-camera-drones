package de.lardtom.camdrones.logic.nav;

import java.util.Arrays;

public final class NavInput {
    public static final double DEFAULT_ANGLE_LIMIT = 60.0;
    public static final double THROTTLE_CENTER = 0.5;

    public enum Sensor {
        FORWARD,
        BACK,
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    private final double[] obstacles = new double[Sensor.values().length];
    private final double[] ranges = new double[Sensor.values().length];
    private double sightHeight = Double.NaN;
    private double time;
    private double x;
    private double y;
    private double z;
    private double vx;
    private double vy;
    private double vz;
    private double heading;
    private double pitch;
    private double roll;
    private boolean gpsFix = true;
    private int satellites = 12;
    private boolean compassHealthy = true;
    private double compassError;
    private double heightAboveGround = Double.NaN;
    private boolean grounded;
    private double battery = 1.0;
    private boolean pilot;
    private double stickRoll;
    private double stickPitch;
    private double stickYaw;
    private double stickThrottle = THROTTLE_CENTER;
    private double angleLimit = DEFAULT_ANGLE_LIMIT;
    private double hoverThrottle = Double.NaN;
    private boolean target;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double targetVx;
    private double targetVy;
    private double targetVz;

    public NavInput() {
        Arrays.fill(this.obstacles, Double.NaN);
        Arrays.fill(this.ranges, Double.NaN);
    }

    public NavInput time(double seconds) {
        this.time = seconds;
        return this;
    }

    public NavInput position(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public NavInput velocity(double vx, double vy, double vz) {
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        return this;
    }

    public NavInput attitude(double heading, double pitch, double roll) {
        this.heading = Compass.normalize(heading);
        this.pitch = pitch;
        this.roll = roll;
        return this;
    }

    public NavInput gps(boolean fix, int satellites) {
        this.gpsFix = fix;
        this.satellites = satellites;
        return this;
    }

    public NavInput compass(boolean healthy, double errorDegrees) {
        this.compassHealthy = healthy;
        this.compassError = Double.isFinite(errorDegrees) ? errorDegrees : 0.0;
        return this;
    }

    public NavInput heightAboveGround(double metres) {
        this.heightAboveGround = metres;
        return this;
    }

    public NavInput grounded(boolean value) {
        this.grounded = value;
        return this;
    }

    public NavInput battery(double charge) {
        this.battery = charge;
        return this;
    }

    public NavInput pilot(boolean value) {
        this.pilot = value;
        return this;
    }

    public NavInput sticks(double roll, double pitch, double yaw, double throttle) {
        this.stickRoll = clampFinite(roll, -1.0, 1.0, 0.0);
        this.stickPitch = clampFinite(pitch, -1.0, 1.0, 0.0);
        this.stickYaw = clampFinite(yaw, -1.0, 1.0, 0.0);
        this.stickThrottle = clampFinite(throttle, 0.0, 1.0, THROTTLE_CENTER);
        return this;
    }

    public NavInput angleLimit(double degrees) {
        this.angleLimit = Double.isFinite(degrees) && degrees >= 10.0 ? degrees : DEFAULT_ANGLE_LIMIT;
        return this;
    }

    public NavInput hoverThrottle(double throttle) {
        this.hoverThrottle = Double.isFinite(throttle) && throttle > 0.0 && throttle < 1.0 ? throttle : Double.NaN;
        return this;
    }

    public NavInput obstacle(Sensor sensor, double metres) {
        this.obstacles[sensor.ordinal()] = Double.isNaN(metres) ? Double.NaN : Math.max(0.0, metres);
        return this;
    }

    public NavInput range(Sensor sensor, double metres) {
        this.ranges[sensor.ordinal()] = Double.isNaN(metres) ? Double.NaN : Math.max(0.0, metres);
        return this;
    }

    public NavInput sightHeight(double metres) {
        this.sightHeight = metres;
        return this;
    }

    public NavInput clearObstacles() {
        Arrays.fill(this.obstacles, Double.NaN);
        Arrays.fill(this.ranges, Double.NaN);
        return this;
    }

    public NavInput target(double x, double y, double z, double vx, double vy, double vz) {
        this.target = true;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.targetVx = vx;
        this.targetVy = vy;
        this.targetVz = vz;
        return this;
    }

    public NavInput clearTarget() {
        this.target = false;
        return this;
    }

    public double time() {
        return this.time;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }

    public double vx() {
        return this.vx;
    }

    public double vy() {
        return this.vy;
    }

    public double vz() {
        return this.vz;
    }

    public double horizontalSpeed() {
        return Math.hypot(this.vx, this.vz);
    }

    public double heading() {
        return this.heading;
    }

    public double pitch() {
        return this.pitch;
    }

    public double roll() {
        return this.roll;
    }

    public boolean gpsFix() {
        return this.gpsFix;
    }

    public int satellites() {
        return this.satellites;
    }

    public boolean compassHealthy() {
        return this.compassHealthy;
    }

    public double compassError() {
        return this.compassError;
    }

    public double heightAboveGround() {
        return this.heightAboveGround;
    }

    public boolean grounded() {
        return this.grounded;
    }

    public double battery() {
        return this.battery;
    }

    public boolean pilot() {
        return this.pilot;
    }

    public double stickRoll() {
        return this.pilot ? this.stickRoll : 0.0;
    }

    public double stickPitch() {
        return this.pilot ? this.stickPitch : 0.0;
    }

    public double stickYaw() {
        return this.pilot ? this.stickYaw : 0.0;
    }

    public double stickThrottle() {
        return this.pilot ? this.stickThrottle : THROTTLE_CENTER;
    }

    public double angleLimit() {
        return this.angleLimit;
    }

    public double hoverThrottle() {
        return this.hoverThrottle;
    }

    public double obstacle(Sensor sensor) {
        return this.obstacles[sensor.ordinal()];
    }

    public double range(Sensor sensor) {
        double range = this.ranges[sensor.ordinal()];
        return Double.isNaN(range) ? ObstacleGuard.SENSOR_RANGE : Math.min(range, ObstacleGuard.SENSOR_RANGE);
    }

    public double sightHeight() {
        return this.sightHeight;
    }

    public boolean hasTarget() {
        return this.target;
    }

    public double targetX() {
        return this.targetX;
    }

    public double targetY() {
        return this.targetY;
    }

    public double targetZ() {
        return this.targetZ;
    }

    public double targetVx() {
        return this.targetVx;
    }

    public double targetVy() {
        return this.targetVy;
    }

    public double targetVz() {
        return this.targetVz;
    }

    private static double clampFinite(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
