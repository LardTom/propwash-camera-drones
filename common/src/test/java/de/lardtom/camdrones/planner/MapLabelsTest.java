package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.planner.model.MapLabels;
import de.lardtom.camdrones.planner.model.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapLabelsTest {
    private static final Rect MAP = new Rect(10, 20, 140, 110);
    private static final int TEXT_HEIGHT = 9;

    private static List<MapLabels.Anchor> circle(int count, double radius) {
        List<MapLabels.Anchor> anchors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * i / count;
            int width = i + 1 < 10 ? 5 : 11;
            anchors.add(new MapLabels.Anchor(MAP.centerX() + Math.sin(angle) * radius, MAP.centerY() - Math.cos(angle) * radius, width,
                    TEXT_HEIGHT, 4));
        }
        return anchors;
    }

    private static void assertNoOverlap(List<MapLabels.Anchor> anchors, List<Optional<Rect>> placed, List<Rect> blocked) {
        List<Rect> boxes = new ArrayList<>();
        for (int i = 0; i < placed.size(); i++) {
            if (placed.get(i).isEmpty()) {
                continue;
            }
            Rect box = placed.get(i).get();
            assertTrue(box.inside(MAP), "Beschriftung " + (i + 1) + " ragt aus der Karte: " + box);
            for (Rect other : boxes) {
                assertTrue(!box.overlaps(other), "Beschriftung " + (i + 1) + " überlagert " + other);
            }
            for (Rect other : blocked) {
                assertTrue(!box.overlaps(other), "Beschriftung " + (i + 1) + " überlagert " + other);
            }
            for (MapLabels.Anchor anchor : anchors) {
                Rect marker = new Rect((int) Math.round(anchor.x()) - anchor.marker(), (int) Math.round(anchor.y()) - anchor.marker(),
                        2 * anchor.marker() + 1, 2 * anchor.marker() + 1);
                assertTrue(!box.overlaps(marker), "Beschriftung " + (i + 1) + " liegt auf einem Punkt");
            }
            boxes.add(box);
        }
    }

    @Test
    void twelvePointCircleGetsEveryLabelWithoutOverlap() {
        for (double radius : new double[]{20.0, 28.0, 40.0}) {
            List<MapLabels.Anchor> anchors = circle(12, radius);
            List<Optional<Rect>> placed = MapLabels.place(anchors, MAP, List.of());
            assertEquals(12, placed.stream().filter(Optional::isPresent).count(), "Radius " + radius);
            assertNoOverlap(anchors, placed, List.of());
        }
    }

    @Test
    void labelsGoOutwardOfTheCircle() {
        List<MapLabels.Anchor> anchors = circle(12, 30.0);
        List<Optional<Rect>> placed = MapLabels.place(anchors, MAP, List.of());
        for (int i = 0; i < anchors.size(); i++) {
            Rect box = placed.get(i).orElseThrow();
            double ax = anchors.get(i).x() - MAP.centerX();
            double ay = anchors.get(i).y() - MAP.centerY();
            double bx = box.x() + box.w() / 2.0 - anchors.get(i).x();
            double by = box.y() + box.h() / 2.0 - anchors.get(i).y();
            assertTrue(ax * bx + ay * by > 0.0, "Beschriftung " + (i + 1) + " zeigt nach innen");
        }
    }

    @Test
    void crowdedPointsNeverOverlapAndTheFirstWins() {
        List<MapLabels.Anchor> anchors = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            anchors.add(new MapLabels.Anchor(MAP.centerX() + (i % 6) * 3.0, MAP.centerY() + (i / 6) * 3.0, 11, TEXT_HEIGHT, 3));
        }
        List<Rect> blocked = List.of(new Rect(MAP.x() + 3, MAP.y() + 3, 6, TEXT_HEIGHT));
        List<Optional<Rect>> placed = MapLabels.place(anchors, MAP, blocked);
        assertTrue(placed.getFirst().isPresent(), "die wichtigste Beschriftung bekommt immer einen Platz");
        assertNoOverlap(List.of(), placed, blocked);
    }

    @Test
    void labelsStayInsideTheMapAtTheEdge() {
        List<MapLabels.Anchor> anchors = List.of(new MapLabels.Anchor(MAP.right() - 2, MAP.y() + 2, 11, TEXT_HEIGHT, 3),
                new MapLabels.Anchor(MAP.x() + 1, MAP.bottom() - 1, 11, TEXT_HEIGHT, 3));
        List<Optional<Rect>> placed = MapLabels.place(anchors, MAP, List.of());
        assertTrue(placed.get(0).isPresent() && placed.get(1).isPresent());
        assertNoOverlap(List.of(), placed, List.of());
    }
}
