package de.lardtom.camdrones.logic.nav;

public final class SportThrottle {
    public static final double ALTITUDE_GAIN = 0.9;
    public static final double RATE_GAIN = 5.0;
    public static final double INTEGRAL_GAIN = 6.0;
    public static final double INTEGRAL_LIMIT = 40.0;
    public static final double HOLD_RATE = 1.5;
    public static final double CLIMB_LEAD = 1.0;
    public static final double MAX_DOWN = 0.5;
    public static final double MAX_UP = 4.0;
    public static final double MIN_COS = 0.5;
    public static final double MIN_FACTOR = 0.05;
    public static final double EXHAUSTED = 0.98;

    private double integral;
    private double targetY = Double.NaN;
    private double throttle = Double.NaN;
    private double error = Double.NaN;
    private double lastTime = Double.NaN;

    public boolean apply(NavInput in, NavOutput out) {
        double dt = NavMode.step(this.lastTime, in.time());
        this.lastTime = in.time();
        double hover = in.hoverThrottle();
        if (!out.hasSticks() || out.vertical() == NavOutput.Vertical.NONE || out.landing() || Double.isNaN(hover)) {
            this.release();
            return false;
        }
        double y = in.y();
        double limit = HOLD_RATE;
        double want;
        if (out.vertical() == NavOutput.Vertical.CLIMB) {
            double climb = out.climbRate();
            if (Double.isNaN(this.targetY)) {
                this.targetY = y;
            }
            this.targetY = Math.clamp(this.targetY + climb * dt, y - CLIMB_LEAD, y + CLIMB_LEAD);
            limit = Math.max(HOLD_RATE, Math.abs(climb));
            want = Math.clamp(climb + ALTITUDE_GAIN * (this.targetY - y), -limit, limit);
        } else {
            this.targetY = out.altitudeTarget();
            want = Math.clamp(ALTITUDE_GAIN * (this.targetY - y), -limit, limit);
        }
        if (in.grounded() && want <= 0.0) {
            this.integral = 0.0;
            this.targetY = y;
            this.throttle = 0.0;
            this.error = 0.0;
        } else {
            double error = want - in.vy();
            this.error = error;
            boolean stuck = Double.isFinite(this.throttle) && (this.throttle >= 1.0 && error > 0.0 || this.throttle <= 0.0 && error < 0.0);
            if (!stuck) {
                this.integral = Math.clamp(this.integral + INTEGRAL_GAIN * error * dt, -INTEGRAL_LIMIT, INTEGRAL_LIMIT);
            }
            double g = HorizontalControl.G;
            double acceleration = Math.clamp(RATE_GAIN * error + this.integral, -MAX_DOWN * g, MAX_UP * g);
            double cos = Math.max(MIN_COS, Math.cos(Math.toRadians(in.pitch())) * Math.cos(Math.toRadians(in.roll())));
            double factor = Math.max(MIN_FACTOR, (1.0 + acceleration / g) / cos);
            this.throttle = Math.clamp(hover * Math.sqrt(factor), 0.0, 1.0);
        }
        out.sticks(out.stickRoll(), out.stickPitch(), out.stickYaw(), this.throttle);
        out.clearVertical();
        return true;
    }

    public double throttle() {
        return this.throttle;
    }

    public double error() {
        return this.error;
    }

    public boolean exhausted() {
        return !(this.throttle < EXHAUSTED);
    }

    public double integral() {
        return this.integral;
    }

    private void release() {
        this.integral = 0.0;
        this.targetY = Double.NaN;
        this.throttle = Double.NaN;
        this.error = Double.NaN;
    }

    public void reset() {
        this.release();
        this.lastTime = Double.NaN;
    }
}
