package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.client.PilotNavContext;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Pilot {
    public static final String REMOTE = "propwash:remote";
    public static final String GOGGLES = "propwash:goggles_creative";
    public static final String DRONE = "pilot.drone";
    public static final String ARM = "key.propwash.drone_arm";
    public static final String PITCH_FORWARD = "key.propwash.kb_pitch_forward";
    public static final double THROTTLE_CENTER = 0.5;
    private static final double THROTTLE_SLACK = 0.03;
    private static final List<KeyMapping> HELD = new ArrayList<>();
    private static final String COMPASS_PHASE = "pilot.compassPhase";
    private static final String COMPASS_SINCE = "pilot.compassSince";
    private static final String COMPASS_ATTEMPT = "pilot.compassAttempt";
    private static final int COMPASS_ATTEMPTS = 4;
    private static final int RESTART_RETRY_TICKS = 60;

    private Pilot() {
    }

    public static DronePreset preset(CamPresets.Entry entry) {
        for (DronePreset preset : PropwashApi.builds().presets()) {
            if (preset.id().equals(entry.id())) {
                return preset;
            }
        }
        throw new IllegalStateException("Preset " + entry.id() + " ist bei Propwash nicht angemeldet");
    }

    public static void spawn(MinecraftServer server, Map<String, Object> state, CamPresets.Entry entry, BlockPos ground, float yaw) {
        spawn(server, state, preset(entry).data(), entry.path(), ground, yaw);
    }

    public static void spawn(MinecraftServer server, Map<String, Object> state, DroneData data, String label, BlockPos ground, float yaw) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        Vec3 stand = Vec3.atBottomCenterOf(ground.above()).add(0.0, 0.0, -3.0);
        player.teleportTo(level, stand.x, stand.y, stand.z, Set.<Relative>of(), yaw, 10.0F, true);
        player.setItemSlot(EquipmentSlot.HEAD, Arena.stack(GOGGLES));
        ItemStack drone = PropwashApi.builds().createDrone(data);
        player.setItemInHand(InteractionHand.MAIN_HAND, drone);
        Vec3 top = Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0);
        BlockHitResult hit = new BlockHitResult(top, Direction.UP, ground, false);
        Set<UUID> before = new HashSet<>();
        for (Entity existing : Arena.drones(level)) {
            before.add(existing.getUUID());
        }
        InteractionResult placed = drone.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        Entity spawned = null;
        for (Entity candidate : Arena.drones(level)) {
            if (!before.contains(candidate.getUUID())) {
                spawned = candidate;
            }
        }
        if (spawned == null) {
            throw new IllegalStateException("Drohne " + label + " nicht gesetzt: " + placed);
        }
        state.put(DRONE, spawned.getUUID());
        player.setItemInHand(InteractionHand.MAIN_HAND, Arena.stack(REMOTE));
    }

    public static Entity serverDrone(MinecraftServer server, Map<String, Object> state) {
        Object id = state.get(DRONE);
        Entity drone = id instanceof UUID uuid ? Arena.level(server).getEntity(uuid) : null;
        if (drone == null) {
            throw new IllegalStateException("Drohne fehlt am Server");
        }
        return drone;
    }

    public static void bind(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = serverDrone(server, state);
        if (!PropwashApi.drones().isDrone(drone)) {
            throw new IllegalStateException("keine Drohne: " + drone);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, Arena.stack(REMOTE));
        player.interactOn(drone, InteractionHand.MAIN_HAND, drone.position());
    }

    public static void start(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = serverDrone(server, state);
        ItemStack remote = player.getItemInHand(InteractionHand.MAIN_HAND);
        remote.use(player.level(), player, InteractionHand.MAIN_HAND);
        Optional<UUID> pilot = PropwashApi.drones().view(drone).flatMap(DroneView::pilot);
        if (pilot.isEmpty() || !pilot.get().equals(player.getUUID())) {
            throw new IllegalStateException("Flug nicht gestartet (Pilot " + pilot.map(UUID::toString).orElse("keiner") + ")");
        }
    }

    public static void release(MinecraftServer server) {
        ServerPlayer player = Arena.player(server);
        for (Entity drone : Arena.drones(player.level())) {
            Optional<UUID> pilot = PropwashApi.drones().view(drone).flatMap(DroneView::pilot);
            if (pilot.isPresent() && pilot.get().equals(player.getUUID())) {
                player.setItemInHand(InteractionHand.MAIN_HAND, Arena.stack(REMOTE));
                player.getItemInHand(InteractionHand.MAIN_HAND).use(player.level(), player, InteractionHand.MAIN_HAND);
            }
        }
    }

    public static Script launch(CamPresets.Entry entry, int dx, int dz, float yaw) {
        return launch(Script.create().server("Drohne " + entry.path() + " setzen",
                (server, state) -> spawn(server, state, entry, Arena.ground(dx, dz), yaw)));
    }

    public static Script launch(String label, DroneData data, int dx, int dz, float yaw) {
        return launch(Script.create().server("Drohne " + label + " setzen",
                (server, state) -> spawn(server, state, data, label, Arena.ground(dx, dz), yaw)));
    }

    private static Script launch(Script spawned) {
        return spawned
                .sleep(12)
                .server("Funke binden", Pilot::bind)
                .sleep(14)
                .server("Flug starten", Pilot::start)
                .await("FPV-Ansicht am Client", 200, () -> PropwashClientApi.input().flying() && PropwashClientApi.input().drone().isPresent())
                .sleep(20)
                .then("scharf schalten", Pilot::arm)
                .then("Kompass prüfen", Pilot::compass);
    }

    public static StepResult compass(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        int phase = (int) state.getOrDefault(COMPASS_PHASE, 0);
        int since = (int) state.getOrDefault(COMPASS_SINCE, 0);
        int attempt = (int) state.getOrDefault(COMPASS_ATTEMPT, 0);
        int waited = ticks - since;
        switch (phase) {
            case 0 -> {
                if (waited < 20) {
                    return StepResult.pending();
                }
                boolean disturbed = status().map(s -> s.warns(NavOutput.Warning.COMPASS)).orElse(false);
                if (!disturbed) {
                    return StepResult.pass(attempt == 0 ? "" : "Kompass nach " + attempt + " Neustart(s) in Ordnung");
                }
                if (attempt >= COMPASS_ATTEMPTS) {
                    return StepResult.fail("Kompass bleibt nach " + attempt + " Neustarts gestört");
                }
                ctx.log("Kompass laut Propwash in diesem Flug gestört (Zufall, etwa 10 % der Flüge), Flug neu starten (" + (attempt + 1) + ")");
                ctx.onServer(Pilot::release);
                state.put(COMPASS_ATTEMPT, attempt + 1);
                state.put(COMPASS_PHASE, 1);
                state.put(COMPASS_SINCE, ticks);
            }
            case 1 -> {
                if (PropwashClientApi.input().flying()) {
                    return waited > 200 ? StepResult.fail("Flug endet für den Neustart nicht") : StepResult.pending();
                }
                if (waited < 10) {
                    return StepResult.pending();
                }
                restart(ctx, state);
                state.put(COMPASS_PHASE, 2);
                state.put(COMPASS_SINCE, ticks);
            }
            case 2 -> {
                if (!PropwashClientApi.input().flying() || PropwashClientApi.input().drone().isEmpty()) {
                    if (waited % RESTART_RETRY_TICKS == RESTART_RETRY_TICKS - 1 && waited < 3 * RESTART_RETRY_TICKS) {
                        restart(ctx, state);
                    }
                    return waited > 4 * RESTART_RETRY_TICKS ? StepResult.fail("Flug startet nach dem Kompassfehler nicht neu") : StepResult.pending();
                }
                if (!armed()) {
                    if (waited % 40 == 20) {
                        click(ARM);
                    }
                    return waited > 300 ? StepResult.fail("Drohne lässt sich nach dem Neustart nicht scharf schalten") : StepResult.pending();
                }
                state.put(COMPASS_PHASE, 0);
                state.put(COMPASS_SINCE, ticks);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
        return StepResult.pending();
    }

    private static void restart(SelfTest.StepContext ctx, Map<String, Object> state) {
        ctx.onServer(server -> {
            try {
                bind(server, state);
                start(server, state);
            } catch (IllegalStateException e) {
                ctx.log("Neustart des Flugs: " + e.getMessage() + ", nächster Versuch");
            }
        });
    }

    public static Optional<Entity> drone() {
        return PropwashClientApi.input().drone();
    }

    public static Optional<NavStatus> status() {
        return PilotNavContext.INSTANCE.lastStatus();
    }

    public static boolean armed() {
        return drone().flatMap(d -> PropwashApi.drones().view(d)).map(DroneView::armed).orElse(false);
    }

    public static StepResult arm(SelfTest.StepContext ctx, int ticks) {
        if (armed()) {
            return StepResult.pass();
        }
        if (ticks % 40 == 0) {
            click(ARM);
        }
        return ticks > 200 ? StepResult.fail("Drohne lässt sich nicht scharf schalten") : StepResult.pending();
    }

    public static boolean steer(double target) {
        double throttle = PropwashClientApi.input().throttle();
        Minecraft mc = Minecraft.getInstance();
        if (throttle < target - THROTTLE_SLACK) {
            hold(mc.options.keyDown, false);
            hold(mc.options.keyUp, true);
            return false;
        }
        if (throttle > target + THROTTLE_SLACK) {
            hold(mc.options.keyUp, false);
            hold(mc.options.keyDown, true);
            return false;
        }
        hold(mc.options.keyUp, false);
        hold(mc.options.keyDown, false);
        return true;
    }

    public static StepResult throttleTo(SelfTest.StepContext ctx, int ticks, double target) {
        if (steer(target)) {
            return StepResult.pass();
        }
        return ticks > 200 ? StepResult.fail("Gas bleibt bei " + String.format(Locale.ROOT, "%.2f", PropwashClientApi.input().throttle()))
                : StepResult.pending();
    }

    public static void hold(KeyMapping key, boolean down) {
        key.setDown(down);
        if (down && !HELD.contains(key)) {
            HELD.add(key);
        } else if (!down) {
            HELD.remove(key);
        }
    }

    public static void hold(String name, boolean down) {
        KeyMapping key = KeyMapping.get(name);
        if (key == null) {
            throw new IllegalStateException("Taste " + name + " fehlt");
        }
        hold(key, down);
    }

    public static void hold(CamControl control, boolean down) {
        hold(CamKeys.key(control).orElseThrow(() -> new IllegalStateException("Taste für " + control + " fehlt")), down);
    }

    public static void click(String name) {
        KeyMapping key = KeyMapping.get(name);
        if (key == null) {
            throw new IllegalStateException("Taste " + name + " fehlt");
        }
        KeyMapping.click(InputConstants.getKey(key.saveString()));
    }

    public static void releaseKeys() {
        for (KeyMapping key : List.copyOf(HELD)) {
            key.setDown(false);
        }
        HELD.clear();
    }
}
