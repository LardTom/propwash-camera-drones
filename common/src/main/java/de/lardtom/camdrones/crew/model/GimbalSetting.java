package de.lardtom.camdrones.crew.model;

import de.lardtom.camdrones.logic.nav.Compass;

public record GimbalSetting(Mode mode, double tilt, double pan, double heading, double zoom) {
    public static final double TILT_LIMIT = 90.0;
    public static final double ANGLE_EPSILON = 0.2;
    public static final double ZOOM_EPSILON = 0.005;
    public static final GimbalSetting CENTER = new GimbalSetting(Mode.FOLLOW, 0.0, 0.0, 0.0, 0.0);

    public enum Mode {
        FOLLOW,
        LOCK,
        FPV
    }

    public GimbalSetting {
        mode = mode == null ? Mode.FOLLOW : mode;
        tilt = Math.clamp(finite(tilt), -TILT_LIMIT, TILT_LIMIT);
        pan = Compass.wrap180(finite(pan));
        heading = Compass.normalize(finite(heading));
        zoom = Math.clamp(finite(zoom), 0.0, 1.0);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    public GimbalSetting withMode(Mode value) {
        return new GimbalSetting(value, this.tilt, this.pan, this.heading, this.zoom);
    }

    public GimbalSetting withTilt(double value) {
        return new GimbalSetting(this.mode, value, this.pan, this.heading, this.zoom);
    }

    public GimbalSetting withPan(double value) {
        return new GimbalSetting(this.mode, this.tilt, value, this.heading, this.zoom);
    }

    public GimbalSetting withHeading(double value) {
        return new GimbalSetting(this.mode, this.tilt, this.pan, value, this.zoom);
    }

    public GimbalSetting withZoom(double value) {
        return new GimbalSetting(this.mode, this.tilt, this.pan, this.heading, value);
    }

    public boolean locked() {
        return this.mode == Mode.LOCK;
    }

    public boolean fpv() {
        return this.mode == Mode.FPV;
    }

    public boolean differs(GimbalSetting other) {
        return other == null || this.mode != other.mode || Math.abs(this.tilt - other.tilt) > ANGLE_EPSILON
                || Math.abs(Compass.wrap180(this.pan - other.pan)) > ANGLE_EPSILON
                || Math.abs(Compass.wrap180(this.heading - other.heading)) > ANGLE_EPSILON
                || Math.abs(this.zoom - other.zoom) > ZOOM_EPSILON;
    }
}
