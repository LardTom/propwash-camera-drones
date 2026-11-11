package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CompassCheck;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerOwners;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CamFlights {
    public static final String SERIAL = "cam.serial";
    public static final String MARK = "cam.mark";
    private static final String FLIGHT = "cam.flight";

    private CamFlights() {
    }

    public static Script groundDrone(CamPresets.Entry entry, int dx, int dz, float yaw) {
        return Script.create()
                .server("Drohne " + entry.path() + " setzen und koppeln", (server, state) -> {
                    Pilot.spawn(server, state, entry, Arena.ground(dx, dz), yaw);
                    Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    pair(server, state);
                })
                .then("Drohne am Client", (ctx, ticks) -> clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending());
    }

    public static void pair(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = Pilot.serverDrone(server, state);
        UUID serial = UUID.randomUUID();
        PropwashApi.droneData().set(drone, CamDroneData.serial(), serial);
        PlannerOwners.claim(drone, player.getUUID());
        state.put(SERIAL, serial);
    }

    public static UUID serial(Map<String, Object> state) {
        Object serial = state.get(SERIAL);
        if (!(serial instanceof UUID uuid)) {
            throw new IllegalStateException("Drohne nicht gekoppelt");
        }
        return uuid;
    }

    public static void mission(MinecraftServer server, Map<String, Object> state, Mission mission) {
        Entity drone = Pilot.serverDrone(server, state);
        PropwashApi.droneData().set(drone, CamDroneData.mission(), MissionData.of(mission));
        PropwashApi.droneData().set(drone, CamDroneData.missionState(), MissionStatus.IDLE);
    }

    public static Optional<Entity> clientDrone(Map<String, Object> state) {
        Object id = state.get(Pilot.DRONE);
        Minecraft mc = Minecraft.getInstance();
        if (!(id instanceof UUID uuid) || mc.level == null) {
            return Optional.empty();
        }
        for (Entity entity : Arena.clientDrones(mc)) {
            if (entity.getUUID().equals(uuid)) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    public static Optional<MissionStatus> clientMissionStatus(Map<String, Object> state) {
        return clientDrone(state).map(drone -> PropwashApi.droneData().get(drone, CamDroneData.missionState()));
    }

    public static Optional<DroneView> clientView(Map<String, Object> state) {
        return clientDrone(state).flatMap(drone -> PropwashApi.drones().view(drone));
    }

    public static StepResult send(SelfTest.StepContext ctx, CustomPacketPayload payload) {
        ctx.state().put(MARK, StatusProbe.mark());
        if (!CamNetwork.sendToServer(payload)) {
            return StepResult.fail("Paket " + payload.type().id() + " lässt sich nicht senden");
        }
        return StepResult.pass();
    }

    public static int mark(SelfTest.StepContext ctx) {
        return (int) ctx.state().getOrDefault(MARK, 0);
    }

    public static StepResult awaitStatus(SelfTest.StepContext ctx, int ticks, AutonomyStatus wanted, int maxTicks) {
        int mark = mark(ctx);
        if (StatusProbe.seen(mark, wanted)) {
            return StepResult.pass("Meldung " + wanted.name());
        }
        return ticks > maxTicks ? StepResult.fail("keine Meldung " + wanted.name() + " (bekommen: " + StatusProbe.describe(mark) + ")")
                : StepResult.pending();
    }

    public static StepResult serverFlight(SelfTest.StepContext ctx, int ticks, String wantedPath) {
        Map<String, Object> state = ctx.state();
        Object pending = state.get(FLIGHT);
        if (pending == null) {
            CompletableFuture<Optional<Identifier>> future = new CompletableFuture<>();
            ctx.onServer(server -> future.complete(PropwashApi.drones().autonomy().active(Pilot.serverDrone(server, state))))
                    .whenComplete((v, t) -> {
                        if (t != null) {
                            future.completeExceptionally(t);
                        }
                    });
            state.put(FLIGHT, future);
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<Optional<Identifier>> future = (CompletableFuture<Optional<Identifier>>) pending;
        if (!future.isDone()) {
            return ticks > 200 ? StepResult.fail("Server antwortet nicht") : StepResult.pending();
        }
        state.remove(FLIGHT);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail(String.valueOf(future.exceptionNow()));
        }
        Optional<Identifier> active = future.join();
        if (active.isEmpty() || !active.get().getPath().equals(wantedPath)) {
            if (ticks < 100) {
                return StepResult.pending();
            }
            return StepResult.fail("Flug am Server " + active.map(Identifier::toString).orElse("keiner") + " statt camdrones:" + wantedPath);
        }
        return StepResult.pass("Flug ohne Pilot am Server: " + active.get());
    }

    public static void lookAt(Entity drone) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, drone.position().add(0.0, 0.2, 0.0));
        }
    }

    public static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    public static double surface() {
        return Arena.ground().getY() + 1.0;
    }

    public static Vec3 above(int dx, int dz, double height) {
        BlockPos ground = Arena.ground(dx, dz);
        return new Vec3(ground.getX() + 0.5, ground.getY() + 1.0 + height, ground.getZ() + 0.5);
    }

    public static String format(Vec3 v) {
        return String.format(Locale.ROOT, "%.1f/%.1f/%.1f", v.x, v.y, v.z);
    }

    public static void stopFlights(MinecraftServer server) {
        CompassCheck.testDisturbance(0.0);
        for (Entity drone : Arena.drones(Arena.level(server))) {
            if (PropwashApi.drones().autonomy().active(drone).isPresent()) {
                PropwashApi.drones().autonomy().stop(drone);
            }
        }
    }
}
