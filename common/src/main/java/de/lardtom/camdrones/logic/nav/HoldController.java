package de.lardtom.camdrones.logic.nav;

public final class HoldController implements NavMode {
    public static final double BRAKE_DONE_SPEED = 0.3;
    public static final double BRAKE_TIMEOUT = 3.0;
    public static final double HOLD_SPEED = 5.0;
    public static final String LABEL = "HOLD";

    private boolean horizontalEngaged;
    private boolean braking;
    private double brakeSince;
    private double holdX;
    private double holdZ;
    private boolean verticalEngaged;
    private boolean settlingVertical;
    private double holdY;
    private boolean headingEngaged;
    private double holdHeading;

    @Override
    public void update(NavInput in, NavOutput out) {
        this.horizontal(in, out);
        this.vertical(in, out);
        if (!this.headingEngaged) {
            this.headingEngaged = true;
            this.holdHeading = in.heading();
        }
        out.heading(this.holdHeading);
        out.label(LABEL);
    }

    public void horizontal(NavInput in, NavOutput out) {
        if (!this.horizontalEngaged) {
            this.horizontalEngaged = true;
            this.braking = true;
            this.brakeSince = in.time();
        }
        if (this.braking && (in.horizontalSpeed() < BRAKE_DONE_SPEED || in.time() - this.brakeSince > BRAKE_TIMEOUT)) {
            this.braking = false;
            this.holdX = in.x();
            this.holdZ = in.z();
        }
        if (this.braking) {
            out.velocity(0.0, 0.0);
        } else {
            out.holdPosition(this.holdX, this.holdZ);
        }
        out.maxSpeed(HOLD_SPEED);
    }

    public void vertical(NavInput in, NavOutput out) {
        if (!this.verticalEngaged) {
            this.verticalEngaged = true;
            this.settlingVertical = true;
        }
        if (this.settlingVertical && Math.abs(in.vy()) < BRAKE_DONE_SPEED) {
            this.settlingVertical = false;
            this.holdY = in.y();
        }
        if (this.settlingVertical) {
            out.climb(0.0);
        } else {
            out.holdAltitude(this.holdY);
        }
    }

    public void holdAt(double x, double y, double z, double heading) {
        this.horizontalEngaged = true;
        this.braking = false;
        this.holdX = x;
        this.holdZ = z;
        this.verticalEngaged = true;
        this.settlingVertical = false;
        this.holdY = y;
        this.headingEngaged = true;
        this.holdHeading = Compass.normalize(heading);
    }

    public boolean braking() {
        return this.horizontalEngaged && this.braking;
    }

    public boolean engaged() {
        return this.horizontalEngaged;
    }

    public double holdX() {
        return this.holdX;
    }

    public double holdY() {
        return this.holdY;
    }

    public double holdZ() {
        return this.holdZ;
    }

    @Override
    public void reset() {
        this.horizontalEngaged = false;
        this.braking = false;
        this.verticalEngaged = false;
        this.settlingVertical = false;
        this.headingEngaged = false;
    }

    public void resetHorizontal() {
        this.horizontalEngaged = false;
        this.braking = false;
    }
}
