package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.planner.model.MapCanvas;
import de.lardtom.camdrones.planner.model.MapView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapCanvasTest {
    private static final int W = 120;
    private static final int H = 90;

    private static final class World implements MapCanvas.Source {
        private int calls;
        private int loadedFromX = Integer.MIN_VALUE;
        private final List<Integer> northSteps = new ArrayList<>();

        @Override
        public int pixel(int x, int z, int northZ) {
            this.calls++;
            this.northSteps.add(z - northZ);
            return x < this.loadedFromX ? MapCanvas.UNLOADED : color(x, z);
        }
    }

    private record Look(int wrong, int empty) {
    }

    private static int color(int x, int z) {
        return 0xFF000000 | (x & 0xFFF) << 12 | z & 0xFFF;
    }

    private static Look look(MapCanvas canvas, double cx, double cz, double scale, int width, int height) {
        double layer = canvas.front().orElseThrow().scale();
        int wrong = 0;
        int empty = 0;
        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {
                int shown = canvas.shown(px + 0.5, py + 0.5);
                if (shown == MapCanvas.NONE || shown == MapCanvas.UNLOADED) {
                    empty++;
                    continue;
                }
                double wx = cx + (px + 0.5 - width / 2.0) * scale;
                double wz = cz + (py + 0.5 - height / 2.0) * scale;
                int ex = (int) Math.floor((Math.floor(wx / layer) + 0.5) * layer);
                int ez = (int) Math.floor((Math.floor(wz / layer) + 0.5) * layer);
                if (shown != color(ex, ez)) {
                    wrong++;
                }
            }
        }
        return new Look(wrong, empty);
    }

    private static Look look(MapCanvas canvas, double cx, double cz, double scale) {
        return look(canvas, cx, cz, scale, W, H);
    }

    private static MapCanvas complete(World world, double cx, double cz, double scale) {
        MapCanvas canvas = new MapCanvas();
        canvas.request(cx, cz, scale, W, H);
        canvas.work(world, Integer.MAX_VALUE);
        assertTrue(canvas.complete());
        canvas.front().orElseThrow().drain((x, y, w, h) -> {
        });
        return canvas;
    }

    private static int uploaded(MapCanvas canvas, List<int[]> regions) {
        int[] area = new int[1];
        canvas.front().orElseThrow().drain((x, y, w, h) -> {
            regions.add(new int[]{x, y, w, h});
            area[0] += w * h;
        });
        return area[0];
    }

    @Test
    void firstImageIsCompleteAndShowsEveryPlaceWhereItIs() {
        World world = new World();
        MapCanvas canvas = complete(world, 1000.3, 2000.7, 1.0);
        assertEquals((W + 1) * (H + 1), world.calls);
        assertEquals(new Look(0, 0), look(canvas, 1000.3, 2000.7, 1.0));
    }

    @Test
    void panningShiftsTheImageAndSamplesOnlyTheNewStrips() {
        World world = new World();
        MapCanvas canvas = complete(world, 1000.3, 2000.7, 1.0);
        world.calls = 0;
        canvas.request(1003.3, 1998.7, 1.0, W, H);
        assertFalse(canvas.complete());
        canvas.work(world, Integer.MAX_VALUE);
        assertTrue(canvas.complete());
        assertEquals(3 * (H + 1) + 2 * (W + 1) - 3 * 2, world.calls, "nur die neu sichtbaren Spalten und Zeilen");
        assertEquals(new Look(0, 0), look(canvas, 1003.3, 1998.7, 1.0));
    }

    @Test
    void uploadsOnlyTheChangedStrip() {
        World world = new World();
        MapCanvas canvas = complete(world, 1000.3, 2000.7, 1.0);
        canvas.request(1001.3, 2000.7, 1.0, W, H);
        canvas.work(world, Integer.MAX_VALUE);
        List<int[]> regions = new ArrayList<>();
        assertEquals(H + 1, uploaded(canvas, regions), "eine Spalte statt der ganzen Textur");
        assertEquals(1, regions.size());
        canvas.request(1001.3, 2001.7, 1.0, W, H);
        canvas.work(world, Integer.MAX_VALUE);
        regions.clear();
        assertEquals(W + 1, uploaded(canvas, regions), "eine Zeile");
        assertEquals(1, regions.size());
    }

    @Test
    void subPixelDragMovesOnlyTheDrawnImage() {
        World world = new World();
        MapCanvas canvas = complete(world, 1000.3, 2000.7, 1.0);
        double before = canvas.placement().orElseThrow().x();
        world.calls = 0;
        canvas.request(1000.6, 2000.7, 1.0, W, H);
        canvas.work(world, Integer.MAX_VALUE);
        assertEquals(0, world.calls);
        assertEquals(before - 0.3, canvas.placement().orElseThrow().x(), 1.0E-9);
        assertTrue(canvas.placement().orElseThrow().x() <= 0.0 && canvas.placement().orElseThrow().x() > -1.0);
        assertEquals(new Look(0, 0), look(canvas, 1000.6, 2000.7, 1.0));
    }

    @Test
    void draggingNeverShowsAnotherPlaceEvenWithASmallBudget() {
        World world = new World();
        double cx = 1000.3;
        double cz = 2000.7;
        MapCanvas canvas = complete(world, cx, cz, 1.0);
        for (int frame = 0; frame < 50; frame++) {
            cx += 2.6;
            cz += frame < 25 ? 1.7 : -3.1;
            canvas.request(cx, cz, 1.0, W, H);
            canvas.work(world, 200);
            assertEquals(0, look(canvas, cx, cz, 1.0).wrong(), "Bild " + frame);
        }
        canvas.work(world, Integer.MAX_VALUE);
        assertTrue(canvas.complete());
        assertEquals(new Look(0, 0), look(canvas, cx, cz, 1.0));
    }

    @Test
    void draggingWithEnoughBudgetKeepsTheMapCompleteEveryFrame() {
        World world = new World();
        double cx = 1000.3;
        MapCanvas canvas = complete(world, cx, 2000.7, 1.0);
        for (int frame = 0; frame < 30; frame++) {
            cx += 6.0;
            world.calls = 0;
            canvas.request(cx, 2000.7, 1.0, W, H);
            canvas.work(world, 8 * (H + 1));
            assertTrue(canvas.complete(), "Bild " + frame);
            assertEquals(6 * (H + 1), world.calls);
            assertEquals(new Look(0, 0), look(canvas, cx, 2000.7, 1.0));
        }
    }

    @Test
    void zoomKeepsTheOldImageUntilTheNewOneIsComplete() {
        World world = new World();
        MapCanvas canvas = complete(world, 1000.3, 2000.7, 1.0);
        canvas.request(1000.3, 2000.7, 2.0, W, H);
        int frames = 0;
        while (!canvas.complete()) {
            canvas.work(world, 1500);
            frames++;
            double shownScale = canvas.front().orElseThrow().scale();
            assertTrue(shownScale == 1.0 || canvas.complete(), "kein halb neues Bild");
            assertEquals(0, look(canvas, 1000.3, 2000.7, 2.0).wrong(), "Bild " + frames);
            assertTrue(frames < 100);
        }
        assertTrue(frames > 3, "Aufbau über mehrere Bilder");
        assertEquals(2.0, canvas.front().orElseThrow().scale());
        assertEquals(new Look(0, 0), look(canvas, 1000.3, 2000.7, 2.0));
    }

    @Test
    void zoomedOutImageCoversOnlyTheMiddleUntilTheNewOneIsReady() {
        World world = new World();
        MapCanvas canvas = complete(world, 1000.0, 2000.0, 1.0);
        canvas.request(1000.0, 2000.0, 2.0, W, H);
        canvas.work(world, 100);
        assertEquals(1.0, canvas.front().orElseThrow().scale());
        assertEquals(MapCanvas.NONE, canvas.shown(2.0, 2.0), "Rand leer statt alt");
        assertTrue(canvas.shown(W / 2.0, H / 2.0) != MapCanvas.NONE, "Mitte zeigt das alte Bild halb so groß");
        assertEquals(0.5, canvas.placement().orElseThrow().size());
    }

    @Test
    void panningWhileTheZoomedImageIsBuiltMovesThatImage() {
        World world = new World();
        double cx = 1000.3;
        MapCanvas canvas = complete(world, cx, 2000.7, 1.0);
        canvas.request(cx, 2000.7, 0.5, W, H);
        for (int frame = 0; frame < 40 && !canvas.complete(); frame++) {
            cx += 1.3;
            canvas.request(cx, 2000.7, 0.5, W, H);
            canvas.work(world, 900);
            assertEquals(0, look(canvas, cx, 2000.7, 0.5).wrong(), "Bild " + frame);
        }
        canvas.work(world, Integer.MAX_VALUE);
        assertTrue(canvas.complete());
        assertEquals(new Look(0, 0), look(canvas, cx, 2000.7, 0.5));
    }

    @Test
    void zoomingBackBeforeTheNewImageIsReadyKeepsTheOldOne() {
        World world = new World();
        MapCanvas canvas = complete(world, 1000.3, 2000.7, 1.0);
        canvas.request(1000.3, 2000.7, 4.0, W, H);
        canvas.work(world, 300);
        assertFalse(canvas.complete());
        world.calls = 0;
        canvas.request(1000.3, 2000.7, 1.0, W, H);
        assertTrue(canvas.complete());
        assertTrue(canvas.back().isEmpty());
        canvas.work(world, Integer.MAX_VALUE);
        assertEquals(0, world.calls);
    }

    @Test
    void resizeBuildsTheNewSizeBehindTheOldImage() {
        World world = new World();
        MapCanvas canvas = complete(world, 1000.3, 2000.7, 1.0);
        canvas.request(1000.3, 2000.7, 1.0, W + 40, H + 10);
        while (!canvas.complete()) {
            canvas.work(world, 2000);
            assertEquals(0, look(canvas, 1000.3, 2000.7, 1.0, W + 40, H + 10).wrong());
        }
        assertEquals(W + 41, canvas.front().orElseThrow().width());
        assertEquals(new Look(0, 0), look(canvas, 1000.3, 2000.7, 1.0, W + 40, H + 10));
    }

    @Test
    void jumpFarAwayRefillsWithoutShowingTheOldPlace() {
        World world = new World();
        MapCanvas canvas = complete(world, 1000.3, 2000.7, 1.0);
        canvas.request(2500.3, 900.7, 1.0, W, H);
        canvas.work(world, 4000);
        Look partial = look(canvas, 2500.3, 900.7, 1.0);
        assertEquals(0, partial.wrong());
        assertTrue(partial.empty() > 0);
        canvas.work(world, Integer.MAX_VALUE);
        assertEquals(new Look(0, 0), look(canvas, 2500.3, 900.7, 1.0));
    }

    @Test
    void invalidateRebuildsBehindTheOldImage() {
        World world = new World();
        MapCanvas canvas = complete(world, 1000.3, 2000.7, 1.0);
        canvas.invalidate();
        assertFalse(canvas.complete());
        canvas.request(1000.3, 2000.7, 1.0, W, H);
        canvas.work(world, 100);
        assertEquals(0, canvas.front().orElseThrow().slot(), "altes Bild bleibt bis zum Tausch");
        assertEquals(new Look(0, 0), look(canvas, 1000.3, 2000.7, 1.0));
        canvas.work(world, Integer.MAX_VALUE);
        assertTrue(canvas.complete());
        assertEquals(1, canvas.front().orElseThrow().slot());
    }

    @Test
    void refreshAsksOnlyThePlaceholdersAgain() {
        World world = new World();
        world.loadedFromX = 1000;
        MapCanvas canvas = complete(world, 1000.3, 2000.7, 1.0);
        Look before = look(canvas, 1000.3, 2000.7, 1.0);
        assertTrue(before.empty() > 0);
        world.loadedFromX = Integer.MIN_VALUE;
        world.calls = 0;
        int marked = canvas.refreshUnloaded();
        canvas.work(world, Integer.MAX_VALUE);
        assertEquals(marked, world.calls);
        assertEquals((1000 - 940) * (H + 1), marked);
        assertEquals(new Look(0, 0), look(canvas, 1000.3, 2000.7, 1.0));
    }

    @Test
    void largeMapUsesCoarserTexelsAndStillCoversEveryPixel() {
        World world = new World();
        int width = 1200;
        int height = 700;
        MapCanvas canvas = new MapCanvas();
        canvas.request(1000.3, 2000.7, 1.0, width, height);
        canvas.work(world, Integer.MAX_VALUE);
        assertTrue(canvas.complete());
        MapCanvas.Layer layer = canvas.front().orElseThrow();
        assertTrue(layer.width() <= MapCanvas.MAX_SIZE + 1 && layer.height() <= MapCanvas.MAX_SIZE + 1);
        assertEquals(3.0, layer.scale());
        assertEquals(3.0, canvas.placement().orElseThrow().size());
        assertEquals(new Look(0, 0), look(canvas, 1000.3, 2000.7, 1.0, width, height));
        canvas.request(1007.3, 1995.7, 1.0, width, height);
        canvas.work(world, Integer.MAX_VALUE);
        assertEquals(new Look(0, 0), look(canvas, 1007.3, 1995.7, 1.0, width, height));
    }

    @Test
    void northNeighbourIsTheSameAsOnTheOldMap() {
        for (double scale : MapView.ZOOMS) {
            World world = new World();
            complete(world, 1000.3, 2000.7, scale);
            int step = (int) Math.max(1.0, scale);
            assertTrue(world.northSteps.stream().allMatch(s -> s == step), "Maßstab " + scale);
        }
    }

    @Test
    void alignedCenterSamplesTheSameBlocksAsTheOldMap() {
        for (double scale : MapView.ZOOMS) {
            World world = new World();
            MapCanvas canvas = complete(world, 1000.0, 2000.0, scale);
            for (int py = 0; py < H; py += 7) {
                for (int px = 0; px < W; px += 5) {
                    int oldX = (int) Math.floor(1000.0 + (px + 0.5 - W / 2.0) * scale);
                    int oldZ = (int) Math.floor(2000.0 + (py + 0.5 - H / 2.0) * scale);
                    assertEquals(color(oldX, oldZ), canvas.shown(px + 0.5, py + 0.5), "Maßstab " + scale + " bei " + px + "/" + py);
                }
            }
        }
    }
}
