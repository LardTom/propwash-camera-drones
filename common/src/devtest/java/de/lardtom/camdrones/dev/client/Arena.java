package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CompassCheck;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.worldrule.WorldRule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Arena {
    public static final double RADIUS = 160.0;
    public static final Identifier WIND = Identifier.parse("propwash:wind");
    public static final int CALM_WIND = 1;
    public static final int STRONG_WIND = 3;
    private static final String TAG = "[Kamera-Selbsttest]";
    private static final String STAGE = "arena.stage";
    private static final String SINCE = "arena.since";
    private static final int RELEASE_RETRY_TICKS = 30;
    private static final int RELEASE_TICKS = 200;
    private static final Map<BlockPos, BlockState> PLACED = new LinkedHashMap<>();
    private static final Map<Identifier, String> RULES = new LinkedHashMap<>();
    private static Vec3 spawn = Vec3.ZERO;
    private static BlockPos ground = BlockPos.ZERO;

    private Arena() {
    }

    static void remember(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        spawn = mc.player.position();
        ground = BlockPos.containing(spawn.x, spawn.y - 1.0, spawn.z);
        CamDrones.LOG.info("{} Arena bei {} (Boden {})", TAG, spawn, ground.toShortString());
    }

    public static BlockPos ground() {
        return ground;
    }

    public static BlockPos ground(int dx, int dz) {
        return ground.offset(dx, 0, dz);
    }

    public static Vec3 spawn() {
        return spawn;
    }

    public static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler am Server");
        }
        return players.getFirst();
    }

    public static ServerLevel level(MinecraftServer server) {
        return player(server).level();
    }

    public static void place(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos key = pos.immutable();
        PLACED.putIfAbsent(key, level.getBlockState(key));
        level.setBlockAndUpdate(key, state);
    }

    public static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            place(level, pos, state);
        }
    }

    public static int changedBlocks() {
        return PLACED.size();
    }

    public static ItemStack stack(String id) {
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id))
                .orElseThrow(() -> new IllegalStateException("Item " + id + " fehlt"));
        return new ItemStack(item);
    }

    public static void rule(MinecraftServer server, Identifier id, String value) {
        WorldRule<?> rule = PropwashApi.worldRules().find(id).orElseThrow(() -> new IllegalStateException("Welt-Regel " + id + " fehlt"));
        RULES.putIfAbsent(id, String.valueOf(rule.get(server.overworld())));
        command(server, "gamerule " + id + " " + value);
        Object now = rule.get(server.overworld());
        if (!value.equals(String.valueOf(now))) {
            throw new IllegalStateException("Welt-Regel " + id + " ist " + now + " statt " + value);
        }
    }

    public static void gameRule(MinecraftServer server, Identifier id, String value) {
        RULES.putIfAbsent(id, gameRule(server, id));
        command(server, "gamerule " + id + " " + value);
        String now = gameRule(server, id);
        if (!value.equals(now)) {
            throw new IllegalStateException("Spielregel " + id + " ist " + now + " statt " + value);
        }
    }

    public static String gameRule(MinecraftServer server, Identifier id) {
        GameRule<?> rule = BuiltInRegistries.GAME_RULE.getOptional(id)
                .orElseThrow(() -> new IllegalStateException("Spielregel " + id + " fehlt"));
        return String.valueOf(server.getGameRules().get(rule));
    }

    public static void calmWind(MinecraftServer server) {
        command(server, "gamerule " + WIND + " " + CALM_WIND);
        String now = gameRule(server, WIND);
        if (!String.valueOf(CALM_WIND).equals(now)) {
            throw new IllegalStateException("Wind ist " + now + " statt " + CALM_WIND);
        }
        CamDrones.LOG.info("{} Wind in der Arena: {} = {}", TAG, WIND, now);
    }

    public static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static AABB box(ServerLevel level) {
        return new AABB(spawn.x - RADIUS, level.getMinY(), spawn.z - RADIUS, spawn.x + RADIUS, level.getMaxY(), spawn.z + RADIUS);
    }

    public static List<Entity> drones(ServerLevel level) {
        return level.getEntities((Entity) null, box(level), entity -> PropwashApi.drones().isDrone(entity));
    }

    public static List<Entity> clientDrones(Minecraft mc) {
        List<Entity> out = new ArrayList<>();
        if (mc.level == null) {
            return out;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (PropwashApi.drones().isDrone(entity)) {
                out.add(entity);
            }
        }
        return out;
    }

    public static StepResult reset(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int stage = (int) state.getOrDefault(STAGE, 0);
        int since = (int) state.computeIfAbsent(SINCE, key -> ctx.tick());
        int ticks = ctx.tick() - since;
        switch (stage) {
            case 0 -> {
                Pilot.releaseKeys();
                KeyMapping.releaseAll();
                CompassCheck.testDisturbance(0.0);
                PropwashClientApi.recording().stop();
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                Shots.restoreWindow(mc);
                Shots.restoreLanguage(mc);
                next(ctx, 1);
            }
            case 1 -> {
                if (!PropwashClientApi.input().flying()) {
                    next(ctx, 2);
                    return StepResult.pending();
                }
                if (ticks >= RELEASE_TICKS) {
                    return StepResult.fail("Flug endet nicht");
                }
                if (ticks % RELEASE_RETRY_TICKS == 0) {
                    ctx.onServer(Pilot::release);
                }
            }
            case 2 -> {
                StepResult cleared = Script.server(ctx, ticks, (server, s) -> clearServer(server));
                if (!cleared.done() || cleared.failed()) {
                    return cleared;
                }
                next(ctx, 3);
            }
            case 3 -> {
                if (ticks < 5) {
                    return StepResult.pending();
                }
                List<Entity> left = clientDrones(mc);
                if (!left.isEmpty()) {
                    return ticks < 100 ? StepResult.pending() : StepResult.fail(left.size() + " Drohnen bleiben am Client");
                }
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + stage);
            }
        }
        return StepResult.pending();
    }

    private static void next(SelfTest.StepContext ctx, int stage) {
        ctx.state().put(STAGE, stage);
        ctx.state().put(SINCE, ctx.tick() + 1);
    }

    private static void clearServer(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        CamFlights.stopFlights(server);
        for (Entity drone : drones(level)) {
            drone.discard();
        }
        player.closeContainer();
        List<Map.Entry<BlockPos, BlockState>> placed = new ArrayList<>(PLACED.entrySet());
        for (int i = placed.size() - 1; i >= 0; i--) {
            level.setBlockAndUpdate(placed.get(i).getKey(), placed.get(i).getValue());
        }
        PLACED.clear();
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box(level))) {
            item.discard();
        }
        for (Map.Entry<Identifier, String> rule : RULES.entrySet()) {
            command(server, "gamerule " + rule.getKey() + " " + rule.getValue());
        }
        RULES.clear();
        player.getInventory().clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.setGameMode(GameType.CREATIVE);
        player.setHealth(player.getMaxHealth());
        player.teleportTo(level, spawn.x, spawn.y, spawn.z, Set.<Relative>of(), 0.0F, 0.0F, true);
        player.setDeltaMovement(Vec3.ZERO);
    }
}
