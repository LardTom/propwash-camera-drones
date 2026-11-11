package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.planner.model.MapColumns;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapColumnsTest {
    private static final MapColumns.Shade SHADE = (color, brightness) -> 0xFF000000 | color << 8 | brightness;

    private static final class Ground implements MapColumns.Source {
        private int calls;
        private boolean loaded = true;

        @Override
        public int column(int x, int z) {
            this.calls++;
            if (!this.loaded) {
                return MapColumns.NOT_LOADED;
            }
            int top = 60 + Math.floorMod(x * 3 + z * 5, 7);
            return MapColumns.pack(1 + Math.floorMod(x + z, 60), top, top);
        }
    }

    @Test
    void packKeepsColorAndHeights() {
        for (int y : new int[]{-2032, -64, 0, 63, 319, 2031}) {
            for (int color : new int[]{0, 1, 33, 63}) {
                int packed = MapColumns.pack(color, y, y + 1);
                assertTrue(packed != MapColumns.NOT_LOADED);
                assertEquals(color, MapColumns.colorId(packed));
                assertEquals(y, MapColumns.y(packed));
                assertEquals(y + 1, MapColumns.top(packed));
            }
        }
    }

    @Test
    void shadingLikeTheVanillaMap() {
        MapColumns.Source here = (x, z) -> z == 0 ? MapColumns.pack(5, 64, 64) : MapColumns.NOT_LOADED;
        assertEquals(SHADE.argb(5, MapColumns.NORMAL), MapColumns.sample(here, SHADE, 0, 0, -1), "Norden nicht geladen");
        for (int[] c : new int[][]{{63, MapColumns.HIGH}, {64, MapColumns.NORMAL}, {65, MapColumns.LOW}}) {
            int northTop = c[0];
            MapColumns.Source s = (x, z) -> z == 0 ? MapColumns.pack(5, 64, 64) : MapColumns.pack(7, northTop, northTop);
            assertEquals(SHADE.argb(5, c[1]), MapColumns.sample(s, SHADE, 0, 0, -1));
        }
        MapColumns.Source air = (x, z) -> MapColumns.pack(0, 10, 70);
        assertEquals(MapColumns.UNLOADED, MapColumns.sample(air, SHADE, 0, 0, -1), "keine Kartenfarbe");
        MapColumns.Source none = (x, z) -> MapColumns.NOT_LOADED;
        assertEquals(MapColumns.UNLOADED, MapColumns.sample(none, SHADE, 0, 0, -1), "Chunk nicht geladen");
    }

    @Test
    void columnsAreComputedOncePerBlock() {
        Ground ground = new Ground();
        MapColumns columns = new MapColumns();
        for (int pass = 0; pass < 3; pass++) {
            for (int z = 0; z < 40; z++) {
                for (int x = -20; x < 20; x++) {
                    assertEquals(MapColumns.sample(new Ground(), SHADE, x, z, z - 1), columns.pixel(ground, SHADE, x, z, z - 1));
                }
            }
            assertEquals(40 * 41, ground.calls, "Durchgang " + pass);
        }
        assertEquals(40 * 41, columns.computed());
    }

    @Test
    void unloadedChunksAreAskedAgain() {
        Ground ground = new Ground();
        ground.loaded = false;
        MapColumns columns = new MapColumns();
        assertEquals(MapColumns.UNLOADED, columns.pixel(ground, SHADE, 3, 4, 3));
        assertEquals(MapColumns.UNLOADED, columns.pixel(ground, SHADE, 3, 4, 3));
        assertEquals(2, ground.calls);
        ground.loaded = true;
        assertTrue(columns.pixel(ground, SHADE, 3, 4, 3) != MapColumns.UNLOADED);
    }

    @Test
    void clearForgetsEverything() {
        Ground ground = new Ground();
        MapColumns columns = new MapColumns();
        columns.pixel(ground, SHADE, 1, 1, 0);
        columns.clear();
        assertEquals(0, columns.tiles());
        ground.calls = 0;
        columns.pixel(ground, SHADE, 1, 1, 0);
        assertEquals(2, ground.calls);
    }

    @Test
    void tileLimitBoundsTheMemory() {
        Ground ground = new Ground();
        MapColumns columns = new MapColumns();
        for (int cz = 0; cz < 80; cz++) {
            for (int cx = 0; cx < 80; cx++) {
                columns.column(ground, cx * 16, cz * 16);
            }
        }
        assertEquals(MapColumns.TILE_LIMIT, columns.tiles());
    }
}
