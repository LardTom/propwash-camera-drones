package de.lardtom.camdrones.logic.nav;

public final class HorizontalControl {
    public static final double G = 9.81;
    public static final double POSITION_GAIN = 1.0;
    public static final double VELOCITY_GAIN = 2.0;
    public static final double WIND_GAIN = 1.0;
    public static final double WIND_LIMIT = 3.0;
    public static final double MAX_TILT = 40.0;
    public static final double MIN_SIGHT_TILT = 5.0;
    public static final double DRAG_FEED_FORWARD = 0.25;
    public static final double MOVING_SPEED = 0.5;
    public static final double SETTLED_ERROR = 0.3;
    public static final double SETTLE_START = 1.5;
    public static final double CRUISE_DECAY = 0.3;
    public static final double STEADY_ACCELERATION = 0.8;
    public static final double STEADY_SECONDS = 0.5;
    public static final double ACCELERATION_SMOOTHING = 0.1;

    private double windX;
    private double windZ;
    private double cruiseX;
    private double cruiseZ;
    private boolean moving;
    private boolean settling;
    private double commandX;
    private double commandZ;
    private boolean active;
    private double lastTime = Double.NaN;
    private double lastVx = Double.NaN;
    private double lastVz = Double.NaN;
    private double accelerationX;
    private double accelerationZ;
    private double steadyFor;
    private double lastError;

    public boolean apply(NavInput in, NavOutput out) {
        double dt = NavMode.step(this.lastTime, in.time());
        this.lastTime = in.time();
        if (out.hasSticks() || out.horizontal() == NavOutput.Horizontal.NONE || out.vertical() == NavOutput.Vertical.NONE) {
            this.release();
            return false;
        }
        double limit = Double.isNaN(out.maxSpeed()) ? HoldController.HOLD_SPEED : out.maxSpeed();
        double wantX;
        double wantZ;
        if (out.horizontal() == NavOutput.Horizontal.POSITION) {
            wantX = POSITION_GAIN * (out.targetX() - in.x());
            wantZ = POSITION_GAIN * (out.targetZ() - in.z());
        } else {
            wantX = out.velocityX();
            wantZ = out.velocityZ();
        }
        double want = Math.hypot(wantX, wantZ);
        if (want > limit) {
            wantX *= limit / want;
            wantZ *= limit / want;
        }
        boolean nowMoving = out.horizontal() == NavOutput.Horizontal.VELOCITY && Math.min(want, limit) >= MOVING_SPEED;
        this.measure(in, dt);
        if (this.moving && !nowMoving) {
            this.settling = true;
            this.steadyFor = 0.0;
        }
        this.moving = nowMoving;
        this.commandX = wantX;
        this.commandZ = wantZ;
        this.active = true;
        double heading = in.heading();
        double fx = Compass.forwardX(heading);
        double fz = Compass.forwardZ(heading);
        double rx = Compass.rightX(heading);
        double rz = Compass.rightZ(heading);
        double forward = 0.0;
        double right = 0.0;
        if (in.grounded()) {
            this.windX = 0.0;
            this.windZ = 0.0;
            this.cruiseX = 0.0;
            this.cruiseZ = 0.0;
            this.settling = false;
        } else {
            double ex = wantX - in.vx();
            double ez = wantZ - in.vz();
            double error = Math.hypot(ex, ez);
            double ffX = DRAG_FEED_FORWARD * wantX;
            double ffZ = DRAG_FEED_FORWARD * wantZ;
            double aMax = maxAcceleration(in);
            double vf = in.vx() * fx + in.vz() * fz;
            double vr = in.vx() * rx + in.vz() * rz;
            double ahead = sightAcceleration(in, NavInput.Sensor.FORWARD, vf);
            double behind = sightAcceleration(in, NavInput.Sensor.BACK, -vf);
            double toRight = sightAcceleration(in, NavInput.Sensor.RIGHT, vr);
            double toLeft = sightAcceleration(in, NavInput.Sensor.LEFT, -vr);
            if (!this.moving) {
                double keep = Math.exp(-dt / CRUISE_DECAY);
                this.cruiseX *= keep;
                this.cruiseZ *= keep;
                if (error > SETTLE_START && this.lastError <= SETTLE_START) {
                    this.settling = true;
                    this.steadyFor = 0.0;
                } else if (this.settling && (error < SETTLED_ERROR || this.steadyFor >= STEADY_SECONDS)) {
                    this.settling = false;
                }
            }
            this.lastError = error;
            double rawX = VELOCITY_GAIN * ex + this.windX + this.cruiseX + ffX;
            double rawZ = VELOCITY_GAIN * ez + this.windZ + this.cruiseZ + ffZ;
            double rawForward = rawX * fx + rawZ * fz;
            double rawRight = rawX * rx + rawZ * rz;
            boolean free = Math.hypot(rawX, rawZ) < aMax && rawForward <= ahead && -rawForward <= behind && rawRight <= toRight
                    && -rawRight <= toLeft;
            if (free && this.moving) {
                this.cruiseX += WIND_GAIN * ex * dt;
                this.cruiseZ += WIND_GAIN * ez * dt;
                double total = Math.hypot(this.windX + this.cruiseX, this.windZ + this.cruiseZ);
                if (total > WIND_LIMIT) {
                    double scale = WIND_LIMIT / total;
                    this.cruiseX = (this.windX + this.cruiseX) * scale - this.windX;
                    this.cruiseZ = (this.windZ + this.cruiseZ) * scale - this.windZ;
                }
            } else if (free && !this.settling) {
                this.windX += WIND_GAIN * ex * dt;
                this.windZ += WIND_GAIN * ez * dt;
                double w = Math.hypot(this.windX, this.windZ);
                if (w > WIND_LIMIT) {
                    this.windX *= WIND_LIMIT / w;
                    this.windZ *= WIND_LIMIT / w;
                }
            }
            double ax = VELOCITY_GAIN * ex + this.windX + this.cruiseX + ffX;
            double az = VELOCITY_GAIN * ez + this.windZ + this.cruiseZ + ffZ;
            double a = Math.hypot(ax, az);
            if (a > aMax) {
                ax *= aMax / a;
                az *= aMax / a;
            }
            forward = Math.clamp(ax * fx + az * fz, -behind, ahead);
            right = Math.clamp(ax * rx + az * rz, -toLeft, toRight);
        }
        double pitch = Math.toDegrees(Math.atan2(forward, G)) / in.angleLimit();
        double roll = Math.toDegrees(Math.atan2(right, G)) / in.angleLimit();
        out.clearHorizontal();
        out.sticks(roll, pitch, out.yaw() == NavOutput.Yaw.NONE ? in.stickYaw() : 0.0, in.stickThrottle());
        return true;
    }

