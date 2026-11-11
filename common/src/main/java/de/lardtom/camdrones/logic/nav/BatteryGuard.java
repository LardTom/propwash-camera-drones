package de.lardtom.camdrones.logic.nav;

public final class BatteryGuard {
    public static final double LOW = 0.2;
    public static final double CRITICAL = 0.1;
    public static final double REARM = 0.05;
    public static final double MAX_LAND_RATE = 3.0;
    public static final String LABEL = "LAND";

    private boolean returnRequested;
    private boolean landing;

    public static double landRate(FlightClass flightClass) {
        return Math.min(flightClass.descendSpeed(), MAX_LAND_RATE);
    }

    public boolean returnRequested() {
        return this.returnRequested;
    }

    public void returnRequested(boolean value) {
        this.returnRequested = value;
    }

    public boolean landing() {
        return this.landing;
    }

    private static boolean critical(NavInput in) {
        return in.pilot() && Double.isFinite(in.battery()) && in.battery() < CRITICAL;
    }

    public boolean startsLanding(NavInput in) {
        boolean now = critical(in);
        boolean starts = now && !this.landing;
        this.landing = now;
        return starts;
    }

    public void apply(NavInput in, NavOutput out, FlightClass flightClass) {
        double charge = in.battery();
        if (!in.pilot() || !Double.isFinite(charge)) {
            return;
        }
        if (charge >= LOW + REARM) {
            this.returnRequested = false;
        }
        if (charge < LOW) {
            out.warn(NavOutput.Warning.LOW_BATTERY);
            if (!this.returnRequested && !in.grounded()) {
                this.returnRequested = true;
                out.requestReturnHome();
            }
        }
        if (critical(in)) {
            out.climb(-landRate(flightClass));
            out.land();
            out.label(LABEL);
        }
    }
}
