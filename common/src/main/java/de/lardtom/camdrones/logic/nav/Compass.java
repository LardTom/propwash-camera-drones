package de.lardtom.camdrones.logic.nav;

public final class Compass {
    private Compass() {
    }

    public static double normalize(double degrees) {
        double d = degrees % 360.0;
        if (d < 0.0) {
            d += 360.0;
        }
        return d >= 360.0 ? 0.0 : d + 0.0;
    }

    public static double wrap180(double degrees) {
        double d = normalize(degrees);
        return d > 180.0 ? d - 360.0 : d;
    }

    public static double headingTo(double dx, double dz) {
        if (dx == 0.0 && dz == 0.0) {
            return 0.0;
        }
        return normalize(Math.toDegrees(Math.atan2(dx, -dz)));
    }

    public static double forwardX(double heading) {
        return Math.sin(Math.toRadians(heading));
    }

    public static double forwardZ(double heading) {
        return -Math.cos(Math.toRadians(heading));
    }

    public static double rightX(double heading) {
        return Math.cos(Math.toRadians(heading));
    }

    public static double rightZ(double heading) {
        return Math.sin(Math.toRadians(heading));
    }

    public static double lerp(double from, double to, double t) {
        return normalize(from + wrap180(to - from) * t);
    }

    public static double approach(double current, double target, double maxStep) {
        double error = wrap180(target - current);
        return normalize(current + Math.clamp(error, -maxStep, maxStep));
    }

    public static double bearingRate(double dx, double dz, double dvx, double dvz) {
        double r2 = dx * dx + dz * dz;
        if (r2 < 1.0E-6) {
            return 0.0;
        }
        return Math.toDegrees((-dz * dvx + dx * dvz) / r2);
    }
}