    private void measure(NavInput in, double dt) {
        if (dt > 0.0 && Double.isFinite(this.lastVx) && Double.isFinite(this.lastVz)) {
            double a = NormalMode.alpha(ACCELERATION_SMOOTHING, dt);
            this.accelerationX += ((in.vx() - this.lastVx) / dt - this.accelerationX) * a;
            this.accelerationZ += ((in.vz() - this.lastVz) / dt - this.accelerationZ) * a;
            this.steadyFor = Math.hypot(this.accelerationX, this.accelerationZ) < STEADY_ACCELERATION ? this.steadyFor + dt : 0.0;
        }
        this.lastVx = in.vx();
        this.lastVz = in.vz();
    }

    public static double maxAcceleration() {
        return G * Math.tan(Math.toRadians(MAX_TILT));
    }

    public static double maxTilt(NavInput in) {
        return Math.min(MAX_TILT, in.angleLimit());
    }

    public static double maxAcceleration(NavInput in) {
        return G * Math.tan(Math.toRadians(maxTilt(in)));
    }

    public static double sightAcceleration(NavInput in, NavInput.Sensor sensor, double toward) {
        double height = in.sightHeight();
        if (!(toward > 0.0) || Double.isNaN(height) || Double.isNaN(in.obstacle(sensor))) {
            return Double.POSITIVE_INFINITY;
        }
        double floor = G * Math.tan(Math.toRadians(MIN_SIGHT_TILT));
        return Math.max(floor, G * Math.max(0.0, height) / ObstacleGuard.sightNeeded(toward, ObstacleGuard.brakeAcceleration(in)));
    }

    public boolean active() {
        return this.active;
    }

    public double commandX() {
        return this.commandX;
    }

    public double commandZ() {
        return this.commandZ;
    }

    public double windX() {
        return this.windX;
    }

    public double windZ() {
        return this.windZ;
    }

    public double cruiseX() {
        return this.cruiseX;
    }

    public double cruiseZ() {
        return this.cruiseZ;
    }

    public boolean settling() {
        return this.settling;
    }

    public double steadyFor() {
        return this.steadyFor;
    }

    private void release() {
        this.active = false;
        this.cruiseX = 0.0;
        this.cruiseZ = 0.0;
        this.moving = false;
        this.settling = false;
        this.commandX = 0.0;
        this.commandZ = 0.0;
        this.lastVx = Double.NaN;
        this.lastVz = Double.NaN;
        this.accelerationX = 0.0;
        this.accelerationZ = 0.0;
        this.steadyFor = 0.0;
        this.lastError = 0.0;
    }

    public void reset() {
        this.release();
        this.windX = 0.0;
        this.windZ = 0.0;
        this.lastTime = Double.NaN;
    }
}
