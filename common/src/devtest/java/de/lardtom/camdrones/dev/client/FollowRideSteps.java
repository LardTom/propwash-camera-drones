package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.net.SmartMode;
import de.lardtom.camdrones.net.StatusMessage;
import de.lardtom.camdrones.part.CamPresets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FollowRideSteps {
    public static final String BOAT = "follow: Spieler im Boot, Abstand gehalten";
    public static final String HORSE = "follow: Spieler auf dem Pferd, Abstand gehalten";
    public static final double DISTANCE_TOLERANCE = 2.0;
    public static final double REST_TOLERANCE = 1.5;
    public static final double MIN_SPEED = 2.0;
    public static final double HORSE_SPEED = 0.1;
    public static final int RIDE_TICKS = 20 * 16;
    public static final int TURN_TICKS = 20 * 9;
    public static final int CHANNEL_LENGTH = 90;
    private static final int BOAT_DX = -120;
    private static final int BOAT_DZ = 60;
    private static final int HORSE_DX = -20;
    private static final int HORSE_DZ = -60;
    private static final String VEHICLE = "ride.vehicle";
    private static final String CALM = "ride.calm";
    private static final String LAST = "ride.last";
    private static final String TRACK = "ride.track";
    private static final String STATS = "ride.stats";
    private static final String MOUNT_MARK = "ride.mountMark";
    private static final int STEP_TICKS = 20 * 180;

    private FollowRideSteps() {
    }

    public static void register() {
        SelfTest.clientStep(31, BOAT, STEP_TICKS, script(true), FollowRideSteps::cleanup);
        SelfTest.clientStep(32, HORSE, STEP_TICKS, script(false), FollowRideSteps::cleanup);
    }

    private static Script script(boolean boat) {
        int dx = boat ? BOAT_DX : HORSE_DX;
        int dz = boat ? BOAT_DZ : HORSE_DZ;
        return Script.create()
                .append(CamFlights.groundDrone(CamPresets.TOUR, dx + 4, dz - 6, 0.0F))
                .server(boat ? "Kanal und Boot" : "gezähmtes Pferd mit Sattel", (server, state) -> vehicle(server, state, boat, dx, dz))
                .then("Follow me auf den Spieler", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) {
                        return StepResult.fail("kein Spieler");
                    }
                    return CamFlights.send(ctx, new SmartMode.Request(CamFlights.serial(ctx.state()), SmartState.follow(mc.player.getUUID(),
                            FollowController.Style.BEHIND)));
                })
                .then("Meldung Follow me gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.FOLLOW_STARTED, 100))
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "follow"))
                .then("abheben und zum Spieler", FollowRideSteps::settle)
                .then(boat ? "ins Boot steigen" : "aufs Pferd steigen", (ctx, ticks) -> {
                    ctx.state().put(MOUNT_MARK, StatusProbe.mark());
                    return Script.server(ctx, ticks, FollowRideSteps::mount);
                })
                .then("sitzt auf", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null && mc.player.getVehicle() != null) {
                        mc.player.setYRot(0.0F);
                        return ticks >= 20 ? StepResult.pass("Spieler sitzt in " + mc.player.getVehicle().getType().getDescription().getString())
                                : StepResult.pending();
                    }
                    return ticks > 100 ? StepResult.fail("Spieler sitzt nicht auf") : StepResult.pending();
                })
                .then("Drohne bleibt beim Aufsitzen am Ziel", FollowRideSteps::settle)
                .then(boat ? "Boot fährt den Kanal entlang" : "Pferd läuft eine Bahn mit Kurve", (ctx, ticks) -> ride(ctx, ticks, boat))
                .then("bleibt stehen", FollowRideSteps::rest)
                .then("kein Zielverlust", (ctx, ticks) -> {
                    Optional<StatusMessage.Payload> lost = StatusProbe.since((int) ctx.state().get(MOUNT_MARK)).stream()
                            .filter(p -> p.status() == AutonomyStatus.FOLLOW_TARGET_LOST || p.status().name().startsWith("ENDED_")).findFirst();
                    if (lost.isPresent()) {
                        return StepResult.fail("Follow me endet beim Reiten: " + lost.get().status());
                    }
                    return CamFlights.serverFlight(ctx, ticks, "follow");
                });
    }

    private static void vehicle(MinecraftServer server, Map<String, Object> state, boolean boat, int dx, int dz) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        BlockPos start = Arena.ground(dx, dz);
        Entity vehicle;
        if (boat) {
            Arena.fill(level, Arena.ground(dx - 2, dz - 3), Arena.ground(dx + 2, dz + CHANNEL_LENGTH), Blocks.WATER.defaultBlockState());
            Boat created = EntityTypes.OAK_BOAT.create(level, EntitySpawnReason.COMMAND);
            if (created == null) {
                throw new IllegalStateException("Boot nicht erzeugt");
            }
            created.snapTo(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5, 0.0F, 0.0F);
            vehicle = created;
        } else {
            Horse created = EntityTypes.HORSE.create(level, EntitySpawnReason.COMMAND);
            if (created == null) {
                throw new IllegalStateException("Pferd nicht erzeugt");
            }
            created.snapTo(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5, 0.0F, 0.0F);
            created.setTamed(true);
            created.setOwner(player);
            created.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
            created.setPersistenceRequired();
            AttributeInstance speed = created.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(HORSE_SPEED);
            }
            vehicle = created;
        }
        if (!level.addFreshEntity(vehicle)) {
            throw new IllegalStateException("Fahrzeug nicht in die Welt gesetzt");
        }
        state.put(VEHICLE, vehicle.getUUID());
        player.teleportTo(level, start.getX() + 4.5, start.getY() + 1.0, start.getZ() + 0.5, Set.<Relative>of(), 0.0F, 10.0F, true);
    }

    private static void mount(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity vehicle = player.level().getEntity((UUID) state.get(VEHICLE));
        if (vehicle == null) {
            throw new IllegalStateException("Fahrzeug fehlt am Server");
        }
        if (!player.startRiding(vehicle)) {
            throw new IllegalStateException("Spieler kann nicht aufsitzen");
        }
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        Pilot.hold(Minecraft.getInstance().options.keyUp, false);
        return Script.server(ctx, ctx.tick(), (server, state) -> {
            ServerPlayer player = Arena.player(server);
            player.stopRiding();
            for (Entity entity : player.level().getEntities((Entity) null, player.getBoundingBox().inflate(Arena.RADIUS),
                    e -> e instanceof Boat || e instanceof Horse)) {
                entity.discard();
            }
        });
    }

    private static Optional<double[]> relative(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        Optional<Entity> drone = CamFlights.clientDrone(state);
        if (drone.isEmpty() || mc.player == null) {
            return Optional.empty();
        }
        Vec3 d = drone.get().position();
        Vec3 p = mc.player.position();
        @SuppressWarnings("unchecked")
        Deque<Vec3> track = (Deque<Vec3>) state.computeIfAbsent(TRACK, key -> new ArrayDeque<Vec3>());
        track.addLast(p);
        while (track.size() > 11) {
            track.removeFirst();
        }
        Vec3 then = track.peekFirst();
        double mx = p.x - then.x;
        double mz = p.z - then.z;
        double moved = Math.hypot(mx, mz);
        double playerSpeed = moved * 20.0 / Math.max(1, track.size() - 1);
        double dx = d.x - p.x;
        double dz = d.z - p.z;
        double along = moved > 1.0E-3 ? (dx * mx + dz * mz) / moved : Double.NaN;
        double speed = state.get(LAST) instanceof Vec3 last ? CamFlights.horizontal(d, last) * 20.0 : 0.0;
        state.put(LAST, d);
        return Optional.of(new double[]{Math.hypot(dx, dz), along, d.y - CamFlights.surface(), speed, playerSpeed});
    }

    private static StepResult settle(SelfTest.StepContext ctx, int ticks) {
        Optional<double[]> rel = relative(ctx);
        if (rel.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        double[] r = rel.get();
        boolean calm = r[2] > FollowController.HEIGHT - 1.5 && Math.abs(r[0] - FollowController.DISTANCE) < 2.0 && r[3] < 0.5;
        int calmTicks = calm ? (int) ctx.state().getOrDefault(CALM, 0) + 1 : 0;
        ctx.state().put(CALM, calmTicks);
        if (calmTicks >= 20) {
            ctx.state().remove(CALM);
            return StepResult.pass("steht " + Flight.format(r[0]) + " m vom Spieler in " + Flight.format(r[2]) + " m Höhe");
        }
        return ticks > 20 * 40 ? StepResult.fail("kommt nicht zum Spieler: Abstand " + Flight.format(r[0]) + " m, Höhe " + Flight.format(r[2])
                + " m, Tempo " + Flight.format(r[3]) + " m/s") : StepResult.pending();
    }

    private static StepResult ride(SelfTest.StepContext ctx, int ticks, boolean boat) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getVehicle() == null) {
            return StepResult.fail("Spieler sitzt nicht mehr auf");
        }
        boolean driving = ticks < RIDE_TICKS;
        Pilot.hold(mc.options.keyUp, driving && (!boat || ticks % 2 == 0));
        if (!boat && ticks == TURN_TICKS) {
            mc.player.setYRot(mc.player.getYRot() + 90.0F);
        }
        Optional<double[]> rel = relative(ctx);
        if (rel.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        double[] r = rel.get();
        double[] stats = (double[]) ctx.state().computeIfAbsent(STATS, key -> new double[]{0.0, 0.0, Double.MAX_VALUE, 0.0, 0.0, 0.0, 0.0});
        boolean counted = ticks >= 60 && ticks < RIDE_TICKS && (boat || ticks < TURN_TICKS || ticks >= TURN_TICKS + 80) && r[4] >= MIN_SPEED
                && Double.isFinite(r[1]);
        if (counted) {
            stats[0] += 1.0;
            stats[1] += r[0];
            stats[2] = Math.min(stats[2], r[0]);
            stats[3] = Math.max(stats[3], r[0]);
            if (r[1] < 0.0) {
                stats[4] += 1.0;
            }
            stats[5] += r[4];
        }
        if (driving) {
            return StepResult.pending();
        }
        Pilot.hold(mc.options.keyUp, false);
        int samples = (int) stats[0];
        if (samples < 100) {
            return StepResult.fail((boat ? "Boot" : "Pferd") + " kommt kaum voran: " + samples + " Messungen über " + Flight.format(MIN_SPEED) + " m/s");
        }
        double mean = stats[1] / samples;
        String measured = (boat ? "im Boot" : "auf dem Pferd") + " mit " + Flight.format(stats[5] / samples) + " m/s: Abstand im Mittel "
                + Flight.format(mean) + " m (" + Flight.format(stats[2]) + " bis " + Flight.format(stats[3]) + " m), hinter dem Spieler in "
                + (int) stats[4] + " von " + samples + " Messungen";
        if (Math.abs(mean - FollowController.DISTANCE) > DISTANCE_TOLERANCE || stats[2] < FollowController.DISTANCE - 2.0 * DISTANCE_TOLERANCE
                || stats[3] > FollowController.DISTANCE + 2.0 * DISTANCE_TOLERANCE) {
            return StepResult.fail(measured + ", erwartet " + Flight.format(FollowController.DISTANCE) + " ± " + Flight.format(DISTANCE_TOLERANCE) + " m");
        }
        if (stats[4] < samples * 0.9) {
            return StepResult.fail(measured + ": Drohne nicht hinter dem Spieler");
        }
        return StepResult.pass(measured);
    }

    private static StepResult rest(SelfTest.StepContext ctx, int ticks) {
        Optional<double[]> rel = relative(ctx);
        if (rel.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        double[] r = rel.get();
        boolean calm = Math.abs(r[0] - FollowController.DISTANCE) < REST_TOLERANCE && r[3] < 0.3 && r[4] < 0.3;
        int calmTicks = calm ? (int) ctx.state().getOrDefault(CALM, 0) + 1 : 0;
        ctx.state().put(CALM, calmTicks);
        if (calmTicks >= 20) {
            CamFlights.clientDrone(ctx.state()).ifPresent(CamFlights::lookAt);
            return StepResult.pass("steht nach " + Flight.format(ticks / 20.0) + " s " + Flight.format(r[0]) + " m vom Spieler");
        }
        return ticks > 20 * 20 ? StepResult.fail("kommt nicht zur Ruhe: Abstand " + Flight.format(r[0]) + " m, Tempo " + Flight.format(r[3]) + " m/s")
                : StepResult.pending();
    }
}
