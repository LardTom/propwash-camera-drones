package de.lardtom.camdrones.planner.model;

import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;

import java.util.List;

public final class MapView {
    public static final double[] ZOOMS = {0.25, 0.5, 1.0, 2.0, 4.0, 8.0};
    public static final int DEFAULT_ZOOM = 2;
    public static final int FIT_MARGIN = 12;

    private double centerX;
    private double centerZ;
    private int zoom = DEFAULT_ZOOM;

    public MapView() {
    }

    public MapView(double centerX, double centerZ, int zoom) {
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.zoom = Math.clamp(zoom, 0, ZOOMS.length - 1);
    }

    public double centerX() {
        return this.centerX;
    }

    public double centerZ() {
        return this.centerZ;
    }

    public int zoom() {
        return this.zoom;
    }

    public double blocksPerPixel() {
        return ZOOMS[this.zoom];
    }

    public void center(double x, double z) {
        if (Double.isFinite(x) && Double.isFinite(z)) {
            this.centerX = x;
            this.centerZ = z;
        }
    }

    public double worldX(double px, Rect rect) {
        return this.centerX + (px - (rect.x() + rect.w() / 2.0)) * this.blocksPerPixel();
    }

    public double worldZ(double py, Rect rect) {
        return this.centerZ + (py - (rect.y() + rect.h() / 2.0)) * this.blocksPerPixel();
    }

    public double screenX(double wx, Rect rect) {
        return rect.x() + rect.w() / 2.0 + (wx - this.centerX) / this.blocksPerPixel();
    }

    public double screenY(double wz, Rect rect) {
        return rect.y() + rect.h() / 2.0 + (wz - this.centerZ) / this.blocksPerPixel();
    }

    public boolean zoomBy(int steps, double px, double py, Rect rect) {
        int next = Math.clamp(this.zoom - steps, 0, ZOOMS.length - 1);
        if (next == this.zoom) {
            return false;
        }
        double wx = this.worldX(px, rect);
        double wz = this.worldZ(py, rect);
        this.zoom = next;
        this.centerX = wx - (px - (rect.x() + rect.w() / 2.0)) * this.blocksPerPixel();
        this.centerZ = wz - (py - (rect.y() + rect.h() / 2.0)) * this.blocksPerPixel();
        return true;
    }

    public void pan(double dxPixels, double dyPixels) {
        this.centerX -= dxPixels * this.blocksPerPixel();
        this.centerZ -= dyPixels * this.blocksPerPixel();
    }

    public void fit(List<Waypoint> waypoints, List<PointOfInterest> points, double fallbackX, double fallbackZ, Rect rect) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Waypoint w : waypoints) {
            minX = Math.min(minX, w.x());
            maxX = Math.max(maxX, w.x());
            minZ = Math.min(minZ, w.z());
            maxZ = Math.max(maxZ, w.z());
        }
        for (PointOfInterest p : points) {
            minX = Math.min(minX, p.x() - p.radius());
            maxX = Math.max(maxX, p.x() + p.radius());
            minZ = Math.min(minZ, p.z() - p.radius());
            maxZ = Math.max(maxZ, p.z() + p.radius());
        }
        if (!Double.isFinite(minX)) {
            this.center(fallbackX, fallbackZ);
            this.zoom = DEFAULT_ZOOM;
            return;
        }
        this.center((minX + maxX) / 2.0, (minZ + maxZ) / 2.0);
        double spanX = maxX - minX;
        double spanZ = maxZ - minZ;
        double roomX = Math.max(1.0, rect.w() - 2.0 * FIT_MARGIN);
        double roomZ = Math.max(1.0, rect.h() - 2.0 * FIT_MARGIN);
        int chosen = ZOOMS.length - 1;
        for (int i = 0; i < ZOOMS.length; i++) {
            if (spanX / ZOOMS[i] <= roomX && spanZ / ZOOMS[i] <= roomZ) {
                chosen = i;
                break;
            }
        }
        this.zoom = Math.max(chosen, 1);
    }

    public MapView copy() {
        return new MapView(this.centerX, this.centerZ, this.zoom);
    }
}
