package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

public final class StartProbes {
    public static final Identifier BATTERY_CHARGE = Identifier.fromNamespaceAndPath("propwash", "battery_charge");
    public static final double RAY_LENGTH = 96.0;
    public static final double RAY_STEP = 0.1;
    public static final int AZIMUTHS = 8;
    public static final double[] ELEVATIONS_DEG = {90.0, 60.0, 30.0};
    public static final double[] WEIGHTS = {1.5, 1.0, 0.6};

    private StartProbes() {
    }

    public static StartCheck.Battery battery(Entity drone) {
        try {
            ItemStack stack = drone.getPickResult();
            if (stack == null || stack.isEmpty() || !PropwashApi.builds().isDrone(stack)) {
                return StartCheck.Battery.UNKNOWN;
            }
            Optional<DataComponentType<?>> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(BATTERY_CHARGE);
            if (type.isEmpty()) {
                return StartCheck.Battery.UNKNOWN;
            }
            Object charge = stack.get(type.get());
            if (charge == null) {
                return StartCheck.Battery.NONE;
            }
            return charge instanceof Number number ? StartCheck.Battery.charged(number.doubleValue()) : StartCheck.Battery.UNKNOWN;
        } catch (RuntimeException e) {
            return StartCheck.Battery.UNKNOWN;
        }
    }

    public static double skyView(Entity drone) {
        Vec3 center = drone.getBoundingBox().getCenter();
        return skyView(drone.level(), center.x, center.y, center.z);
    }

    public static double skyView(Level level, double x, double y, double z) {
        if (level.dimensionType().hasCeiling()) {
            return 0.0;
        }
        if (y > level.getMaxY() + 1) {
            return 1.0;
        }
        Vec3 from = new Vec3(x, y, z);
        double open = 0.0;
        double total = 0.0;
        for (int e = 0; e < ELEVATIONS_DEG.length; e++) {
            double elevation = Math.toRadians(ELEVATIONS_DEG[e]);
            int count = ELEVATIONS_DEG[e] >= 90.0 ? 1 : AZIMUTHS;
            for (int a = 0; a < count; a++) {
                double azimuth = (a + 0.5 * (e % 2)) * 2.0 * Math.PI / AZIMUTHS;
                double horizontal = Math.cos(elevation);
                Vec3 direction = new Vec3(horizontal * Math.sin(azimuth), Math.sin(elevation), horizontal * Math.cos(azimuth));
                total += WEIGHTS[e];
                if (clear(level, from, from.add(direction.scale(RAY_LENGTH)))) {
                    open += WEIGHTS[e];
                }
            }
        }
        return total > 0.0 ? open / total : 1.0;
    }

    private static boolean clear(Level level, Vec3 from, Vec3 to) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int steps = (int) Math.ceil(RAY_LENGTH / RAY_STEP);
        long last = Long.MIN_VALUE;
        for (int i = 0; i <= steps; i++) {
            double t = Math.min(1.0, i * RAY_STEP / RAY_LENGTH);
            double py = from.y + (to.y - from.y) * t;
            if (py > level.getMaxY() + 1) {
                return true;
            }
            pos.set(from.x + (to.x - from.x) * t, py, from.z + (to.z - from.z) * t);
            long key = pos.asLong();
            if (key == last) {
                continue;
            }
            last = key;
            if (!level.isLoaded(pos)) {
                return true;
            }
            BlockState state = level.getBlockState(pos);
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty() && fluid.getShape(level, pos).clip(from, to, pos) != null) {
                return false;
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty() && shape.clip(from, to, pos) != null) {
                return false;
            }
        }
        return true;
    }
}
