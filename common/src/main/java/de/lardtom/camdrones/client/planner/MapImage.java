package de.lardtom.camdrones.client.planner;

import com.mojang.blaze3d.systems.RenderSystem;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.planner.model.MapCanvas;
import de.lardtom.camdrones.planner.model.MapColumns;
import de.lardtom.camdrones.planner.model.MapView;
import de.lardtom.camdrones.planner.model.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicInteger;

final class MapImage implements AutoCloseable {
    static final int UNLOADED = MapCanvas.UNLOADED;
    static final long FRAME_NANOS = 4_000_000L;
    static final long TICK_NANOS = 4_000_000L;
    static final int REFRESH_TICKS = 20;
    private static final int MAX_DEPTH = 12;
    private static final int BATCH = 512;
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final MapColumns.Shade SHADE = (color, brightness) -> MapColor.getColorFromPackedId(color << 2 | brightness);

    private record Slot(Identifier id, DynamicTexture texture, int width, int height) {
    }

    private MapCanvas canvas = new MapCanvas();
    private final MapColumns columns = new MapColumns();
    private final Slot[] slots = new Slot[2];
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    final MapTiming timing = new MapTiming();
    private @Nullable ClientLevel level;
    private @Nullable ByteBuffer scratch;
    private int width = 1;
    private int height = 1;
    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;
    private double lastScale = Double.NaN;
    private int idleTicks;

    void ensure(int w, int h) {
        this.width = Math.clamp(w, 1, MapCanvas.MAX_MAP);
        this.height = Math.clamp(h, 1, MapCanvas.MAX_MAP);
    }

    void request(MapView view) {
        if (view.centerX() != this.lastX || view.centerZ() != this.lastZ || view.blocksPerPixel() != this.lastScale) {
            this.lastX = view.centerX();
            this.lastZ = view.centerZ();
            this.lastScale = view.blocksPerPixel();
            this.idleTicks = 0;
        }
        this.canvas.request(view.centerX(), view.centerZ(), view.blocksPerPixel(), this.width, this.height);
    }

    boolean complete() {
        return this.canvas.complete();
    }

    void build(@Nullable ClientLevel level, int rows) {
        long started = System.nanoTime();
        if (level != null) {
            this.level(level);
            if (++this.idleTicks % REFRESH_TICKS == 0 && this.canvas.complete()) {
                this.canvas.refreshUnloaded();
            }
            this.work(Math.max(1, rows) * Math.min(this.width, MapCanvas.MAX_SIZE), started + TICK_NANOS);
            this.upload();
        }
        this.timing.tick(System.nanoTime() - started);
    }

    void draw(GuiGraphicsExtractor g, Rect inner, MapView view, @Nullable ClientLevel level) {
        long started = System.nanoTime();
        if (level != null) {
            this.level(level);
        }
        this.ensure(inner.w(), inner.h());
        this.request(view);
        if (this.level != null) {
            this.work(Integer.MAX_VALUE, started + FRAME_NANOS);
        }
        this.upload();
        long work = System.nanoTime() - started;
        this.canvas.placement().ifPresent(p -> this.blit(g, inner, p));
        this.timing.frame(view.centerX(), view.centerZ(), view.blocksPerPixel(), work);
    }

    private void level(ClientLevel level) {
        if (level != this.level) {
            if (this.level != null) {
                this.columns.clear();
                this.canvas.invalidate();
            }
            this.level = level;
        }
    }

