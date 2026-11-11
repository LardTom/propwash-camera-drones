package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.logic.nav.ObstacleGuard;

public final class ObstacleBeeper {
    public static final double RANGE = ObstacleGuard.WARN_DISTANCE;
    public static final double STOP = ObstacleGuard.STOP_DISTANCE;
    public static final double SLOWEST = 0.8;
    public static final double FASTEST = 0.1;
    public static final float LOW_PITCH = 1.0F;
    public static final float HIGH_PITCH = 1.3F;

    private double last = Double.NaN;

    public static boolean audible(double distance) {
        return Double.isFinite(distance) && distance < RANGE;
    }

    public static double closeness(double distance) {
        if (!audible(distance)) {
            return 0.0;
        }
        return Math.clamp((RANGE - distance) / (RANGE - STOP), 0.0, 1.0);
    }

    public static double interval(double distance) {
        return SLOWEST + (FASTEST - SLOWEST) * closeness(distance);
    }

    public static float pitch(double distance) {
        return (float) (LOW_PITCH + (HIGH_PITCH - LOW_PITCH) * closeness(distance));
    }

    public boolean update(double time, double distance, boolean warn) {
        if (!warn || !audible(distance) || !Double.isFinite(time)) {
            this.last = Double.NaN;
            return false;
        }
        if (Double.isNaN(this.last) || time < this.last || time - this.last >= interval(distance) - 1.0E-6) {
            this.last = time;
            return true;
        }
        return false;
    }

    public boolean active() {
        return !Double.isNaN(this.last);
    }

    public void reset() {
        this.last = Double.NaN;
    }
}
