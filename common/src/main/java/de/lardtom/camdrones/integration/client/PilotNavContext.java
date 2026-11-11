package de.lardtom.camdrones.integration.client;

import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.integration.NavContext;
import de.lardtom.camdrones.integration.NavContexts;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.NavTarget;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.CameraPose;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class PilotNavContext implements NavContext {
    public static final PilotNavContext INSTANCE = new PilotNavContext();
    public static final String POI_REFUSED = "camdrones.nav.poi_refused";
    private static final double TICKS_PER_SECOND = 20.0;

    private volatile NavStatus status;

    private PilotNavContext() {
    }

    public static void install() {
        NavContexts.pilot(() -> INSTANCE);
    }

    public Optional<NavStatus> lastStatus() {
        return Optional.ofNullable(this.status);
    }

    private static Optional<Entity> drone() {
        return PropwashClientApi.input().drone();
    }

    private static Optional<Level> level() {
        return drone().map(Entity::level);
    }

    @Override
    public boolean active(CamControl control) {
        return PropwashClientApi.input().switchActive(control.id());
    }

    @Override
    public int maxAltitude() {
        return level().map(CamWorldRules::maxAltitude).orElse(CamWorldRules.DEFAULT_MAX_ALTITUDE);
    }

    @Override
    public int maxDistance() {
        return level().map(CamWorldRules::maxDistance).orElse(CamWorldRules.DEFAULT_MAX_DISTANCE);
    }

    @Override
    public Optional<Mission> mission() {
        if (!CamDroneData.registered()) {
            return Optional.empty();
        }
        return drone().map(drone -> PropwashApi.droneData().get(drone, CamDroneData.mission())).flatMap(MissionData::mission);
    }

    @Override
    public Optional<NavTarget> followTarget() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return Optional.empty();
        }
        Vec3 motion = player.getDeltaMovement();
        return Optional.of(new NavTarget(player.getX(), player.getY(), player.getZ(), motion.x * TICKS_PER_SECOND,
                motion.y * TICKS_PER_SECOND, motion.z * TICKS_PER_SECOND));
    }

    @Override
    public Optional<NavTarget> pointOfInterest(double x, double y, double z, double dirX, double dirY, double dirZ, double range) {
        Optional<Entity> drone = drone();
        if (drone.isEmpty()) {
            return Optional.empty();
        }
        Optional<NavTarget> point = aim(drone.get(), x, y, z, dirX, dirY, dirZ, range);
        if (point.isEmpty()) {
            refused(range);
        }
        return point;
    }

    @Override
    public double probe(double x, double y, double z, double dirX, double dirZ, double range) {
        Optional<Entity> drone = drone();
        if (drone.isEmpty() || !(range > 0.0)) {
            return Double.NaN;
        }
        Entity entity = drone.get();
        double distance = PropwashApi.drones().obstacleDistance(entity.level(), new Vec3(x, y, z), new Vec3(dirX, 0.0, dirZ), range, false, entity);
        return distance >= range ? Double.POSITIVE_INFINITY : distance;
    }

    private static Component refusedText(double range) {
        return Component.translatable(POI_REFUSED, Math.round(range)).withStyle(ChatFormatting.YELLOW);
    }

    private static void refused(double range) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            LocalPlayer player = minecraft.player;
            if (player != null) {
                player.sendOverlayMessage(refusedText(range));
            }
        });
    }

    private static Optional<NavTarget> aim(Entity entity, double x, double y, double z, double dirX, double dirY, double dirZ, double range) {
        Optional<CameraPose> pose = PropwashApi.drones().cameraPose(entity);
        Vec3 from = pose.map(CameraPose::eye).orElseGet(() -> new Vec3(x, y, z));
        Vec3 look = pose.map(CameraPose::look).orElseGet(() -> new Vec3(dirX, dirY, dirZ));
        if (look.lengthSqr() < 1.0E-6) {
            return Optional.empty();
        }
        Vec3 to = from.add(look.normalize().scale(range));
        BlockHitResult hit = entity.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        Vec3 point = hit.getLocation();
        return Optional.of(NavTarget.at(point.x, point.y, point.z));
    }

    @Override
    public void status(NavStatus status) {
        this.status = status;
    }
}
