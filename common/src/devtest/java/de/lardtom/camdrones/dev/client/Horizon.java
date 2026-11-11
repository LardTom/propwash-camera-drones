package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Horizon {
    public static final int COLUMNS = 24;
    public static final double MARGIN = 0.12;
    public static final double ROTATED_MARGIN = 0.28;
    public static final int MIN_COLUMNS = 12;
    public static final int RUN = 4;
    public static final double CONTROL_DEG = 10.0;

    private Horizon() {
    }

    @FunctionalInterface
    private interface Pixels {
        int get(int x, int y);
    }

    public record Result(double angleDeg, int columns, double meanRow, double worstResidual, int height) {
        public String describe() {
            return String.format(Locale.GERMANY, "Horizont %.2f° geneigt (%d Spalten, Zeile %.0f von %d, Abweichung höchstens %.1f px)", this.angleDeg,
                    this.columns, this.meanRow, this.height, this.worstResidual);
        }
    }

    public record Check(Result level, Result control) {
        public boolean controlOk() {
            return Double.isFinite(this.control.angleDeg()) && Math.abs(Math.abs(this.control.angleDeg()) - CONTROL_DEG) < 1.0;
        }

        public String describe() {
            return this.level.describe() + String.format(Locale.GERMANY, "; Gegenprobe: dasselbe Bild um %.0f° gedreht misst %.2f°", CONTROL_DEG,
                    Math.abs(this.control.angleDeg()));
        }
    }

    public static Check check(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file); NativeImage image = NativeImage.read(in)) {
            return new Check(measure(image::getPixel, image.getWidth(), image.getHeight(), MARGIN),
                    measure(rotated(image, CONTROL_DEG), image.getWidth(), image.getHeight(), ROTATED_MARGIN));
        }
    }

    public static boolean ground(int argb) {
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        return g > b + 6 && g + 8 >= r;
    }

    private static Pixels rotated(NativeImage image, double degrees) {
        double cx = image.getWidth() / 2.0;
        double cy = image.getHeight() / 2.0;
        double cos = Math.cos(Math.toRadians(degrees));
        double sin = Math.sin(Math.toRadians(degrees));
        int sky = 0xFF8090FF;
        return (x, y) -> {
            double dx = x - cx;
            double dy = y - cy;
            int sx = (int) Math.round(cx + dx * cos + dy * sin);
            int sy = (int) Math.round(cy - dx * sin + dy * cos);
            if (sx < 0 || sy < 0 || sx >= image.getWidth() || sy >= image.getHeight()) {
                return sky;
            }
            return image.getPixel(sx, sy);
        };
    }

    private static Result measure(Pixels pixels, int width, int height, double margin) {
        List<double[]> points = new ArrayList<>();
        int bottom = (int) Math.round(height * (1.0 - margin / 2.0)) - 1;
        for (int i = 0; i < COLUMNS; i++) {
            int x = (int) Math.round(width * (margin + (1.0 - 2.0 * margin) * i / (COLUMNS - 1.0)));
            x = Math.clamp(x, 0, width - 1);
            if (!ground(pixels.get(x, bottom))) {
                continue;
            }
            int found = -1;
            for (int y = bottom; y >= RUN; y--) {
                boolean run = true;
                for (int k = 0; k < RUN; k++) {
                    if (ground(pixels.get(x, y - k))) {
                        run = false;
                        break;
                    }
                }
                if (run) {
                    found = y;
                    break;
                }
            }
            if (found > 0) {
                points.add(new double[]{x, found});
            }
        }
        if (points.size() < MIN_COLUMNS) {
            return new Result(Double.NaN, points.size(), Double.NaN, Double.NaN, height);
        }
        double mx = 0.0;
        double my = 0.0;
        for (double[] p : points) {
            mx += p[0];
            my += p[1];
        }
        mx /= points.size();
        my /= points.size();
        double sxx = 0.0;
        double sxy = 0.0;
        for (double[] p : points) {
            sxx += (p[0] - mx) * (p[0] - mx);
            sxy += (p[0] - mx) * (p[1] - my);
        }
        double slope = sxx == 0.0 ? 0.0 : sxy / sxx;
        double worst = 0.0;
        for (double[] p : points) {
            worst = Math.max(worst, Math.abs(p[1] - (my + slope * (p[0] - mx))));
        }
        return new Result(Math.toDegrees(Math.atan(slope)), points.size(), my, worst, height);
    }
}
