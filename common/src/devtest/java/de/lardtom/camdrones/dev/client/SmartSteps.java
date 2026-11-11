package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.net.SmartMode;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class SmartSteps {
    public static final String POI = "poi: Kreis um ein Leuchtfeuer";
    public static final String FOLLOW = "follow: Spieler läuft eine Bahn, Abstand gehalten";
    public static final double ORBIT_RADIUS = 10.0;
    public static final double ORBIT_SPEED = 3.0;
    public static final double ORBIT_RADIUS_TOLERANCE = 1.0;
    public static final double ORBIT_HEADING_TOLERANCE = 10.0;
    public static final double ORBIT_HEIGHT_TOLERANCE = 1.0;
    public static final double ORBIT_SWEEP = 300.0;
    public static final double WIDE_RADIUS = 14.0;
    public static final double WIDE_SPEED = 4.0;
    public static final double WIDE_SPEED_TOLERANCE = 0.6;
    public static final double FOLLOW_DISTANCE_TOLERANCE = 2.0;
    public static final double FOLLOW_REST_TOLERANCE = 1.0;
    public static final int WALK_TICKS = 20 * 16;
    public static final int TURN_TICKS = 20 * 10;
    private static final int POI_DX = -60;
    private static final int POI_DZ = 40;
    private static final int FOLLOW_DX = 60;
    private static final int FOLLOW_DZ = 60;
    private static final String CENTER = "smart.center";
    private static final String LAST_ANGLE = "smart.lastAngle";
    private static final String SWEPT = "smart.swept";
    private static final String WORST_RADIUS = "smart.worstRadius";
    private static final String WORST_HEADING = "smart.worstHeading";
    private static final String HEIGHT_MIN = "smart.heightMin";
    private static final String HEIGHT_MAX = "smart.heightMax";
    private static final String SHOT = "smart.shot";
    private static final String SAMPLES = "smart.samples";
    private static final String SUM = "smart.sum";
    private static final String MIN = "smart.min";
    private static final String MAX = "smart.max";
    private static final String BEHIND = "smart.behind";
    private static final String CALM = "smart.calm";
    private static final String LAST = "smart.last";
    private static final String WIDE = "smart.wide";
    private static final int STEP_TICKS = 20 * 240;

    private SmartSteps() {
    }

    public static void register() {
        SelfTest.clientStep(13, POI, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .server("Leuchtfeuer setzen", SmartSteps::beacon)
                .append(CamFlights.groundDrone(CamPresets.TOUR, POI_DX + (int) ORBIT_RADIUS, POI_DZ, 0.0F))
                .then("Point of Interest um das Leuchtfeuer", (ctx, ticks) -> {
                    Vec3 center = (Vec3) ctx.state().get(CENTER);
                    return CamFlights.send(ctx, new SmartMode.Request(CamFlights.serial(ctx.state()),
                            SmartState.pointOfInterest(center.x, center.y, center.z, ORBIT_RADIUS, ORBIT_SPEED, true)));
                })
                .then("Meldung Kreisen gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.POI_STARTED, 100))
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "poi"))
                .then("Kreis um das Leuchtfeuer", SmartSteps::orbit)
                .append(Shots.photos("poi_beacon_orbit"))
                .then("Radius " + (int) WIDE_RADIUS + " m und Tempo " + (int) WIDE_SPEED + " m/s", (ctx, ticks) -> {
                    Vec3 center = (Vec3) ctx.state().get(CENTER);
                    return CamFlights.send(ctx, new SmartMode.Request(CamFlights.serial(ctx.state()),
                            SmartState.pointOfInterest(center.x, center.y, center.z, WIDE_RADIUS, WIDE_SPEED, true)));
                })
                .then("neuer Kreis", SmartSteps::wider));
        SelfTest.clientStep(14, FOLLOW, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .append(CamFlights.groundDrone(CamPresets.TOUR, FOLLOW_DX, FOLLOW_DZ, 180.0F))
                .then("Follow me auf den Spieler", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) {
                        return StepResult.fail("kein Spieler");
                    }
                    return CamFlights.send(ctx, new SmartMode.Request(CamFlights.serial(ctx.state()),
                            SmartState.follow(mc.player.getUUID(), FollowController.Style.BEHIND)));
                })
                .then("Meldung Follow me gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.FOLLOW_STARTED, 100))
                .then("Flug am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "follow"))
                .then("abheben und hinter den Spieler", SmartSteps::settle)
                .then("Spieler läuft eine Bahn mit Kurve", SmartSteps::walk)
                .then("Spieler bleibt stehen", SmartSteps::rest)
                .append(Shots.photos("follow_behind_player")));
    }

    private static void beacon(MinecraftServer server, Map<String, Object> state) {
        BlockPos ground = Arena.ground(POI_DX, POI_DZ);
        Arena.place(Arena.level(server), ground.above(), Blocks.BEACON.defaultBlockState());
        state.put(CENTER, new Vec3(ground.getX() + 0.5, ground.getY() + 1.5, ground.getZ() + 0.5));
    }

    private static StepResult orbit(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        Optional<Entity> drone = CamFlights.clientDrone(state);
        Optional<DroneView> view = CamFlights.clientView(state);
        if (drone.isEmpty() || view.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        Vec3 center = (Vec3) state.get(CENTER);
        Vec3 p = drone.get().position();
        double radius = CamFlights.horizontal(p, center);
        double height = p.y - CamFlights.surface();
        boolean established = state.containsKey(LAST_ANGLE) || Math.abs(radius - ORBIT_RADIUS) < 0.5 && height > 7.0;
        if (!established) {
            return ticks > 20 * 40 ? StepResult.fail("kommt nicht auf die Kreisbahn: Radius " + Flight.format(radius) + " m, Höhe "
                    + Flight.format(height) + " m") : StepResult.pending();
        }
        double angle = Math.toDegrees(Math.atan2(p.z - center.z, p.x - center.x));
        if (state.containsKey(LAST_ANGLE)) {
            double step = Compass.wrap180(angle - (double) state.get(LAST_ANGLE));
            state.put(SWEPT, (double) state.getOrDefault(SWEPT, 0.0) + Math.abs(step));
        }
        state.put(LAST_ANGLE, angle);
        state.put(WORST_RADIUS, Math.max((double) state.getOrDefault(WORST_RADIUS, 0.0), Math.abs(radius - ORBIT_RADIUS)));
        Vec3 forward = view.get().forward();
        double nose = Compass.headingTo(forward.x, forward.z);
        double toCenter = Compass.headingTo(center.x - p.x, center.z - p.z);
        double headingError = Math.abs(Compass.wrap180(nose - toCenter));
        double swept = (double) state.getOrDefault(SWEPT, 0.0);
        if (swept > 45.0) {
            state.put(WORST_HEADING, Math.max((double) state.getOrDefault(WORST_HEADING, 0.0), headingError));
            state.put(HEIGHT_MIN, Math.min((double) state.getOrDefault(HEIGHT_MIN, height), height));
            state.put(HEIGHT_MAX, Math.max((double) state.getOrDefault(HEIGHT_MAX, height), height));
        }
        double worstRadius = (double) state.get(WORST_RADIUS);
        if (worstRadius > ORBIT_RADIUS_TOLERANCE) {
            return StepResult.fail("Radius weicht " + Flight.format(worstRadius) + " m ab (Grenze " + Flight.format(ORBIT_RADIUS_TOLERANCE)
                    + " m) nach " + Flight.format(swept) + "°");
        }
        double worstHeading = (double) state.getOrDefault(WORST_HEADING, 0.0);
        if (worstHeading > ORBIT_HEADING_TOLERANCE) {
            return StepResult.fail("Nase zeigt " + Flight.format(worstHeading) + "° neben die Mitte (Grenze " + Flight.format(ORBIT_HEADING_TOLERANCE)
                    + "°)");
        }
        if (swept >= 180.0 && !state.containsKey(SHOT) && Shots.enabled()) {
            Shots.clearOverlays(Minecraft.getInstance());
            state.put(SHOT, Shots.capture("poi_beacon_orbit_flight"));
        }
        if (swept < ORBIT_SWEEP) {
            return ticks > 20 * 120 ? StepResult.fail("kreist zu langsam: " + Flight.format(swept) + "° in " + Flight.format(ticks / 20.0) + " s")
                    : StepResult.pending();
        }
        if (state.get(SHOT) instanceof CompletableFuture<?> shot && !shot.isDone()) {
            return StepResult.pending();
        }
        double heightSpread = (double) state.get(HEIGHT_MAX) - (double) state.get(HEIGHT_MIN);
        if (heightSpread > ORBIT_HEIGHT_TOLERANCE) {
            return StepResult.fail("Höhe schwankt im Kreis um " + Flight.format(heightSpread) + " m");
        }
        return StepResult.pass("kreist " + Flight.format(swept) + "° um das Leuchtfeuer, Radius " + Flight.format(ORBIT_RADIUS) + " m auf "
                + Flight.format(worstRadius) + " m genau, Nase höchstens " + Flight.format(worstHeading) + "° neben der Mitte, Höhe schwankt "
                + Flight.format(heightSpread) + " m");
    }

    private static StepResult wider(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        Optional<Entity> drone = CamFlights.clientDrone(state);
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        Vec3 center = (Vec3) state.get(CENTER);
        Vec3 p = drone.get().position();
        double radius = CamFlights.horizontal(p, center);
        double speed = 0.0;
        if (state.get(LAST) instanceof Vec3 last) {
            speed = CamFlights.horizontal(p, last) * 20.0;
        }
        state.put(LAST, p);
        if (ticks < 20 * 12) {
            return StepResult.pending();
        }
        double[] sums = (double[]) state.computeIfAbsent(WIDE, key -> new double[4]);
        sums[0] += 1.0;
        sums[1] += speed;
        sums[2] = Math.max(sums[2], Math.abs(radius - WIDE_RADIUS));
        if (ticks < 20 * 17) {
            return StepResult.pending();
        }
        double mean = sums[1] / sums[0];
        if (sums[2] > ORBIT_RADIUS_TOLERANCE || Math.abs(mean - WIDE_SPEED) > WIDE_SPEED_TOLERANCE) {
            return StepResult.fail("nach der Änderung Radius auf " + Flight.format(sums[2]) + " m genau und Tempo " + Flight.format(mean)
                    + " m/s statt " + Flight.format(WIDE_RADIUS) + " m und " + Flight.format(WIDE_SPEED) + " m/s");
        }
        return StepResult.pass("nach der Änderung kreist die Drohne mit " + Flight.format(WIDE_RADIUS) + " m Radius (auf " + Flight.format(sums[2])
                + " m genau) und " + Flight.format(mean) + " m/s");
    }

    private static Optional<double[]> relative(Map<String, Object> state) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Entity> drone = CamFlights.clientDrone(state);
        if (drone.isEmpty() || mc.player == null) {
            return Optional.empty();
        }
        Vec3 d = drone.get().position();
        Vec3 p = mc.player.position();
        Vec3 look = Vec3.directionFromRotation(0.0F, mc.player.getYRot());
        double dx = d.x - p.x;
        double dz = d.z - p.z;
        double distance = Math.hypot(dx, dz);
        double along = dx * look.x + dz * look.z;
        double speed = 0.0;
        if (state.get(LAST) instanceof Vec3 last) {
            speed = CamFlights.horizontal(d, last) * 20.0;
        }
        state.put(LAST, d);
        return Optional.of(new double[]{distance, along, d.y - CamFlights.surface(), speed});
    }

    private static StepResult settle(SelfTest.StepContext ctx, int ticks) {
        Optional<double[]> rel = relative(ctx.state());
        if (rel.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        double[] r = rel.get();
        boolean calm = r[2] > FollowController.HEIGHT - 1.5 && Math.abs(r[0] - FollowController.DISTANCE) < 2.0 && r[3] < 0.5;
        int calmTicks = calm ? (int) ctx.state().getOrDefault(CALM, 0) + 1 : 0;
        ctx.state().put(CALM, calmTicks);
        if (calmTicks >= 20) {
            ctx.state().remove(CALM);
            return StepResult.pass("steht " + Flight.format(r[0]) + " m hinter dem Spieler in " + Flight.format(r[2]) + " m Höhe");
        }
        return ticks > 20 * 40 ? StepResult.fail("kommt nicht hinter den Spieler: Abstand " + Flight.format(r[0]) + " m, Höhe "
                + Flight.format(r[2]) + " m, Tempo " + Flight.format(r[3]) + " m/s") : StepResult.pending();
    }

    private static StepResult walk(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        Map<String, Object> state = ctx.state();
        if (ticks == TURN_TICKS) {
            mc.player.setYRot(mc.player.getYRot() + 90.0F);
        }
        Pilot.hold(mc.options.keyUp, ticks < WALK_TICKS);
        Optional<double[]> rel = relative(state);
        if (rel.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        double[] r = rel.get();
        boolean counted = ticks >= 60 && ticks < TURN_TICKS || ticks >= TURN_TICKS + 80 && ticks < WALK_TICKS;
        if (counted) {
            state.put(SAMPLES, (int) state.getOrDefault(SAMPLES, 0) + 1);
            state.put(SUM, (double) state.getOrDefault(SUM, 0.0) + r[0]);
            state.put(MIN, Math.min((double) state.getOrDefault(MIN, r[0]), r[0]));
            state.put(MAX, Math.max((double) state.getOrDefault(MAX, r[0]), r[0]));
            if (r[1] < 0.0) {
                state.put(BEHIND, (int) state.getOrDefault(BEHIND, 0) + 1);
            }
        }
        if (ticks < WALK_TICKS) {
            return StepResult.pending();
        }
        int samples = (int) state.getOrDefault(SAMPLES, 0);
        if (samples == 0) {
            return StepResult.fail("keine Messung");
        }
        double mean = (double) state.get(SUM) / samples;
        double min = (double) state.get(MIN);
        double max = (double) state.get(MAX);
        int behind = (int) state.getOrDefault(BEHIND, 0);
        String measured = "beim Laufen Abstand im Mittel " + Flight.format(mean) + " m (" + Flight.format(min) + " bis " + Flight.format(max)
                + " m), hinter dem Spieler in " + behind + " von " + samples + " Messungen";
        if (Math.abs(mean - FollowController.DISTANCE) > FOLLOW_DISTANCE_TOLERANCE || min < FollowController.DISTANCE - 2.0 * FOLLOW_DISTANCE_TOLERANCE
                || max > FollowController.DISTANCE + 2.0 * FOLLOW_DISTANCE_TOLERANCE) {
            return StepResult.fail(measured + ", erwartet " + Flight.format(FollowController.DISTANCE) + " ± " + Flight.format(FOLLOW_DISTANCE_TOLERANCE)
                    + " m");
        }
        if (behind < samples * 0.9) {
            return StepResult.fail(measured + ": Drohne nicht hinter dem Spieler");
        }
        return StepResult.pass(measured);
    }

    private static StepResult rest(SelfTest.StepContext ctx, int ticks) {
        Optional<double[]> rel = relative(ctx.state());
        if (rel.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        double[] r = rel.get();
        boolean calm = Math.abs(r[0] - FollowController.DISTANCE) < FOLLOW_REST_TOLERANCE && r[3] < 0.3;
        int calmTicks = calm ? (int) ctx.state().getOrDefault(CALM, 0) + 1 : 0;
        ctx.state().put(CALM, calmTicks);
        if (calmTicks >= 20) {
            CamFlights.clientDrone(ctx.state()).ifPresent(CamFlights::lookAt);
            return StepResult.pass("steht nach " + Flight.format(ticks / 20.0) + " s " + Flight.format(r[0]) + " m hinter dem Spieler");
        }
        return ticks > 20 * 15 ? StepResult.fail("kommt nicht zur Ruhe: Abstand " + Flight.format(r[0]) + " m, Tempo " + Flight.format(r[3]) + " m/s")
                : StepResult.pending();
    }
}
