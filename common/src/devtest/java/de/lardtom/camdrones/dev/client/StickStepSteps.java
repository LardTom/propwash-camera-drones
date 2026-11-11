package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.client.hud.CamOsd;
import de.lardtom.camdrones.client.hud.CamOsdSource;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class StickStepSteps {
    public static final String NAME = "flug: Tempo-Antwort auf Knüppel-Sprung";
    public static final int NUMBER = 621;
    public static final String PITCH_BACK = "key.propwash.kb_pitch_back";
    public static final double REACHED = 0.9;
    public static final double HALF = 0.5;
    public static final double NORMAL_SECONDS = 2.5;
    public static final double CINE_SECONDS = 3.0;
    public static final double SPORT_SECONDS_PER_SPEED = 0.3;
    public static final double SPORT_OVERSHOOT = 1.1;
    public static final double HEIGHT_DROP = 1.5;
    public static final int HINT_TICKS = 100;
    public static final double PULL_BACK = 0.3;
    public static final double DIP = 0.3;
    public static final double STOPPED = 0.3;
    public static final int SPEED_WINDOW = 10;
    public static final int MAX_PUSH_TICKS = 20 * 8;
    public static final int MAX_SPORT_TICKS = 20 * 10;
    public static final int AFTER_REACHED_TICKS = 20;
    public static final int HOLD_TICKS = 60;
    public static final int MAX_RELEASE_TICKS = 20 * 10;
    private static final int STEP_TICKS = 20 * 240;
    private static final String LOG = "stickstep.log";
    private static final String PROBLEMS = "stickstep.problems";
    private static final String PEAK = "stickstep.peak";
    private static final String HALF_AT = "stickstep.half";
    private static final String REACHED_AT = "stickstep.reached";
    private static final String TILT = "stickstep.tilt";
    private static final String DIPPED = "stickstep.dipped";
    private static final String LABELS = "stickstep.labels";
    private static final String ORIGIN = "stickstep.origin";
    private static final String AXIS = "stickstep.axis";
    private static final String FURTHEST = "stickstep.furthest";
    private static final String STOPPED_AT = "stickstep.stopped";
    private static final String PULLED = "stickstep.pulled";
    private static final String TOP_AT = "stickstep.top";
    private static final String PREVIOUS = "stickstep.previous";
    private static final String START_HEIGHT = "stickstep.startHeight";
    private static final String LOWEST = "stickstep.lowest";

    private StickStepSteps() {
    }

    public static void register() {
        List<CamPresets.Entry> entries = List.of(CamPresets.COMPACT, CamPresets.TOUR, CamPresets.CINE);
        for (int i = 0; i < entries.size(); i++) {
            CamPresets.Entry entry = entries.get(i);
            FlightClass flightClass = entry.flightClass();
            SelfTest.clientStep(NUMBER + i, NAME + " – " + entry.path(), STEP_TICKS, Script.create()
                    .server("Wind aus", (server, state) -> Arena.gameRule(server, Arena.WIND, "0"))
                    .append(KeyboardDeflection.set(KeyboardDeflection.MAX_PERCENT))
                    .append(Pilot.launch(entry, -60 - 10 * i, -110, 0.0F))
                    .then("OSD-Hinweis beim Start", StickStepSteps::startHint)
                    .append(Flight.gpsFix())
                    .then("OSD-Hinweis N mit GPS-Fix", (ctx, ticks) -> hint(ctx, ticks, "N"))
                    .append(Flight.climb(8.0))
                    .then("N: Knüppel voll vorwärts", (ctx, ticks) -> push(ctx, ticks, flightClass, NavLogic.StickMode.NORMAL, Pilot.PITCH_FORWARD))
                    .then("N: loslassen", (ctx, ticks) -> release(ctx, ticks, NavLogic.StickMode.NORMAL, Pilot.PITCH_FORWARD))
                    .append(Flight.mode(NavLogic.StickMode.CINE))
                    .then("OSD-Hinweis C", (ctx, ticks) -> hint(ctx, ticks, "C"))
                    .then("C: Knüppel voll rückwärts", (ctx, ticks) -> push(ctx, ticks, flightClass, NavLogic.StickMode.CINE, PITCH_BACK))
                    .then("C: loslassen", (ctx, ticks) -> release(ctx, ticks, NavLogic.StickMode.CINE, PITCH_BACK))
                    .append(Flight.mode(NavLogic.StickMode.SPORT))
                    .then("OSD-Hinweis S", (ctx, ticks) -> hint(ctx, ticks, "S"))
                    .then("S: Knüppel voll vorwärts", (ctx, ticks) -> push(ctx, ticks, flightClass, NavLogic.StickMode.SPORT, Pilot.PITCH_FORWARD))
                    .then("S: loslassen", (ctx, ticks) -> {
                        Flight.track(ctx);
                        Flight.position().ifPresent(p -> ctx.state().put(PREVIOUS, p));
                        Pilot.hold(Pilot.PITCH_FORWARD, false);
                        return ticks >= 10 ? StepResult.pass() : StepResult.pending();
                    })
                    .append(Flight.mode(NavLogic.StickMode.NORMAL))
                    .then("OSD-Hinweis N nach S", (ctx, ticks) -> hint(ctx, ticks, "N"))
                    .then("N nach S: bremsen", (ctx, ticks) -> release(ctx, ticks, NavLogic.StickMode.NORMAL, Pilot.PITCH_FORWARD))
                    .then("Ergebnis", (ctx, ticks) -> {
                        String problems = (String) ctx.state().getOrDefault(PROBLEMS, "");
                        String log = (String) ctx.state().getOrDefault(LOG, "");
                        return problems.isEmpty() ? StepResult.pass(log) : StepResult.fail(problems + " (alle Werte: " + log + ")");
                    }), ctx -> {
                        Pilot.releaseKeys();
                        return KeyboardDeflection.restore(ctx);
                    });
        }
    }

    public static double topSpeed(FlightClass flightClass, NavLogic.StickMode mode) {
        return switch (mode) {
            case NORMAL -> flightClass.normalSpeed();
            case CINE -> flightClass.cineSpeed();
            case SPORT -> flightClass.sportSpeed();
        };
    }

    public static double limitSeconds(FlightClass flightClass, NavLogic.StickMode mode) {
        return switch (mode) {
            case NORMAL -> NORMAL_SECONDS;
            case CINE -> CINE_SECONDS;
            case SPORT -> SPORT_SECONDS_PER_SPEED * flightClass.sportSpeed();
        };
    }

    public static String label(NavLogic.StickMode mode) {
        return switch (mode) {
            case NORMAL -> "N";
            case CINE -> "C";
            case SPORT -> "S";
        };
    }

    private static double tilt(Entity drone) {
        return PropwashApi.drones().view(drone).map(DroneView::up).map(up -> Math.toDegrees(Math.acos(Math.clamp(up.y, -1.0, 1.0))))
                .orElse(Double.NaN);
    }

    public static double normalTop(FlightClass flightClass) {
        double speed = flightClass.normalSpeed();
        return flightClass.allAroundSensors() ? Math.min(speed, ObstacleGuard.commandLimit(Double.POSITIVE_INFINITY)) : speed;
    }

    private static double reference(FlightClass flightClass, NavLogic.StickMode mode) {
        return switch (mode) {
            case NORMAL -> normalTop(flightClass);
            case CINE -> flightClass.cineSpeed();
            case SPORT -> flightClass.sportSpeed();
        };
    }

    private static StepResult startHint(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        String mode = CamOsdSource.lastHintMode();
        if (mode.equals("N") || mode.equals("ATTI")) {
            log(ctx, "OSD-Hinweis beim Start für " + mode + (mode.equals("ATTI") ? " (noch kein GPS-Fix)" : ""));
            return StepResult.pass();
        }
        return ticks > HINT_TICKS ? StepResult.fail("kein OSD-Hinweis beim Start (zuletzt „" + mode + "“)") : StepResult.pending();
    }

    private static StepResult hint(SelfTest.StepContext ctx, int ticks, String mode) {
        Flight.track(ctx);
        String shown = CamOsdSource.last().hint();
        if (mode.equals(shown)) {
            String text = String.valueOf(CamOsd.modeHint(CamOsdSource.last()).text());
            if (!text.startsWith(mode + ":")) {
                return StepResult.fail("OSD-Hinweis „" + text + "“ für " + mode);
            }
            log(ctx, "OSD-Hinweis „" + text + "“ nach " + Flight.format(ticks / 20.0) + " s");
            return StepResult.pass();
        }
        return ticks > HINT_TICKS ? StepResult.fail("kein OSD-Hinweis für " + mode + " (gezeigt: „" + shown + "“, Anzeige "
                + Pilot.status().map(NavStatus::label).orElse("-") + ")") : StepResult.pending();
    }

    private static StepResult push(SelfTest.StepContext ctx, int ticks, FlightClass flightClass, NavLogic.StickMode mode, String key) {
        Flight.track(ctx);
        Pilot.hold(key, true);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks == 0) {
            ctx.state().put(PEAK, 0.0);
            ctx.state().put(TILT, 0.0);
            ctx.state().put(DIPPED, 0.0);
            ctx.state().remove(HALF_AT);
            ctx.state().remove(REACHED_AT);
            ctx.state().remove(TOP_AT);
            ctx.state().put(LABELS, "");
            ctx.state().put(START_HEIGHT, Flight.height());
            ctx.state().put(LOWEST, Flight.height());
        }
        ctx.state().put(LOWEST, Math.min((double) ctx.state().get(LOWEST), Flight.height()));
        double speed = Flight.horizontalSpeed(ctx, SPEED_WINDOW);
        double reference = reference(flightClass, mode);
        double peak = (double) ctx.state().get(PEAK);
        if (ticks > SPEED_WINDOW && Double.isFinite(speed)) {
            ctx.state().put(DIPPED, Math.max((double) ctx.state().get(DIPPED), peak - speed));
            peak = Math.max(peak, speed);
            ctx.state().put(PEAK, peak);
            double seconds = (ticks - SPEED_WINDOW / 2.0) / 20.0;
            if (speed >= HALF * reference) {
                ctx.state().putIfAbsent(HALF_AT, seconds);
            }
            if (speed >= REACHED * reference) {
                ctx.state().putIfAbsent(REACHED_AT, seconds);
            }
            if (speed >= REACHED * topSpeed(flightClass, mode)) {
                ctx.state().putIfAbsent(TOP_AT, seconds);
            }
        }
        Flight.position().ifPresent(p -> ctx.state().put(PREVIOUS, p));
        ctx.state().put(TILT, Math.max((double) ctx.state().get(TILT), tilt(drone.get())));
        String shown = Pilot.status().map(NavStatus::label).orElse("-");
        String labels = (String) ctx.state().get(LABELS);
        if (!labels.contains("[" + shown + "]")) {
            ctx.state().put(LABELS, labels + "[" + shown + "]");
        }
        if (ticks % 5 == 0) {
            ctx.log(label(mode) + " " + flightClass.key() + " t=" + Flight.format(ticks / 20.0) + " s: " + Flight.format(speed) + " m/s, Neigung "
                    + Flight.format(tilt(drone.get())) + "°, Knüppel " + Flight.stick() + ", Höhe " + Flight.format(Flight.height()) + " m, x "
                    + Flight.format(drone.get().getX() - Arena.spawn().x) + ", z " + Flight.format(drone.get().getZ() - Arena.spawn().z) + ", Anzeige " + shown);
        }
        Object reachedAt = ctx.state().get(REACHED_AT);
        Object topAt = ctx.state().get(TOP_AT);
        boolean done = topAt != null && ticks >= Math.round((double) topAt * 20.0) + SPEED_WINDOW / 2 + AFTER_REACHED_TICKS;
        int maxTicks = mode == NavLogic.StickMode.SPORT ? MAX_SPORT_TICKS : MAX_PUSH_TICKS;
        if (!done && ticks < maxTicks) {
            return StepResult.pending();
        }
        String what = mode == NavLogic.StickMode.SPORT ? "Sport-Tempo" : "Höchsttempo";
        if (mode == NavLogic.StickMode.NORMAL && reference < flightClass.normalSpeed()) {
            what = "Höchsttempo mit Sensoren";
        }
        String line = label(mode) + " " + flightClass.key() + ": 90 % vom " + what + " " + Flight.format(reference) + " m/s nach "
                + (reachedAt == null ? "nie (" + maxTicks / 20 + " s)" : Flight.format((double) reachedAt) + " s") + ", 50 % nach "
                + (ctx.state().get(HALF_AT) == null ? "nie" : Flight.format((double) ctx.state().get(HALF_AT)) + " s") + ", Spitze "
                + Flight.format(peak) + " m/s, Höhe " + Flight.format((double) ctx.state().get(START_HEIGHT)) + " m, tiefste "
                + Flight.format((double) ctx.state().get(LOWEST)) + " m, größte Neigung " + Flight.format((double) ctx.state().get(TILT))
                + "°, Knüppel " + Flight.stick() + ", Anzeige " + ctx.state().get(LABELS);
        log(ctx, line);
        double limit = limitSeconds(flightClass, mode);
        if (reachedAt == null || (double) reachedAt > limit) {
            problem(ctx, label(mode) + " " + flightClass.key() + " erreicht 90 % vom " + what + " nicht in " + Flight.format(limit) + " s");
        }
        if (mode == NavLogic.StickMode.SPORT && peak > SPORT_OVERSHOOT * reference) {
            problem(ctx, label(mode) + " " + flightClass.key() + " wird mit " + Flight.format(peak) + " m/s schneller als "
                    + Flight.format(SPORT_OVERSHOOT * 100.0) + " % vom Sport-Tempo " + Flight.format(reference) + " m/s");
        }
        if ((double) ctx.state().get(DIPPED) > DIP && (reachedAt == null)) {
            problem(ctx, label(mode) + " " + flightClass.key() + " wird mit vollem Knüppel um " + Flight.format((double) ctx.state().get(DIPPED))
                    + " m/s langsamer");
        }
        double drop = (double) ctx.state().get(START_HEIGHT) - (double) ctx.state().get(LOWEST);
        if (drop > HEIGHT_DROP) {
            problem(ctx, label(mode) + " " + flightClass.key() + " sinkt mit vollem Knüppel " + Flight.format(drop) + " m (Grenze "
                    + Flight.format(HEIGHT_DROP) + " m)");
        }
        if (!ctx.state().get(LABELS).equals("[" + label(mode) + "]")) {
            problem(ctx, label(mode) + " " + flightClass.key() + " zeigt mit vollem Knüppel " + ctx.state().get(LABELS));
        }
        return StepResult.pass();
    }

    private static StepResult release(SelfTest.StepContext ctx, int ticks, NavLogic.StickMode mode, String key) {
        Flight.track(ctx);
        Pilot.hold(Pilot.PITCH_FORWARD, false);
        Pilot.hold(PITCH_BACK, false);
        Optional<Vec3> now = Flight.position();
        if (now.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks == 0) {
            Optional<Entity> drone = Pilot.drone();
            Vec3 previous = (Vec3) ctx.state().getOrDefault(PREVIOUS, now.get());
            Vec3 axis = new Vec3(now.get().x - previous.x, 0.0, now.get().z - previous.z);
            if (axis.length() < 1.0E-3) {
                axis = drone.flatMap(d -> PropwashApi.drones().view(d)).map(DroneView::forward).map(f -> new Vec3(f.x, 0.0, f.z))
                        .orElse(new Vec3(0.0, 0.0, 1.0));
                if (PITCH_BACK.equals(key)) {
                    axis = axis.scale(-1.0);
                }
            }
            ctx.state().put(ORIGIN, now.get());
            ctx.state().put(AXIS, axis.normalize());
            ctx.state().put(FURTHEST, 0.0);
            ctx.state().put(PULLED, 0.0);
            ctx.state().remove(STOPPED_AT);
            return StepResult.pending();
        }
        Vec3 origin = (Vec3) ctx.state().get(ORIGIN);
        Vec3 axis = (Vec3) ctx.state().get(AXIS);
        double along = now.get().subtract(origin).dot(axis);
        double furthest = Math.max((double) ctx.state().get(FURTHEST), along);
        ctx.state().put(FURTHEST, furthest);
        ctx.state().put(PULLED, Math.max((double) ctx.state().get(PULLED), furthest - along));
        double speed = Flight.horizontalSpeed(ctx, SPEED_WINDOW);
        if (ticks % 5 == 0) {
            ctx.log(label(mode) + " loslassen t=" + Flight.format(ticks / 20.0) + " s: " + Flight.format(speed) + " m/s, Weg " + Flight.format(along)
                    + " m, Anzeige " + Pilot.status().map(NavStatus::label).orElse("-"));
        }
        if (ticks > SPEED_WINDOW && Double.isFinite(speed) && speed < STOPPED) {
            ctx.state().putIfAbsent(STOPPED_AT, ticks);
        }
        Object stoppedAt = ctx.state().get(STOPPED_AT);
        if (stoppedAt == null) {
            if (ticks > MAX_RELEASE_TICKS) {
                problem(ctx, label(mode) + " steht nach dem Loslassen nach 10 s noch nicht (" + Flight.format(speed) + " m/s)");
                return StepResult.pass();
            }
            return StepResult.pending();
        }
        if (ticks < (int) stoppedAt + HOLD_TICKS) {
            return StepResult.pending();
        }
        double pulled = (double) ctx.state().get(PULLED);
        log(ctx, label(mode) + " loslassen: steht nach " + Flight.format((int) stoppedAt / 20.0) + " s, Bremsweg " + Flight.format(furthest)
                + " m, zurückgezogen " + Flight.format(pulled) + " m");
        if (pulled > PULL_BACK) {
            problem(ctx, label(mode) + " zieht nach dem Loslassen " + Flight.format(pulled) + " m zurück (Grenze " + Flight.format(PULL_BACK) + " m)");
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
