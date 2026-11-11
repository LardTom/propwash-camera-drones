package de.lardtom.camdrones.logic.nav;

import de.lardtom.camdrones.logic.mission.Waypoint;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class NavOutput {
    public static final double MAX_SPEED = 20.0;
    public static final double MAX_CLIMB = 8.0;
    public static final int MAX_LABEL = 8;

    public enum Horizontal {
        NONE,
        POSITION,
        VELOCITY
    }

    public enum Vertical {
        NONE,
        ALTITUDE,
        CLIMB
    }

    public enum Yaw {
        NONE,
        RATE,
        HEADING
    }

    public enum Warning {
        OBSTACLE,
        OBSTACLE_STOP,
        ALTITUDE_LIMIT,
        DISTANCE_LIMIT,
        NO_GPS,
        PAUSED,
        COMPASS,
        LOW_BATTERY,
        OBSTACLE_OFF
    }

    public interface Sink {
        void sticks(double roll, double pitch, double yaw, double throttle);

        void angleMode();

        void holdAltitude(double y);

        void climb(double metresPerSecond);

        void holdPosition(double x, double z);

        void velocity(double vx, double vz);

        void maxSpeed(double metresPerSecond);

        void yawRate(double degreesPerSecond);

        void heading(double compassDegrees);

        void label(String text);

        void requestReturnHome();

        void land();
    }

    private final EnumSet<Warning> warnings = EnumSet.noneOf(Warning.class);
    private final Set<Warning> warningView = Collections.unmodifiableSet(this.warnings);
    private Horizontal horizontal = Horizontal.NONE;
    private double horizontalX;
    private double horizontalZ;
    private double maxSpeed = Double.NaN;
    private Vertical vertical = Vertical.NONE;
    private double verticalValue;
    private Yaw yaw = Yaw.NONE;
    private double yawValue;
    private boolean sticks;
    private double stickRoll;
    private double stickPitch;
    private double stickYaw;
    private double stickThrottle;
    private String label;
    private boolean returnHome;
    private boolean land;
    private double gimbalTilt = Double.NaN;
    private boolean gimbalTarget;
    private double gimbalX;
    private double gimbalY;
    private double gimbalZ;
    private Waypoint.Action action = Waypoint.Action.NONE;

    public NavOutput reset() {
        this.horizontal = Horizontal.NONE;
        this.maxSpeed = Double.NaN;
        this.vertical = Vertical.NONE;
        this.yaw = Yaw.NONE;
        this.sticks = false;
        this.label = null;
        this.returnHome = false;
        this.land = false;
        this.gimbalTilt = Double.NaN;
        this.gimbalTarget = false;
        this.action = Waypoint.Action.NONE;
        this.warnings.clear();
        return this;
    }

    public void holdPosition(double x, double z) {
        if (Double.isFinite(x) && Double.isFinite(z)) {
            this.horizontal = Horizontal.POSITION;
            this.horizontalX = x;
            this.horizontalZ = z;
        }
    }

    public void velocity(double vx, double vz) {
        if (Double.isFinite(vx) && Double.isFinite(vz)) {
            this.horizontal = Horizontal.VELOCITY;
            this.horizontalX = vx;
            this.horizontalZ = vz;
        }
    }

    public void clearHorizontal() {
        this.horizontal = Horizontal.NONE;
    }

    public void maxSpeed(double metresPerSecond) {
        if (Double.isFinite(metresPerSecond)) {
            this.maxSpeed = Math.clamp(metresPerSecond, 0.0, MAX_SPEED);
        }
    }

    public void holdAltitude(double y) {
        if (Double.isFinite(y)) {
            this.vertical = Vertical.ALTITUDE;
            this.verticalValue = y;
        }
    }

    public void clearVertical() {
        this.vertical = Vertical.NONE;
    }

    public void climb(double metresPerSecond) {
        if (Double.isFinite(metresPerSecond)) {
            this.vertical = Vertical.CLIMB;
            this.verticalValue = Math.clamp(metresPerSecond, -MAX_CLIMB, MAX_CLIMB);
        }
    }

    public void yawRate(double degreesPerSecond) {
        if (Double.isFinite(degreesPerSecond)) {
            this.yaw = Yaw.RATE;
            this.yawValue = degreesPerSecond;
        }
    }

    public void heading(double compassDegrees) {
        if (Double.isFinite(compassDegrees)) {
            this.yaw = Yaw.HEADING;
            this.yawValue = Compass.normalize(compassDegrees);
        }
    }

    public void sticks(double roll, double pitch, double yaw, double throttle) {
        if (Double.isFinite(roll) && Double.isFinite(pitch) && Double.isFinite(yaw) && Double.isFinite(throttle)) {
            this.sticks = true;
            this.stickRoll = Math.clamp(roll, -1.0, 1.0);
            this.stickPitch = Math.clamp(pitch, -1.0, 1.0);
            this.stickYaw = Math.clamp(yaw, -1.0, 1.0);
            this.stickThrottle = Math.clamp(throttle, 0.0, 1.0);
        }
    }

    public void clearSticks() {
        this.sticks = false;
    }

    public void label(String text) {
        this.label = text == null || text.length() <= MAX_LABEL ? text : text.substring(0, MAX_LABEL);
    }

    public void requestReturnHome() {
        this.returnHome = true;
    }

    public void land() {
        this.land = true;
    }

    public void gimbalTilt(double degrees) {
        if (Double.isFinite(degrees)) {
            this.gimbalTilt = degrees;
        }
    }

    public void gimbalTarget(double x, double y, double z) {
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
            this.gimbalTarget = true;
            this.gimbalX = x;
            this.gimbalY = y;
            this.gimbalZ = z;
        }
    }

    public void action(Waypoint.Action value) {
        this.action = value == null ? Waypoint.Action.NONE : value;
    }

    public void warn(Warning warning) {
        this.warnings.add(warning);
    }

    public Horizontal horizontal() {
        return this.horizontal;
    }

    public double targetX() {
        return this.horizontal == Horizontal.POSITION ? this.horizontalX : Double.NaN;
    }

    public double targetZ() {
        return this.horizontal == Horizontal.POSITION ? this.horizontalZ : Double.NaN;
    }

    public double velocityX() {
        return this.horizontal == Horizontal.VELOCITY ? this.horizontalX : 0.0;
    }

    public double velocityZ() {
        return this.horizontal == Horizontal.VELOCITY ? this.horizontalZ : 0.0;
    }

    public double maxSpeed() {
        return this.maxSpeed;
    }

    public Vertical vertical() {
        return this.vertical;
    }

    public double altitudeTarget() {
        return this.vertical == Vertical.ALTITUDE ? this.verticalValue : Double.NaN;
    }

    public double climbRate() {
        return this.vertical == Vertical.CLIMB ? this.verticalValue : 0.0;
    }

    public Yaw yaw() {
        return this.yaw;
    }

    public double yawRateTarget() {
        return this.yaw == Yaw.RATE ? this.yawValue : 0.0;
    }

    public double headingTarget() {
        return this.yaw == Yaw.HEADING ? this.yawValue : Double.NaN;
    }

    public boolean hasSticks() {
        return this.sticks;
    }

    public double stickRoll() {
        return this.sticks ? this.stickRoll : 0.0;
    }

    public double stickPitch() {
        return this.sticks ? this.stickPitch : 0.0;
    }

    public double stickYaw() {
        return this.sticks ? this.stickYaw : 0.0;
    }

    public double stickThrottle() {
        return this.sticks ? this.stickThrottle : 0.0;
    }

    public String label() {
        return this.label;
    }

    public boolean returnHome() {
        return this.returnHome;
    }

    public boolean landing() {
        return this.land;
    }

    public double gimbalTilt() {
        return this.gimbalTilt;
    }

    public boolean hasGimbalTarget() {
        return this.gimbalTarget;
    }

    public double gimbalX() {
        return this.gimbalX;
    }

    public double gimbalY() {
        return this.gimbalY;
    }

    public double gimbalZ() {
        return this.gimbalZ;
    }

    public Waypoint.Action action() {
        return this.action;
    }

    public Set<Warning> warnings() {
        return this.warningView;
    }

    public boolean has(Warning warning) {
        return this.warnings.contains(warning);
    }

    public void writeTo(Sink sink) {
        if (this.sticks) {
            sink.sticks(this.stickRoll, this.stickPitch, this.stickYaw, this.stickThrottle);
            sink.angleMode();
        }
        switch (this.vertical) {
            case ALTITUDE -> sink.holdAltitude(this.verticalValue);
            case CLIMB -> sink.climb(this.verticalValue);
            case NONE -> {
            }
        }
        switch (this.horizontal) {
            case POSITION -> sink.holdPosition(this.horizontalX, this.horizontalZ);
            case VELOCITY -> sink.velocity(this.horizontalX, this.horizontalZ);
            case NONE -> {
            }
        }
        if (!Double.isNaN(this.maxSpeed)) {
            sink.maxSpeed(this.maxSpeed);
        }
        switch (this.yaw) {
            case RATE -> sink.yawRate(this.yawValue);
            case HEADING -> sink.heading(this.yawValue);
            case NONE -> {
            }
        }
        if (this.label != null) {
            sink.label(this.label);
        }
        if (this.returnHome) {
            sink.requestReturnHome();
        }
        if (this.land) {
            sink.land();
        }
    }
}
