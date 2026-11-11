package de.lardtom.camdrones.client.record.model;

public record PathSample(int tick, double x, double y, double z, double yaw, double pitch, double roll, double fov) {
    public boolean finite() {
        return Double.isFinite(this.x) && Double.isFinite(this.y) && Double.isFinite(this.z) && Double.isFinite(this.yaw)
                && Double.isFinite(this.pitch) && Double.isFinite(this.roll) && Double.isFinite(this.fov);
    }

    public PathSample continuing(PathSample previous) {
        if (previous == null) {
            return this;
        }
        double unwrappedYaw = previous.yaw + CameraAngles.wrap(this.yaw - previous.yaw);
        double unwrappedRoll = previous.roll + CameraAngles.wrap(this.roll - previous.roll);
        return new PathSample(this.tick, this.x, this.y, this.z, unwrappedYaw, this.pitch, unwrappedRoll, this.fov);
    }
}
