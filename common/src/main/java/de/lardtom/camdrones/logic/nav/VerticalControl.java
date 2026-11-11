package de.lardtom.camdrones.logic.nav;

public final class VerticalControl {
    public static final double ALTITUDE_GAIN = 0.9;
    public static final double BIAS_SMOOTHING = 2.0;
    public static final double BIAS_LIMIT = 2.0;
    public static final double RATE_GAIN = 1.0;
    public static final double STICK_RATE_GAIN = 2.0;
    public static final double STICK_RATE = 0.5;
    public static final double RATE_LIMIT = 2.0;
    public static final double HOLD_RATE = 1.5;
    public static final double BRAKE_RATE_GAIN = 2.0;

    private double bias;
    private boolean primed;
    private double lastTime = Double.NaN;

    public void apply(NavInput in, NavOutput out, double estimatedY, double estimatedVy) {
        this.apply(in, out, estimatedY, estimatedVy, false);
    }

    public void apply(NavInput in, NavOutput out, double estimatedY, double estimatedVy, boolean braking) {
        double dt = NavMode.step(this.lastTime, in.time());
        this.lastTime = in.time();
        if (!Double.isFinite(estimatedY) || !Double.isFinite(estimatedVy)) {
            this.reset();
            return;
        }
        double measured = Math.clamp(estimatedVy - in.vy(), -BIAS_LIMIT, BIAS_LIMIT);
        if (!this.primed) {
            this.bias = measured;
            this.primed = true;
        } else {
            this.bias += (measured - this.bias) * NormalMode.alpha(BIAS_SMOOTHING, dt);
        }
        double offset = estimatedY - in.y();
        boolean air = !in.grounded();
        switch (out.vertical()) {
            case ALTITUDE -> {
                double target = out.altitudeTarget();
                double lift = 0.0;
                if (air) {
                    double want = Math.clamp(ALTITUDE_GAIN * (target - in.y()), -HOLD_RATE, HOLD_RATE);
                    lift = (this.bias + (braking ? settle(want, in.vy()) : catchUp(want, in.vy()))) / ALTITUDE_GAIN;
                }
                out.holdAltitude(target + offset + lift);
            }
            case CLIMB -> {
                double want = out.climbRate();
                if (air) {
                    out.climb(want + (braking ? settle(want, in.vy()) : catchUp(want, in.vy())));
                }
            }
            case NONE -> {
            }
        }
    }

    public static double catchUp(double want, double vy) {
        double gain = Math.abs(want) >= STICK_RATE ? STICK_RATE_GAIN : RATE_GAIN;
        return Math.clamp(gain * (want - vy), 0.0, RATE_LIMIT);
    }

    public static double settle(double want, double vy) {
        return Math.clamp(BRAKE_RATE_GAIN * (want - vy), -RATE_LIMIT, RATE_LIMIT);
    }

    public double bias() {
        return this.bias;
    }

    public void reset() {
        this.bias = 0.0;
        this.primed = false;
        this.lastTime = Double.NaN;
    }
}
