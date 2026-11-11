package de.lardtom.camdrones.client.hud;

public final class WindWatch {
    public static final double SETTLE_SECONDS = 4.0;
    public static final double PERSIST_SECONDS = 2.0;
    public static final double SMOOTHING_SECONDS = 1.0;
    public static final double ON_TILT = 15.0;
    public static final double OFF_TILT = 10.0;
    public static final double ON_DRIFT = 2.0;
    public static final double OFF_DRIFT = 1.0;
    public static final double CENTERED_STICK = 0.1;

    private double lastTime = Double.NaN;
    private double holdingSince = Double.NaN;
    private double candidateSince = Double.NaN;
    private double tilt = Double.NaN;
    private double drift = Double.NaN;
    private boolean high;

    public static boolean centered(double roll, double pitch, double yaw) {
        return Math.abs(roll) < CENTERED_STICK && Math.abs(pitch) < CENTERED_STICK && Math.abs(yaw) < CENTERED_STICK;
    }

    public boolean update(double time, boolean holding, double pitchDeg, double rollDeg, double horizontalSpeed) {
        if (!Double.isFinite(time) || !Double.isNaN(this.lastTime) && time < this.lastTime) {
            this.reset();
            return false;
        }
        double dt = Double.isNaN(this.lastTime) ? 0.0 : time - this.lastTime;
        this.lastTime = time;
        if (!holding || !Double.isFinite(pitchDeg) || !Double.isFinite(rollDeg)) {
            this.holdingSince = Double.NaN;
            this.candidateSince = Double.NaN;
            this.tilt = Double.NaN;
            this.drift = Double.NaN;
            this.high = false;
            return false;
        }
        if (Double.isNaN(this.holdingSince)) {
            this.holdingSince = time;
        }
        double tiltNow = Math.hypot(pitchDeg, rollDeg);
        double driftNow = Double.isFinite(horizontalSpeed) ? Math.abs(horizontalSpeed) : 0.0;
        double a = dt <= 0.0 ? 1.0 : 1.0 - Math.exp(-dt / SMOOTHING_SECONDS);
        this.tilt = Double.isNaN(this.tilt) ? tiltNow : this.tilt + (tiltNow - this.tilt) * a;
        this.drift = Double.isNaN(this.drift) ? driftNow : this.drift + (driftNow - this.drift) * a;
        if (time - this.holdingSince < SETTLE_SECONDS) {
            this.candidateSince = Double.NaN;
            this.high = false;
            return false;
        }
        if (this.high) {
            this.high = this.tilt >= OFF_TILT || this.drift >= OFF_DRIFT;
            return this.high;
        }
        if (this.tilt >= ON_TILT || this.drift >= ON_DRIFT) {
            if (Double.isNaN(this.candidateSince)) {
                this.candidateSince = time;
            }
            this.high = time - this.candidateSince >= PERSIST_SECONDS - 1.0E-9;
        } else {
            this.candidateSince = Double.NaN;
        }
        return this.high;
    }

    public boolean high() {
        return this.high;
    }

    public double tilt() {
        return this.tilt;
    }

    public void reset() {
        this.lastTime = Double.NaN;
        this.holdingSince = Double.NaN;
        this.candidateSince = Double.NaN;
        this.tilt = Double.NaN;
        this.drift = Double.NaN;
        this.high = false;
    }
}
