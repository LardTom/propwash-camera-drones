package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.NavAssist;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.TabletState;
import de.lardtom.camdrones.planner.model.WaypointPlacement;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class PlannerTablets {
    public static final double DRONE_BOX_INFLATE = 0.3;
    public static final SoundEvent WAYPOINT_SOUND = SoundEvent.createVariableRangeEvent(CamDrones.id("planner.waypoint"));
    public static final SoundEvent CLICK_SOUND = SoundEvent.createVariableRangeEvent(CamDrones.id("planner.click"));
    public static final float SOUND_VOLUME = 0.6F;

    private PlannerTablets() {
    }

    public static TabletState read(ItemStack stack) {
        if (!PlannerItems.isPlanner(stack)) {
            return TabletState.EMPTY;
        }
        PlannerMissions missions = stack.getOrDefault(PlannerItems.missions(), PlannerMissions.EMPTY);
        PlannerSettings settings = stack.getOrDefault(PlannerItems.settings(), PlannerSettings.DEFAULT);
        return new TabletState(missions.entries(), settings.active(), settings.groundHeight());
    }

    public static void write(ItemStack stack, TabletState state) {
        stack.set(PlannerItems.missions(), PlannerMissions.of(state));
        stack.set(PlannerItems.settings(), PlannerSettings.of(state));
    }

    public static Optional<PlannerLink> link(ItemStack stack) {
        return PlannerItems.isPlanner(stack) ? Optional.ofNullable(stack.get(PlannerItems.link())) : Optional.empty();
    }

    public static Optional<Entity> aimedDrone(Player player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double range = player.entityInteractionRange() + 1.0;
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double limit = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Entity best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (DroneView view : PropwashApi.drones().dronesNear(level, eye, range + 2.0, false)) {
            AABB box = view.entity().getBoundingBox().inflate(DRONE_BOX_INFLATE);
            Optional<Vec3> hit = box.contains(eye) ? Optional.of(eye) : box.clip(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = hit.get().distanceTo(eye);
            if (distance <= limit && distance < bestDistance) {
                bestDistance = distance;
                best = view.entity();
            }
        }
        return Optional.ofNullable(best);
    }

    public static OptionalInt aimedMarker(Player player, ItemStack stack) {
        Mission mission = read(stack).activeMission();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        return WaypointPlacement.pick(mission.waypoints(), eye.x, eye.y, eye.z, look.x, look.y, look.z, WaypointPlacement.MARKER_REACH,
                WaypointPlacement.MARKER_RADIUS);
    }

    public static void addBlockWaypoint(ServerPlayer player, ItemStack stack, int blockX, double topY, int blockZ) {
        TabletState state = read(stack);
        Waypoint waypoint = WaypointPlacement.aboveBlock(blockX, topY, blockZ, state.groundHeight());
        TabletState.PlacementResult result = state.addWaypoint(waypoint);
        switch (result.outcome()) {
            case ADDED -> {
                write(stack, result.state());
                tabletSound(player, WAYPOINT_SOUND, 1.0F);
                player.sendOverlayMessage(Component.translatable(FieldText.PREFIX + "placed.added", result.index() + 1,
                        result.state().groundHeight(), result.state().activeName()));
            }
            case FULL -> player.sendOverlayMessage(Component.translatable(FieldText.PREFIX + "placed.full").withStyle(ChatFormatting.RED));
            default -> player.sendOverlayMessage(Component.translatable(FieldText.PREFIX + "placed.locked").withStyle(ChatFormatting.RED));
        }
    }

    public static boolean deleteAimedMarker(ServerPlayer player, ItemStack stack) {
        OptionalInt marker = aimedMarker(player, stack);
        if (marker.isEmpty()) {
            return false;
        }
        TabletState.PlacementResult result = read(stack).removeWaypoint(marker.getAsInt());
        if (!result.changed()) {
            return false;
        }
        write(stack, result.state());
        tabletSound(player, WAYPOINT_SOUND, 0.7F);
        player.sendOverlayMessage(Component.translatable(FieldText.PREFIX + "placed.removed", marker.getAsInt() + 1));
        return true;
    }

    public static boolean pairAimedDrone(ServerPlayer player, ItemStack stack) {
        Optional<Entity> aimed = aimedDrone(player);
        return aimed.isPresent() && pairDrone(player, stack, aimed.get());
    }

    public static boolean pairDrone(ServerPlayer player, ItemStack stack, Entity drone) {
        Optional<DroneView> view = PropwashApi.drones().view(drone);
        if (view.isEmpty()) {
            return false;
        }
        Optional<UUID> pilot = view.get().pilot();
        if (pilot.isPresent() && !pilot.get().equals(player.getUUID())) {
            player.sendOverlayMessage(Component.translatable(FieldText.PREFIX + "pair.occupied").withStyle(ChatFormatting.RED));
            return true;
        }
        if (view.get().flying()) {
            player.sendOverlayMessage(Component.translatable(FieldText.PREFIX + "pair.flying").withStyle(ChatFormatting.RED));
            return true;
        }
        boolean operator = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        if (!operator && !PlannerOwners.claimable(drone, player.getUUID())) {
            player.sendOverlayMessage(Component.translatable(FieldText.PREFIX + "pair.not_owner").withStyle(ChatFormatting.RED));
            return true;
        }
        UUID serial = serialOf(drone);
        if (CamDroneData.NO_SERIAL.equals(serial)) {
            serial = UUID.randomUUID();
            if (CamDroneData.registered()) {
                PropwashApi.droneData().set(drone, CamDroneData.serial(), serial);
            }
        }
        PlannerOwners.claim(drone, player.getUUID());
        String name = drone.hasCustomName() && drone.getCustomName() != null ? drone.getCustomName().getString() : "";
        PlannerLink link = PlannerLink.of(serial, name, flightClass(view.get().massKg()));
        stack.set(PlannerItems.link(), link);
        tabletSound(player, CLICK_SOUND, 1.2F);
        Component shown = name.isEmpty() ? Component.literal(link.shortSerial()) : Component.literal(link.name());
        player.sendOverlayMessage(Component.translatable(FieldText.PREFIX + "pair.done", shown));
        return true;
    }

    static void tabletSound(ServerPlayer player, SoundEvent sound, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, SOUND_VOLUME, pitch);
    }

    public static UUID serialOf(Entity drone) {
        if (!CamDroneData.registered()) {
            return CamDroneData.NO_SERIAL;
        }
        UUID serial = PropwashApi.droneData().get(drone, CamDroneData.serial());
        return serial == null ? CamDroneData.NO_SERIAL : serial;
    }

    public static FlightClass flightClass(double massKg) {
        if (massKg < NavAssist.COMPACT_MAX_KG) {
            return FlightClass.COMPACT;
        }
        return massKg < NavAssist.TOUR_MAX_KG ? FlightClass.TOUR : FlightClass.CINE;
    }

    public static Optional<Entity> findDrone(ServerLevel level, UUID serial) {
        if (serial == null || CamDroneData.NO_SERIAL.equals(serial) || !CamDroneData.registered()) {
            return Optional.empty();
        }
        for (Entity entity : level.getAllEntities()) {
            if (PropwashApi.drones().isDrone(entity) && serial.equals(serialOf(entity))) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    public static boolean save(ServerPlayer player, int slot, PlannerMissions missions, PlannerSettings settings) {
        Inventory inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.getContainerSize()) {
            return false;
        }
        ItemStack stack = inventory.getItem(slot);
        if (!PlannerItems.isPlanner(stack)) {
            return false;
        }
        TabletState state = new TabletState(missions.entries(), settings.active(), settings.groundHeight());
        write(stack, state);
        return true;
    }
}
