package de.lardtom.camdrones.planner.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class MapLabels {
    public static final int GAP = 1;
    public static final int OFFSET = 2;
    private static final int[] EXTRA = {0, 5};
    private static final double EXTRA_PENALTY = 0.06;
    private static final int[][] DIRECTIONS = {{1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    public record Anchor(double x, double y, int width, int height, int marker) {
    }

    private MapLabels() {
    }

    public static List<Optional<Rect>> place(List<Anchor> anchors, Rect bounds, List<Rect> blocked) {
        double cx = 0.0;
        double cy = 0.0;
        for (Anchor anchor : anchors) {
            cx += anchor.x();
            cy += anchor.y();
        }
        if (!anchors.isEmpty()) {
            cx /= anchors.size();
            cy /= anchors.size();
        }
        List<Rect> taken = new ArrayList<>(blocked);
        for (Anchor anchor : anchors) {
            taken.add(marker(anchor));
        }
        List<Optional<Rect>> out = new ArrayList<>();
        for (Anchor anchor : anchors) {
            Rect chosen = null;
            for (Rect candidate : candidates(anchor, cx, cy)) {
                if (!candidate.inside(bounds)) {
                    continue;
                }
                Rect padded = new Rect(candidate.x() - GAP, candidate.y() - GAP, candidate.w() + 2 * GAP, candidate.h() + 2 * GAP);
                boolean free = true;
                for (Rect other : taken) {
                    if (padded.overlaps(other)) {
                        free = false;
                        break;
                    }
                }
                if (free) {
                    chosen = candidate;
                    break;
                }
            }
            out.add(Optional.ofNullable(chosen));
            if (chosen != null) {
                taken.add(chosen);
            }
        }
        return out;
    }

    static Rect marker(Anchor anchor) {
        int x = (int) Math.round(anchor.x());
        int y = (int) Math.round(anchor.y());
        return new Rect(x - anchor.marker(), y - anchor.marker(), 2 * anchor.marker() + 1, 2 * anchor.marker() + 1);
    }

    static List<Rect> candidates(Anchor anchor, double cx, double cy) {
        int x = (int) Math.round(anchor.x());
        int y = (int) Math.round(anchor.y());
        int w = anchor.width();
        int h = anchor.height();
        double ox = anchor.x() - cx;
        double oy = anchor.y() - cy;
        double outward = Math.hypot(ox, oy);
        List<Rect> rects = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (int extra : EXTRA) {
            int m = anchor.marker() + OFFSET + 1 + extra;
            for (int[] d : DIRECTIONS) {
                int rx = d[0] > 0 ? x + m : d[0] < 0 ? x - m - w : x - w / 2;
                int ry = d[1] > 0 ? y + m - (d[0] == 0 ? 0 : 2) : d[1] < 0 ? y - m - h + (d[0] == 0 ? 0 : 2) : y - h / 2;
                double alignment = outward < 1.0 ? 0.0 : (d[0] * ox + d[1] * oy) / (outward * Math.hypot(d[0], d[1]));
                rects.add(new Rect(rx, ry, w, h));
                scores.add(alignment - extra * EXTRA_PENALTY + (d[0] > 0 && d[1] == 0 ? 0.01 : 0.0));
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < rects.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble(i -> -scores.get(i)));
        List<Rect> sorted = new ArrayList<>();
        for (int i : order) {
            sorted.add(rects.get(i));
        }
        return sorted;
    }
}
