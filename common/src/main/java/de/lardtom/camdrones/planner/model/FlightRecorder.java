package de.lardtom.camdrones.planner.model;

import java.util.Optional;

public final class FlightRecorder {
    public static final double SPACING = 10.0;
    public static final double TURN_DEGREES = 30.0;
    public static final double MIN_TURN_DISTANCE = 3.0;
    public static final double MIN_DIRECTION_DISTANCE = 1.0;

    private boolean active;
    private boolean hasLast;
    private double lastX;
    private double lastY;
    private double lastZ;
    private boolean hasDirection;
    private double dirX;
    private double dirZ;
    private int recorded;

    public boolean active() {
        return this.active;
    }

    public int recorded() {
        return this.recorded;
    }

    public void start() {
        this.active = true;
        this.hasLast = false;
        this.hasDirection = false;
        this.recorded = 0;
    }

    public void stop() {
        this.active = false;
        this.hasLast = false;
        this.hasDirection = false;
    }

    public Optional<Point> sample(double x, double y, double z) {
        if (!this.active || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return Optional.empty();
        }
        if (!this.hasLast) {
            return Optional.of(this.take(x, y, z));
        }
        double dx = x - this.lastX;
        double dy = y - this.lastY;
        double dz = z - this.lastZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double horizontal = Math.hypot(dx, dz);
        if (distance >= SPACING) {
            return Optional.of(this.take(x, y, z));
        }
        if (horizontal >= MIN_DIRECTION_DISTANCE) {
            double nx = dx / horizontal;
            double nz = dz / horizontal;
            if (!this.hasDirection) {
                this.dirX = nx;
                this.dirZ = nz;
                this.hasDirection = true;
            } else if (distance >= MIN_TURN_DISTANCE && angleDegrees(this.dirX, this.dirZ, nx, nz) >= TURN_DEGREES) {
                return Optional.of(this.take(x, y, z));
            }
        }
        return Optional.empty();
    }

    private Point take(double x, double y, double z) {
        this.hasLast = true;
        this.hasDirection = false;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.recorded++;
        return new Point(x, y, z);
    }

    static double angleDegrees(double ax, double az, double bx, double bz) {
        double dot = Math.clamp(ax * bx + az * bz, -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    public record Point(double x, double y, double z) {
    }
}
