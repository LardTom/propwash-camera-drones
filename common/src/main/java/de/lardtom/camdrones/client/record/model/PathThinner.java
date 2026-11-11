package de.lardtom.camdrones.client.record.model;

import java.util.ArrayList;
import java.util.List;

public final class PathThinner {
    public static final double POSITION_TOLERANCE = 0.05;
    public static final double ANGLE_TOLERANCE = 1.0;
    public static final double LOOSEN = 1.5;
    public static final int MAX_SPAN_TICKS = 100;
    public static final int MAX_KEYFRAMES = 800;
    public static final double FOV_STEP = 0.25;
    private static final int MAX_ROUNDS = 24;

    private PathThinner() {
    }

    public static List<PathSample> camera(List<PathSample> samples) {
        return camera(samples, MAX_KEYFRAMES);
    }

    public static List<PathSample> camera(List<PathSample> samples, int maxKeyframes) {
        if (samples.size() <= 2) {
            return List.copyOf(samples);
        }
        double scale = 1.0;
        List<PathSample> kept = thin(samples, POSITION_TOLERANCE, ANGLE_TOLERANCE);
        for (int round = 0; kept.size() > Math.max(2, maxKeyframes) && round < MAX_ROUNDS; round++) {
            scale *= LOOSEN;
            kept = thin(samples, POSITION_TOLERANCE * scale, ANGLE_TOLERANCE * scale);
        }
        return List.copyOf(kept);
    }

    public static List<PathSample> fov(List<PathSample> samples) {
        List<PathSample> out = new ArrayList<>();
        PathSample last = null;
        for (PathSample sample : samples) {
            if (last == null || Math.abs(sample.fov() - last.fov()) >= FOV_STEP) {
                out.add(sample);
                last = sample;
            }
        }
        if (!samples.isEmpty() && last != null && last != samples.getLast() && Math.abs(samples.getLast().fov() - last.fov()) > 1.0E-6) {
            out.add(samples.getLast());
        }
        return List.copyOf(out);
    }

    static List<PathSample> thin(List<PathSample> samples, double position, double angle) {
        List<PathSample> out = new ArrayList<>();
        out.add(samples.getFirst());
        int anchor = 0;
        for (int end = 2; end < samples.size(); end++) {
            boolean tooLong = samples.get(end).tick() - samples.get(anchor).tick() > MAX_SPAN_TICKS;
            if (tooLong || !fits(samples, anchor, end, position, angle)) {
                out.add(samples.get(end - 1));
                anchor = end - 1;
            }
        }
        out.add(samples.getLast());
        return out;
    }

    public static double worstError(List<PathSample> samples, List<PathSample> keys, double metresPerDegree) {
        double worst = 0.0;
        int k = 0;
        for (PathSample sample : samples) {
            while (k + 1 < keys.size() - 1 && keys.get(k + 1).tick() <= sample.tick()) {
                k++;
            }
            PathSample a = keys.get(k);
            PathSample b = keys.get(Math.min(k + 1, keys.size() - 1));
            double t = b.tick() == a.tick() ? 0.0 : Math.clamp((sample.tick() - a.tick()) / (double) (b.tick() - a.tick()), 0.0, 1.0);
            worst = Math.max(worst, Math.max(distance(a, b, t, sample), angleError(a, b, t, sample) * metresPerDegree));
        }
        return worst;
    }

    private static boolean fits(List<PathSample> samples, int anchor, int end, double position, double angle) {
        PathSample a = samples.get(anchor);
        PathSample b = samples.get(end);
        double span = b.tick() - a.tick();
        for (int i = anchor + 1; i < end; i++) {
            PathSample s = samples.get(i);
            double t = span <= 0.0 ? 0.0 : (s.tick() - a.tick()) / span;
            if (distance(a, b, t, s) > position || angleError(a, b, t, s) > angle) {
                return false;
            }
        }
        return true;
    }

    private static double distance(PathSample a, PathSample b, double t, PathSample s) {
        double dx = a.x() + (b.x() - a.x()) * t - s.x();
        double dy = a.y() + (b.y() - a.y()) * t - s.y();
        double dz = a.z() + (b.z() - a.z()) * t - s.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double angleError(PathSample a, PathSample b, double t, PathSample s) {
        double yaw = Math.abs(a.yaw() + (b.yaw() - a.yaw()) * t - s.yaw());
        double pitch = Math.abs(a.pitch() + (b.pitch() - a.pitch()) * t - s.pitch());
        double roll = Math.abs(a.roll() + (b.roll() - a.roll()) * t - s.roll());
        return Math.max(yaw, Math.max(pitch, roll));
    }
}