    private void work(int max, long deadline) {
        ClientLevel world = this.level;
        if (world == null) {
            return;
        }
        MapColumns.Source ground = (x, z) -> column(world, this.pos, x, z);
        MapCanvas.Source source = (x, z, northZ) -> this.columns.pixel(ground, SHADE, x, z, northZ);
        long before = this.columns.computed();
        long done = 0L;
        while (done < max && !this.canvas.idle()) {
            int n = this.canvas.work(source, (int) Math.min(BATCH, max - done));
            if (n == 0) {
                break;
            }
            done += n;
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        this.timing.written(done);
        this.timing.columns(this.columns.computed() - before);
    }

    private void upload() {
        this.canvas.front().ifPresent(layer -> {
            Slot slot = this.slot(layer);
            layer.drain((x, y, w, h) -> this.write(slot, layer, x, y, w, h));
        });
    }

    private Slot slot(MapCanvas.Layer layer) {
        Slot slot = this.slots[layer.slot()];
        if (slot != null && slot.width() == layer.width() && slot.height() == layer.height()) {
            return slot;
        }
        if (slot != null) {
            Minecraft.getInstance().getTextureManager().release(slot.id());
        }
        Identifier id = CamDrones.id("planner_map/" + COUNTER.incrementAndGet());
        DynamicTexture texture = new DynamicTexture(() -> "Kamera-Drohnen Flugplaner-Karte", layer.width(), layer.height(), true);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        slot = new Slot(id, texture, layer.width(), layer.height());
        this.slots[layer.slot()] = slot;
        return slot;
    }

    private void write(Slot slot, MapCanvas.Layer layer, int x, int y, int w, int h) {
        int bytes = w * h * 4;
        ByteBuffer buffer = this.scratch;
        if (buffer == null || buffer.capacity() < bytes) {
            buffer = ByteBuffer.allocateDirect(Math.max(bytes, 1 << 16)).order(ByteOrder.LITTLE_ENDIAN);
            this.scratch = buffer;
        }
        buffer.clear();
        for (int v = y; v < y + h; v++) {
            for (int u = x; u < x + w; u++) {
                buffer.putInt(ARGB.toABGR(layer.texel(u, v)));
            }
        }
        buffer.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(slot.texture().getTexture(), buffer, 0, 0, x, y, w, h);
        this.timing.uploaded((long) w * h);
    }

    private void blit(GuiGraphicsExtractor g, Rect inner, MapCanvas.Placement p) {
        Slot slot = this.slots[p.slot()];
        if (slot == null || slot.width() != p.width() || slot.height() != p.height()) {
            return;
        }
        int right = p.width() - p.u0();
        int lower = p.height() - p.v0();
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) (inner.x() + p.x()), (float) (inner.y() + p.y()));
        pose.scale((float) p.size(), (float) p.size());
        part(g, slot, p, 0, 0, p.u0(), p.v0(), right, lower);
        if (p.u0() > 0) {
            part(g, slot, p, right, 0, 0, p.v0(), p.u0(), lower);
        }
        if (p.v0() > 0) {
            part(g, slot, p, 0, lower, p.u0(), 0, right, p.v0());
        }
        if (p.u0() > 0 && p.v0() > 0) {
            part(g, slot, p, right, lower, 0, 0, p.u0(), p.v0());
        }
        pose.popMatrix();
    }

    private static void part(GuiGraphicsExtractor g, Slot slot, MapCanvas.Placement p, int x, int y, int u, int v, int w, int h) {
        g.blit(RenderPipelines.GUI_TEXTURED, slot.id(), x, y, u, v, w, h, p.width(), p.height());
    }

    static int column(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
            return MapColumns.NOT_LOADED;
        }
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int min = level.getMinY();
        MapColor color = MapColor.NONE;
        int y = top;
        for (int depth = 0; depth < MAX_DEPTH && y >= min; depth++, y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            color = state.getMapColor(level, pos);
            if (color != MapColor.NONE) {
                break;
            }
        }
        return MapColumns.pack(color == MapColor.NONE ? 0 : color.id, y, top);
    }

    static int sample(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int z, int northZ) {
        return MapColumns.sample((cx, cz) -> column(level, pos, cx, cz), SHADE, x, z, northZ);
    }

    @Override
    public void close() {
        for (int i = 0; i < this.slots.length; i++) {
            if (this.slots[i] != null) {
                Minecraft.getInstance().getTextureManager().release(this.slots[i].id());
                this.slots[i] = null;
            }
        }
        this.canvas = new MapCanvas();
        this.columns.clear();
        this.level = null;
    }
}
