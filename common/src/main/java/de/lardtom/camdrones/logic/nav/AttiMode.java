package de.lardtom.camdrones.logic.nav;

public final class AttiMode implements NavMode {
    public static final int MIN_SATELLITES = 6;
    public static final double REGAIN_SECONDS = 2.0;
    public static final double MAX_ANGLE = 25.0;
    public static final String LABEL = "ATTI";

    private final NormalMode.AltitudeStick altitude;
    private boolean engaged;
    private double fixSince = Double.NaN;
    private double lastTime = Double.NaN;
    private NavOutput.Warning cause = NavOutput.Warning.NO_GPS;

    public AttiMode(FlightClass flightClass) {
        this.altitude = new NormalMode.AltitudeStick(flightClass, 0.0);
    }

    public static boolean positionUsable(NavInput in) {
        return gpsUsable(in) && in.compassHealthy();
    }

    public static boolean gpsUsable(NavInput in) {
        return in.gpsFix() && in.satellites() >= MIN_SATELLITES;
    }

    public boolean engaged(NavInput in) {
        if (!positionUsable(in)) {
            if (!this.engaged) {
                this.altitude.reset();
                this.lastTime = Double.NaN;
            }
            this.engaged = true;
            this.fixSince = Double.NaN;
            this.cause = gpsUsable(in) ? NavOutput.Warning.COMPASS : NavOutput.Warning.NO_GPS;
        } else if (this.engaged) {
            if (Double.isNaN(this.fixSince)) {
                this.fixSince = in.time();
            }
            if (in.time() - this.fixSince >= REGAIN_SECONDS) {
                this.engaged = false;
                this.fixSince = Double.NaN;
            }
        }
        return this.engaged;
    }

    public boolean engaged() {
        return this.engaged;
    }

    public NavOutput.Warning cause() {
        return this.cause;
    }

    @Override
    public void update(NavInput in, NavOutput out) {
        double dt = NavMode.step(this.lastTime, in.time());
        this.lastTime = in.time();
        double scale = Math.min(1.0, MAX_ANGLE / in.angleLimit());
        out.sticks(in.stickRoll() * scale, in.stickPitch() * scale, 0.0, in.stickThrottle());
        this.altitude.update(in, out, dt);
        out.yawRate(NavMode.deadband(in.stickYaw(), NormalMode.STICK_DEADBAND) * NormalMode.YAW_RATE);
        out.label(LABEL);
        if (!gpsUsable(in)) {
            out.warn(NavOutput.Warning.NO_GPS);
        }
        if (!in.compassHealthy()) {
            out.warn(NavOutput.Warning.COMPASS);
        }
        if (gpsUsable(in) && in.compassHealthy()) {
            out.warn(this.cause);
        }
    }

    @Override
    public void reset() {
        this.engaged = false;
        this.fixSince = Double.NaN;
        this.lastTime = Double.NaN;
        this.cause = NavOutput.Warning.NO_GPS;
        this.altitude.reset();
    }
}
