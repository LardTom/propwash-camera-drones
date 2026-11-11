package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.logic.estimate.FlightTimeEstimate;
import de.lardtom.camdrones.logic.nav.FlightClass;

public final class FlightLeft {
    public static final double AIRBORNE_HEIGHT = 0.5;
    public static final double AIRBORNE_SPEED = 0.5;

    private FlightClass flightClass;
    private FlightTimeEstimate estimate;
    private double sampledAt = Double.NaN;
    private double benchSeconds = Double.NaN;

    public static boolean airborne(boolean armed, double heightAboveHome, double speed, double verticalSpeed) {
        return armed && (heightAboveHome > AIRBORNE_HEIGHT || speed > AIRBORNE_SPEED || Math.abs(verticalSpeed) > AIRBORNE_SPEED);
    }

    public void bench(double fullPackSeconds) {
        this.benchSeconds = fullPackSeconds > 0.0 && Double.isFinite(fullPackSeconds) ? fullPackSeconds : Double.NaN;
    }

    public double benchSeconds() {
        return this.benchSeconds;
    }

    public void sample(FlightClass flightClass, double time, double charge) {
        this.sample(flightClass, time, charge, true);
    }

    public void sample(FlightClass flightClass, double time, double charge, boolean airborne) {
        if (flightClass == null) {
            return;
        }
        if (flightClass != this.flightClass || this.estimate == null) {
            this.flightClass = flightClass;
            this.estimate = new FlightTimeEstimate(flightClass);
            this.sampledAt = Double.NaN;
        }
        if (!Double.isFinite(time) || !Double.isFinite(charge) || time == this.sampledAt) {
            return;
        }
        this.sampledAt = time;
        this.estimate.sample(time, Math.clamp(charge, 0.0, 1.0), airborne);
    }

    public double remaining(double charge, double speed, double climb) {
        if (this.estimate == null || !Double.isFinite(charge)) {
            return Double.NaN;
        }
        double s = Double.isFinite(speed) ? Math.max(0.0, speed) : 0.0;
        double c = Double.isFinite(climb) ? Math.max(0.0, climb) : 0.0;
        return this.estimate.remainingSeconds(Math.clamp(charge, 0.0, 1.0), s, c, this.benchSeconds);
    }

    public boolean measured() {
        return this.estimate != null && this.estimate.measured();
    }

    public void reset() {
        this.flightClass = null;
        this.estimate = null;
        this.sampledAt = Double.NaN;
        this.benchSeconds = Double.NaN;
    }
}
