package de.lardtom.camdrones.client.gimbal.model;

public final class TiltGlide {
    public static final long GLIDE_NANOS = (SendGate.SEND_MILLIS + SendGate.SEND_MILLIS / 2L) * 1_000_000L;
    public static final double EPSILON = 1.0E-6;

    private boolean started;
    private double from;
    private double target;
    private long changedNanos;

    public double shown(double wanted, long nowNanos) {
        if (!Double.isFinite(wanted)) {
            return wanted;
        }
        if (!this.started) {
            this.started = true;
            this.from = wanted;
            this.target = wanted;
            this.changedNanos = nowNanos;
            return wanted;
        }
        if (Math.abs(wanted - this.target) > EPSILON) {
            this.from = this.at(nowNanos);
            this.target = wanted;
            this.changedNanos = nowNanos;
        }
        return this.at(nowNanos);
    }

    public GimbalResolver.Aim apply(GimbalResolver.Aim aim, boolean remote, long nowNanos) {
        if (aim == null || !remote || aim.automatic()) {
            this.reset();
            return aim;
        }
        return aim.withTilt(this.shown(aim.tilt(), nowNanos));
    }

    public boolean gliding(long nowNanos) {
        return this.started && nowNanos - this.changedNanos < GLIDE_NANOS && Math.abs(this.target - this.from) > EPSILON;
    }

    public void reset() {
        this.started = false;
    }

    private double at(long nowNanos) {
        double f = Math.clamp((nowNanos - this.changedNanos) / (double) GLIDE_NANOS, 0.0, 1.0);
        return this.from + (this.target - this.from) * f;
    }
}
