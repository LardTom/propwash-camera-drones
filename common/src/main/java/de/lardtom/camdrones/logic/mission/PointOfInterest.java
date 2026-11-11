package de.lardtom.camdrones.logic.mission;

public record PointOfInterest(int xDm, int yDm, int zDm, int radiusDm) {
    public PointOfInterest {
        MissionLimits.requirePosition(xDm, yDm, zDm);
        MissionLimits.require(radiusDm >= 0 && radiusDm <= MissionLimits.MAX_RADIUS_DM, "Radius ungültig: " + radiusDm);
    }

    public static PointOfInterest at(double x, double y, double z, double radius) {
        return new PointOfInterest(MissionLimits.toDecimetres(x), MissionLimits.toDecimetres(y), MissionLimits.toDecimetres(z),
                (int) Math.round(Math.max(0.0, radius) * 10.0));
    }

    public double x() {
        return this.xDm / 10.0;
    }

    public double y() {
        return this.yDm / 10.0;
    }

    public double z() {
        return this.zDm / 10.0;
    }

    public double radius() {
        return this.radiusDm / 10.0;
    }

    public PointOfInterest withRadius(double metres) {
        return new PointOfInterest(this.xDm, this.yDm, this.zDm, (int) Math.round(Math.max(0.0, metres) * 10.0));
    }
}
