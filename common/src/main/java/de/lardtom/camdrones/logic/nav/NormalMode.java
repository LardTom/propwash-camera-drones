package de.lardtom.camdrones.logic.nav;

public class NormalMode implements NavMode {
    public static final double STICK_DEADBAND = 0.05;
    public static final double THROTTLE_DEADBAND = 0.1;
    public static final double YAW_RATE = 90.0;
    public static final String LABEL = "N";

    private final double speed;
    private final double yawRate;
    private final double smoothing;
    private final String label;
    private final HoldController hold = new HoldController();
    private final AltitudeStick altitude;
    private double lastTime = Double.NaN;
    private double commandX;
    private double commandZ;
    private double commandYaw;

    public NormalMode(FlightClass flightClass) {
        this(flightClass, flightClass.normalSpeed(), YAW_RATE, 0.0, LABEL);
    }

    protected NormalMode(FlightClass flightClass, double speed, double yawRate, double smoothing, String label) {
        this.speed = flightClass.assistedSpeed(speed);
        this.yawRate = yawRate;
        this.smoothing = smoothing;
        this.label = label;
        this.altitude = new AltitudeStick(flightClass, smoothing);
    }

    protected double frameHeading(NavInput in) {
        return in.heading();
    }

    public double speed() {
        return this.speed;
    }

    @Override
    public void update(NavInput in, NavOutput out) {
        double dt = NavMode.step(this.lastTime, in.time());
        this.lastTime = in.time();
        double pitch = NavMode.deadband(in.stickPitch(), STICK_DEADBAND);
        double roll = NavMode.deadband(in.stickRoll(), STICK_DEADBAND);
        double frame = this.frameHeading(in);
        double wantX = (pitch * Compass.forwardX(frame) + roll * Compass.rightX(frame)) * this.speed;
        double wantZ = (pitch * Compass.forwardZ(frame) + roll * Compass.rightZ(frame)) * this.speed;
        double want = Math.hypot(wantX, wantZ);
        if (want > this.speed) {
            wantX *= this.speed / want;
            wantZ *= this.speed / want;
        }
        double wantYaw = NavMode.deadband(in.stickYaw(), STICK_DEADBAND) * this.yawRate;
        double a = alpha(this.smoothing, dt);
        this.commandX += (wantX - this.commandX) * a;
        this.commandZ += (wantZ - this.commandZ) * a;
        this.commandYaw += (wantYaw - this.commandYaw) * a;
        if (want > 0.0 || Math.hypot(this.commandX, this.commandZ) > 0.05) {
            this.hold.resetHorizontal();
            out.velocity(this.commandX, this.commandZ);
            out.maxSpeed(this.speed);
        } else {
            this.commandX = 0.0;
            this.commandZ = 0.0;
            this.hold.horizontal(in, out);
        }
        this.altitude.update(in, out, dt);
        out.yawRate(this.commandYaw);
        out.label(this.label);
    }

    @Override
    public void reset() {
        this.hold.reset();
        this.altitude.reset();
        this.lastTime = Double.NaN;
        this.commandX = 0.0;
        this.commandZ = 0.0;
        this.commandYaw = 0.0;
    }

    static double alpha(double smoothing, double dt) {
        return smoothing <= 0.0 ? 1.0 : 1.0 - Math.exp(-dt / smoothing);
    }

    public static final class AltitudeStick {
        private final FlightClass flightClass;
        private final double smoothing;
        private double command;
        private boolean holding;
        private boolean settling;
        private double holdY;

        public AltitudeStick(FlightClass flightClass, double smoothing) {
            this.flightClass = flightClass;
            this.smoothing = smoothing;
        }

        public double wanted(NavInput in) {
            double t = NavMode.deadband((in.stickThrottle() - NavInput.THROTTLE_CENTER) * 2.0, THROTTLE_DEADBAND);
            return t > 0.0 ? t * this.flightClass.climbSpeed() : t * this.flightClass.descendSpeed();
        }

        public void update(NavInput in, NavOutput out, double dt) {
            double want = this.wanted(in);
            this.command += (want - this.command) * alpha(this.smoothing, dt);
            if (want != 0.0 || Math.abs(this.command) > 0.05) {
                this.holding = false;
                out.climb(this.command);
                return;
            }
            this.command = 0.0;
            if (!this.holding) {
                this.holding = true;
                this.settling = true;
            }
            if (this.settling && Math.abs(in.vy()) < HoldController.BRAKE_DONE_SPEED) {
                this.settling = false;
                this.holdY = in.y();
            }
            if (this.settling) {
                out.climb(0.0);
            } else {
                out.holdAltitude(this.holdY);
            }
        }

        public double deficit(NavInput in) {
            return this.holding && !this.settling ? this.holdY - in.y() : 0.0;
        }

        public void reset() {
            this.command = 0.0;
            this.holding = false;
            this.settling = false;
        }
    }
}
