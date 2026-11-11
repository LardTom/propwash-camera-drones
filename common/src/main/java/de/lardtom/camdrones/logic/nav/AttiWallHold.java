package de.lardtom.camdrones.logic.nav;

public final class AttiWallHold {
    public static final double CENTER = NormalMode.STICK_DEADBAND;

    private static final NavInput.Sensor[] AROUND = {NavInput.Sensor.FORWARD, NavInput.Sensor.BACK, NavInput.Sensor.LEFT, NavInput.Sensor.RIGHT};

    private final HoldController hold = new HoldController();
    private final HorizontalControl control = new HorizontalControl();
    private boolean active;

    public boolean active() {
        return this.active;
    }

    public void reset() {
        this.active = false;
        this.hold.reset();
        this.control.reset();
    }

    public static boolean sensing(NavInput in, FlightClass flightClass) {
        if (!flightClass.allAroundSensors()) {
            return false;
        }
        for (NavInput.Sensor sensor : AROUND) {
            if (!Double.isNaN(in.obstacle(sensor)) || !Double.isNaN(in.range(sensor))) {
                return true;
            }
        }
        return false;
    }

    public static double toward(NavInput in, NavInput.Sensor sensor) {
        return switch (sensor) {
            case FORWARD -> in.stickPitch();
            case BACK -> -in.stickPitch();
            case RIGHT -> in.stickRoll();
            case LEFT -> -in.stickRoll();
            case UP, DOWN -> 0.0;
        };
    }

    public static boolean pushingAtSeenWall(NavInput in) {
        if (!in.pilot()) {
            return false;
        }
        for (NavInput.Sensor sensor : AROUND) {
            double distance = in.obstacle(sensor);
            if (Double.isFinite(distance) && distance < ObstacleGuard.SENSOR_RANGE && toward(in, sensor) > CENTER) {
                return true;
            }
        }
        return false;
    }

    public static boolean centered(NavInput in) {
        return Math.abs(in.stickRoll()) <= CENTER && Math.abs(in.stickPitch()) <= CENTER;
    }

    public void engage(NavInput in, FlightClass flightClass, boolean guarded) {
        this.reset();
        this.active = guarded && flightClass.allAroundSensors() && pushingAtSeenWall(in);
    }

    public void apply(NavInput in, NavOutput out) {
        if (!this.active) {
            return;
        }
        if (!in.pilot() || centered(in)) {
            this.reset();
            return;
        }
        out.clearSticks();
        this.hold.horizontal(in, out);
        if (!this.control.apply(in, out)) {
            out.clearHorizontal();
            out.sticks(0.0, 0.0, 0.0, in.stickThrottle());
        }
        out.warn(NavOutput.Warning.OBSTACLE_STOP);
    }
}
