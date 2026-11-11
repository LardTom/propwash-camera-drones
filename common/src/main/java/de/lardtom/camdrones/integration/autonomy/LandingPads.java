package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.NavTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

public final class LandingPads {
    public static final Identifier ID = CamDrones.id("landing_pad");
    public static final double REACH = 1.2;
    public static final int DEPTH = 64;

    private LandingPads() {
    }

    public static Optional<Block> block() {
        return BuiltInRegistries.BLOCK.getOptional(ID);
    }

    public static Optional<NavTarget> below(Level level, double x, double y, double z) {
        Optional<Block> pad = block();
        if (pad.isEmpty() || level == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return Optional.empty();
        }
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = Math.min((int) Math.floor(y), level.getMaxY());
        int bottom = Math.max(top - DEPTH, level.getMinY());
        NavTarget best = null;
        double bestDistance = REACH;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                double cx = bx + dx + 0.5;
                double cz = bz + dz + 0.5;
                double distance = Math.hypot(cx - x, cz - z);
                if (distance > bestDistance) {
                    continue;
                }
                for (int by = top; by >= bottom; by--) {
                    pos.set(bx + dx, by, bz + dz);
                    if (!level.isLoaded(pos)) {
                        break;
                    }
                    BlockState state = level.getBlockState(pos);
                    VoxelShape shape = state.getCollisionShape(level, pos);
                    if (shape.isEmpty() && state.getFluidState().isEmpty()) {
                        continue;
                    }
                    if (state.is(pad.get())) {
                        best = NavTarget.at(cx, by + (shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y)), cz);
                        bestDistance = distance;
                    }
                    break;
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
