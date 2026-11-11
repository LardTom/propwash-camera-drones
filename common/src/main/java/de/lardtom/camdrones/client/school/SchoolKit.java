package de.lardtom.camdrones.client.school;

import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.guide.scene.ActorSpec;
import de.lardtom.propwash.guide.scene.BlockSpec;
import de.lardtom.propwash.guide.scene.Ease;
import de.lardtom.propwash.guide.scene.SceneBuilder;
import de.lardtom.propwash.guide.scene.SceneEvaluator;
import de.lardtom.propwash.guide.scene.ScenePos;

import java.util.ArrayList;
import java.util.List;

public final class SchoolKit {
    public static final String GOGGLES = "propwash:goggles_digital";
    public static final String REMOTE = "propwash:remote";
    public static final String PLANNER = "camdrones:flight_planner";
    public static final String LANDING_PAD = "camdrones:landing_pad";
    public static final String KEY_ARM = "key.propwash.drone_arm";
    public static final String KEY_FLIGHT_MODE = "key.propwash.drone_flight_mode";
    public static final String KEY_MODE_CYCLE = "key.camdrones.mode_cycle";
    public static final String KEY_MISSION_START = "key.camdrones.mission_start";
    public static final String KEY_PAUSE = "key.camdrones.pause";
    public static final String KEY_TILT_UP = "key.camdrones.gimbal_tilt_up";
    public static final String KEY_TILT_DOWN = "key.camdrones.gimbal_tilt_down";
    public static final BlockSpec GRASS = BlockSpec.of("minecraft:grass_block");
    public static final BlockSpec STONE = BlockSpec.of("minecraft:stone");
    public static final BlockSpec STONE_BRICKS = BlockSpec.of("minecraft:stone_bricks");
    public static final BlockSpec PAD = BlockSpec.of(LANDING_PAD);
    public static final BlockSpec BEACON = BlockSpec.of("minecraft:beacon");
    public static final float DRONE_SCALE = 2.0F;
    public static final float PAD_TOP = 2.0F / 16.0F;
    public static final float GENTLE_TILT = 10.0F;
    public static final float SPORT_TILT = 24.0F;
    private static final float CRUISE_SPEED = 0.2F;
    private static final float MAX_BANK = 18.0F;
    private static final float BANK_PER_DEGREE_PER_TICK = 1.2F;

    private SchoolKit() {
    }

    public static ActorSpec.Drone drone() {
        return PropwashClientApi.flightSchoolDrone(CamPresets.TOUR.build(), true, DRONE_SCALE);
    }

    public static void ground(SceneBuilder.StepBuilder s, int x0, int z0, int x1, int z1, BlockSpec surface, float stagger, float length) {
        float base = s.now();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                s.at(base + ((x - x0) + (z - z0)) * stagger).drop(x, 0, z, surface, length);
            }
        }
        s.at(base);
    }

    public static float pilotYaw(ScenePos feet, ScenePos target) {
        float dx = target.x() - feet.x();
        float dz = target.z() - feet.z();
        if (dx * dx + dz * dz < 1.0E-6F) {
            return 0.0F;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static float heading(ScenePos from, ScenePos to) {
        float dx = to.x() - from.x();
        float dz = to.z() - from.z();
        if (dx * dx + dz * dz < 1.0E-8F) {
            return Float.NaN;
        }
        return (float) Math.toDegrees(Math.atan2(dx, -dz));
    }

    public static ScenePos padCentre(int x, int y, int z) {
        return new ScenePos(x + 0.5F, y + PAD_TOP, z + 0.5F);
    }

    public static List<ScenePos> circle(ScenePos centre, float radius, float height, int points, float startDegrees) {
        List<ScenePos> out = new ArrayList<>(points + 1);
        for (int i = 0; i <= points; i++) {
            double angle = Math.toRadians(startDegrees + 360.0 * i / points);
            out.add(new ScenePos(centre.x() + (float) (Math.sin(angle) * radius), height, centre.z() - (float) (Math.cos(angle) * radius)));
        }
        return out;
    }

    public static void fly(SceneBuilder.StepBuilder s, String drone, ScenePos from, List<ScenePos> path, Ease ease, float start, float length,
                           int samples, float maxTilt) {
        s.at(start).move(drone, length, ease, path.toArray(ScenePos[]::new));
        int count = Math.max(2, samples);
        float slice = length / count;
        List<ScenePos> points = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; i++) {
            points.add(SceneEvaluator.path(from, path, ease.apply(i / (float) count)));
        }
        float lastYaw = Float.NaN;
        for (int i = 1; i <= count; i++) {
            ScenePos a = points.get(i - 1);
            ScenePos b = points.get(i);
            float yaw = heading(a, b);
            if (Float.isNaN(yaw)) {
                yaw = Float.isNaN(lastYaw) ? 0.0F : lastYaw;
            }
            float horizontal = (float) Math.sqrt((b.x() - a.x()) * (b.x() - a.x()) + (b.z() - a.z()) * (b.z() - a.z())) / slice;
            float pitch = -maxTilt * Math.min(1.0F, horizontal / CRUISE_SPEED);
            float turnRate = Float.isNaN(lastYaw) ? 0.0F : wrap(yaw - lastYaw) / slice;
            float roll = Math.clamp(turnRate * BANK_PER_DEGREE_PER_TICK, -MAX_BANK, MAX_BANK);
            if (i == count) {
                pitch = 0.0F;
                roll = 0.0F;
            }
            s.at(start + (i - 1) * slice).turn(drone, yaw, pitch, roll, slice, Ease.LINEAR);
            lastYaw = yaw;
        }
        s.at(start);
    }

    public static void orbit(SceneBuilder.StepBuilder s, String drone, ScenePos centre, List<ScenePos> circle, float start, float length) {
        s.at(start).move(drone, length, Ease.LINEAR, circle.toArray(ScenePos[]::new));
        int count = circle.size() - 1;
        float slice = length / Math.max(1, count);
        float last = Float.NaN;
        for (int i = 1; i <= count; i++) {
            float yaw = heading(circle.get(i), centre);
            if (!Float.isNaN(last)) {
                yaw = last + wrap(yaw - last);
            }
            s.at(start + (i - 1) * slice).turn(drone, yaw, -4.0F, -6.0F, slice, Ease.LINEAR);
            last = yaw;
        }
        s.at(start);
    }

    public static float wrap(float degrees) {
        float d = degrees % 360.0F;
        if (d >= 180.0F) {
            d -= 360.0F;
        }
        if (d < -180.0F) {
            d += 360.0F;
        }
        return d;
    }
}
