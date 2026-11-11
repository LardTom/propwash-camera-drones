package de.lardtom.camdrones.client.hud;

import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class HudHooks {
    private static final ToDoubleFunction<Entity> NO_TILT = drone -> Double.NaN;
    private static final Predicate<Entity> NO_OPERATOR = drone -> false;
    private static final Predicate<Entity> NO_STOP = drone -> false;

    private static volatile ToDoubleFunction<Entity> gimbalTilt = NO_TILT;
    private static volatile Predicate<Entity> operatorBound = NO_OPERATOR;
    private static volatile Predicate<Entity> gimbalStop = NO_STOP;

    private HudHooks() {
    }

    public static void gimbalTilt(ToDoubleFunction<Entity> source) {
        gimbalTilt = Objects.requireNonNull(source, "source");
    }

    public static void operatorBound(Predicate<Entity> source) {
        operatorBound = Objects.requireNonNull(source, "source");
    }

    public static void gimbalStop(Predicate<Entity> source) {
        gimbalStop = Objects.requireNonNull(source, "source");
    }

    public static double gimbalTilt(Entity drone) {
        try {
            return gimbalTilt.applyAsDouble(drone);
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    public static boolean operatorBound(Entity drone) {
        try {
            return operatorBound.test(drone);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean gimbalStop(Entity drone) {
        try {
            return gimbalStop.test(drone);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void reset() {
        gimbalTilt = NO_TILT;
        operatorBound = NO_OPERATOR;
        gimbalStop = NO_STOP;
    }
}
