package de.lardtom.camdrones.logic.nav;

import de.lardtom.camdrones.logic.gimbal.GimbalAim;

public final class AutoShots implements NavMode {
    public static final double RETREAT_SECONDS = 15.0;
    public static final double RETREAT_SPEED = 2.0;
    public static final double RETREAT_CLIMB = 1.0;
    public static final double RISE_SECONDS = 12.0;
    public static final double RISE_CLIMB = 1.5;
    public static final double CIRCLE_SECONDS = 20.0;
    public static final double SPIRAL_SECONDS = 20.0;
    public static final double SPIRAL_WIDEN = 10.0;
    public static final double SPIRAL_CLIMB = 0.75;
    public static final double MIN_RADIUS = 5.0;
    public static final double SUBJECT_DISTANCE = 10.0;
    public static final String LABEL = "AUTO";

    public enum Shot {
        RETREAT,
        RISE,
        CIRCLE,
        SPIRAL
    }

    private final FlightClass flightClass;
    private final Shot shot;
    private final double subjectX;
    private final double subjectY;
    private final double subjectZ;
    private final HoldController hold = new HoldController();
    private boolean started;
    private boolean done;
    private double startTime;
    private double startX;
    private double startY;
    private double startZ;
    private double startHeading;
    private double startTilt;
    private double radius;
    private OrbitController orbit;

    public AutoShots(FlightClass flightClass, Shot shot, double subjectX, double subjectY, double subjectZ) {
        this.flightClass = flightClass;
        this.shot = shot;
        this.subjectX = subjectX;
        this.subjectY = subjectY;
        this.subjectZ = subjectZ;
    }

    public static AutoShots ahead(FlightClass flightClass, Shot shot, NavInput in) {
        double h = in.heading();
        double ground = Double.isFinite(in.heightAboveGround()) ? in.heightAboveGround() : 0.0;
        return new AutoShots(flightClass, shot, in.x() + Compass.forwardX(h) * SUBJECT_DISTANCE, in.y() - ground,
                in.z() + Compass.forwardZ(h) * SUBJECT_DISTANCE);
    }

    public static double duration(Shot shot) {
        return switch (shot) {
            case RETREAT -> RETREAT_SECONDS;
            case RISE -> RISE_SECONDS;
            case CIRCLE -> CIRCLE_SECONDS;
            case SPIRAL -> SPIRAL_SECONDS;
        };
    }

    public Shot shot() {
        return this.shot;
    }

    public boolean done() {
        return this.done;
    }

    public double subjectX() {
        return this.subjectX;
    }

    public double subjectY() {
        return this.subjectY;
    }

    public double subjectZ() {
        return this.subjectZ;
    }

    public double progress(NavInput in) {
        if (!this.started) {
            return 0.0;
        }
        return Math.clamp((in.time() - this.startTime) / duration(this.shot), 0.0, 1.0);
    }

    public void startTilt(double degrees) {
        this.startTilt = degrees;
    }

    @Override
    public void update(NavInput in, NavOutput out) {
        if (!this.started) {
            this.start(in);
        }
        double t = this.progress(in);
        if (!this.done && t >= 1.0) {
            this.done = true;
            this.hold.reset();
        }
        if (this.done) {
            this.hold.update(in, out);
            if (this.shot != Shot.RISE) {
                out.gimbalTarget(this.subjectX, this.subjectY, this.subjectZ);
            } else {
                out.gimbalTilt(FlightClass.GIMBAL_MIN_TILT);
            }
            return;
        }
        switch (this.shot) {
            case RETREAT -> this.retreat(in, out);
            case RISE -> this.rise(out, t);
            case CIRCLE -> this.orbit.update(in, out);
            case SPIRAL -> {
                this.orbit.radius(this.radius + SPIRAL_WIDEN * t);
                this.orbit.altitude(this.startY + SPIRAL_CLIMB * SPIRAL_SECONDS * t);
                this.orbit.update(in, out);
            }
        }
        out.label(LABEL);
    }

    private void start(NavInput in) {
        this.started = true;
        this.startTime = in.time();
        this.startX = in.x();
        this.startY = in.y();
        this.startZ = in.z();
        this.startHeading = in.heading();
        this.radius = Math.max(MIN_RADIUS, Math.hypot(in.x() - this.subjectX, in.z() - this.subjectZ));
        if (this.shot == Shot.CIRCLE || this.shot == Shot.SPIRAL) {
            double seconds = duration(this.shot);
            double mean = this.shot == Shot.SPIRAL ? this.radius + SPIRAL_WIDEN / 2.0 : this.radius;
            double speed = 2.0 * Math.PI * mean / seconds;
            this.orbit = new OrbitController(this.flightClass, this.subjectX, this.subjectY, this.subjectZ, this.radius, in.y(), speed, true);
            this.orbit.label(LABEL);
        }
    }

    private void retreat(NavInput in, NavOutput out) {
        double speed = Math.min(RETREAT_SPEED, this.flightClass.cineSpeed());
        double bx = -Compass.forwardX(this.startHeading);
        double bz = -Compass.forwardZ(this.startHeading);
        double px = in.x() - this.startX;
        double pz = in.z() - this.startZ;
        double along = px * bx + pz * bz;
        double ex = px - along * bx;
        double ez = pz - along * bz;
        out.velocity(bx * speed - WaypointFollower.CROSS_GAIN * ex, bz * speed - WaypointFollower.CROSS_GAIN * ez);
        out.maxSpeed(speed + 1.0);
        out.climb(Math.min(RETREAT_CLIMB, this.flightClass.climbSpeed()));
        out.heading(this.startHeading);
        out.gimbalTarget(this.subjectX, this.subjectY, this.subjectZ);
    }

    private void rise(NavOutput out, double t) {
        out.holdPosition(this.startX, this.startZ);
        out.maxSpeed(HoldController.HOLD_SPEED);
        out.climb(Math.min(RISE_CLIMB, this.flightClass.climbSpeed()));
        out.heading(this.startHeading);
        out.gimbalTilt(GimbalAim.interpolate(this.startTilt, FlightClass.GIMBAL_MIN_TILT, t));
    }

    @Override
    public void reset() {
        this.started = false;
        this.done = false;
        this.hold.reset();
        this.orbit = null;
    }
}
