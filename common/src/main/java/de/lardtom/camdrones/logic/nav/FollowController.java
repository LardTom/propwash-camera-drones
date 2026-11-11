package de.lardtom.camdrones.logic.nav;

public final class FollowController implements NavMode {
    public static final double DISTANCE = 8.0;
    public static final double HEIGHT = 5.0;
    public static final double MIN_DISTANCE = 3.0;
    public static final double MAX_DISTANCE = 40.0;
    public static final double MOVING_SPEED = 0.6;
    public static final double DIRECTION_SMOOTHING = 1.0;
    public static final double POSITION_GAIN = 0.8;
    public static final double DISTANCE_STICK_RATE = 3.0;
    public static final double HEADING_GAIN = 3.0;
    public static final double MAX_YAW_RATE = 120.0;
    public static final String LABEL = "FOLLOW";

    public enum Style {
        BEHIND,
        PARALLEL,
        WATCH
    }

    private final FlightClass flightClass;
    private final Style style;
    private final NormalMode.AltitudeStick altitudeStick;
    private final HoldController lost = new HoldController();
    private double distance;
    private double height;
    private boolean engaged;
    private double directionX;
    private double directionZ;
    private double side;
    private double watchX;
    private double watchY;
    private double watchZ;
    private double lastTime = Double.NaN;

    public FollowController(FlightClass flightClass, Style style) {
        this(flightClass, style, DISTANCE, HEIGHT);
    }

    public FollowController(FlightClass flightClass, Style style, double distance, double height) {
        this.flightClass = flightClass;
        this.style = style;
        this.altitudeStick = new NormalMode.AltitudeStick(flightClass, 0.0);
        this.distance = Math.clamp(distance, MIN_DISTANCE, MAX_DISTANCE);
        this.height = height;
    }

    public Style style() {
        return this.style;
    }

    public double distance() {
        return this.distance;
    }

    public double height() {
        return this.height;
    }

    public double desiredX(NavInput in) {
        return switch (this.style) {
            case BEHIND -> in.targetX() - this.directionX * this.distance;
            case PARALLEL -> in.targetX() - this.directionZ * this.side * this.distance;
            case WATCH -> this.watchX;
        };
    }

    public double desiredZ(NavInput in) {
        return switch (this.style) {
            case BEHIND -> in.targetZ() - this.directionZ * this.distance;
            case PARALLEL -> in.targetZ() + this.directionX * this.side * this.distance;
            case WATCH -> this.watchZ;
        };
    }

    @Override
    public void update(NavInput in, NavOutput out) {
        double dt = NavMode.step(this.lastTime, in.time());
        this.lastTime = in.time();
        if (!in.hasTarget()) {
            this.lost.update(in, out);
            out.label(LABEL);
            return;
        }
        this.lost.reset();
        if (!this.engaged) {
            this.engage(in);
        }
        this.distance = Math.clamp(this.distance - NavMode.deadband(in.stickPitch(), NormalMode.STICK_DEADBAND) * DISTANCE_STICK_RATE * dt,
                MIN_DISTANCE, MAX_DISTANCE);
        this.height += this.altitudeStick.wanted(in) * dt;
        double targetSpeed = Math.hypot(in.targetVx(), in.targetVz());
        if (targetSpeed > MOVING_SPEED && this.style != Style.WATCH) {
            double a = NormalMode.alpha(DIRECTION_SMOOTHING, dt);
            double nx = this.directionX + (in.targetVx() / targetSpeed - this.directionX) * a;
            double nz = this.directionZ + (in.targetVz() / targetSpeed - this.directionZ) * a;
            double n = Math.hypot(nx, nz);
            if (n > 1.0E-6) {
                this.directionX = nx / n;
                this.directionZ = nz / n;
            }
        }
        double speedLimit = this.flightClass.assistedSpeed(this.flightClass.normalSpeed());
        double ex = this.desiredX(in) - in.x();
        double ez = this.desiredZ(in) - in.z();
        double vx = POSITION_GAIN * ex;
        double vz = POSITION_GAIN * ez;
        if (this.style != Style.WATCH) {
            vx += in.targetVx();
            vz += in.targetVz();
        }
        out.velocity(vx, vz);
        out.maxSpeed(speedLimit);
        out.holdAltitude(this.style == Style.WATCH ? this.watchY : in.targetY() + this.height);
        double toX = in.targetX() - in.x();
        double toZ = in.targetZ() - in.z();
        double bearing = Compass.headingTo(toX, toZ);
        double feedForward = Compass.bearingRate(toX, toZ, in.targetVx() - in.vx(), in.targetVz() - in.vz());
        double yaw = Math.hypot(toX, toZ) < 1.0 ? 0.0 : feedForward + HEADING_GAIN * Compass.wrap180(bearing - in.heading());
        out.yawRate(Math.clamp(yaw, -MAX_YAW_RATE, MAX_YAW_RATE));
        out.gimbalTarget(in.targetX(), in.targetY(), in.targetZ());
        out.label(LABEL);
    }

    private void engage(NavInput in) {
        this.engaged = true;
        double px = in.x() - in.targetX();
        double pz = in.z() - in.targetZ();
        double pd = Math.hypot(px, pz);
        double travel = Math.hypot(in.targetVx(), in.targetVz());
        this.side = 1.0;
        if (this.style == Style.PARALLEL && travel > MOVING_SPEED) {
            this.directionX = in.targetVx() / travel;
            this.directionZ = in.targetVz() / travel;
            this.side = px * -this.directionZ + pz * this.directionX >= 0.0 ? 1.0 : -1.0;
        } else if (this.style == Style.PARALLEL && pd > 1.0E-3) {
            this.directionX = pz / pd;
            this.directionZ = -px / pd;
        } else if (pd > 1.0E-3) {
            this.directionX = -px / pd;
            this.directionZ = -pz / pd;
        } else {
            this.directionX = Compass.forwardX(in.heading());
            this.directionZ = Compass.forwardZ(in.heading());
        }
        this.watchX = in.x();
        this.watchY = in.y();
        this.watchZ = in.z();
    }

    @Override
    public void reset() {
        this.engaged = false;
        this.lastTime = Double.NaN;
        this.lost.reset();
        this.altitudeStick.reset();
    }
}
