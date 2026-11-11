package de.lardtom.camdrones.planner.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class MapCanvas {
    public static final int UNLOADED = MapColumns.UNLOADED;
    public static final int NONE = 0;
    public static final int MAX_SIZE = 512;
    public static final int MAX_MAP = 8192;

    @FunctionalInterface
    public interface Source {
        int pixel(int x, int z, int northZ);
    }

    @FunctionalInterface
    public interface Region {
        void accept(int x, int y, int w, int h);
    }

    public record Placement(int slot, double x, double y, double size, int u0, int v0, int width, int height) {
    }

    public static final class Layer {
        private final int slot;
        private final int width;
        private final int height;
        private final double scale;
        private final int[] pixels;
        private final boolean[] waiting;
        private final boolean[] dirty;
        private final boolean[] dirtyRow;
        private int pending;
        private int cursor;
        private int originX;
        private int originZ;
        private boolean placed;
        private boolean anyDirty;

        Layer(int slot, int width, int height, double scale) {
            this.slot = slot;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.pixels = new int[width * height];
            this.waiting = new boolean[width * height];
            this.dirty = new boolean[width * height];
            this.dirtyRow = new boolean[height];
            Arrays.fill(this.pixels, UNLOADED);
            Arrays.fill(this.dirty, true);
            Arrays.fill(this.dirtyRow, true);
            this.anyDirty = true;
        }

        public int slot() {
            return this.slot;
        }

        public int width() {
            return this.width;
        }

        public int height() {
            return this.height;
        }

        public double scale() {
            return this.scale;
        }

        public int originX() {
            return this.originX;
        }

        public int originZ() {
            return this.originZ;
        }

        public int pending() {
            return this.pending;
        }

        public int texel(int u, int v) {
            return this.pixels[v * this.width + u];
        }

        public int pixelAt(int gx, int gz) {
            if (gx < this.originX || gz < this.originZ || gx >= this.originX + this.width || gz >= this.originZ + this.height) {
                return NONE;
            }
            return this.pixels[Math.floorMod(gz, this.height) * this.width + Math.floorMod(gx, this.width)];
        }

        boolean fits(double scale, int width, int height) {
            return this.scale == scale && this.width == width && this.height == height;
        }

        void moveTo(int ox, int oz) {
            if (!this.placed) {
                this.placed = true;
                this.originX = ox;
                this.originZ = oz;
                this.markAll();
                return;
            }
            int dx = ox - this.originX;
            int dz = oz - this.originZ;
            if (dx == 0 && dz == 0) {
                return;
            }
            int oldX = this.originX;
            int oldZ = this.originZ;
            this.originX = ox;
            this.originZ = oz;
            if (Math.abs(dx) >= this.width || Math.abs(dz) >= this.height) {
                this.markAll();
                return;
            }
            int fromX = dx > 0 ? oldX + this.width : ox;
            int toX = dx > 0 ? ox + this.width : oldX;
            for (int gx = fromX; gx < toX; gx++) {
                int u = Math.floorMod(gx, this.width);
                for (int v = 0; v < this.height; v++) {
                    this.markPending(v * this.width + u);
                }
            }
            int fromZ = dz > 0 ? oldZ + this.height : oz;
            int toZ = dz > 0 ? oz + this.height : oldZ;
            for (int gz = fromZ; gz < toZ; gz++) {
                int base = Math.floorMod(gz, this.height) * this.width;
                for (int u = 0; u < this.width; u++) {
                    this.markPending(base + u);
                }
            }
        }

        private void markAll() {
            for (int i = 0; i < this.pixels.length; i++) {
                this.markPending(i);
            }
            this.cursor = Math.floorMod(this.originZ, this.height) * this.width;
        }

        int markUnloaded() {
            int marked = 0;
            for (int i = 0; i < this.pixels.length; i++) {
                if (!this.waiting[i] && this.pixels[i] == UNLOADED) {
                    this.waiting[i] = true;
                    this.pending++;
                    marked++;
                }
            }
            return marked;
        }

        private void markPending(int i) {
            if (!this.waiting[i]) {
                this.waiting[i] = true;
                this.pending++;
            }
            this.set(i, UNLOADED);
        }

        private void set(int i, int value) {
            if (this.pixels[i] != value) {
                this.pixels[i] = value;
                this.dirty[i] = true;
                this.dirtyRow[i / this.width] = true;
                this.anyDirty = true;
            }
        }

        int sample(Source source, int max) {
            if (this.pending == 0 || max <= 0) {
                return 0;
            }
            int n = this.pixels.length;
            int ringX = Math.floorMod(this.originX, this.width);
            int ringZ = Math.floorMod(this.originZ, this.height);
            double north = Math.max(1.0, this.scale);
            int done = 0;
            for (int step = 0; step < n && this.pending > 0 && done < max; step++) {
                int i = this.cursor;
                this.cursor = i + 1 == n ? 0 : i + 1;
                if (!this.waiting[i]) {
                    continue;
                }
                int gx = this.originX + Math.floorMod(i % this.width - ringX, this.width);
                int gz = this.originZ + Math.floorMod(i / this.width - ringZ, this.height);
                double wx = (gx + 0.5) * this.scale;
                double wz = (gz + 0.5) * this.scale;
                int value = source.pixel((int) Math.floor(wx), (int) Math.floor(wz), (int) Math.floor(wz - north));
                this.waiting[i] = false;
                this.pending--;
                done++;
                this.set(i, value);
            }
            return done;
        }

        public void drain(Region region) {
            if (!this.anyDirty) {
                return;
            }
            this.anyDirty = false;
            List<int[]> open = new ArrayList<>();
            List<int[]> next = new ArrayList<>();
            for (int v = 0; v < this.height; v++) {
                next.clear();
                if (this.dirtyRow[v]) {
                    this.dirtyRow[v] = false;
                    int base = v * this.width;
                    int u = 0;
                    while (u < this.width) {
                        if (!this.dirty[base + u]) {
                            u++;
                            continue;
                        }
                        int a = u;
                        while (u < this.width && this.dirty[base + u]) {
                            this.dirty[base + u] = false;
                            u++;
                        }
                        int[] match = null;
                        for (int[] r : open) {
                            if (r[0] == a && r[1] == u) {
                                match = r;
                                break;
                            }
                        }
                        if (match != null) {
                            open.remove(match);
                            next.add(match);
                        } else {
                            next.add(new int[]{a, u, v});
                        }
                    }
                }
                for (int[] r : open) {
                    region.accept(r[0], r[2], r[1] - r[0], v - r[2]);
                }
                List<int[]> swap = open;
                open = next;
                next = swap;
            }
            for (int[] r : open) {
                region.accept(r[0], r[2], r[1] - r[0], this.height - r[2]);
            }
        }
    }

    private Layer front;
    private Layer back;
    private boolean stale;
    private int mapWidth = 1;
    private int mapHeight = 1;
    private double centerX;
    private double centerZ;
    private double scale = Double.NaN;
    private double texel = Double.NaN;
    private int texelsX = 1;
    private int texelsZ = 1;
    private long written;

    public void request(double centerX, double centerZ, double blocksPerPixel, int width, int height) {
        if (!(blocksPerPixel > 0.0) || !Double.isFinite(blocksPerPixel) || !Double.isFinite(centerX) || !Double.isFinite(centerZ)) {
            return;
        }
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.scale = blocksPerPixel;
        this.mapWidth = Math.clamp(width, 1, MAX_MAP);
        this.mapHeight = Math.clamp(height, 1, MAX_MAP);
        int pixelsPerTexel = Math.ceilDiv(Math.max(this.mapWidth, this.mapHeight), MAX_SIZE);
        this.texel = blocksPerPixel * pixelsPerTexel;
        this.texelsX = Math.ceilDiv(this.mapWidth, pixelsPerTexel) + 1;
        this.texelsZ = Math.ceilDiv(this.mapHeight, pixelsPerTexel) + 1;
        int ox = (int) Math.floor((centerX - this.mapWidth / 2.0 * blocksPerPixel) / this.texel);
        int oz = (int) Math.floor((centerZ - this.mapHeight / 2.0 * blocksPerPixel) / this.texel);
        if (this.front == null) {
            this.front = new Layer(0, this.texelsX, this.texelsZ, this.texel);
            this.front.moveTo(ox, oz);
            this.back = null;
            this.stale = false;
            return;
        }
        if (!this.stale && this.front.fits(this.texel, this.texelsX, this.texelsZ)) {
            this.front.moveTo(ox, oz);
            this.back = null;
            return;
        }
        if (this.back == null || !this.back.fits(this.texel, this.texelsX, this.texelsZ)) {
            this.back = new Layer(1 - this.front.slot, this.texelsX, this.texelsZ, this.texel);
        }
        this.back.moveTo(ox, oz);
    }

    public int work(Source source, int max) {
        int done = 0;
        if (this.front != null) {
            done += this.front.sample(source, max);
        }
        if (this.back != null) {
            done += this.back.sample(source, max - done);
            if (this.back.pending == 0) {
                this.front = this.back;
                this.back = null;
                this.stale = false;
            }
        }
        this.written += done;
        return done;
    }

    public boolean idle() {
        return (this.front == null || this.front.pending == 0) && this.back == null;
    }

    public boolean complete() {
        return this.front != null && this.back == null && !this.stale && this.front.pending == 0
                && this.front.fits(this.texel, this.texelsX, this.texelsZ);
    }

    public void invalidate() {
        if (this.front != null) {
            this.stale = true;
            this.back = null;
        }
    }

    public int refreshUnloaded() {
        return this.front == null || this.back != null ? 0 : this.front.markUnloaded();
    }

    public Optional<Layer> front() {
        return Optional.ofNullable(this.front);
    }

    public Optional<Layer> back() {
        return Optional.ofNullable(this.back);
    }

    public long written() {
        return this.written;
    }

    public Optional<Placement> placement() {
        Layer f = this.front;
        if (f == null || Double.isNaN(this.scale)) {
            return Optional.empty();
        }
        double size = f.scale / this.scale;
        double x = this.mapWidth / 2.0 + (f.originX * f.scale - this.centerX) / this.scale;
        double y = this.mapHeight / 2.0 + (f.originZ * f.scale - this.centerZ) / this.scale;
        return Optional.of(new Placement(f.slot, x, y, size, Math.floorMod(f.originX, f.width), Math.floorMod(f.originZ, f.height), f.width,
                f.height));
    }

    public int shown(double px, double py) {
        Optional<Placement> placed = this.placement();
        if (placed.isEmpty() || px < 0.0 || py < 0.0 || px >= this.mapWidth || py >= this.mapHeight) {
            return NONE;
        }
        Placement p = placed.get();
        double tx = (px - p.x()) / p.size();
        double ty = (py - p.y()) / p.size();
        if (tx < 0.0 || ty < 0.0 || tx >= p.width() || ty >= p.height()) {
            return NONE;
        }
        int u = ((int) tx + p.u0()) % p.width();
        int v = ((int) ty + p.v0()) % p.height();
        return this.front.texel(u, v);
    }
}
