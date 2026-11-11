package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.client.hud.CamOsdSource;
import de.lardtom.camdrones.client.hud.HudText;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class FlightSteps {
    public static final String HOLD = "flug: Knüppel los, Drohne hält ≤ 1 m in 5 s";
    public static final String SPEEDS = "flug: Kino- und Sport-Tempo im Bereich";
    public static final String ATTI = "flug: unter Blöcken ATTI mit Warnung";
    public static final String WALL = "hindernis: Stopp vor der Wand";
    public static final String STRONG_WIND = "flug: Halten bei starkem Wind";
    public static final double STRONG_WIND_LIMIT = 1.5;
    public static final double BENCH_SHARE = 0.9;
    public static final double STRONG_WIND_SPEED = 0.5;
    public static final double STRONG_WIND_MIN_TILT = 1.5;
    public static final int STRONG_WIND_TICKS = 200;
    public static final double HOLD_LIMIT = 1.0;
    public static final int HOLD_TICKS = 100;
    public static final double WALL_TOLERANCE = 0.3;
    public static final double ATTI_HEIGHT_LIMIT = 1.5;
    private static final String START = "flug.start";
    private static final String DEVIATION = "flug.deviation";
    private static final String WALL_AXIS = "flug.wallAxis";
    private static final String WALL_FACE = "flug.wallFace";
    private static final String WALL_SIGN = "flug.wallSign";
    private static final String CLOSEST = "flug.closest";
    private static final String CINE = "flug.cine";
    private static final String TILT = "flug.tilt";
    private static final String SAMPLES = "flug.samples";
    private static final int STEP_TICKS = 20 * 150;

    private FlightSteps() {
    }

    public static void register() {
        SelfTest.clientStep(3, HOLD, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .append(Pilot.launch(CamPresets.TOUR, 0, 8, 0.0F))
                .append(Flight.takeoff(4.0))
                .then("Restflugzeit aus der Werkbank", FlightSteps::flightLeftFromBench)
                .then("vorwärts", (ctx, ticks) -> forward(ctx, ticks, 40))
                .then("loslassen und bremsen", (ctx, ticks) -> {
                    StepResult braked = Flight.brake(ctx, ticks);
                    if (!braked.done() && ticks > HOLD_TICKS) {
                        return StepResult.fail("steht nach 5 s noch nicht (" + Flight.format(Flight.horizontalSpeed(ctx, 10)) + " m/s)");
                    }
                    return braked.done() && !braked.failed() ? StepResult.pass("steht nach " + Flight.format(ticks / 20.0) + " s") : braked;
                })
                .then("5 s halten", FlightSteps::hold)
                .check("Modus N", () -> mode(NavLogic.StickMode.NORMAL, "N"))
                .append(Shots.photos("flight_hold_osd")));
        SelfTest.clientStep(4, SPEEDS, STEP_TICKS, Script.create()
                .append(Pilot.launch(CamPresets.TOUR, 12, 8, 0.0F))
                .append(Flight.takeoff(4.0))
                .then("N vorwärts", (ctx, ticks) -> speed(ctx, ticks, 80, NavLogic.StickMode.NORMAL))
                .then("bremsen", Flight::brake)
                .append(Flight.mode(NavLogic.StickMode.CINE))
                .then("C vorwärts", (ctx, ticks) -> speed(ctx, ticks, 110, NavLogic.StickMode.CINE))
                .then("bremsen", Flight::brake)
                .append(Flight.mode(NavLogic.StickMode.SPORT))
                .then("S vorwärts", (ctx, ticks) -> speed(ctx, ticks, 70, NavLogic.StickMode.SPORT))
                .append(Flight.mode(NavLogic.StickMode.NORMAL))
                .then("bremsen in N", Flight::brake));
        SelfTest.clientStep(5, ATTI, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .server("Dach bauen", (server, state) -> roof(server, -24, 8))
                .append(Pilot.launch(CamPresets.TOUR, -24, 8, 0.0F))
                .append(Flight.climb(2.5))
                .then("ATTI ohne GPS-Fix", FlightSteps::atti)
                .then("Höhe in ATTI", FlightSteps::attiHeight)
                .append(Shots.photos("flight_atti_osd")));
        SelfTest.clientStep(6, WALL, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .append(Pilot.launch(CamPresets.TOUR, 24, 8, 0.0F))
                .append(Flight.takeoff(3.0))
                .server("Wand vor die Drohne", FlightSteps::wall)
                .then("auf die Wand zu", FlightSteps::towardsWall)
                .append(Shots.photos("obstacle_stop_osd"))
                .then("loslassen", Flight::brake));
        SelfTest.clientStep(21, STRONG_WIND, STEP_TICKS, Script.create()
                .server("Wind " + Arena.STRONG_WIND, (server, state) -> Arena.gameRule(server, Arena.WIND, String.valueOf(Arena.STRONG_WIND)))
                .append(Pilot.launch(CamPresets.TOUR, 0, -16, 0.0F))
                .append(Flight.takeoff(4.0))
                .then("vorwärts", (ctx, ticks) -> forward(ctx, ticks, 30))
                .then("loslassen und bremsen", (ctx, ticks) -> {
                    Flight.track(ctx);
                    Pilot.hold(Pilot.PITCH_FORWARD, false);
                    double speed = Flight.horizontalSpeed(ctx, 10);
                    if (ticks >= 20 * 8 && speed < STRONG_WIND_SPEED) {
                        return StepResult.pass("nach 8 s noch " + Flight.format(speed) + " m/s");
                    }
                    return ticks > 20 * 12 ? StepResult.fail("kommt bei starkem Wind nicht zur Ruhe (" + Flight.format(speed) + " m/s nach "
                            + Flight.format(ticks / 20.0) + " s, " + Flight.describe() + ")") : StepResult.pending();
                })
                .then("10 s halten", FlightSteps::holdInStrongWind)
                .check("Modus N", () -> mode(NavLogic.StickMode.NORMAL, "N")));
    }

    private static StepResult flightLeftFromBench(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks < 10) {
            return StepResult.pending();
        }
        double bench = CamOsdSource.benchSeconds(drone.get());
        double left = CamOsdSource.last().flightSecondsLeft();
        if (!(bench > 0.0)) {
            return StepResult.fail("keine Werkbank-Schätzung für die Drohne");
        }
        if (CamOsdSource.flightLeftMeasured()) {
            return StepResult.fail("Restflugzeit schon nach wenigen Sekunden aus der Messung geschätzt");
        }
        if (!(left >= bench * BENCH_SHARE && left <= bench + 1.0)) {
            return ticks > 60 ? StepResult.fail("Restflugzeit " + HudText.duration(left) + " statt der Werkbank-Schätzung "
                    + HudText.duration(bench)) : StepResult.pending();
        }
        return StepResult.pass("Restflugzeit " + HudText.duration(left) + " aus der Werkbank-Schätzung " + HudText.duration(bench)
                + " (gemischt, voller Akku), bis genug gemessen ist");
    }

    private static StepResult holdInStrongWind(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Vec3> now = Flight.position();
        Optional<Entity> drone = Pilot.drone();
        if (now.isEmpty() || drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        Vec3 start = (Vec3) ctx.state().computeIfAbsent(START, key -> now.get());
        double deviation = Math.max((double) ctx.state().getOrDefault(DEVIATION, 0.0), now.get().distanceTo(start));
        ctx.state().put(DEVIATION, deviation);
        double tilt = PropwashApi.drones().view(drone.get()).map(view -> Math.toDegrees(Math.acos(Math.clamp(view.up().y, -1.0, 1.0))))
                .orElse(0.0);
        ctx.state().put(TILT, (double) ctx.state().getOrDefault(TILT, 0.0) + tilt);
        ctx.state().put(SAMPLES, (int) ctx.state().getOrDefault(SAMPLES, 0) + 1);
        if (Pilot.status().map(NavStatus::atti).orElse(true)) {
            return StepResult.fail("hält im starken Wind nicht mit GPS: " + Flight.describe());
        }
        if (deviation > STRONG_WIND_LIMIT) {
            return StepResult.fail("weicht im starken Wind " + Flight.format(deviation) + " m ab (Grenze " + Flight.format(STRONG_WIND_LIMIT)
                    + " m) nach " + Flight.format(ticks / 20.0) + " s");
        }
        if (ticks < STRONG_WIND_TICKS) {
            return StepResult.pending();
        }
        double meanTilt = (double) ctx.state().get(TILT) / (int) ctx.state().get(SAMPLES);
        if (meanTilt < STRONG_WIND_MIN_TILT) {
            return StepResult.fail("Wind wirkt nicht: mittlere Neigung nur " + Flight.format(meanTilt) + "° (mindestens "
                    + Flight.format(STRONG_WIND_MIN_TILT) + "° gegen den Wind)");
        }
        return StepResult.pass("hält bei Wind " + Arena.STRONG_WIND + " 10 s auf " + Flight.format(deviation) + " m genau (Grenze "
                + Flight.format(STRONG_WIND_LIMIT) + " m), stemmt sich mit " + Flight.format(meanTilt) + "° gegen den Wind");
    }

    static StepResult forward(SelfTest.StepContext ctx, int ticks, int duration) {
        Flight.track(ctx);
        Pilot.hold(Pilot.PITCH_FORWARD, true);
        if (ticks < duration) {
            return StepResult.pending();
        }
        return StepResult.pass("vorwärts mit Knüppel " + Flight.stick() + " auf " + Flight.format(Flight.horizontalSpeed(ctx, 10)) + " m/s");
    }

    private static StepResult hold(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Vec3> now = Flight.position();
        if (now.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        Vec3 start = (Vec3) ctx.state().computeIfAbsent(START, key -> now.get());
        double deviation = Math.max((double) ctx.state().getOrDefault(DEVIATION, 0.0), now.get().distanceTo(start));
        ctx.state().put(DEVIATION, deviation);
        if (deviation > HOLD_LIMIT) {
            return StepResult.fail("weicht " + Flight.format(deviation) + " m ab (Grenze " + Flight.format(HOLD_LIMIT) + " m) nach "
                    + Flight.format(ticks / 20.0) + " s");
        }
        if (ticks < HOLD_TICKS) {
            return StepResult.pending();
        }
        return StepResult.pass("hält mit losgelassenen Knüppeln 5 s auf " + Flight.format(deviation) + " m genau, Höhe " + Flight.format(Flight.height())
                + " m");
    }

    private static StepResult mode(NavLogic.StickMode wanted, String label) {
        Optional<NavStatus> status = Pilot.status();
        if (status.isEmpty()) {
            return StepResult.fail("kein Status der Flughilfe");
        }
        if (status.get().stickMode() != wanted || !label.equals(status.get().label())) {
            return StepResult.fail("Modus " + status.get().stickMode() + " mit Anzeige " + status.get().label());
        }
        return StepResult.pass("Anzeige " + label + ", Klasse " + status.get().flightClass().key());
    }

    private static StepResult speed(SelfTest.StepContext ctx, int ticks, int duration, NavLogic.StickMode mode) {
        Flight.track(ctx);
        Pilot.hold(Pilot.PITCH_FORWARD, true);
        if (ticks < duration) {
            return StepResult.pending();
        }
        Pilot.hold(Pilot.PITCH_FORWARD, false);
        double stick = Math.abs(PropwashClientApi.input().pitch());
        double speed = Flight.horizontalSpeed(ctx, 20);
        FlightClass flightClass = Pilot.status().map(NavStatus::flightClass).orElse(FlightClass.TOUR);
        Optional<NavStatus> status = Pilot.status();
        if (status.isEmpty() || status.get().stickMode() != mode) {
            return StepResult.fail("Modus " + status.map(s -> s.stickMode().name()).orElse("unbekannt") + " statt " + mode);
        }
        String measured = mode + " " + Flight.format(speed) + " m/s bei Knüppel " + Flight.format(stick);
        switch (mode) {
            case NORMAL -> {
                double expected = stick * flightClass.normalSpeed();
                if (Math.abs(speed - expected) > 0.15 * expected + 0.3) {
                    return StepResult.fail(measured + ", erwartet " + Flight.format(expected) + " m/s");
                }
            }
            case CINE -> {
                double expected = stick * flightClass.cineSpeed();
                if (Math.abs(speed - expected) > 0.15 * expected + 0.15 || speed > flightClass.cineSpeed() + 0.2) {
                    return StepResult.fail(measured + ", erwartet " + Flight.format(expected) + " m/s, höchstens "
                            + Flight.format(flightClass.cineSpeed()) + " m/s");
                }
                ctx.state().put(CINE, speed);
            }
            case SPORT -> {
                double cine = (double) ctx.state().getOrDefault(CINE, flightClass.cineSpeed());
                if (speed < Math.max(2.0 * cine, stick * flightClass.cineSpeed() * 2.0) || speed > flightClass.sportSpeed() + 1.0) {
                    return StepResult.fail(measured + ", erwartet zwischen " + Flight.format(2.0 * cine) + " und "
                            + Flight.format(flightClass.sportSpeed()) + " m/s");
                }
            }
        }
        return StepResult.pass(measured);
    }

    private static void roof(MinecraftServer server, int dx, int dz) {
        ServerLevel level = Arena.level(server);
        BlockPos from = Arena.ground(dx - 12, dz - 12).above(7);
        BlockPos to = Arena.ground(dx + 12, dz + 12).above(7);
        Arena.fill(level, from, to, Blocks.STONE.defaultBlockState());
    }

    private static StepResult atti(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<NavStatus> status = Pilot.status();
        boolean atti = status.map(NavStatus::atti).orElse(false);
        if (atti && "ATTI".equals(status.get().label())) {
            return StepResult.pass("ATTI nach " + Flight.format(ticks / 20.0) + " s unter dem Dach, Anzeige " + status.get().label()
                    + ", Warnung NO GPS ATTI " + (status.get().warns(NavOutput.Warning.NO_GPS) ? "gesetzt" : "aus dem ATTI-Zustand"));
        }
        return ticks > 600 ? StepResult.fail("kein ATTI nach 30 s: " + status.map(s -> s.label() + ", Warnungen " + s.warnings()).orElse("kein Status"))
                : StepResult.pending();
    }

    private static StepResult attiHeight(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        double height = Flight.height();
        double start = (double) ctx.state().computeIfAbsent(START, key -> height);
        double deviation = Math.max((double) ctx.state().getOrDefault(DEVIATION, 0.0), Math.abs(height - start));
        ctx.state().put(DEVIATION, deviation);
        if (ticks % 10 == 0) {
            ctx.log("ATTI t=" + ticks + " Höhe " + Flight.format(height) + " m, senkrecht " + Flight.format(Flight.verticalSpeed(ctx, 10)) + " m/s");
        }
        if (deviation > ATTI_HEIGHT_LIMIT) {
            return StepResult.fail("Höhe ändert sich in ATTI um " + Flight.format(deviation) + " m");
        }
        return ticks < 100 ? StepResult.pending() : StepResult.pass("hält in ATTI 5 s die Höhe barometrisch auf " + Flight.format(deviation)
                + " m (Grenze " + Flight.format(ATTI_HEIGHT_LIMIT) + " m)");
    }

    private static void wall(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        DroneView view = PropwashApi.drones().view(drone).orElseThrow(() -> new IllegalStateException("keine Ansicht der Drohne"));
        Vec3 forward = view.forward();
        boolean alongX = Math.abs(forward.x) > Math.abs(forward.z);
        int sign = (alongX ? forward.x : forward.z) >= 0.0 ? 1 : -1;
        BlockPos base = BlockPos.containing(drone.getX(), Arena.ground().getY(), drone.getZ());
        BlockPos center = alongX ? base.offset(sign * 16, 0, 0) : base.offset(0, 0, sign * 16);
        BlockPos from = alongX ? center.offset(0, 1, -6) : center.offset(-6, 1, 0);
        BlockPos to = alongX ? center.offset(0, 10, 6) : center.offset(6, 10, 0);
        Arena.fill(Arena.level(server), from, to, Blocks.STONE.defaultBlockState());
        double face = sign > 0 ? (alongX ? center.getX() : center.getZ()) : (alongX ? center.getX() + 1 : center.getZ() + 1);
        state.put(WALL_AXIS, alongX);
        state.put(WALL_SIGN, sign);
        state.put(WALL_FACE, face);
    }

    private static double wallDistance(SelfTest.StepContext ctx) {
        Optional<Vec3> position = Flight.position();
        if (position.isEmpty()) {
            return Double.NaN;
        }
        boolean alongX = (boolean) ctx.state().get(WALL_AXIS);
        int sign = (int) ctx.state().get(WALL_SIGN);
        double face = (double) ctx.state().get(WALL_FACE);
        double coordinate = alongX ? position.get().x : position.get().z;
        return (face - coordinate) * sign;
    }

    private static StepResult towardsWall(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.hold(Pilot.PITCH_FORWARD, true);
        double distance = wallDistance(ctx);
        double closest = Math.min((double) ctx.state().getOrDefault(CLOSEST, Double.MAX_VALUE), distance);
        ctx.state().put(CLOSEST, closest);
        if (closest < ObstacleGuard.STOP_DISTANCE - WALL_TOLERANCE) {
            return StepResult.fail("kommt der Wand auf " + Flight.format(closest) + " m nahe (Stopp bei " + Flight.format(ObstacleGuard.STOP_DISTANCE)
                    + " m)");
        }
        if (ticks < 60) {
            return StepResult.pending();
        }
        StepResult calm = Flight.calm(ctx, ticks, 360);
        if (!calm.done()) {
            return calm;
        }
        if (calm.failed()) {
            return StepResult.fail("hält vor der Wand nicht an, Abstand " + Flight.format(distance) + " m");
        }
        Optional<NavStatus> status = Pilot.status();
        boolean warned = status.map(s -> s.warns(NavOutput.Warning.OBSTACLE) || s.warns(NavOutput.Warning.OBSTACLE_STOP)).orElse(false);
        if (!warned) {
            return StepResult.fail("steht " + Flight.format(distance) + " m vor der Wand, aber ohne Warnung OBSTACLE: "
                    + status.map(NavStatus::warnings).orElse(Set.of()));
        }
        return StepResult.pass("steht mit gedrücktem Knüppel " + Flight.format(distance) + " m vor der Wand (nächster Abstand "
                + Flight.format(closest) + " m, Sensor " + status.get().nearestSensor().map(Enum::name).orElse("-") + " "
                + Flight.format(status.get().nearestDistance()) + " m), Warnungen " + status.get().warnings());
    }
}
