package de.lardtom.camdrones.logic.nav;

public final class SportMode implements NavMode {
    public static final double MAX_ANGLE = 35.0;
    public static final double MIN_ANGLE = 10.0;
    public static final double THROTTLE_RESERVE = 0.9;
    public static final double LOAD_SMOOTHING = 0.2;
    public static final double ERROR_SMOOTHING = 0.2;
    public static final double ERROR_LIMIT = 1.0;
    public static final double LEAN_GAIN = 6.0;
    public static final double LEAN_INTEGRAL = 4.0;
    public static final double LEAN_RECOVERY = 50.0;
    public static final double SINK_TOLERANCE = 0.15;
    public static final double SINK_RECOVERY = 30.0;
    public static final double LEAN_DROP_RATE = 20.0;
    public static final double LEAN_RISE_RATE = 5.0;
    public static final double GOVERNOR_GAIN = 1.5;
    public static final double GOVERNOR_RATE = 4.0;
    public static final double GOVERNOR_LEAD = 0.5;
    public static final double ACCELERATION_SMOOTHING = 0.2;
    public static final String LABEL = "S";

    private final NormalMode.AltitudeStick altitude;
    private final SportThrottle throttle;
    private final double topSpeed;
    private double lastTime = Double.NaN;
    private double lean = Double.NaN;
    private double base = Double.NaN;
    private double load = Double.NaN;
    private double error = Double.NaN;
    private double sink = Double.NaN;
    private double governor = Double.NaN;
    private double lastSpeed = Double.NaN;
    private double acceleration;

    public SportMode(FlightClass flightClass) {
        this(flightClass, null);
    }

    public SportMode(FlightClass flightClass, SportThrottle throttle) {
        this.altitude = new NormalMode.AltitudeStick(flightClass, 0.0);
        this.throttle = throttle;
        this.topSpeed = flightClass.sportSpeed();
    }

    public double topSpeed() {
        return this.topSpeed;
    }

    public double acceleration() {
        return this.acceleration;
    }

    public static double stickScale(NavInput in) {
        return Math.min(1.0, MAX_ANGLE / in.angleLimit());
    }

    public double lean() {
        return this.lean;
    }

    private double scale(NavInput in, double dt) {
        double max = Math.min(MAX_ANGLE, in.angleLimit());
        double floor = Math.min(MIN_ANGLE, max);
        if (Double.isNaN(this.base) || this.base > max) {
            this.base = max;
        }
        double rate = LEAN_RISE_RATE;
        double press = 0.0;
        if (in.grounded()) {
            this.load = Double.NaN;
            this.error = Double.NaN;
            this.sink = Double.NaN;
        } else {
            double throttle = this.throttle == null || Double.isNaN(in.hoverThrottle()) ? Double.NaN : this.throttle.throttle();
            double error = this.throttle == null ? Double.NaN : this.throttle.error();
            double share = 1.0;
            if (Double.isFinite(throttle) && Double.isFinite(error)) {
                this.load = smooth(this.load, throttle, LOAD_SMOOTHING, dt);
                share = NavMode.smoothstep((this.load - THROTTLE_RESERVE) / (1.0 - THROTTLE_RESERVE));
            } else {
                this.load = Double.NaN;
                error = Math.min(0.0, this.altitude.wanted(in)) - in.vy() + SportThrottle.ALTITUDE_GAIN * this.altitude.deficit(in);
            }
            this.error = smooth(this.error, limit(error, in), ERROR_SMOOTHING, dt);
            this.sink = smooth(this.sink, Math.clamp(Math.min(error, -in.vy()), 0.0, ERROR_LIMIT + Math.max(0.0, -in.vy())), ERROR_SMOOTHING, dt);
            if (Double.isNaN(this.load)) {
                double margin = SINK_TOLERANCE - this.error;
                rate = LEAN_INTEGRAL * Math.min(0.0, margin) + SINK_RECOVERY * Math.max(0.0, margin);
            } else {
                rate = -LEAN_INTEGRAL * share * this.error + LEAN_RECOVERY * Math.max(0.0, THROTTLE_RESERVE - this.load);
            }
            press = LEAN_GAIN * share * this.sink;
        }
        this.base = Math.clamp(this.base + Math.clamp(rate, -LEAN_DROP_RATE, LEAN_RISE_RATE) * dt, floor, max);
        this.lean = Math.clamp(this.base - press, floor, max);
        return this.lean / in.angleLimit();
    }

    private static double limit(double error, NavInput in) {
        return Math.clamp(error, -ERROR_LIMIT, ERROR_LIMIT + Math.max(0.0, -in.vy()));
    }

    private static double smooth(double value, double target, double smoothing, double dt) {
        return Double.isNaN(value) ? target : value + (target - value) * NormalMode.alpha(smoothing, dt);
    }

    private double cap(NavInput in, double dt, double speed) {
        double max = Math.min(MAX_ANGLE, in.angleLimit());
        if (dt > 0.0 && Double.isFinite(this.lastSpeed)) {
            this.acceleration += ((speed - this.lastSpeed) / dt - this.acceleration) * NormalMode.alpha(ACCELERATION_SMOOTHING, dt);
        }
        this.lastSpeed = speed;
        double over = speed - this.topSpeed;
        if (Double.isNaN(this.governor)) {
            this.governor = max;
        }
        this.governor = Math.clamp(this.governor - GOVERNOR_RATE * over * dt, 0.0, max);
        double ahead = over + GOVERNOR_LEAD * Math.max(0.0, this.acceleration);
        return Math.clamp(this.governor - GOVERNOR_GAIN * ahead, 0.0, max);
    }

    @Override
    public void update(NavInput in, NavOutput out) {
        double dt = NavMode.step(this.lastTime, in.time());
        this.lastTime = in.time();
        double scale = this.scale(in, dt);
        double roll = in.stickRoll() * scale;
        double pitch = in.stickPitch() * scale;
        double heading = in.heading();
        double forward = in.vx() * Compass.forwardX(heading) + in.vz() * Compass.forwardZ(heading);
        double right = in.vx() * Compass.rightX(heading) + in.vz() * Compass.rightZ(heading);
        double speed = Math.hypot(forward, right);
        double cap = this.cap(in, dt, speed) / in.angleLimit();
        if (speed > 1.0E-6) {
            double along = (pitch * forward + roll * right) / speed;
            if (along > cap) {
                double cut = (along - cap) / speed;
                pitch -= forward * cut;
                roll -= right * cut;
            }
        }
        out.sticks(roll, pitch, in.stickYaw(), in.stickThrottle());
        this.altitude.update(in, out, dt);
        out.label(LABEL);
    }

    @Override
    public void reset() {
        this.altitude.reset();
        this.lastTime = Double.NaN;
        this.lean = Double.NaN;
        this.base = Double.NaN;
        this.load = Double.NaN;
        this.error = Double.NaN;
        this.sink = Double.NaN;
        this.governor = Double.NaN;
        this.lastSpeed = Double.NaN;
        this.acceleration = 0.0;
    }
}
