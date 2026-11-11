package de.lardtom.camdrones.planner.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MapColumns {
    public static final int UNLOADED = 0xFF161616;
    public static final int NOT_LOADED = 0;
    public static final int LOW = 0;
    public static final int NORMAL = 1;
    public static final int HIGH = 2;
    public static final int TILE_LIMIT = 4096;
    private static final int KNOWN = 1 << 30;
    private static final int OFFSET = 2048;
    private static final int HEIGHT_MASK = 4095;
    private static final int COLOR_MASK = 63;

    @FunctionalInterface
    public interface Source {
        int column(int x, int z);
    }

    @FunctionalInterface
    public interface Shade {
        int argb(int colorId, int brightness);
    }

    private final Map<Long, int[]> tiles = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) {
            return this.size() > TILE_LIMIT;
        }
    };
    private long lastKey = Long.MIN_VALUE;
    private int[] lastTile;
    private long computed;

    public static int pack(int colorId, int y, int top) {
        return KNOWN | Math.clamp(colorId, 0, COLOR_MASK) | Math.clamp(y + OFFSET, 0, HEIGHT_MASK) << 6
                | Math.clamp(top + OFFSET, 0, HEIGHT_MASK) << 18;
    }

    public static int colorId(int column) {
        return column & COLOR_MASK;
    }

    public static int y(int column) {
        return (column >>> 6 & HEIGHT_MASK) - OFFSET;
    }

    public static int top(int column) {
        return (column >>> 18 & HEIGHT_MASK) - OFFSET;
    }

    public static int sample(Source source, Shade shade, int x, int z, int northZ) {
        int here = source.column(x, z);
        if (here == NOT_LOADED || colorId(here) == 0) {
            return UNLOADED;
        }
        return shaded(here, source.column(x, northZ), shade);
    }

    public int pixel(Source source, Shade shade, int x, int z, int northZ) {
        int here = this.column(source, x, z);
        if (here == NOT_LOADED || colorId(here) == 0) {
            return UNLOADED;
        }
        return shaded(here, this.column(source, x, northZ), shade);
    }

    private static int shaded(int here, int north, Shade shade) {
        int brightness = NORMAL;
        if (north != NOT_LOADED) {
            int y = y(here);
            int top = top(north);
            if (y > top) {
                brightness = HIGH;
            } else if (y < top) {
                brightness = LOW;
            }
        }
        return shade.argb(colorId(here), brightness);
    }

    public int column(Source source, int x, int z) {
        long key = (long) (x >> 4) << 32 | (z >> 4) & 0xFFFFFFFFL;
        int index = (z & 15) << 4 | x & 15;
        int[] tile = key == this.lastKey ? this.lastTile : this.tiles.get(key);
        if (tile != null) {
            this.lastKey = key;
            this.lastTile = tile;
            if (tile[index] != NOT_LOADED) {
                return tile[index];
            }
        }
        int value = source.column(x, z);
        this.computed++;
        if (value != NOT_LOADED) {
            if (tile == null) {
                tile = new int[256];
                this.tiles.put(key, tile);
                this.lastKey = key;
                this.lastTile = tile;
            }
            tile[index] = value;
        }
        return value;
    }

    public void clear() {
        this.tiles.clear();
        this.lastKey = Long.MIN_VALUE;
        this.lastTile = null;
    }

    public long computed() {
        return this.computed;
    }

    public int tiles() {
        return this.tiles.size();
    }
}
