package de.lardtom.camdrones.dev.server;

import com.mojang.authlib.GameProfile;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerOwners;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DronePreset;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

final class ServerSetup {
    static final int ARENA_OFFSET = 40;
    static final double AIRBORNE_CLEARANCE = 0.2;
    static final List<GuestMessage> GUEST_MESSAGES = new CopyOnWriteArrayList<>();

    record GuestMessage(Component text, boolean overlay) {
    }

    private ServerSetup() {
    }

    static @Nullable ServerPlayer client(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.getUUID().equals(JoinEnv.GUEST_ID)) {
                return player;
            }
        }
        return null;
    }

    static BlockPos ground(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
    }

    static BlockPos arena(ServerLevel level) {
        BlockPos spawn = level.getRespawnData().pos();
        return ground(level, spawn.getX() + ARENA_OFFSET, spawn.getZ() + ARENA_OFFSET);
    }

    static void teleport(ServerPlayer player, BlockPos ground, float yaw) {
        Vec3 stand = Vec3.atBottomCenterOf(ground.above());
        player.teleportTo(player.level(), stand.x, stand.y, stand.z, Set.<Relative>of(), yaw, 10.0F, true);
    }

    static DronePreset preset(CamPresets.Entry entry) {
        for (DronePreset preset : PropwashApi.builds().presets()) {
            if (preset.id().equals(entry.id())) {
                return preset;
            }
        }
        throw new IllegalStateException("Preset " + entry.id() + " fehlt");
    }

    static Entity place(ServerPlayer player, ItemStack stack, BlockPos ground) {
        ServerLevel level = player.level();
        ItemStack previous = player.getItemInHand(InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Set<UUID> before = new HashSet<>();
        AABB box = new AABB(ground).inflate(4.0);
        for (Entity existing : level.getEntities((Entity) null, box, PropwashApi.drones()::isDrone)) {
            before.add(existing.getUUID());
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        player.setItemInHand(InteractionHand.MAIN_HAND, previous);
        for (Entity candidate : level.getEntities((Entity) null, box, PropwashApi.drones()::isDrone)) {
            if (!before.contains(candidate.getUUID())) {
                return candidate;
            }
        }
        throw new IllegalStateException("Drohne nicht gesetzt: " + result);
    }

    static Entity spawn(ServerPlayer player, CamPresets.Entry entry, BlockPos ground) {
        return place(player, PropwashApi.builds().createDrone(preset(entry).data()), ground);
    }

    static UUID pair(Entity drone, UUID owner) {
        UUID serial = UUID.randomUUID();
        PropwashApi.droneData().set(drone, CamDroneData.serial(), serial);
        PlannerOwners.claim(drone, owner);
        return serial;
    }

    static void mission(Entity drone, Mission mission) {
        PropwashApi.droneData().set(drone, CamDroneData.mission(), MissionData.of(mission));
        PropwashApi.droneData().set(drone, CamDroneData.missionState(), MissionStatus.IDLE);
    }

    static boolean airborne(Entity drone) {
        return drone.level().noCollision(drone, drone.getBoundingBox().move(0.0, -AIRBORNE_CLEARANCE, 0.0));
    }

    static double height(Entity drone) {
        BlockPos ground = ground((ServerLevel) drone.level(), drone.getBlockX(), drone.getBlockZ());
        return drone.getY() - (ground.getY() + 1.0);
    }

    static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    static void command(ServerPlayer player, String command) {
        String line = command.startsWith("/") ? command.substring(1) : command;
        player.level().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), line);
    }

    static ServerPlayer guest(MinecraftServer server, ServerLevel level, Vec3 position) {
        GameProfile profile = new GameProfile(JoinEnv.GUEST_ID, JoinEnv.GUEST_NAME);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer guest = new ServerPlayer(server, level, profile, ClientInformation.createDefault()) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }

            @Override
            public void sendSystemMessage(Component message, boolean overlay) {
                GUEST_MESSAGES.add(new GuestMessage(message, overlay));
                super.sendSystemMessage(message, overlay);
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, guest, cookie);
        guest.teleportTo(level, position.x, position.y, position.z, Set.<Relative>of(), 0.0F, 0.0F, true);
        guest.snapTo(position.x, position.y, position.z, 0.0F, 0.0F);
        ServerTest.line("simulierter Spieler " + JoinEnv.GUEST_NAME + " am Server angemeldet bei " + format(position));
        return guest;
    }

    static void leave(MinecraftServer server, @Nullable ServerPlayer guest) {
        if (guest != null && server.getPlayerList().getPlayer(guest.getUUID()) == guest) {
            server.getPlayerList().remove(guest);
            ServerTest.line("simulierter Spieler " + JoinEnv.GUEST_NAME + " abgemeldet");
        }
    }

    static void move(ServerPlayer player, double dx, double dz) {
        player.snapTo(player.getX() + dx, player.getY(), player.getZ() + dz, player.getYRot(), player.getXRot());
        player.setKnownMovement(new Vec3(dx, 0.0, dz));
    }

    static String format(Vec3 v) {
        return String.format(Locale.ROOT, "%.1f/%.1f/%.1f", v.x, v.y, v.z);
    }

    static String format(double v) {
        return String.format(Locale.GERMANY, "%.2f", v);
    }
}
