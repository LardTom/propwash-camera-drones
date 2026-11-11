package de.lardtom.camdrones.client.hud;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraViewLayoutTest {
    private static final int LINE = 9;
    private static final int[][] IMAGES = {
            {0, 0, 320, 240},
            {0, 0, 640, 480},
            {0, 0, 854, 480},
            {107, 0, 747, 480},
            {40, 0, 280, 180}};

    @Test
    void everythingInsideTheImageWithoutOverlap() {
        for (int[] image : IMAGES) {
            CameraViewLayout layout = CameraViewLayout.of(image[0], image[1], image[2], image[3], LINE);
            String size = image[2] - image[0] + "x" + (image[3] - image[1]);
            List<CameraViewLayout.Box> parts = List.of(layout.topBar(), layout.recording(), layout.info(), layout.map(), layout.tiltScale(),
                    layout.tiltLabels());
            for (CameraViewLayout.Box part : parts) {
                assertFalse(part.empty(), size + " " + part);
                assertTrue(part.inside(layout.image()), size + " " + part);
            }
            for (int i = 0; i < parts.size(); i++) {
                for (int j = i + 1; j < parts.size(); j++) {
                    boolean scalePair = parts.get(i) == layout.tiltScale() && parts.get(j) == layout.tiltLabels();
                    if (!scalePair) {
                        assertFalse(parts.get(i).overlaps(parts.get(j)), size + ": " + parts.get(i) + " / " + parts.get(j));
                    }
                }
            }
            assertTrue(layout.info().width() >= 20 * CameraViewLayout.CHAR_WIDTH, size + " Infoblock zu schmal");
            assertTrue(layout.map().width() >= CameraViewLayout.MIN_MAP, size);
            assertEquals(layout.map().width(), layout.map().height());
            assertTrue(layout.tiltScale().height() >= 3 * LINE, size + " Neigungsskala zu kurz");
            assertTrue(layout.warningY() > layout.recording().y1() && layout.warningY() + LINE < layout.info().y0(), size + " Warnzeile");
        }
    }

    @Test
    void tiltScaleRunsFromTopToBottom() {
        CameraViewLayout layout = CameraViewLayout.of(0, 0, 320, 240, LINE);
        CameraViewLayout.Box scale = layout.tiltScale();
        assertEquals(scale.y0(), layout.tiltY(30.0, -90.0, 30.0));
        assertEquals(scale.y1() - 1, layout.tiltY(-90.0, -90.0, 30.0));
        assertEquals(scale.y0(), layout.tiltY(80.0, -90.0, 30.0));
        assertTrue(layout.tiltY(0.0, -90.0, 30.0) < layout.tiltY(-45.0, -90.0, 30.0));
        assertEquals(scale.centerY(), layout.tiltY(0.0, 10.0, 10.0));
    }

    @Test
    void degenerateImageDoesNotThrow() {
        CameraViewLayout layout = CameraViewLayout.of(10, 10, 5, 5, LINE);
        assertTrue(layout.image().empty());
    }
}
