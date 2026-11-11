package de.lardtom.camdrones.client.hud;

public final class LimitChime {
    public static final double REARM = 1.0;

    private boolean armed = true;
    private double clearSince = Double.NaN;

    public boolean update(double time, boolean limit) {
        if (!Double.isFinite(time)) {
            this.reset();
            return false;
        }
        if (limit) {
            this.clearSince = Double.NaN;
            if (this.armed) {
                this.armed = false;
                return true;
            }
            return false;
        }
        if (!this.armed) {
            if (Double.isNaN(this.clearSince) || time < this.clearSince) {
                this.clearSince = time;
            }
            if (time - this.clearSince >= REARM - 1.0E-6) {
                this.armed = true;
                this.clearSince = Double.NaN;
            }
        }
        return false;
    }

    public boolean armed() {
        return this.armed;
    }

    public void reset() {
        this.armed = true;
        this.clearSince = Double.NaN;
    }
}
