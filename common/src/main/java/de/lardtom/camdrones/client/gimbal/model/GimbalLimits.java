package de.lardtom.camdrones.client.gimbal.model;

import de.lardtom.camdrones.logic.nav.Compass;

public record GimbalLimits(double minTilt, double maxTilt, boolean fullPan, double yawLimit) {
    public static final double PAN_RANGE = 180.0;
    public static final double LIMITED_YAW = 25.0;
    public static final double STOP_MARGIN = 0.5;
    public static final GimbalLimits DEFAULT = of(-90.0, 30.0, false);

    public GimbalLimits {
        if (!(minTilt < maxTilt) || minTilt < -90.0 || maxTilt > 90.0) {
            throw new IllegalArgumentException("Neigungsbereich ungültig: " + minTilt + ".." + maxTilt);
        }
        if (!(yawLimit >= 0.0 && yawLimit <= PAN_RANGE)) {
            throw new IllegalArgumentException("Schwenkgrenze ungültig: " + yawLimit);
        }
    }

    public static GimbalLimits of(double minTilt, double maxTilt, boolean fullPan) {
        return new GimbalLimits(minTilt, maxTilt, fullPan, fullPan ? PAN_RANGE : LIMITED_YAW);
    }

    public double clampTilt(double tilt) {
        return Math.clamp(Double.isFinite(tilt) ? tilt : 0.0, this.minTilt, this.maxTilt);
    }

    public double tiltFromDial(double value) {
        double v = Math.clamp(Double.isFinite(value) ? value : 0.0, -1.0, 1.0);
        return v < 0.0 ? -v * this.minTilt : v * this.maxTilt;
    }

    public double panFromDial(double value) {
        if (!this.fullPan) {
            return 0.0;
        }
        return Math.clamp(Double.isFinite(value) ? value : 0.0, -1.0, 1.0) * PAN_RANGE;
    }

    public double heading(double heading, double frameHeading) {
        double wanted = Double.isFinite(heading) ? heading : frameHeading;
        if (this.fullPan) {
            return Compass.normalize(wanted);
        }
        return Compass.normalize(frameHeading + Math.clamp(Compass.wrap180(wanted - frameHeading), -this.yawLimit, this.yawLimit));
    }

    public boolean atStop(double heading, double frameHeading) {
        return !this.fullPan && Double.isFinite(heading) && Double.isFinite(frameHeading)
                && Math.abs(Compass.wrap180(heading - frameHeading)) >= this.yawLimit - STOP_MARGIN;
    }
}
