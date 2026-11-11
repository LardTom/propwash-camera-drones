package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class StormStepSteps {
    public static final String NAME = "flug: Loslassen im Sturm, auch nach S";
    public static final int NUMBER = 624;
    public static final String WIND_LEVEL = "2";
    public static final String WEATHER = "weather rain 6000";
    public static final String CLEAR = "weather clear";
    public static final double PULL_BACK = 0.5;
    public static final double MIN_TILT = 5.0;
    public static final int HOVER_TICKS = 20 * 12;
    public static final int PUSH_TICKS = 20 * 3;
    public static final int RELEASE_TICKS = 20 * 8;
    public static final int SPORT_TICKS = 50;
    public static final int AFTER_SPORT_TICKS = 20 * 10;
    public static final int CLEAR_TICKS = 20 * 6;
    public static final String PITCH_BACK = "key.propwash.kb_pitch_back";
    public static final String ROLL_LEFT = "key.propwash.kb_roll_left";
    public static final String ROLL_RIGHT = "key.propwash.kb_roll_right";
    public static final List<String> KEYS = List.of(Pilot.PITCH_FORWARD, PITCH_BACK, ROLL_LEFT, ROLL_RIGHT);
    private static final int STEP_TICKS = 20 * 300;
    private static final String LOG = "storm.log";
    private static final String PROBLEMS = "storm.problems";
    private static final String UP_X = "storm.upX";
    private static final String UP_Z = "storm.upZ";
    private static final String TILT = "storm.tilt";
    private static final String SAMPLES = "storm.samples";
    private static final String HOVER_START = "storm.hoverStart";
    private static final String WANDER = "storm.wander";
    private static final String UPWIND = "storm.upwind";
    private static final String ACROSS = "storm.across";
    private static final String DOWNWIND = "storm.downwind";
    private static final String ORIGIN = "storm.origin";
    private static final String AXIS = "storm.axis";
    private static final String SPEED = "storm.speed";
    private static final String FURTHEST = "storm.furthest";
    private static final String PULLED = "storm.pulled";
    private static final String PREVIOUS = "storm.previous";
    private static final String CLEARED = "storm.cleared";

    private StormStepSteps() {
    }

    public static void register() {
        SelfTest.clientStep(NUMBER, NAME, STEP_TICKS, Script.create()
                .server("Wind " + WIND_LEVEL + " und Regen", (server, state) -> {
                    Arena.gameRule(server, Arena.WIND, WIND_LEVEL);
                    Arena.command(server, WEATHER);
                })
                .append(KeyboardDeflection.set(KeyboardDeflection.MAX_PERCENT))
                .append(Pilot.launch(CamPresets.TOUR, -40, -60, 0.0F))
                .append(Flight.gpsFix())
                .append(Flight.climb(8.0))
                .then("schweben und Wind bestimmen", StormStepSteps::hover)
                .then("N gegen den Wind", (ctx, ticks) -> push(ctx, ticks, UPWIND))
                .then("N loslassen gegen den Wind", (ctx, ticks) -> release(ctx, ticks, "N gegen den Wind"))
                .append(Flight.mode(NavLogic.StickMode.CINE))
                .append(Flight.mode(NavLogic.StickMode.SPORT))
                .then("S mit dem Wind", StormStepSteps::sport)
                .append(Flight.mode(NavLogic.StickMode.NORMAL))
                .then("halten nach S", StormStepSteps::afterSport)
                .then("N nach S gegen den Wind", (ctx, ticks) -> push(ctx, ticks, UPWIND))
                .then("N nach S loslassen gegen den Wind", (ctx, ticks) -> release(ctx, ticks, "N nach S gegen den Wind"))
                .then("N nach S quer zum Wind", (ctx, ticks) -> push(ctx, ticks, ACROSS))
                .then("N nach S loslassen quer zum Wind", (ctx, ticks) -> release(ctx, ticks, "N nach S quer zum Wind"))
                .server("Wetter klar, Wind ruhig", (server, state) -> {
                    Arena.command(server, CLEAR);
                    Arena.gameRule(server, Arena.WIND, String.valueOf(Arena.CALM_WIND));
                })
                .then("Regen hört auf", (ctx, ticks) -> {
                    Flight.track(ctx);
                    if (ticks < CLEAR_TICKS) {
                        return StepResult.pending();
                    }
                    Flight.position().ifPresent(p -> log(ctx, "Drohne bei x " + Flight.format(p.x - Arena.spawn().x) + ", z "
                            + Flight.format(p.z - Arena.spawn().z) + " zum Start der Arena"));
                    return StepResult.pass();
                })
                .then("Ergebnis", (ctx, ticks) -> {
                    String problems = (String) ctx.state().getOrDefault(PROBLEMS, "");
                    String log = (String) ctx.state().getOrDefault(LOG, "");
                    return problems.isEmpty() ? StepResult.pass(log) : StepResult.fail(problems + " (alle Werte: " + log + ")");
                }), ctx -> {
                    Pilot.releaseKeys();
                    if (!ctx.state().containsKey(CLEARED)) {
                        ctx.state().put(CLEARED, true);
                        ctx.onServer(server -> Arena.command(server, CLEAR));
                    }
                    return KeyboardDeflection.restore(ctx);
                });
    }

    private static Optional<DroneView> view() {
        return Pilot.drone().flatMap(d -> PropwashApi.drones().view(d));
    }

    private static double tilt(DroneView view) {
        return Math.toDegrees(Math.acos(Math.clamp(view.up().y, -1.0, 1.0)));
    }

    private static StepResult hover(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<DroneView> view = view();
        Optional<Vec3> now = Flight.position();
        if (view.isEmpty() || now.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks < HOVER_TICKS / 2) {
            return StepResult.pending();
        }
        Vec3 start = (Vec3) ctx.state().computeIfAbsent(HOVER_START, key -> now.get());
        Vec3 up = view.get().up();
        ctx.state().merge(UP_X, up.x, (a, b) -> (double) a + (double) b);
        ctx.state().merge(UP_Z, up.z, (a, b) -> (double) a + (double) b);
        ctx.state().merge(TILT, tilt(view.get()), (a, b) -> (double) a + (double) b);
        ctx.state().merge(SAMPLES, 1, (a, b) -> (int) a + (int) b);
        double dx = now.get().x - start.x;
        double dz = now.get().z - start.z;
        ctx.state().put(WANDER, Math.max((double) ctx.state().getOrDefault(WANDER, 0.0), Math.hypot(dx, dz)));
        if (ticks < HOVER_TICKS) {
            return StepResult.pending();
        }
        int samples = (int) ctx.state().get(SAMPLES);
        double tilt = (double) ctx.state().get(TILT) / samples;
        Vec3 upwind = new Vec3((double) ctx.state().get(UP_X), 0.0, (double) ctx.state().get(UP_Z));
        if (tilt < MIN_TILT || upwind.length() < 1.0E-3) {
            return StepResult.fail("kein Sturm: mittlere Neigung im Schweben " + Flight.format(tilt) + "° (mindestens " + Flight.format(MIN_TILT) + "°)");
        }
        upwind = upwind.normalize();
        Vec3 forward = view.get().forward();
        Vec3 nose = new Vec3(forward.x, 0.0, forward.z).normalize();
        Vec3 right = new Vec3(-nose.z, 0.0, nose.x);
        String against = best(upwind, nose, right);
        String across = best(new Vec3(-upwind.z, 0.0, upwind.x), nose, right);
        String with = best(upwind.scale(-1.0), nose, right);
        ctx.state().put(UPWIND, against);
        ctx.state().put(ACROSS, across);
        ctx.state().put(DOWNWIND, with);
        log(ctx, "Neigung im Schweben " + Flight.format(tilt) + "°, Abweichung beim Halten " + Flight.format((double) ctx.state().get(WANDER))
                + " m, gegen den Wind mit " + against + ", quer mit " + across + ", Anzeige " + Pilot.status().map(NavStatus::label).orElse("-"));
        return StepResult.pass();
    }

    private static String best(Vec3 wanted, Vec3 nose, Vec3 right) {
        double forward = wanted.dot(nose);
        double side = wanted.dot(right);
        if (Math.abs(forward) >= Math.abs(side)) {
            return forward >= 0.0 ? Pilot.PITCH_FORWARD : PITCH_BACK;
        }
        return side >= 0.0 ? ROLL_RIGHT : ROLL_LEFT;
    }

    private static StepResult push(SelfTest.StepContext ctx, int ticks, String which) {
        Flight.track(ctx);
        String key = (String) ctx.state().get(which);
        Pilot.hold(key, true);
        if (Pilot.drone().isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        ctx.state().put(SPEED, Flight.horizontalSpeed(ctx, 10));
        Flight.position().ifPresent(p -> ctx.state().put(PREVIOUS, p));
        return ticks >= PUSH_TICKS ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult sport(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.hold((String) ctx.state().get(DOWNWIND), ticks < SPORT_TICKS);
        if (ticks == SPORT_TICKS) {
            log(ctx, "S mit dem Wind " + Flight.format(Flight.horizontalSpeed(ctx, 10)) + " m/s, Anzeige " + Pilot.status().map(NavStatus::label).orElse("-"));
        }
        return ticks >= SPORT_TICKS + 10 ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult afterSport(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        if (Pilot.drone().isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks < AFTER_SPORT_TICKS) {
            return StepResult.pending();
        }
        log(ctx, "nach S 10 s gehalten: " + Flight.format(Flight.horizontalSpeed(ctx, 10)) + " m/s, Neigung " + view().map(StormStepSteps::tilt)
                .map(Flight::format).orElse("-") + "°, Anzeige " + Pilot.status().map(NavStatus::label).orElse("-"));
        return StepResult.pass();
    }

    private static StepResult release(SelfTest.StepContext ctx, int ticks, String name) {
        Flight.track(ctx);
        for (String key : KEYS) {
            Pilot.hold(key, false);
        }
        Optional<Vec3> now = Flight.position();
        if (now.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks == 0) {
            Vec3 previous = (Vec3) ctx.state().getOrDefault(PREVIOUS, now.get());
            Vec3 flat = new Vec3(now.get().x - previous.x, 0.0, now.get().z - previous.z);
            ctx.state().put(ORIGIN, now.get());
            ctx.state().put(AXIS, flat.length() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize());
            ctx.state().put(FURTHEST, 0.0);
            ctx.state().put(PULLED, 0.0);
            return StepResult.pending();
        }
        Vec3 origin = (Vec3) ctx.state().get(ORIGIN);
        Vec3 axis = (Vec3) ctx.state().get(AXIS);
        double along = now.get().subtract(origin).dot(axis);
        double furthest = Math.max((double) ctx.state().get(FURTHEST), along);
        ctx.state().put(FURTHEST, furthest);
        ctx.state().put(PULLED, Math.max((double) ctx.state().get(PULLED), furthest - along));
        if (ticks % 10 == 0) {
            ctx.log(name + " t=" + Flight.format(ticks / 20.0) + " s: Weg " + Flight.format(along) + " m, " + Flight.format(Flight.horizontalSpeed(ctx, 10))
                    + " m/s, Neigung " + view().map(StormStepSteps::tilt).map(Flight::format).orElse("-") + "°");
        }
        if (ticks < RELEASE_TICKS) {
            return StepResult.pending();
        }
        double pulled = (double) ctx.state().get(PULLED);
        log(ctx, name + ": " + Flight.format((double) ctx.state().get(SPEED)) + " m/s, Bremsweg " + Flight.format(furthest) + " m, zurückgezogen "
                + Flight.format(pulled) + " m, Anzeige " + Pilot.status().map(NavStatus::label).orElse("-"));
        double limit = Math.max(PULL_BACK, (double) ctx.state().getOrDefault(WANDER, 0.0));
        if (pulled > limit) {
            problem(ctx, name + " zieht nach dem Loslassen " + Flight.format(pulled) + " m zurück (Grenze " + Flight.format(limit)
                    + " m, " + Flight.format(PULL_BACK) + " m oder die Abweichung beim Halten)");
        }
        return StepResult.pass();
    }

    private static void problem(SelfTest.StepContext ctx, String line) {
        String previous = (String) ctx.state().getOrDefault(PROBLEMS, "");
        ctx.state().put(PROBLEMS, previous.isEmpty() ? line : previous + "; " + line);
    }

    private static void log(SelfTest.StepContext ctx, String line) {
        String previous = (String) ctx.state().getOrDefault(LOG, "");
        ctx.state().put(LOG, previous.isEmpty() ? line : previous + "; " + line);
        ctx.log(line);
    }
}
