package de.lardtom.camdrones.integration;

public record NavTarget(double x, double y, double z, double vx, double vy, double vz) {
    public NavTarget {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Ziel ungültig: " + x + ", " + y + ", " + z);
        }
        vx = Double.isFinite(vx) ? vx : 0.0;
        vy = Double.isFinite(vy) ? vy : 0.0;
        vz = Double.isFinite(vz) ? vz : 0.0;
    }

    public static NavTarget at(double x, double y, double z) {
        return new NavTarget(x, y, z, 0.0, 0.0, 0.0);
    }
}
