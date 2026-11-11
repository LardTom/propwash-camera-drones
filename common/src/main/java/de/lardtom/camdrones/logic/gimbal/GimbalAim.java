package de.lardtom.camdrones.logic.gimbal;

import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FlightClass;

public final class GimbalAim {
    public static final double RECENTER_TILT = 0.0;
    public static final double DOWN_TILT = FlightClass.GIMBAL_MIN_TILT;
    public static final double DEFAULT_RATE = 90.0;

    public record Aim(double tilt, double pan, double yaw, boolean reachable, boolean needsYaw) {
    }

    private GimbalAim() {
    }

    public static double tiltTo(double dx, double dy, double dz) {
        double horizontal = Math.hypot(dx, dz);
        if (horizontal < 1.0E-9) {
            return dy < 0.0 ? -90.0 : dy > 0.0 ? 90.0 : 0.0;
        }
        return Math.toDegrees(Math.atan2(dy, horizontal));
    }

    public static double clampTilt(FlightClass flightClass, double tilt) {
        return Math.clamp(tilt, FlightClass.GIMBAL_MIN_TILT, flightClass.gimbalMaxTilt());
    }

    public static Aim aim(FlightClass flightClass, double droneX, double droneY, double droneZ, double droneHeading, double targetX,
                          double targetY, double targetZ) {
        double dx = targetX - droneX;
        double dy = targetY - droneY;
        double dz = targetZ - droneZ;
        double rawTilt = tiltTo(dx, dy, dz);
        double tilt = clampTilt(flightClass, rawTilt);
        boolean vertical = Math.hypot(dx, dz) < 1.0E-9;
        double yaw = vertical ? Compass.normalize(droneHeading) : Compass.headingTo(dx, dz);
        double pan = Compass.wrap180(yaw - droneHeading);
        boolean needsYaw = !flightClass.gimbalFullPan() && Math.abs(pan) > 0.5;
        boolean reachable = Math.abs(tilt - rawTilt) < 1.0E-6 && !needsYaw;
        return new Aim(tilt, flightClass.gimbalFullPan() ? pan : 0.0, yaw, reachable, needsYaw);
    }

    public static double approach(double current, double target, double maxRate, double dt) {
        double step = Math.max(0.0, maxRate) * Math.max(0.0, dt);
        return current + Math.clamp(target - current, -step, step);
    }

    public static double interpolate(double from, double to, double progress) {
        double c = Math.clamp(progress, 0.0, 1.0);
        double s = c * c * (3.0 - 2.0 * c);
        return from + (to - from) * s;
    }

    public static double recenter(double currentTilt) {
        return Math.abs(currentTilt - RECENTER_TILT) < 1.0 ? DOWN_TILT : RECENTER_TILT;
    }
}
