package de.lardtom.camdrones.part;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.lardtom.propwash.api.part.AnchorSpec;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FrontFit {
    static final double MAX_GAP_MM = 1.0;
    static final double MAX_OVERLAP_MM = 2.0;
    static final double MM_PER_MODEL_UNIT = 8.0;
    static final double MODEL_UNITS_PER_BLOCK = 16.0;
    static final double NEAR_MM = 80.0;
    static final double STEP_MM = 0.5;
    private static final int[][] FACES = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 6, 7, 5}};

    private FrontFit() {
    }

    record Fit(double gapMm, double overlapMm) {
        boolean ok() {
            return this.gapMm <= MAX_GAP_MM && this.overlapMm <= MAX_OVERLAP_MM;
        }

        String describe() {
            return String.format(Locale.ROOT, "Abstand %.1f mm, Überschneidung %.1f mm", this.gapMm, this.overlapMm);
        }
    }

    static List<Vector3d[]> solids(JsonObject renderModel, JsonObject model) {
        double[] scale = {1.0, 1.0, 1.0};
        double[] translation = {0.0, 0.0, 0.0};
        if (renderModel.get("transformation") instanceof JsonObject transformation) {
            requireIdentity(transformation, "left_rotation");
            requireIdentity(transformation, "right_rotation");
            if (transformation.get("scale") instanceof JsonArray array) {
                scale = triple(array);
            }
            if (transformation.get("translation") instanceof JsonArray array) {
                translation = triple(array);
            }
        } else if (renderModel.has("transformation")) {
            throw new IllegalArgumentException("transformation nur als Objekt lesbar: " + renderModel.get("transformation"));
        }
        List<Vector3d[]> out = new ArrayList<>();
        for (JsonElement entry : model.getAsJsonArray("elements")) {
            JsonObject element = entry.getAsJsonObject();
            double[] from = triple(element.getAsJsonArray("from"));
            double[] to = triple(element.getAsJsonArray("to"));
            JsonObject rotation = element.get("rotation") instanceof JsonObject r ? r : null;
            Matrix3d matrix = rotation == null ? null : rotation(rotation);
            double[] origin = rotation != null && rotation.get("origin") instanceof JsonArray o ? triple(o) : new double[]{8.0, 8.0, 8.0};
            double[] rescale = rotation == null ? new double[]{1.0, 1.0, 1.0} : rescale(rotation);
            Vector3d[] corners = new Vector3d[8];
            for (int i = 0; i < 8; i++) {
                Vector3d c = new Vector3d((i & 1) == 0 ? from[0] : to[0], (i & 2) == 0 ? from[1] : to[1], (i & 4) == 0 ? from[2] : to[2]);
                if (matrix != null) {
                    c.sub(origin[0], origin[1], origin[2]);
                    matrix.transform(c);
                    c.mul(rescale[0], rescale[1], rescale[2]);
                    c.add(origin[0], origin[1], origin[2]);
                }
                double mmPerBlock = MM_PER_MODEL_UNIT * MODEL_UNITS_PER_BLOCK;
                corners[i] = new Vector3d((c.x / MODEL_UNITS_PER_BLOCK * scale[0] + translation[0] - 0.5) * mmPerBlock,
                        (c.y / MODEL_UNITS_PER_BLOCK * scale[1] + translation[1] - 0.5) * mmPerBlock,
                        (c.z / MODEL_UNITS_PER_BLOCK * scale[2] + translation[2] - 0.5) * mmPerBlock);
            }
            out.add(corners);
        }
        return out;
    }

    static Fit measure(List<Vector3d[]> solids, AnchorSpec anchor, double widthMm, double heightMm, double depthMm) {
        if (anchor.tiltDeg() != 0.0) {
            throw new IllegalArgumentException("Anker vorne mit Neigung " + anchor.tiltDeg() + "° wird hier nicht gemessen");
        }
        double[] lo = {anchor.x() - widthMm / 2.0, anchor.y() - heightMm, anchor.z() - depthMm};
        double[] hi = {anchor.x() + widthMm / 2.0, anchor.y(), anchor.z()};
        double gap = Double.POSITIVE_INFINITY;
        double overlap = 0.0;
        Vector3d p = new Vector3d();
        for (Vector3d[] corners : solids) {
            if (!near(corners, lo, hi)) {
                continue;
            }
            for (int[] face : FACES) {
                Vector3d a = corners[face[0]];
                Vector3d b = corners[face[1]];
                Vector3d d = corners[face[3]];
                int nu = Math.max(1, (int) Math.ceil(a.distance(b) / STEP_MM));
                int nv = Math.max(1, (int) Math.ceil(a.distance(d) / STEP_MM));
                for (int i = 0; i <= nu; i++) {
                    for (int j = 0; j <= nv; j++) {
                        bilinear(corners, face, (double) i / nu, (double) j / nv, p);
                        double dx = Math.max(Math.max(lo[0] - p.x, 0.0), p.x - hi[0]);
                        double dy = Math.max(Math.max(lo[1] - p.y, 0.0), p.y - hi[1]);
                        double dz = Math.max(Math.max(lo[2] - p.z, 0.0), p.z - hi[2]);
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        gap = Math.min(gap, distance);
                        if (distance == 0.0) {
                            double depth = Math.min(Math.min(p.x - lo[0], hi[0] - p.x), Math.min(p.y - lo[1], hi[1] - p.y));
                            overlap = Math.max(overlap, Math.min(depth, Math.min(p.z - lo[2], hi[2] - p.z)));
                        }
                    }
                }
            }
        }
        return new Fit(gap, overlap);
    }

    private static boolean near(Vector3d[] corners, double[] lo, double[] hi) {
        for (int axis = 0; axis < 3; axis++) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (Vector3d c : corners) {
                min = Math.min(min, c.get(axis));
                max = Math.max(max, c.get(axis));
            }
            if (min > hi[axis] + NEAR_MM || max < lo[axis] - NEAR_MM) {
                return false;
            }
        }
        return true;
    }

    private static void bilinear(Vector3d[] corners, int[] face, double u, double v, Vector3d out) {
        Vector3d p0 = corners[face[0]];
        Vector3d p1 = corners[face[1]];
        Vector3d p2 = corners[face[2]];
        Vector3d p3 = corners[face[3]];
        out.set(p0).mul((1 - u) * (1 - v))
                .add(p1.x * u * (1 - v), p1.y * u * (1 - v), p1.z * u * (1 - v))
                .add(p2.x * u * v, p2.y * u * v, p2.z * u * v)
                .add(p3.x * (1 - u) * v, p3.y * (1 - u) * v, p3.z * (1 - u) * v);
    }

    private static void requireIdentity(JsonObject transformation, String key) {
        if (transformation.get(key) instanceof JsonArray array) {
            double[] q = {array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble(), array.get(3).getAsDouble()};
            if (q[0] != 0.0 || q[1] != 0.0 || q[2] != 0.0 || q[3] != 1.0) {
                throw new IllegalArgumentException(key + " ist keine Einheitsdrehung: " + array);
            }
        }
    }

    private static double[] triple(JsonArray array) {
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    private static Matrix3d rotation(JsonObject rotation) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        if (rotation.has("axis")) {
            double angle = rotation.has("angle") ? rotation.get("angle").getAsDouble() : 0.0;
            switch (rotation.get("axis").getAsString()) {
                case "x" -> x = angle;
                case "y" -> y = angle;
                case "z" -> z = angle;
                default -> throw new IllegalArgumentException("unbekannte Achse " + rotation.get("axis"));
            }
        } else {
            x = rotation.has("x") ? rotation.get("x").getAsDouble() : 0.0;
            y = rotation.has("y") ? rotation.get("y").getAsDouble() : 0.0;
            z = rotation.has("z") ? rotation.get("z").getAsDouble() : 0.0;
        }
        return new Matrix3d().rotationZ(Math.toRadians(z)).rotateY(Math.toRadians(y)).rotateX(Math.toRadians(x));
    }

    private static double[] rescale(JsonObject rotation) {
        double[] out = {1.0, 1.0, 1.0};
        if (!rotation.has("rescale") || !rotation.get("rescale").getAsBoolean() || !rotation.has("axis")) {
            return out;
        }
        double cos = Math.abs(Math.cos(Math.toRadians(rotation.has("angle") ? rotation.get("angle").getAsDouble() : 0.0)));
        double factor = cos > 1.0E-6 ? 1.0 / cos : 1.0;
        switch (rotation.get("axis").getAsString()) {
            case "x" -> {
                out[1] = factor;
                out[2] = factor;
            }
            case "y" -> {
                out[0] = factor;
                out[2] = factor;
            }
            default -> {
                out[0] = factor;
                out[1] = factor;
            }
        }
        return out;
    }
}
