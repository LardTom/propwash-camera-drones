package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SportLoadSteps {
    public static final String NAME = "flug: Sport unter Last ohne Ruckeln";
    public static final int NUMBER = 641;
    public static final String PITCH_BACK = "key.propwash.kb_pitch_back";
    public static final double HALF = 0.5;
    public static final double THREE_QUARTERS = 0.75;
    public static final double FULL = 1.0;
    public static final double THROTTLE_SLACK = 0.04;
    public static final double FULL_SLACK = 0.005;
    public static final double WARMUP_SECONDS = 1.5;
    public static final double SWING = 0.5;
    public static final int MAX_SWINGS = 2;
    public static final double FROZEN = 1.0E-3;
    public static final double MAX_TILT_RATE = 12.0;
    public static final int RATE_TICKS = 2;
    public static final double HEIGHT_DROP = 1.5;
    public static final double CLIMB_SHARE = 0.25;
    public static final int HALF_TICKS = 20 * 10;
    public static final int THREE_QUARTER_TICKS = 20 * 8;
    public static final int FULL_TICKS = 20 * 6;
    public static final int SPEED_WINDOW = 10;
    private static final int STEP_TICKS = 20 * 300;
    private static final String LOG = "sportload.log";
    private static final String PROBLEMS = "sportload.problems";
    private static final String TILTS = "sportload.tilts";
    private static final String HEIGHTS = "sportload.heights";
    private static final String SPEEDS = "sportload.speeds";
    private static final String MOVED = "sportload.moved";
    private static final String LAST = "sportload.last";

    private SportLoadSteps() {
    }

    public static void register() {
        List<CamPresets.Entry> entries = List.of(CamPresets.COMPACT, CamPresets.TOUR, CamPresets.CINE);
        for (int i = 0; i < entries.size(); i++) {
            CamPresets.Entry entry = entries.get(i);
            FlightClass flightClass = entry.flightClass();
            SelfTest.clientStep(NUMBER + i, NAME + " – " + entry.path(), STEP_TICKS, Script.create()
                    .server("Wind aus", (server, state) -> Arena.gameRule(server, Arena.WIND, "0"))
                    .append(KeyboardDeflection.set(KeyboardDeflection.MAX_PERCENT))
                    .append(Pilot.launch(entry, -100 - 10 * i, -140, 0.0F))
                    .append(Flight.gpsFix())
                    .append(Flight.climb(8.0))
                    .append(sport())
                    .then("S 50 %: Knüppel voll vorwärts", (ctx, ticks) -> push(ctx, ticks, flightClass, HALF, Pilot.PITCH_FORWARD, HALF_TICKS))
                    .append(stop())
                    .append(sport())
                    .then("S 75 %: Knüppel voll rückwärts", (ctx, ticks) -> push(ctx, ticks, flightClass, THREE_QUARTERS, PITCH_BACK, THREE_QUARTER_TICKS))
                    .append(stop())
                    .append(sport())
                    .then("S 100 %: Knüppel voll vorwärts", (ctx, ticks) -> push(ctx, ticks, flightClass, FULL, Pilot.PITCH_FORWARD, FULL_TICKS))
                    .append(stop())
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

    private static Script sport() {
        return Flight.mode(NavLogic.StickMode.CINE).append(Flight.mode(NavLogic.StickMode.SPORT));
    }

    private static Script stop() {
        return Script.create()
                .then("loslassen", (ctx, ticks) -> {
                    Flight.track(ctx);
                    Pilot.hold(Pilot.PITCH_FORWARD, false);
                    Pilot.hold(PITCH_BACK, false);
                    boolean centred = Pilot.steer(Pilot.THROTTLE_CENTER);
                    return centred && ticks >= 10 ? StepResult.pass() : ticks > 200 ? StepResult.fail("Gas kommt nicht in die Mitte") : StepResult.pending();
                })
                .append(Flight.mode(NavLogic.StickMode.NORMAL))
                .then("bremsen", Flight::brake);
    }

    private static double tilt(Entity drone) {
        return PropwashApi.drones().view(drone).map(DroneView::up).map(up -> Math.toDegrees(Math.acos(Math.clamp(up.y, -1.0, 1.0))))
                .orElse(Double.NaN);
    }

    @SuppressWarnings("unchecked")
    private static StepResult push(SelfTest.StepContext ctx, int ticks, FlightClass flightClass, double throttle, String key, int duration) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks == 0) {
            ctx.state().put(TILTS, new ArrayList<Double>());
            ctx.state().put(HEIGHTS, new ArrayList<Double>());
            ctx.state().put(SPEEDS, new ArrayList<Double>());
            ctx.state().put(MOVED, new ArrayList<Boolean>());
            ctx.state().remove(LAST);
        }
        steer(throttle);
        Pilot.hold(key, true);
        List<Double> tilts = (List<Double>) ctx.state().get(TILTS);
        List<Double> heights = (List<Double>) ctx.state().get(HEIGHTS);
        List<Double> speeds = (List<Double>) ctx.state().get(SPEEDS);
        List<Boolean> moved = (List<Boolean>) ctx.state().get(MOVED);
        Vec3 position = drone.get().position();
        Object last = ctx.state().put(LAST, position);
        moved.add(!(last instanceof Vec3 before) || before.distanceTo(position) > FROZEN);
        tilts.add(tilt(drone.get()));
        heights.add(Flight.height());
        speeds.add(Flight.horizontalSpeed(ctx, SPEED_WINDOW));
        String label = label(throttle);
        if (ticks % 5 == 0) {
            ctx.log(label + " " + flightClass.key() + " t=" + Flight.format(ticks / 20.0) + " s: Neigung " + Flight.format(tilts.getLast()) + "°, "
                    + Flight.format(speeds.getLast()) + " m/s, Höhe " + Flight.format(heights.getLast()) + " m, Gas "
                    + Flight.format(PropwashClientApi.input().throttle()) + ", Anzeige " + Pilot.status().map(NavStatus::label).orElse("-"));
        }
        if (ticks < duration) {
            return StepResult.pending();
        }
        Pilot.hold(key, false);
        evaluate(ctx, flightClass, throttle, tilts, heights, speeds, moved);
        return StepResult.pass();
    }

    private static void steer(double throttle) {
        double now = PropwashClientApi.input().throttle();
        if (throttle >= FULL - THROTTLE_SLACK) {
            Minecraft mc = Minecraft.getInstance();
            Pilot.hold(mc.options.keyDown, false);
            Pilot.hold(mc.options.keyUp, now < FULL - FULL_SLACK);
            return;
        }
        Pilot.steer(throttle);
    }

    private static String label(double throttle) {
        return "S " + Math.round(throttle * 100.0) + " %";
    }

    private static void evaluate(SelfTest.StepContext ctx, FlightClass flightClass, double throttle, List<Double> tilts, List<Double> heights,
                                 List<Double> speeds, List<Boolean> moved) {
        int from = (int) Math.round(WARMUP_SECONDS * 20.0);
        int swings = swings(tilts, from, SWING);
        double rate = maxRate(tilts, moved, from, RATE_TICKS);
        int frozen = 0;
        for (int i = from; i < moved.size(); i++) {
            frozen += moved.get(i) ? 0 : 1;
        }
        double start = heights.getFirst();
        double lowest = start;
        for (double h : heights) {
            lowest = Math.min(lowest, h);
        }
        double end = heights.getLast();
        double minTilt = Double.POSITIVE_INFINITY;
        double maxTilt = 0.0;
        double peak = 0.0;
        for (int i = from; i < tilts.size(); i++) {
            minTilt = Math.min(minTilt, tilts.get(i));
            maxTilt = Math.max(maxTilt, tilts.get(i));
        }
        for (double s : speeds) {
            if (Double.isFinite(s)) {
                peak = Math.max(peak, s);
            }
        }
        String label = label(throttle) + " " + flightClass.key();
        StringBuilder series = new StringBuilder();
        for (int i = 0; i < tilts.size(); i += 2) {
            series.append(i == 0 ? "" : " ").append(Math.round(tilts.get(i) * 10.0) / 10.0);
        }
        ctx.log(label + " Neigungsverlauf (alle 0,1 s, °): " + series);
        StringBuilder climb = new StringBuilder();
        for (int i = 0; i < heights.size(); i += 10) {
            climb.append(i == 0 ? "" : " ").append(Math.round(heights.get(i) * 10.0) / 10.0);
        }
        ctx.log(label + " Höhenverlauf (alle 0,5 s, m): " + climb);
        double seconds = tilts.size() / 20.0;
        double climbed = end - start;
        log(ctx, label + ": " + swings + " Wechsel der Neigung ab " + Flight.format(WARMUP_SECONDS) + " s (Band " + Flight.format(SWING)
                + "°), größte Änderung " + Flight.format(rate) + "°/s, Neigung " + Flight.format(minTilt) + "–" + Flight.format(maxTilt)
                + "°, Spitze " + Flight.format(peak) + " m/s, Höhe " + Flight.format(start) + " → " + Flight.format(end) + " m (tiefste "
                + Flight.format(lowest) + " m) in " + Flight.format(seconds) + " s" + (frozen == 0 ? "" : ", " + frozen + " Ticks ohne Bewegung am Client"));
        if (swings > MAX_SWINGS) {
            problem(ctx, label + " ruckelt: " + swings + " Wechsel der Neigung (höchstens " + MAX_SWINGS + ")");
        }
        if (rate > MAX_TILT_RATE) {
            problem(ctx, label + " ändert die Neigung um " + Flight.format(rate) + "°/s (höchstens " + Flight.format(MAX_TILT_RATE) + "°/s)");
        }
        if (start - lowest > HEIGHT_DROP) {
            problem(ctx, label + " sinkt " + Flight.format(start - lowest) + " m (Grenze " + Flight.format(HEIGHT_DROP) + " m)");
        }
        if (throttle > HALF) {
            double wanted = (throttle - HALF) * 2.0 * flightClass.climbSpeed() * seconds;
            if (climbed < CLIMB_SHARE * wanted) {
                problem(ctx, label + " steigt nur " + Flight.format(climbed) + " m statt mindestens " + Flight.format(CLIMB_SHARE * wanted) + " m");
            }
        }
    }

    public static int swings(List<Double> values, int from, double band) {
        if (values.size() <= from) {
            return 0;
        }
        int count = 0;
        int direction = 0;
        double low = values.get(from);
        double high = low;
        double extreme = low;
        for (int i = from + 1; i < values.size(); i++) {
            double v = values.get(i);
            if (!Double.isFinite(v)) {
                continue;
            }
            if (direction == 0) {
                low = Math.min(low, v);
                high = Math.max(high, v);
                if (v - low >= band) {
                    direction = 1;
                    extreme = v;
                } else if (high - v >= band) {
                    direction = -1;
                    extreme = v;
                }
            } else if (direction > 0) {
                if (v > extreme) {
                    extreme = v;
                } else if (extreme - v >= band) {
                    count++;
                    direction = -1;
                    extreme = v;
                }
            } else if (v < extreme) {
                extreme = v;
            } else if (v - extreme >= band) {
                count++;
                direction = 1;
                extreme = v;
            }
        }
        return count;
    }

    public static double maxRate(List<Double> values, List<Boolean> moved, int from, int span) {
        double worst = 0.0;
        for (int i = Math.max(from, span + 1); i < values.size(); i++) {
            boolean steady = true;
            for (int j = i - span - 1; j <= i; j++) {
                steady &= moved.get(j);
            }
            double a = values.get(i);
            double b = values.get(i - span);
            if (steady && Double.isFinite(a) && Double.isFinite(b)) {
                worst = Math.max(worst, Math.abs(a - b) * 20.0 / span);
            }
        }
        return worst;
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
