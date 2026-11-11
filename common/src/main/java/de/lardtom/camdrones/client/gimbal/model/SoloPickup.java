package de.lardtom.camdrones.client.gimbal.model;

public final class SoloPickup {
    public static final double PICKUP = 0.03;

    private boolean steering;
    private double zoomAnchor = Double.NaN;

    public void start(double zoomStick, boolean steered) {
        this.steering = steered;
        this.zoomAnchor = zoom(zoomStick);
    }

    public boolean steering() {
        return this.steering;
    }

    public boolean update(double panStick, double tiltStick, double zoomStick, boolean recentered) {
        if (this.steering) {
            return true;
        }
        double zoom = zoom(zoomStick);
        if (!Double.isFinite(this.zoomAnchor)) {
            this.zoomAnchor = zoom;
        }
        this.steering = recentered || OperatorGimbalInput.deadband(panStick) != 0.0 || OperatorGimbalInput.deadband(tiltStick) != 0.0
                || Math.abs(zoom - this.zoomAnchor) > PICKUP;
        return this.steering;
    }

    public void reset() {
        this.steering = false;
        this.zoomAnchor = Double.NaN;
    }

    private static double zoom(double stick) {
        return Math.clamp(Double.isFinite(stick) ? stick : 0.0, 0.0, 1.0);
    }
}
