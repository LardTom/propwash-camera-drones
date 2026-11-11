package de.lardtom.camdrones.client.gimbal.model;

import java.util.List;
import java.util.OptionalInt;

public final class GimbalOptics {
    public static final double SENSOR_WIDTH_MM = 36.0;
    public static final double MIN_FOV = 20.0;
    public static final double MAX_FOV = 150.0;
    public static final double DIGITAL_ZOOM = 2.0;
    public static final int DEFAULT_FOCAL_MM = 24;

    public record Range(double wideMm, double teleMm) {
        public Range {
            if (!(wideMm > 0.0) || !(teleMm >= wideMm)) {
                throw new IllegalArgumentException("Brennweiten ungültig: " + wideMm + ".." + teleMm);
            }
        }

        public double focal(double zoom) {
            double z = Math.clamp(Double.isFinite(zoom) ? zoom : 0.0, 0.0, 1.0);
            return this.wideMm * Math.pow(this.teleMm / this.wideMm, z);
        }
    }

    private GimbalOptics() {
    }

    public static Range range(List<Integer> builtInFocalMm, boolean lensBay, OptionalInt lensFocalMm) {
        if (lensBay) {
            double base = lensFocalMm.isPresent() && lensFocalMm.getAsInt() > 0 ? lensFocalMm.getAsInt() : DEFAULT_FOCAL_MM;
            return new Range(base, base * DIGITAL_ZOOM);
        }
        if (builtInFocalMm == null || builtInFocalMm.isEmpty()) {
            return new Range(DEFAULT_FOCAL_MM, DEFAULT_FOCAL_MM * DIGITAL_ZOOM);
        }
        int wide = builtInFocalMm.stream().mapToInt(Integer::intValue).filter(v -> v > 0).min().orElse(DEFAULT_FOCAL_MM);
        int tele = builtInFocalMm.stream().mapToInt(Integer::intValue).filter(v -> v > 0).max().orElse(wide);
        return new Range(wide, tele > wide ? tele : wide * DIGITAL_ZOOM);
    }

    public static double horizontalFov(double focalMm) {
        if (!(focalMm > 0.0)) {
            return MAX_FOV;
        }
        return Math.toDegrees(2.0 * Math.atan(SENSOR_WIDTH_MM / (2.0 * focalMm)));
    }

    public static double fov(Range range, double zoom) {
        return Math.clamp(horizontalFov(range.focal(zoom)), MIN_FOV, MAX_FOV);
    }
}
