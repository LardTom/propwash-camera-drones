package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.GimbalScenes;
import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.GimbalResolver;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.net.SmartMode;
import de.lardtom.camdrones.net.StatusMessage;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.event.PropwashEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CovCamBSmartSteps {
    public static final String STYLES = "follow: Parallel und Blick";
    public static final String RIDES = "follow: im Boot und auf dem Pferd";
    public static final String FAST = "follow: schnelles Ziel";
    public static final String POI_REFUSED = "poi: Himmel und Ziel über 64 m starten keinen Kreis";
    public static final String POI_MESSAGE = "poi: abgelehnter Point of Interest zeigt eine Meldung";
    public static final String OUT_OF_RANGE = "mission: außer Funkreichweite";
    public static final double PARALLEL_TOLERANCE = 2.0;
    public static final double WATCH_DRIFT = 1.0;
    public static final double WATCH_HEADING = 5.0;
    public static final double RIDE_DISTANCE_TOLERANCE = 1.5;
    public static final double AIM_TOLERANCE = 5.0;
    public static final double FAST_SPEED = 30.0;
    public static final double RELEASE_RANGE = 1250.0;
    public static final double RANGE_MISSION_OFFSET = 25.0;
    private static final int STEP_TICKS = 20 * 300;
    private static final String STATS = "covcamb.stats";
    private static final String START = "covcamb.start";
    private static final String CALM = "covcamb.calm";
    private static final String LAST = "covcamb.last";
    private static final String VEHICLE = "covcamb.vehicle";
    private static final String MOUNT_MARK = "covcamb.mountMark";
    private static final String SAMPLES = "covcamb.samples";
    private static final String MARK = "covcamb.mark";
    private static final String NOTES = "covcamb.notes";
    private static final String DIRECTION = "covcamb.direction";
    private static final String ORIGIN = "covcamb.origin";
    private static final String SAVED_KEY = "covcamb.savedKey";
    private static final List<Ended> ENDED = new CopyOnWriteArrayList<>();
    private static boolean listening;

    record Ended(UUID drone, Identifier id, AutonomyEnd reason) {
    }

    private CovCamBSmartSteps() {
    }

    static synchronized void listen() {
        if (listening) {
            return;
        }
        listening = true;
        PropwashEvents.AUTONOMY_ENDED.register((drone, owner, id, reason) -> ENDED.add(new Ended(drone.getUUID(), id, reason)));
    }

    @SuppressWarnings("unchecked")
    private static List<String> notes(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(NOTES, key -> new ArrayList<String>());
    }

    private static StepResult follow(SelfTest.StepContext ctx, FollowController.Style style) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        return CamFlights.send(ctx, new SmartMode.Request(CamFlights.serial(ctx.state()), SmartState.follow(mc.player.getUUID(), style)));
    }

    static void styles() {
        SelfTest.clientStep(511, STYLES, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .append(CovCamBKit.goTo(100, -40))
                .append(CamFlights.groundDrone(CamPresets.TOUR, 100, -40, 0.0F))
                .then("Follow me Parallel", (ctx, ticks) -> follow(ctx, FollowController.Style.PARALLEL))
                .then("Meldung Follow me gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.FOLLOW_STARTED, 100))
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "follow"))
                .then("abheben", (ctx, ticks) -> airborne(ctx, ticks, 3.0))
                .then("Spieler läuft geradeaus, Drohne seitlich", CovCamBSmartSteps::parallel)
                .append(Shots.photos(CovCamBKit.PHOTO + "follow_parallel"))
                .then("Follow me Blick", (ctx, ticks) -> {
                    ctx.state().remove(STATS);
                    ctx.state().remove(START);
                    return follow(ctx, FollowController.Style.WATCH);
                })
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "follow"))
                .sleep(40)
                .then("Spieler läuft, Drohne bleibt und dreht mit", CovCamBSmartSteps::watch)
                .append(Shots.photos(CovCamBKit.PHOTO + "follow_watch"))
                .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx)))), CovCamBSmartSteps::releaseWalk);
    }

    private static StepResult releaseWalk(SelfTest.StepContext ctx) {
        Pilot.hold(Minecraft.getInstance().options.keyUp, false);
        return StepResult.pass();
    }

    private static int surface(ServerLevel level, Vec3 at) {
        BlockPos column = BlockPos.containing(at.x, 0.0, at.z);
        level.getChunk(column.getX() >> 4, column.getZ() >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
    }

    private static StepResult airborne(SelfTest.StepContext ctx, int ticks, double height) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        if (drone.get().getY() - CamFlights.surface() >= height && ticks >= 60) {
            return StepResult.pass();
        }
        return ticks > 20 * 30 ? StepResult.fail("hebt nicht ab") : StepResult.pending();
    }

    private static StepResult parallel(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty() || mc.player == null) {
            return StepResult.fail("Drohne oder Spieler fehlt");
        }
        mc.player.setYRot(180.0F);
        mc.player.setXRot(0.0F);
        int walk = 20 * 16;
        Pilot.hold(mc.options.keyUp, ticks < walk);
        Vec3 look = Vec3.directionFromRotation(0.0F, 180.0F);
        Vec3 d = drone.get().position().subtract(mc.player.position());
        double along = d.x * look.x + d.z * look.z;
        double side = Math.abs(d.x * look.z - d.z * look.x);
        double[] s = (double[]) ctx.state().computeIfAbsent(STATS, key -> new double[]{0.0, 0.0, Double.MAX_VALUE, 0.0, 0.0, 0.0});
        if (ticks >= 20 * 8 && ticks < walk) {
            s[0] += 1.0;
            s[1] += side;
            s[2] = Math.min(s[2], side);
            s[3] = Math.max(s[3], side);
            s[4] = Math.max(s[4], Math.abs(along));
            s[5] += along;
        }
        if (ticks < walk + 10) {
            return StepResult.pending();
        }
        Pilot.hold(mc.options.keyUp, false);
        if (s[0] < 40) {
            return StepResult.fail("zu wenige Messungen: " + (int) s[0]);
        }
        double mean = s[1] / s[0];
        String measured = "Parallel: seitlich im Mittel " + CovCamBKit.f(mean) + " m (" + CovCamBKit.f(s[2]) + " bis " + CovCamBKit.f(s[3])
                + " m), längs höchstens " + CovCamBKit.f(s[4]) + " m vor oder hinter dem Spieler";
        if (s[2] < FollowController.DISTANCE - PARALLEL_TOLERANCE || s[3] > FollowController.DISTANCE + PARALLEL_TOLERANCE
                || s[4] > PARALLEL_TOLERANCE + 1.0) {
            return StepResult.fail(measured + ", erwartet " + CovCamBKit.f(FollowController.DISTANCE) + " ± " + CovCamBKit.f(PARALLEL_TOLERANCE)
                    + " m seitlich");
        }
        notes(ctx).add(measured);
        return StepResult.pass(measured);
    }

    private static StepResult watch(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        Optional<DroneView> view = CamFlights.clientView(ctx.state());
        if (drone.isEmpty() || view.isEmpty() || mc.player == null) {
            return StepResult.fail("Drohne oder Spieler fehlt");
        }
        mc.player.setYRot(90.0F);
        int walk = 20 * 10;
        Pilot.hold(mc.options.keyUp, ticks < walk);
        Vec3 p = drone.get().position();
        Vec3 start = (Vec3) ctx.state().computeIfAbsent(START, key -> p);
        double drift = CamFlights.horizontal(p, start);
        Vec3 target = mc.player.getBoundingBox().getCenter();
        double nose = Compass.headingTo(view.get().forward().x, view.get().forward().z);
        double bearing = Compass.headingTo(target.x - p.x, target.z - p.z);
        double error = Math.abs(Compass.wrap180(nose - bearing));
        double[] s = (double[]) ctx.state().computeIfAbsent(STATS, key -> new double[]{0.0, 0.0, 0.0, 0.0});
        if (ticks >= 20) {
            s[0] += 1.0;
            s[1] = Math.max(s[1], drift);
            s[2] = Math.max(s[2], error);
            s[3] += error;
        }
        if (ticks < walk + 20) {
            return StepResult.pending();
        }
        Pilot.hold(mc.options.keyUp, false);
        String measured = "Blick: Drohne bewegt sich höchstens " + CovCamBKit.f(s[1]) + " m, Nase im Mittel " + CovCamBKit.f(s[3] / s[0])
                + "° und höchstens " + CovCamBKit.f(s[2]) + "° neben dem Spieler";
        if (s[1] >= WATCH_DRIFT || s[2] > WATCH_HEADING) {
            return StepResult.fail(measured + " (erwartet unter " + CovCamBKit.f(WATCH_DRIFT) + " m und ± " + CovCamBKit.f(WATCH_HEADING) + "°)");
        }
        notes(ctx).add(measured);
        return StepResult.pass(measured);
    }

    static void rides() {
        Script script = Script.create().append(Shots.photoWindow());
        script.append(ride(true));
        script.then("aussteigen", CovCamBSmartSteps::dismount);
        script.append(ride(false));
        script.then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx))));
        SelfTest.clientStep(512, RIDES, STEP_TICKS * 2, script, CovCamBSmartSteps::rideCleanup);
    }

    private static Script ride(boolean boat) {
        int dx = boat ? -130 : -30;
        int dz = boat ? 100 : -100;
        String what = boat ? "Boot" : "Pferd";
        return Script.create()
                .then(what + ": Zustand leeren", (ctx, ticks) -> {
                    ctx.state().remove(STATS);
                    ctx.state().remove(LAST);
                    ctx.state().remove(CALM);
                    return StepResult.pass();
                })
                .append(CovCamBKit.goTo(dx + 4, dz - 6))
                .append(CamFlights.groundDrone(CamPresets.TOUR, dx + 4, dz - 6, 0.0F))
                .server(boat ? "Kanal und Boot" : "gezähmtes Pferd mit Sattel", (server, state) -> vehicle(server, state, boat, dx, dz))
                .then(what + ": Follow me", (ctx, ticks) -> follow(ctx, FollowController.Style.BEHIND))
                .then(what + ": Meldung Follow me gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.FOLLOW_STARTED, 100))
                .then(what + ": Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "follow"))
                .then(what + ": Drohne beim Spieler", (ctx, ticks) -> settle(ctx, ticks))
                .then(boat ? "ins Boot steigen" : "aufs Pferd steigen", (ctx, ticks) -> {
                    ctx.state().put(MOUNT_MARK, StatusProbe.mark());
                    return Script.server(ctx, ticks, CovCamBSmartSteps::mount);
                })
                .then(what + ": sitzt auf", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null && mc.player.getVehicle() != null) {
                        mc.player.setYRot(0.0F);
                        return ticks >= 20 ? StepResult.pass() : StepResult.pending();
                    }
                    return ticks > 100 ? StepResult.fail("Spieler sitzt nicht auf") : StepResult.pending();
                })
                .then(what + ": Drohne bleibt beim Aufsitzen am Ziel", CovCamBSmartSteps::settle)
                .run(what + ": Blick nach Norden", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.setYRot(0.0F);
                        mc.player.setXRot(10.0F);
                    }
                })
                .then(boat ? "Boot fährt den Kanal entlang" : "Pferd läuft eine Bahn mit Kurve", (ctx, ticks) -> rideAlong(ctx, ticks, boat))
                .append(Shots.photos(CovCamBKit.PHOTO + "follow_" + (boat ? "boat" : "horse")))
                .then(what + ": kein Zielverlust", (ctx, ticks) -> {
                    Optional<StatusMessage.Payload> lost = StatusProbe.since((int) ctx.state().get(MOUNT_MARK)).stream()
                            .filter(p -> p.status() == AutonomyStatus.FOLLOW_TARGET_LOST || p.status().name().startsWith("ENDED_")).findFirst();
                    if (lost.isPresent()) {
                        return StepResult.fail(what + ": Follow me endet beim Fahren: " + lost.get().status());
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
            Arena.fill(level, Arena.ground(dx - 2, dz - 3), Arena.ground(dx + 2, dz + 90), Blocks.WATER.defaultBlockState());
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
                speed.setBaseValue(FollowRideSteps.HORSE_SPEED);
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

    private static StepResult dismount(SelfTest.StepContext ctx, int ticks) {
        Pilot.hold(Minecraft.getInstance().options.keyUp, false);
        return Script.server(ctx, ticks, (server, state) -> {
            ServerPlayer player = Arena.player(server);
            player.stopRiding();
            CamFlights.stopFlights(server);
            for (Entity drone : Arena.drones(player.level())) {
                drone.discard();
            }
            for (Entity entity : player.level().getEntities((Entity) null, player.getBoundingBox().inflate(Arena.RADIUS),
                    e -> e instanceof Boat || e instanceof Horse)) {
                entity.discard();
            }
        });
    }

    private static StepResult rideCleanup(SelfTest.StepContext ctx) {
        return dismount(ctx, ctx.tick());
    }

    private static double aimError(Entity drone, Vec3 target) {
        Vec3 wanted = target.subtract(drone.position());
        Optional<GimbalLimits> limits = GimbalScenes.limits(drone);
        if (limits.isEmpty()) {
            return Double.NaN;
        }
        double heading = GimbalScenes.heading(drone);
        GimbalResolver.Aim aim = GimbalResolver.resolve(GimbalScenes.scene(drone, limits.get(), heading), CrewData.get(drone).setting());
        return CovCamBKit.angle(CovCamBKit.direction(aim.heading(), aim.tilt()), wanted);
    }

    private static Optional<double[]> relative(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty() || mc.player == null) {
            return Optional.empty();
        }
        if (mc.player.getVehicle() == null) {
            CamFlights.lookAt(drone.get());
        }
        Vec3 d = drone.get().position();
        Vec3 p = mc.player.position();
        double speed = ctx.state().get(LAST) instanceof Vec3 last ? CamFlights.horizontal(d, last) * 20.0 : 0.0;
        ctx.state().put(LAST, d);
        double playerSpeed = mc.player.getVehicle() != null ? mc.player.getVehicle().getDeltaMovement().horizontalDistance() * 20.0
                : mc.player.getDeltaMovement().horizontalDistance() * 20.0;
        return Optional.of(new double[]{CamFlights.horizontal(d, p), d.y - CamFlights.surface(), speed, playerSpeed,
                aimError(drone.get(), mc.player.getBoundingBox().getCenter())});
    }

    private static StepResult settle(SelfTest.StepContext ctx, int ticks) {
        Optional<double[]> rel = relative(ctx);
        if (rel.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        double[] r = rel.get();
        boolean calm = r[1] > FollowController.HEIGHT - 1.5 && Math.abs(r[0] - FollowController.DISTANCE) < 2.0 && r[2] < 0.5;
        int calmTicks = calm ? (int) ctx.state().getOrDefault(CALM, 0) + 1 : 0;
        ctx.state().put(CALM, calmTicks);
        if (calmTicks >= 20) {
            ctx.state().remove(CALM);
            return StepResult.pass();
        }
        return ticks > 20 * 40 ? StepResult.fail("kommt nicht zum Spieler: Abstand " + CovCamBKit.f(r[0]) + " m, Höhe " + CovCamBKit.f(r[1]) + " m")
                : StepResult.pending();
    }

    private static StepResult rideAlong(SelfTest.StepContext ctx, int ticks, boolean boat) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getVehicle() == null) {
            return StepResult.fail("Spieler sitzt nicht mehr auf");
        }
        int rideTicks = FollowRideSteps.RIDE_TICKS;
        boolean driving = ticks < rideTicks;
        Pilot.hold(mc.options.keyUp, driving && (!boat || ticks % 2 == 0));
        if (!boat && ticks == FollowRideSteps.TURN_TICKS) {
            mc.player.setYRot(mc.player.getYRot() + 90.0F);
        }
        Optional<double[]> rel = relative(ctx);
        if (rel.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        double[] r = rel.get();
        @SuppressWarnings("unchecked")
        List<double[]> samples = (List<double[]>) ctx.state().computeIfAbsent(SAMPLES, key -> new ArrayList<double[]>());
        if (ticks == 0) {
            samples.clear();
        }
        boolean counted = ticks >= 60 && ticks < rideTicks && (boat || ticks < FollowRideSteps.TURN_TICKS || ticks >= FollowRideSteps.TURN_TICKS + 80)
                && r[3] >= FollowRideSteps.MIN_SPEED;
        if (counted) {
            samples.add(r);
        }
        if (driving) {
            return StepResult.pending();
        }
        Pilot.hold(mc.options.keyUp, false);
        String what = boat ? "im Boot" : "auf dem Pferd";
        if (samples.size() < 80) {
            return StepResult.fail(what + ": nur " + samples.size() + " Messungen bei Fahrt");
        }
        double sum = 0.0;
        double min = Double.MAX_VALUE;
        double max = 0.0;
        List<Double> aims = new ArrayList<>();
        double speed = 0.0;
        for (double[] s : samples) {
            sum += s[0];
            min = Math.min(min, s[0]);
            max = Math.max(max, s[0]);
            aims.add(s[4]);
            speed += s[3];
        }
        Collections.sort(aims);
        double mean = sum / samples.size();
        double p95 = aims.get((int) Math.floor(0.95 * (aims.size() - 1)));
        double worst = aims.getLast();
        String measured = String.format(Locale.GERMANY, "%s mit %.1f m/s: Abstand im Mittel %.2f m (%.2f bis %.2f m), Gimbal im 95-%%-Wert %.1f° "
                + "und höchstens %.1f° neben dem Spieler (%d Messungen)", what, speed / samples.size(), mean, min, max, p95, worst, samples.size());
        if (Math.abs(mean - FollowController.DISTANCE) > RIDE_DISTANCE_TOLERANCE) {
            return StepResult.fail(measured + ", erwartet " + CovCamBKit.f(FollowController.DISTANCE) + " ± " + CovCamBKit.f(RIDE_DISTANCE_TOLERANCE) + " m");
        }
        if (p95 > AIM_TOLERANCE) {
            return StepResult.fail(measured + ", erwartet ± " + CovCamBKit.f(AIM_TOLERANCE) + "°");
        }
        notes(ctx).add(measured);
        return StepResult.pass(measured);
    }

    record FastSample(double distance, double height, double droneSpeed, boolean follow, boolean flying, boolean alive, double travelled,
                      boolean ticking, String flight, int tick) {
    }

    static void fast() {
        listen();
        SelfTest.clientStep(513, FAST + " (5 s)", STEP_TICKS, fastScript(20 * 5), farCleanup());
    }

    static void fastLong() {
        listen();
        SelfTest.clientStep(519, FAST + " (20 s)", STEP_TICKS, fastScript(20 * 20), farCleanup());
    }

    private static Script fastScript(int dashTicks) {
        return Script.create()
                .append(CovCamBKit.goTo(60, -120))
                .append(CamFlights.groundDrone(CamPresets.TOUR, 60, -120, 0.0F))
                .then("Follow me", (ctx, ticks) -> follow(ctx, FollowController.Style.BEHIND))
                .then("Meldung Follow me gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.FOLLOW_STARTED, 100))
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "follow"))
                .then("Drohne beim Spieler", CovCamBSmartSteps::settle)
                .server("Überleben, Blick nach Osten", (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    player.setGameMode(GameType.SURVIVAL);
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    state.put(ORIGIN, player.position());
                    state.put(MARK, StatusProbe.mark());
                })
                .then("Ziel mit 30 m/s weg", (ctx, ticks) -> dash(ctx, ticks, dashTicks))
                .then("Drohne holt auf", CovCamBSmartSteps::catchUp)
                .then("Ziel außer Funkreichweite", (ctx, ticks) -> Script.server(ctx, ticks, (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    Entity drone = Pilot.serverDrone(server, state);
                    state.put("covcamb.endMark", ENDED.size());
                    Vec3 far = drone.position().add(RELEASE_RANGE + 150.0, 0.0, 0.0);
                    player.teleportTo(player.level(), far.x, surface(player.level(), far), far.z, Set.<Relative>of(), -90.0F, 0.0F, true);
                }))
                .then("Flug endet außer Reichweite mit Meldung", (ctx, ticks) -> outOfRange(ctx, ticks, "Follow me", false))
                .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx))));
    }

    @SuppressWarnings("unchecked")
    private static StepResult dash(SelfTest.StepContext ctx, int ticks, int dashTicks) {
        Map<String, Object> state = ctx.state();
        ConcurrentLinkedQueue<FastSample> queue = (ConcurrentLinkedQueue<FastSample>) state.computeIfAbsent(SAMPLES,
                key -> new ConcurrentLinkedQueue<FastSample>());
        if (ticks < dashTicks) {
            final int tick = ticks;
            ctx.onServer(server -> {
                ServerPlayer player = Arena.player(server);
                double step = FAST_SPEED / 20.0;
                Vec3 to = player.position().add(step, 0.0, 0.0);
                player.teleportTo(player.level(), to.x, to.y, to.z, Set.<Relative>of(), -90.0F, 0.0F, false);
                player.setDeltaMovement(step, 0.0, 0.0);
                double travelled = to.x - ((Vec3) state.get(ORIGIN)).x;
                Optional<Entity> found = serverDrone(server, state);
                if (found.isEmpty()) {
                    String where = "keiner";
                    if (state.get("covcamb.lastDronePos") instanceof BlockPos last) {
                        ServerLevel level = Arena.level(server);
                        boolean loaded = level.getChunkSource().getChunkNow(last.getX() >> 4, last.getZ() >> 4) != null;
                        where = "keiner, letzte Position " + last.toShortString() + ", Chunk geladen " + loaded + ", Entity-Tick " + level.isPositionEntityTicking(last);
                    }
                    queue.add(new FastSample(Double.NaN, Double.NaN, 0.0, false, false, false, travelled, false, where, tick));
                    return;
                }
                Entity drone = found.get();
                state.put("covcamb.lastDronePos", drone.blockPosition());
                FAR_DRONE[0] = drone.blockPosition();
                SmartState smart = PropwashApi.droneData().get(drone, CamDroneData.smart());
                Optional<DroneView> view = PropwashApi.drones().view(drone);
                double height = drone.getY() - ((Vec3) state.get(ORIGIN)).y;
                boolean ticking = ((ServerLevel) drone.level()).isPositionEntityTicking(drone.blockPosition());
                String flight = PropwashApi.drones().autonomy().active(drone).map(Identifier::toString).orElse("keiner");
                queue.add(new FastSample(CamFlights.horizontal(drone.position(), to), height,
                        view.map(DroneView::speed).orElse(0.0), smart != null && smart.function() == SmartState.Function.FOLLOW,
                        view.map(DroneView::flying).orElse(false), !drone.isRemoved(), travelled, ticking, flight, tick));
            });
            return StepResult.pending();
        }
        if (ticks < dashTicks + 20) {
            return StepResult.pending();
        }
        List<FastSample> samples = new ArrayList<>(queue);
        queue.clear();
        if (samples.size() < dashTicks / 2) {
            return StepResult.fail("nur " + samples.size() + " Messungen am Server");
        }
        double maxDistance = 0.0;
        double maxSpeed = 0.0;
        double minHeight = Double.MAX_VALUE;
        for (FastSample s : samples) {
            if (s.tick() % 20 == 0) {
                ctx.log(String.format(Locale.ROOT, "schnell t=%d Weg %.0f m Abstand %.1f m Höhe %.1f m Tempo %.1f m/s Flug %s tickt %s", s.tick(), s.travelled(),
                        s.distance(), s.height(), s.droneSpeed(), s.flight(), s.ticking()));
            }
        }
        for (FastSample s : samples) {
            maxDistance = Math.max(maxDistance, s.distance());
            maxSpeed = Math.max(maxSpeed, s.droneSpeed());
            minHeight = Math.min(minHeight, s.height());
            if (!s.alive() || !s.flying()) {
                return StepResult.fail("Drohne stürzt bzw. fliegt nicht mehr nach " + CovCamBKit.f(s.travelled()) + " m");
            }
            if (!s.follow()) {
                return StepResult.fail("Follow me endet beim schnellen Ziel nach " + CovCamBKit.f(s.travelled()) + " m");
            }
        }
        FastSample last = samples.getLast();
        List<StatusMessage.Payload> statuses = StatusProbe.since((int) state.get(MARK));
        if (statuses.stream().anyMatch(p -> p.status() == AutonomyStatus.FOLLOW_TARGET_LOST || p.status().name().startsWith("ENDED_"))) {
            return StepResult.fail("Meldungen beim schnellen Ziel: " + StatusProbe.describe((int) state.get(MARK)));
        }
        if (maxDistance < 20.0) {
            return StepResult.fail("Drohne fällt nicht zurück (höchstens " + CovCamBKit.f(maxDistance) + " m) – Messung prüfen");
        }
        String note = String.format(Locale.GERMANY, "Ziel %.0f m mit %.0f m/s: Drohne fällt bis %.1f m zurück, fliegt dabei bis %.1f m/s, mindestens "
                + "%.1f m hoch, Follow me bleibt aktiv", last.travelled(), FAST_SPEED, maxDistance, maxSpeed, minHeight);
        notes(ctx).add(note);
        return StepResult.pass(note);
    }

    private static StepResult catchUp(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        if (ticks % 10 == 0) {
            ctx.onServer(server -> serverDrone(server, state).ifPresent(drone -> state.put(LAST, CamFlights.horizontal(drone.position(),
                    Arena.player(server).position()))));
        }
        Object distance = state.get(LAST);
        if (distance instanceof Double d && Math.abs(d - FollowController.DISTANCE) < 2.5 && ticks > 40) {
            notes(ctx).add("Drohne holt nach " + CovCamBKit.f(ticks / 20.0) + " s auf " + CovCamBKit.f(d) + " m auf");
            state.remove(LAST);
            return StepResult.pass();
        }
        return ticks > 20 * 90 ? StepResult.fail("Drohne holt nicht auf (" + distance + " m)") : StepResult.pending();
    }

    private static StepResult outOfRange(SelfTest.StepContext ctx, int ticks, String flight, boolean matchText) {
        Map<String, Object> state = ctx.state();
        int endMark = (int) state.getOrDefault("covcamb.endMark", 0);
        List<Ended> ended = ENDED.size() > endMark ? List.copyOf(ENDED.subList(endMark, ENDED.size())) : List.of();
        Optional<Ended> range = ended.stream().filter(e -> e.reason() == AutonomyEnd.OUT_OF_RANGE).findFirst();
        Optional<StatusMessage.Payload> status = StatusProbe.first((int) state.get(MARK), AutonomyStatus.ENDED_OUT_OF_RANGE);
        if (range.isEmpty() || status.isEmpty()) {
            if (ticks > 20 * 20) {
                return StepResult.fail("kein Ende OUT_OF_RANGE (Ereignisse " + ended.stream().map(e -> e.id() + "/" + e.reason()).toList() + ", Meldungen "
                        + StatusProbe.describe((int) state.get(MARK)) + ")");
            }
            return StepResult.pending();
        }
        if (!state.containsKey("covcamb.followUp")) {
            state.put("covcamb.followUp", ticks);
            state.put("covcamb.heights", new ConcurrentLinkedQueue<double[]>());
        }
        int since = ticks - (int) state.get("covcamb.followUp");
        @SuppressWarnings("unchecked")
        ConcurrentLinkedQueue<double[]> heights = (ConcurrentLinkedQueue<double[]>) state.get("covcamb.heights");
        if (since % 10 == 0 && since <= 20 * 12) {
            ctx.onServer(server -> serverDrone(server, state).ifPresentOrElse(drone -> {
                Optional<Identifier> active = PropwashApi.drones().autonomy().active(drone);
                Vec3 home = (Vec3) state.get(ORIGIN);
                state.put("covcamb.activeId", active.map(Identifier::toString).orElse("keiner"));
                heights.add(new double[]{drone.getY(), CamFlights.horizontal(drone.position(), home), active.isPresent() ? 1.0 : 0.0});
            }, () -> heights.add(new double[]{Double.NaN, Double.NaN, -1.0})));
        }
        if (since < 20 * 13) {
            return StepResult.pending();
        }
        List<double[]> list = new ArrayList<>(heights);
        if (list.size() < 3) {
            return StepResult.fail("keine Messung nach dem Ende");
        }
        List<double[]> known = list.stream().filter(h -> Double.isFinite(h[0])).toList();
        if (known.size() < 3) {
            return StepResult.fail("Drohne nach dem Ende am Server nicht mehr geladen (" + (list.size() - known.size()) + " von " + list.size()
                    + " Messungen ohne Drohne)");
        }
        double[] first = known.getFirst();
        double[] lastSample = known.getLast();
        String text = I18n.get("camdrones.autonomy.status.ended_out_of_range", "Drohne");
        double sank = first[0] - lastSample[0];
        double closer = first[1] - lastSample[1];
        String what = closer > 5.0 ? "fliegt heim" : sank > 0.5 ? "landet an Ort und Stelle" : "bleibt stehen";
        String note = String.format(Locale.GERMANY, "%s: Ende %s mit Meldung %s („%s“), danach %s (in 12 s %.1f m gesunken, %.1f m näher am Start, "
                + "Add-on-Flug am Server danach: %s)", flight, range.get().reason(), status.get().status(), text, what, sank, closer,
                state.getOrDefault("covcamb.activeId", "keiner"));
        notes(ctx).add(note);
        if (matchText) {
            Optional<String> mismatch = textMismatch(text, closer > 5.0, sank > 0.5);
            if (mismatch.isPresent()) {
                return StepResult.fail(note + " – " + mismatch.get());
            }
        }
        return StepResult.pass(note);
    }

    static Optional<String> textMismatch(String text, boolean home, boolean landed) {
        String lower = text.toLowerCase(Locale.ROOT);
        boolean saysHome = lower.contains("heimflug") || lower.contains("home");
        boolean saysLanding = lower.contains("landung") || lower.contains("landing");
        if (home) {
            return saysHome ? Optional.empty() : Optional.of("die Drohne fliegt heim, die Meldung sagt das nicht");
        }
        if (landed) {
            if (saysLanding) {
                return Optional.empty();
            }
            return Optional.of(saysHome ? "die Drohne landet an Ort und Stelle, die Meldung verspricht nur einen Heimflug"
                    : "die Drohne landet an Ort und Stelle, die Meldung nennt keine Landung");
        }
        return Optional.of("die Drohne bleibt stehen, weder Heimflug noch Landung");
    }

    private static StepResult awayFromStart(SelfTest.StepContext ctx, int ticks, double distance) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        double away = CamFlights.horizontal(drone.get().position(), (Vec3) ctx.state().get(ORIGIN));
        if (away >= distance) {
            notes(ctx).add("Drohne " + CovCamBKit.f(away) + " m vom Start");
            return StepResult.pass();
        }
        return ticks > 20 * 40 ? StepResult.fail("Drohne nur " + CovCamBKit.f(away) + " m vom Start") : StepResult.pending();
    }

    static Optional<Entity> serverDrone(MinecraftServer server, Map<String, Object> state) {
        Object id = state.get(Pilot.DRONE);
        if (!(id instanceof UUID uuid)) {
            return Optional.empty();
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity drone = level.getEntity(uuid);
            if (drone != null) {
                return Optional.of(drone);
            }
        }
        return Optional.empty();
    }

    private static final BlockPos[] FAR_DRONE = new BlockPos[1];

    private static Script farCleanup() {
        return Script.create()
                .server("Spieler zurück, Chunk der Drohne laden", (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    player.setGameMode(GameType.CREATIVE);
                    Vec3 spawn = Arena.spawn();
                    player.teleportTo(player.level(), spawn.x, spawn.y, spawn.z, Set.<Relative>of(), 0.0F, 0.0F, true);
                    BlockPos last = FAR_DRONE[0];
                    if (last != null) {
                        Arena.command(server, "forceload add " + last.getX() + " " + last.getZ());
                    }
                })
                .sleep(60)
                .server("Drohnen entfernen", (server, state) -> {
                    CamFlights.stopFlights(server);
                    for (ServerLevel level : server.getAllLevels()) {
                        List<Entity> drones = new ArrayList<>();
                        for (Entity entity : level.getAllEntities()) {
                            if (PropwashApi.drones().isDrone(entity)) {
                                drones.add(entity);
                            }
                        }
                        for (Entity drone : drones) {
                            if (PropwashApi.drones().autonomy().active(drone).isPresent()) {
                                PropwashApi.drones().autonomy().stop(drone);
                            }
                            drone.discard();
                        }
                    }
                    BlockPos last = FAR_DRONE[0];
                    if (last != null) {
                        Arena.command(server, "forceload remove " + last.getX() + " " + last.getZ());
                        FAR_DRONE[0] = null;
                    }
                })
                .sleep(20);
    }

    static void outOfRangeMission() {
        listen();
        SelfTest.clientStep(517, OUT_OF_RANGE + " endet mit Meldung", STEP_TICKS, rangeScript(false), farCleanup());
    }

    static void outOfRangeMessage() {
        listen();
        SelfTest.clientStep(518, OUT_OF_RANGE + ", Meldung passt zum Verhalten", STEP_TICKS, rangeScript(true), farCleanup());
    }

    private static Script rangeScript(boolean matchText) {
        int dx = matchText ? -80 : -110;
        double out = matchText ? RANGE_MISSION_OFFSET : 0.0;
        return Script.create()
                .append(CovCamBKit.goTo(dx, -120))
                .append(CamFlights.groundDrone(CamPresets.TOUR, dx, -120, 0.0F))
                .server("Mission als Schleife", (server, state) -> {
                    Entity drone = Pilot.serverDrone(server, state);
                    double x = drone.getX();
                    double y = drone.getY() + 6.0;
                    double z = drone.getZ();
                    CamFlights.mission(server, state, Mission.of(List.of(Waypoint.at(x, y, z + out + 10.0), Waypoint.at(x + 10.0, y, z + out + 10.0),
                            Waypoint.at(x + 10.0, y, z + out), Waypoint.at(x, y, z + out))).withLoop(true).withBaseSpeed(3.0));
                    state.put(ORIGIN, drone.position());
                })
                .then("Mission starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Meldung Mission gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.MISSION_STARTED, 100))
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("in der Luft", (ctx, ticks) -> airborne(ctx, ticks, 4.0))
                .then("weg vom Start", (ctx, ticks) -> matchText ? awayFromStart(ctx, ticks, out - 5.0) : StepResult.pass())
                .then("Besitzer geht außer Funkreichweite", (ctx, ticks) -> {
                    if (ticks == 0) {
                        ctx.state().put(MARK, StatusProbe.mark());
                    }
                    return Script.server(ctx, ticks, (server, state) -> {
                        ServerPlayer player = Arena.player(server);
                        player.setGameMode(GameType.SURVIVAL);
                        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        state.put("covcamb.endMark", ENDED.size());
                        Entity drone = Pilot.serverDrone(server, state);
                        Vec3 far = drone.position().add(-(RELEASE_RANGE + 150.0), 0.0, 0.0);
                        int y = surface(player.level(), far);
                        player.teleportTo(player.level(), far.x, y, far.z, Set.<Relative>of(), 90.0F, 0.0F, true);
                    });
                })
                .then("Ende außer Reichweite mit Meldung", (ctx, ticks) -> outOfRange(ctx, ticks, "Mission", matchText))
                .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx))));
    }

    static Script poi(boolean message) {
        return Script.create()
                .append(CovCamBKit.goTo(-100, -40))
                .then("Point of Interest auf freie Taste", (ctx, ticks) -> bindPoi(ctx))
                .server("Wand in 80 m und in 40 m", (server, state) -> {
                    ServerLevel level = Arena.level(server);
                    BlockPos base = Arena.ground(-100, -40);
                    Arena.fill(level, base.offset(-6, 1, -80), base.offset(6, 30, -80), Blocks.STONE.defaultBlockState());
                    Arena.fill(level, base.offset(14, 1, -40), base.offset(26, 30, -40), Blocks.STONE.defaultBlockState());
                })
                .append(Pilot.launch(CamPresets.TOUR, -100, -40, 180.0F))
                .append(Flight.takeoff(6.0))
                .then("Gimbal nach oben in den Himmel", (ctx, ticks) -> tilt(ctx, ticks, true))
                .then("Point of Interest im Himmel", (ctx, ticks) -> poiPress(ctx, ticks, false, message, "Himmel"))
                .then("Gimbal waagrecht", (ctx, ticks) -> tilt(ctx, ticks, false))
                .then("Point of Interest auf die Wand in 80 m", (ctx, ticks) -> poiPress(ctx, ticks, false, message, "Wand in 80 m"))
                .then("nach rechts drehen zur Wand in 40 m", CovCamBSmartSteps::turnRight)
                .then("Point of Interest auf die Wand in 40 m", (ctx, ticks) -> poiPress(ctx, ticks, true, false, "Wand in 40 m"))
                .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx))));
    }

    static void poiRefused() {
        SelfTest.clientStep(514, POI_REFUSED, STEP_TICKS, poi(false), CovCamBSmartSteps::unbindPoi);
    }

    static void poiMessage() {
        SelfTest.clientStep(515, POI_MESSAGE, STEP_TICKS, poi(true), CovCamBSmartSteps::unbindPoi);
    }

    private static StepResult bindPoi(SelfTest.StepContext ctx) {
        KeyMapping mapping = CamKeys.key(CamControl.POI_HERE).orElse(null);
        if (mapping == null) {
            return StepResult.fail("keine Taste für Point of Interest");
        }
        POI_KEY[0] = InputConstants.getKey(mapping.saveString());
        mapping.setKey(CovCamBKit.key(InputConstants.KEY_B));
        KeyMapping.resetMapping();
        return StepResult.pass();
    }

    private static final InputConstants.Key[] POI_KEY = new InputConstants.Key[1];

    private static StepResult unbindPoi(SelfTest.StepContext ctx) {
        CamKeys.key(CamControl.POI_HERE).ifPresent(mapping -> {
            if (POI_KEY[0] != null) {
                mapping.setKey(POI_KEY[0]);
            }
        });
        POI_KEY[0] = null;
        KeyMapping.resetMapping();
        return StepResult.pass();
    }

    private static StepResult tilt(SelfTest.StepContext ctx, int ticks, boolean up) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        KeyMapping key = (up ? CamKeys.increase(CamControl.GIMBAL_TILT) : CamKeys.decrease(CamControl.GIMBAL_TILT)).orElseThrow();
        KeyMapping other = (up ? CamKeys.decrease(CamControl.GIMBAL_TILT) : CamKeys.increase(CamControl.GIMBAL_TILT)).orElseThrow();
        double tilt = GimbalClient.tilt(drone.get());
        boolean done = up ? tilt >= 25.0 : Math.abs(tilt) < 2.0;
        if (done && ticks >= 6) {
            Pilot.hold(key, false);
            Pilot.hold(other, false);
            return StepResult.pass();
        }
        if (!up && tilt < 0.0) {
            Pilot.hold(key, false);
            Pilot.hold(other, true);
        } else {
            Pilot.hold(other, false);
            Pilot.hold(key, true);
        }
        return ticks > 200 ? StepResult.fail("Neigung bleibt bei " + CovCamBKit.f(tilt) + "°") : StepResult.pending();
    }

    private static StepResult turnRight(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Optional<DroneView> view = Pilot.drone().flatMap(d -> PropwashApi.drones().view(d));
        if (view.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        Optional<Entity> drone = Pilot.drone();
        Vec3 p = drone.get().position();
        BlockPos base = Arena.ground(-100, -40);
        double bearing = Compass.headingTo(base.getX() + 20.5 - p.x, base.getZ() - 40 - p.z);
        double nose = Compass.headingTo(view.get().forward().x, view.get().forward().z);
        double error = Compass.wrap180(bearing - nose);
        KeyMapping right = KeyMapping.get("key.propwash.kb_yaw_right");
        KeyMapping left = KeyMapping.get("key.propwash.kb_yaw_left");
        Minecraft mc = Minecraft.getInstance();
        if (Math.abs(error) < 3.0 && ticks > 4) {
            Pilot.hold(mc.options.keyRight, false);
            Pilot.hold(mc.options.keyLeft, false);
            return StepResult.pass("Nase " + CovCamBKit.f(nose) + "° zur Wand in 40 m");
        }
        KeyMapping yawRight = right != null && !right.isUnbound() ? right : mc.options.keyRight;
        KeyMapping yawLeft = left != null && !left.isUnbound() ? left : mc.options.keyLeft;
        Pilot.hold(yawRight, error > 0.0);
        Pilot.hold(yawLeft, error < 0.0);
        return ticks > 400 ? StepResult.fail("dreht nicht zur Wand (Fehler " + CovCamBKit.f(error) + "°)") : StepResult.pending();
    }

    private static StepResult poiPress(SelfTest.StepContext ctx, int ticks, boolean expectOrbit, boolean message, String what) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Map<String, Object> state = ctx.state();
        InputConstants.Key key = CovCamBKit.key(InputConstants.KEY_B);
        if (ticks == 0) {
            CovCamAKit.clearOverlay();
            state.put(MARK, StatusProbe.mark());
            CovCamBKit.down(key, true);
            return StepResult.pending();
        }
        if (ticks == 3) {
            CovCamBKit.down(key, false);
        }
        if (ticks < 40) {
            return StepResult.pending();
        }
        Optional<NavStatus> status = Pilot.status();
        NavLogic.Function function = status.map(NavStatus::function).orElse(NavLogic.Function.NONE);
        String overlay = CovCamAKit.overlayText();
        if (expectOrbit) {
            if (function != NavLogic.Function.ORBIT) {
                return StepResult.fail(what + ": kein Kreis (" + function + ") – Gegenprobe gescheitert");
            }
            notes(ctx).add(what + ": Kreis startet (" + status.map(NavStatus::label).orElse("") + ")");
            return StepResult.pass();
        }
        if (function == NavLogic.Function.ORBIT) {
            return StepResult.fail(what + ": Drohne kreist um ein falsches Ziel");
        }
        if (message && overlay.isBlank()) {
            return StepResult.fail(what + ": kein Kreis, aber auch keine Meldung (Funktion " + function + ", Aktionsleiste leer)");
        }
        notes(ctx).add(what + ": kein Kreis (Funktion " + function + ")" + (overlay.isBlank() ? ", keine Meldung" : ", Meldung \"" + overlay + "\""));
        return StepResult.pass();
    }
}
