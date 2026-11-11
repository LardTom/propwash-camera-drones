package de.lardtom.camdrones.client.planner;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

public final class MapProbe {
    private MapProbe() {
    }

    public record Timing(long[] intervals, long[] work, int ticks, long tickWork, long tickWorst, long written, long uploaded, long columns) {
    }

    public record Drawn(double centerX, double centerZ, double scale) {
    }

    public static void reset(PlannerScreen screen) {
        screen.mapImage().timing.reset();
    }

    public static Timing timing(PlannerScreen screen) {
        MapTiming t = screen.mapImage().timing;
        return new Timing(t.intervals(), t.work(), t.ticks(), t.tickWork(), t.tickWorst(), t.written(), t.uploaded(), t.columns());
    }

    public static Drawn drawn(PlannerScreen screen) {
        MapTiming t = screen.mapImage().timing;
        return new Drawn(t.drawnX(), t.drawnZ(), t.drawnScale());
    }

    public static boolean complete(PlannerScreen screen) {
        return screen.mapImage().complete();
    }

    public static int unloaded() {
        return MapImage.UNLOADED;
    }

    public static int expected(ClientLevel level, BlockPos.MutableBlockPos pos, double x, double z, double scale) {
        return MapImage.sample(level, pos, (int) Math.floor(x), (int) Math.floor(z), (int) Math.floor(z - Math.max(1.0, scale)));
    }
}
