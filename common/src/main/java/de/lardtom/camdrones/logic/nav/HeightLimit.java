package de.lardtom.camdrones.logic.nav;

public final class HeightLimit {
    public static final double DEFAULT_MAX_ALTITUDE = 120.0;
    public static final double MAX_ALTITUDE = 512.0;
    public static final double MAX_DISTANCE = 4096.0;
    public static final double APPROACH_GAIN = 1.0;
    public static final double BRAKE_ACCEL = 2.5;
    public static final double BRAKE_LAG = 0.7;
    public static final double WARN_MARGIN = 0.5;

    private static final HeightLimit OFF = new HeightLimit(0.0, 0.0, 0.0, 0.0, 0.0);

    private final double homeX;
    private final double homeY;
    private final double homeZ;
    private final double maxAltitude;
    private final double maxDistance;

    public HeightLimit(double homeX, double homeY, double homeZ, double maxAltitude, double maxDistance) {
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        this.maxAltitude = Math.clamp(Double.isFinite(maxAltitude) ? maxAltitude : 0.0, 0.0, MAX_ALTITUDE);
        this.maxDistance = Math.clamp(Double.isFinite(maxDistance) ? maxDistance : 0.0, 0.0, MAX_DISTANCE);
    }

    public static HeightLimit off() {
        return OFF;
    }

    public boolean active() {
        return this.maxAltitude > 0.0 || this.maxDistance > 0.0;
    }

    public double maxAltitude() {
        return this.maxAltitude;
    }

    public double maxDistance() {
        return this.maxDistance;
    }

    public double ceiling() {
        return this.maxAltitude > 0.0 ? this.homeY + this.maxAltitude : Double.POSITIVE_INFINITY;
    }

    public double distanceFromHome(double x, double z) {
        return Math.hypot(x - this.homeX, z - this.homeZ);
    }

    public void apply(NavInput in, NavOutput out) {
        if (this.maxAltitude > 0.0) {
            this.applyCeiling(in, out);
        }
        if (this.maxDistance > 0.0) {
            this.applyDistance(in, out);
        }
    }

    private void applyCeiling(NavInput in, NavOutput out) {
        double ceiling = this.ceiling();
        double room = ceiling - in.y();
        boolean limited = false;
        if (out.vertical() == NavOutput.Vertical.ALTITUDE && out.altitudeTarget() > ceiling) {
            out.holdAltitude(Math.max(ceiling, Math.min(out.altitudeTarget(), in.y())));
            limited = true;
        } else if (out.vertical() == NavOutput.Vertical.CLIMB && out.climbRate() > 0.0) {
            double allowed = Math.max(0.0, room * APPROACH_GAIN);
            if (out.climbRate() > allowed) {
                out.climb(allowed);
                limited = true;
            }
        }
        if (limited && room < WARN_MARGIN + 1.0 || room < 0.0) {
            out.warn(NavOutput.Warning.ALTITUDE_LIMIT);
        }
    }

    private void applyDistance(NavInput in, NavOutput out) {
        double dx = in.x() - this.homeX;
        double dz = in.z() - this.homeZ;
        double d = Math.hypot(dx, dz);
        if (out.horizontal() == NavOutput.Horizontal.POSITION) {
            double tx = out.targetX() - this.homeX;
            double tz = out.targetZ() - this.homeZ;
            double td = Math.hypot(tx, tz);
            if (td > this.maxDistance) {
                out.holdPosition(this.homeX + tx * this.maxDistance / td, this.homeZ + tz * this.maxDistance / td);
                out.warn(NavOutput.Warning.DISTANCE_LIMIT);
            }
            return;
        }
        if (out.horizontal() != NavOutput.Horizontal.VELOCITY || d < 1.0E-6) {
            return;
        }
        double nx = dx / d;
        double nz = dz / d;
        double outward = out.velocityX() * nx + out.velocityZ() * nz;
        double gap = this.maxDistance - d;
        double allowed = Math.min(Math.max(0.0, gap * APPROACH_GAIN), ObstacleGuard.stopSpeed(gap, BRAKE_ACCEL, BRAKE_LAG));
        if (outward > allowed) {
            double cut = outward - allowed;
            out.velocity(out.velocityX() - nx * cut, out.velocityZ() - nz * cut);
            if (this.maxDistance - d < WARN_MARGIN + 1.0) {
                out.warn(NavOutput.Warning.DISTANCE_LIMIT);
            }
        }
    }
}
