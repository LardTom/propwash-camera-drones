package de.lardtom.camdrones.client.hud;

public final class MiniMap {
    public static final double MIN_RADIUS = 25.0;
    public static final double[] RADII = {25.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0};
    public static final double MARGIN = 1.25;

    public record Point(double x, double y, boolean clamped) {
    }

    private MiniMap() {
    }

    public static double radius(double distance) {
        if (!Double.isFinite(distance)) {
            return MIN_RADIUS;
        }
        double wanted = distance * MARGIN;
        for (double radius : RADII) {
            if (wanted <= radius) {
                return radius;
            }
        }
        return RADII[RADII.length - 1];
    }

    public static Point project(double droneX, double droneZ, double headingDeg, double targetX, double targetZ, double radiusMetres,
                                double halfSize) {
        double dx = targetX - droneX;
        double dz = targetZ - droneZ;
        double h = Math.toRadians(Double.isFinite(headingDeg) ? headingDeg : 0.0);
        double forward = dx * Math.sin(h) - dz * Math.cos(h);
        double right = dx * Math.cos(h) + dz * Math.sin(h);
        double scale = halfSize / Math.max(1.0E-6, radiusMetres);
        double px = right * scale;
        double py = -forward * scale;
        double extent = Math.max(Math.abs(px), Math.abs(py));
        if (extent > halfSize && extent > 0.0) {
            double k = halfSize / extent;
            return new Point(px * k, py * k, true);
        }
        return new Point(px, py, false);
    }
}
