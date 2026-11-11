package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.hud.CamOsd;
import de.lardtom.camdrones.client.hud.CamOsdSource;
import de.lardtom.camdrones.client.hud.HudText;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CovCamAFlightSteps {
    public static final String SPEEDS = "flug: Normal-Tempo, Steigen und Sinken je Klasse";
    public static final String WIND = "flug: Windversatz je Klasse";
    public static final String WIND_LEAN = "flug: Wind wirkt je Klasse verschieden";
    public static final String RESCUE = "flug: GPS Rescue mit Kamera-Drohne";
    public static final String FLIGHT_LEFT = "flug: Restflugzeit zählt bei Akkulaufzeit 10 % herunter";
    public static final String LOW_BATTERY = "flug: Rückkehr bei knappem Akku";
    public static final String TRIPOD = "flug: Luftstativ 3 min";
    public static final String PAUSED = "osd: PAUSED beim Luftstativ";
    public static final String EXIT = "mission: FPV verlassen mit Einstellung";
    public static final String CRASH = "flug: Crash beendet Course Lock";
    public static final Identifier BATTERY_SCALE = Identifier.parse("propwash:battery_scale");
    public static final String WINDY = "2";
    public static final String RESCUE_KEY = "key.propwash.rescue";
    public static final double SPEED_TOLERANCE = 0.15;
    public static final double HEIGHT_HOLD = 0.3;
    public static final double WIND_VISIBLE = 0.3;
    public static final double WIND_BOUND = 1.0;
    public static final double WIND_MIN_LEAN = 1.0;
    public static final double RESCUE_DISTANCE = 60.0;
    public static final double HOME_TOLERANCE = 2.0;
    public static final double TRIPOD_LIMIT = 1.0;
    public static final int TRIPOD_CALM_TICKS = 20 * 60;
    public static final int TRIPOD_TICKS = 20 * 180;
    public static final double EXIT_STAND = 0.5;
    public static final double EXIT_DROP = 0.3;
    public static final double EXIT_HEIGHT = 20.0;
    public static final double EXIT_AWAY = 20.0;
    public static final double LAND_FIRST_SECOND = 1.5;
    public static final int TRIPOD_REFERENCE_TICKS = 40;
    private static final String START = "covcama.start";
    private static final String HOME = "covcama.home";
    private static final String MAX = "covcama.max";
    private static final String SAMPLES = "covcama.samples";
    private static final String LOG = "covcama.log";
    private static final String PEAK = "covcama.peak";
    private static final String RATE = "covcama.rate";
    private static final String WINDS = "covcama.winds";
    private static final String LOWEST = "covcama.lowest";
    private static final String BEFORE = "covcama.before";
    private static final String TILT = "covcama.tilt";
    private static final String MOVED = "covcama.moved";
    private static final String CRASHED = "covcama.crashed";
    private static final String FAST = "covcama.fast";
    private static final String LANDED = "covcama.landed";
    private static final String PROBLEMS = "covcama.problems";
    private static final String REARMED = "covcama.rearmed";
    private static final String REARM_SEEN = "covcama.rearmSeen";
    private static final String REARM_LOW = "covcama.rearmLow";
    private static final String REARM_FLIPPED = "covcama.rearmFlipped";
    private static final String FLIP_KEY = "key.propwash.drone_flip_upright";
    private static final double FLIP_TILT = 25.0;
    private static final int REARM_WATCH_TICKS = 100;
    private static final int STEP_TICKS = 20 * 240;
    private static final int VERTICAL_SETTLE = 50;
    private static final int VERTICAL_TICKS = 100;
    private static final double WIND_HEIGHT = 15.0;
    private static final String COURSE_TEST_KEY = "key.keyboard.home";
    private static @Nullable String savedCourseKey;

    private CovCamAFlightSteps() {
    }

    static void speeds() {
        List<CamPresets.Entry> entries = List.of(CamPresets.COMPACT, CamPresets.TOUR, CamPresets.CINE);
        for (int i = 0; i < entries.size(); i++) {
            CamPresets.Entry entry = entries.get(i);
            int lane = 100 + 12 * i;
            SelfTest.clientStep(311 + i, SPEEDS + " – " + entry.path(), STEP_TICKS, Script.create()
                    .server("Wind aus", (server, state) -> Arena.gameRule(server, Arena.WIND, "0"))
                    .append(KeyboardDeflection.set(KeyboardDeflection.MAX_PERCENT))
                    .append(Pilot.launch(entry, lane, -60, 0.0F))
                    .append(Flight.takeoff(6.0))
                    .then("N 8 s Vollgas vorwärts", (ctx, ticks) -> topSpeed(ctx, ticks, entry.flightClass()))
                    .then("bremsen", Flight::brake)
                    .then("Gas voll hoch", (ctx, ticks) -> vertical(ctx, ticks, entry.flightClass(), true))
                    .then("Gas in die Mitte", (ctx, ticks) -> {
                        Flight.track(ctx);
                        return Pilot.throttleTo(ctx, ticks, Pilot.THROTTLE_CENTER);
                    })
                    .then("Gas Mitte: 5 s Höhe halten", CovCamAFlightSteps::holdHeight)
                    .then("Gas voll runter", (ctx, ticks) -> vertical(ctx, ticks, entry.flightClass(), false))
                    .then("Gas wieder Mitte", (ctx, ticks) -> {
                        Flight.track(ctx);
                        return Pilot.throttleTo(ctx, ticks, Pilot.THROTTLE_CENTER);
                    })
                    .then("Ergebnis", (ctx, ticks) -> {
                        String problems = (String) ctx.state().getOrDefault(PROBLEMS, "");
                        return problems.isEmpty() ? StepResult.pass(String.valueOf(ctx.state().get(LOG)))
                                : StepResult.fail(problems + " (alle Werte: " + ctx.state().get(LOG) + ")");
                    }), ctx -> {
                        Pilot.releaseKeys();
                        return KeyboardDeflection.restore(ctx);
                    });
        }
    }

    static void wind() {
        SelfTest.clientStep(314, WIND, 20 * 420, windScript(true));
    }

    static void windLean() {
        SelfTest.clientStep(324, WIND_LEAN, 20 * 420, windScript(false));
    }

    private static Script windScript(boolean strict) {
        Script script = Script.create()
                .server("Wind = Windig", (server, state) -> Arena.gameRule(server, Arena.WIND, WINDY));
        List<CamPresets.Entry> entries = List.of(CamPresets.COMPACT, CamPresets.TOUR, CamPresets.CINE);
        for (int i = 0; i < entries.size(); i++) {
            CamPresets.Entry entry = entries.get(i);
            script.append(Pilot.launch(entry, -100 - 12 * i, -60, 0.0F))
                    .append(Flight.takeoff(WIND_HEIGHT))
                    .then("Ruhe vor dem Schweben", (ctx, ticks) -> {
                        Flight.track(ctx);
                        return Flight.calm(ctx, ticks, 400);
                    })
                    .then(entry.path() + " 30 s schweben", (ctx, ticks) -> windHover(ctx, ticks, entry))
                    .server("Flug von " + entry.path() + " beenden", CovCamAFlightSteps::endFlight)
                    .await("FPV beendet", 200, () -> !PropwashClientApi.input().flying());
        }
        return script.then("Klassen vergleichen", (ctx, ticks) -> compareWind(ctx, strict));
    }

    static void rescue() {
        SelfTest.clientStep(315, RESCUE, STEP_TICKS, Script.create()
                .append(Pilot.launch(CamPresets.TOUR, -130, -30, 0.0F))
                .then("Homepoint merken", CovCamAFlightSteps::rememberHome)
                .append(Flight.takeoff(10.0))
                .then(Flight.format(RESCUE_DISTANCE) + " m wegfliegen", (ctx, ticks) -> away(ctx, ticks, RESCUE_DISTANCE))
                .then("bremsen", Flight::brake)
                .then("GPS Rescue auslösen", (ctx, ticks) -> {
                    Flight.track(ctx);
                    if (ticks == 0) {
                        Pilot.click(RESCUE_KEY);
                    }
                    return ticks >= 10 ? StepResult.pass() : StepResult.pending();
                })
                .then("Heimflug und Landung", (ctx, ticks) -> homeAndLand(ctx, ticks, 20 * 90)));
    }

    static void flightLeft() {
        SelfTest.clientStep(316, FLIGHT_LEFT, STEP_TICKS, Script.create()
                .server("Akkulaufzeit 10 %", (server, state) -> Arena.gameRule(server, BATTERY_SCALE, "10"))
                .append(Pilot.launch(CamPresets.TOUR, -100, 100, 0.0F))
                .append(Flight.takeoff(4.0))
                .then("Restflugzeit beobachten", CovCamAFlightSteps::countdown));
    }

    static void lowBattery() {
        SelfTest.clientStep(317, LOW_BATTERY, STEP_TICKS, Script.create()
                .server("Akkulaufzeit 2 %", (server, state) -> Arena.gameRule(server, BATTERY_SCALE, "2"))
                .append(Pilot.launch(CamPresets.TOUR, -60, 100, 0.0F))
                .then("Homepoint merken", CovCamAFlightSteps::rememberHome)
                .append(Flight.takeoff(6.0))
                .then("25 m wegfliegen", (ctx, ticks) -> away(ctx, ticks, 25.0))
                .then("bremsen", Flight::brake)
                .then("schweben, bis der Akku knapp wird", CovCamAFlightSteps::lowBatteryReturn));
    }

    static void tripod() {
        SelfTest.clientStep(318, TRIPOD, 20 * 300, Script.create()
                .append(Pilot.launch(CamPresets.TOUR, -130, 40, 0.0F))
                .append(Flight.takeoff(6.0))
                .then("Ruhe", (ctx, ticks) -> {
                    Flight.track(ctx);
                    return Flight.calm(ctx, ticks, 400);
                })
                .then("Pause drücken", (ctx, ticks) -> pause(ctx, ticks))
                .then("3 min halten (1 min Wind Mild, 2 min Windig), Neigung bewegen", CovCamAFlightSteps::tripodHold), CovCamAFlightSteps::resetTilt);
    }

    static void paused() {
        SelfTest.clientStep(319, PAUSED, STEP_TICKS, Script.create()
                .append(Pilot.launch(CamPresets.TOUR, -142, 40, 0.0F))
                .append(Flight.takeoff(5.0))
                .then("Pause drücken", (ctx, ticks) -> pause(ctx, ticks))
                .then("OSD-Warnung PAUSED", (ctx, ticks) -> {
                    Flight.track(ctx);
                    List<String> warnings = CamOsd.warnings(CamOsdSource.last()).stream().map(OsdText::text).toList();
                    if (warnings.contains(CamOsd.Warning.PAUSED.text())) {
                        return StepResult.pass("OSD zeigt PAUSED beim Halten ohne Mission, " + Flight.describe());
                    }
                    return ticks > 100 ? StepResult.fail("OSD zeigt nach Pause ohne Mission kein PAUSED (Warnungen " + warnings + ", " + Flight.describe()
                            + ")") : StepResult.pending();
                }));
    }

    static void exit(DroneSettings.ExitBehaviour exit) {
        int i = exit.ordinal();
        int lane = 40 + 14 * i;
        String label = switch (exit) {
            case HOVER -> "schweben";
            case HOME -> "heim";
            case LAND -> "landen";
        };
        Script script = Script.create()
                .append(Pilot.launch(CamPresets.TOUR, lane, 60, 0.0F))
                .then("Homepoint merken", CovCamAFlightSteps::rememberHome)
                .server("koppeln, Verhalten beim Verlassen: " + label, (server, state) -> {
                    CamFlights.pair(server, state);
                    Entity drone = Pilot.serverDrone(server, state);
                    PropwashApi.droneData().set(drone, CamDroneData.settings(), DroneSettings.DEFAULT.withExit(exit));
                })
                .append(Flight.takeoff(EXIT_HEIGHT));
        if (exit == DroneSettings.ExitBehaviour.HOME) {
            script.then(Flight.format(EXIT_AWAY) + " m wegfliegen", (ctx, ticks) -> away(ctx, ticks, EXIT_AWAY)).then("bremsen", Flight::brake);
        }
        script.then("Lage vor dem Verlassen (Mittel über 1 s)", (ctx, ticks) -> {
                    Flight.track(ctx);
                    Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
                    if (drone.isEmpty()) {
                        return StepResult.fail("Drohne fehlt am Client");
                    }
                    Vec3 sum = (Vec3) ctx.state().getOrDefault(SAMPLES, Vec3.ZERO);
                    sum = sum.add(drone.get().position());
                    ctx.state().put(SAMPLES, sum);
                    if (ticks < 19) {
                        return StepResult.pending();
                    }
                    Vec3 mean = sum.scale(1.0 / 20.0);
                    ctx.state().remove(SAMPLES);
                    ctx.state().put(BEFORE, mean);
                    ctx.state().put(LOWEST, mean.y);
                    return StepResult.pass();
                })
                .server("FPV verlassen", (server, state) -> Pilot.release(server))
                .then("Verhalten nach dem Verlassen", (ctx, ticks) -> afterExit(ctx, ticks, exit));
        SelfTest.clientStep(320 + i, EXIT + " " + label, STEP_TICKS, script);
    }

    static void crash() {
        SelfTest.clientStep(323, CRASH, STEP_TICKS, Script.create()
                .append(KeyboardDeflection.set(KeyboardDeflection.MAX_PERCENT))
                .append(Pilot.launch(CamPresets.COMPACT, 140, -60, 0.0F))
                .append(Flight.takeoff(3.0))
                .server("Wand 24 m vor die Drohne", (server, state) -> CovCamAKit.wall(server, state, 24))
                .append(Flight.mode(NavLogic.StickMode.CINE))
                .append(Flight.mode(NavLogic.StickMode.SPORT))
                .run("Taste für Course Lock belegen", () -> bindCourseLock(true))
                .then("Course Lock einschalten", (ctx, ticks) -> courseLock(ctx, ticks, true))
                .then("im Modus S gegen die Wand", CovCamAFlightSteps::intoWall)
                .then("nach dem Crash", (ctx, ticks) -> afterCrash(ctx, ticks, "nach dem Crash"))
                .then("wieder scharf schalten", CovCamAFlightSteps::rearm)
                .then("nach dem Scharfschalten", CovCamAFlightSteps::afterRearm)
                .then("Ergebnis", (ctx, ticks) -> {
                    String problems = (String) ctx.state().getOrDefault(CRASHED + ".problems", "");
                    return problems.isEmpty() ? StepResult.pass((String) ctx.state().get(CRASHED)) : StepResult.fail(problems);
                }), ctx -> {
                    Pilot.releaseKeys();
                    bindCourseLock(false);
                    return KeyboardDeflection.restore(ctx);
                });
    }

    private static StepResult problem(SelfTest.StepContext ctx, String line) {
        String previous = (String) ctx.state().getOrDefault(PROBLEMS, "");
        ctx.state().put(PROBLEMS, previous.isEmpty() ? line : previous + "; " + line);
        return StepResult.pass();
    }

    private static String log(SelfTest.StepContext ctx, String line) {
        String previous = (String) ctx.state().getOrDefault(LOG, "");
        String now = previous.isEmpty() ? line : previous + "; " + line;
        ctx.state().put(LOG, now);
        ctx.log(line);
        return now;
    }

    private static StepResult topSpeed(SelfTest.StepContext ctx, int ticks, FlightClass flightClass) {
        Flight.track(ctx);
        Pilot.hold(Pilot.PITCH_FORWARD, true);
        double speed = Flight.horizontalSpeed(ctx, 10);
        if (ticks >= 20 && Double.isFinite(speed)) {
            ctx.state().put(PEAK, Math.max((double) ctx.state().getOrDefault(PEAK, 0.0), speed));
        }
        if (ticks % 20 == 0) {
            ctx.log(flightClass.key() + " vorwärts t=" + ticks / 20 + " s: " + Flight.format(speed) + " m/s");
        }
        if (ticks < 160) {
            return StepResult.pending();
        }
        Pilot.hold(Pilot.PITCH_FORWARD, false);
        double peak = (double) ctx.state().getOrDefault(PEAK, 0.0);
        double expected = flightClass.normalSpeed();
        String line = flightClass.key() + ": Spitzentempo N " + Flight.format(peak) + " m/s (Soll " + Flight.format(expected) + " ±15 %, Knüppel "
                + Flight.format(Math.abs(PropwashClientApi.input().pitch())) + ")";
        log(ctx, line);
        if (Math.abs(peak - expected) > SPEED_TOLERANCE * expected) {
            return problem(ctx, line);
        }
        return StepResult.pass();
    }

    private static StepResult vertical(SelfTest.StepContext ctx, int ticks, FlightClass flightClass, boolean up) {
        Flight.track(ctx);
        Minecraft mc = Minecraft.getInstance();
        Pilot.hold(up ? mc.options.keyUp : mc.options.keyDown, true);
        Pilot.hold(up ? mc.options.keyDown : mc.options.keyUp, false);
        double rate = Flight.verticalSpeed(ctx, 10);
        if (ticks >= VERTICAL_SETTLE && Double.isFinite(rate)) {
            @SuppressWarnings("unchecked")
            List<Double> rates = (List<Double>) ctx.state().computeIfAbsent(RATE + up, key -> new ArrayList<Double>());
            rates.add(rate);
        }
        if (ticks % 10 == 0) {
            ctx.log(flightClass.key() + (up ? " steigen" : " sinken") + " t=" + Flight.format(ticks / 20.0) + " s: " + Flight.format(rate) + " m/s, Gas "
                    + Flight.format(PropwashClientApi.input().throttle()) + ", Höhe " + Flight.format(Flight.height()) + " m");
        }
        if (ticks < VERTICAL_TICKS) {
            return StepResult.pending();
        }
        Pilot.hold(mc.options.keyUp, false);
        Pilot.hold(mc.options.keyDown, false);
        @SuppressWarnings("unchecked")
        List<Double> rates = (List<Double>) ctx.state().getOrDefault(RATE + up, List.of());
        double mean = rates.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double expected = up ? flightClass.climbSpeed() : flightClass.descendSpeed();
        double measured = up ? mean : -mean;
        String line = flightClass.key() + ": " + (up ? "Steigen" : "Sinken") + " " + Flight.format(measured) + " m/s bei Gas "
                + Flight.format(PropwashClientApi.input().throttle()) + " (Soll " + Flight.format(expected) + " ±15 %)";
        log(ctx, line);
        if (!(Math.abs(measured - expected) <= SPEED_TOLERANCE * expected)) {
            return problem(ctx, line);
        }
        return StepResult.pass();
    }

    private static StepResult holdHeight(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        if (!ctx.state().containsKey(START)) {
            StepResult calm = Flight.calm(ctx, ticks, 300);
            if (!calm.done()) {
                return calm;
            }
            if (calm.failed()) {
                return calm;
            }
            ctx.state().put(START, Flight.height());
            ctx.state().put(MAX, 0.0);
            ctx.state().put(SAMPLES, ticks);
            return StepResult.pending();
        }
        double start = (double) ctx.state().get(START);
        double deviation = Math.max((double) ctx.state().get(MAX), Math.abs(Flight.height() - start));
        ctx.state().put(MAX, deviation);
        int since = (int) ctx.state().get(SAMPLES);
        if (ticks - since < 100) {
            return StepResult.pending();
        }
        ctx.state().remove(START);
        String line = "Gas Mitte 5 s: Höhe " + Flight.format(start) + " m auf " + Flight.format(deviation) + " m gehalten (Grenze "
                + Flight.format(HEIGHT_HOLD) + " m)";
        log(ctx, line);
        return deviation <= HEIGHT_HOLD ? StepResult.pass() : problem(ctx, line);
    }

    private static StepResult windHover(SelfTest.StepContext ctx, int ticks, CamPresets.Entry entry) {
        Flight.track(ctx);
        Optional<Vec3> now = Flight.position();
        if (now.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks == 0) {
            ctx.state().put(START, now.get());
            ctx.state().put(MAX, 0.0);
            return StepResult.pending();
        }
        Vec3 start = (Vec3) ctx.state().get(START);
        double deviation = CamFlights.horizontal(now.get(), start);
        double max = Math.max((double) ctx.state().get(MAX), deviation);
        ctx.state().put(MAX, max);
        double tilt = Pilot.drone().map(GimbalSteps::frameTilt).orElse(0.0);
        ctx.state().put(TILT, (double) ctx.state().getOrDefault(TILT, 0.0) + tilt);
        if (ticks % 100 == 0) {
            ctx.log(entry.path() + " t=" + ticks / 20 + " s Versatz " + Flight.format(deviation) + " m, Höhe " + Flight.format(Flight.height())
                    + " m, Neigung " + Flight.format(tilt) + "°");
        }
        if (ticks < 600) {
            return StepResult.pending();
        }
        double meanTilt = (double) ctx.state().get(TILT) / ticks;
        ctx.state().remove(TILT);
        @SuppressWarnings("unchecked")
        Map<String, double[]> winds = (Map<String, double[]>) ctx.state().computeIfAbsent(WINDS, key -> new LinkedHashMap<String, double[]>());
        winds.put(entry.path(), new double[]{max, deviation, meanTilt});
        ctx.state().remove(START);
        return StepResult.pass(entry.path() + ": größter Versatz " + Flight.format(max) + " m, am Ende " + Flight.format(deviation) + " m, mittlere Neigung "
                + Flight.format(meanTilt) + "°");
    }

    @SuppressWarnings("unchecked")
    private static StepResult compareWind(SelfTest.StepContext ctx, boolean strict) {
        Map<String, double[]> winds = (Map<String, double[]>) ctx.state().get(WINDS);
        if (winds == null || winds.size() < 3) {
            return StepResult.fail("nicht alle Klassen gemessen");
        }
        double compact = winds.get(CamPresets.COMPACT.path())[0];
        double tour = winds.get(CamPresets.TOUR.path())[0];
        double cine = winds.get(CamPresets.CINE.path())[0];
        String line = String.format(Locale.GERMANY, "Windig, 30 s Schweben in %.0f m im Modus N: größter Versatz Kompakt %.2f m, Tour %.2f m, Kino %.2f m; "
                + "mittlere Neigung gegen den Wind %.1f°, %.1f°, %.1f°", WIND_HEIGHT, compact, tour, cine, winds.get(CamPresets.COMPACT.path())[2],
                winds.get(CamPresets.TOUR.path())[2], winds.get(CamPresets.CINE.path())[2]);
        List<String> problems = new ArrayList<>();
        if (strict) {
            if (!(compact > tour && tour > cine)) {
                problems.add("Reihenfolge nicht Kompakt > Tour > Kino");
            }
            if (!(compact > WIND_VISIBLE)) {
                problems.add("Kompakt nicht sichtbar versetzt (> " + Flight.format(WIND_VISIBLE) + " m)");
            }
        } else {
            double leanCompact = winds.get(CamPresets.COMPACT.path())[2];
            double leanTour = winds.get(CamPresets.TOUR.path())[2];
            double leanCine = winds.get(CamPresets.CINE.path())[2];
            if (!(leanCompact > leanTour && leanTour > leanCine)) {
                problems.add("Neigung gegen den Wind nicht Kompakt > Tour > Kino");
            }
            if (!(leanCine > WIND_MIN_LEAN)) {
                problems.add("Wind wirkt nicht (Kino nur " + Flight.format(leanCine) + "°)");
            }
        }
        for (Map.Entry<String, double[]> e : winds.entrySet()) {
            if (e.getValue()[0] > WIND_BOUND) {
                problems.add(e.getKey() + " korrigiert nicht (über " + Flight.format(WIND_BOUND) + " m)");
            }
        }
        return problems.isEmpty() ? StepResult.pass(line) : StepResult.fail(line + ": " + String.join(", ", problems));
    }

    private static void endFlight(MinecraftServer server, Map<String, Object> state) {
        Pilot.release(server);
        CamFlights.stopFlights(server);
        Entity drone = Pilot.serverDrone(server, state);
        drone.discard();
    }

    private static StepResult rememberHome(SelfTest.StepContext ctx, int ticks) {
        Optional<Vec3> position = Flight.position();
        if (position.isEmpty()) {
            return ticks > 60 ? StepResult.fail("Drohne fehlt") : StepResult.pending();
        }
        ctx.state().put(HOME, position.get());
        return StepResult.pass();
    }

    private static StepResult away(SelfTest.StepContext ctx, int ticks, double distance) {
        Flight.track(ctx);
        Pilot.hold(Pilot.PITCH_FORWARD, true);
        Vec3 home = (Vec3) ctx.state().get(HOME);
        Optional<Vec3> now = Flight.position();
        if (now.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        double away = CamFlights.horizontal(now.get(), home);
        if (away >= distance - 2.0) {
            Pilot.hold(Pilot.PITCH_FORWARD, false);
            return StepResult.pass();
        }
        return ticks > 20 * 30 ? StepResult.fail("kommt nur " + Flight.format(away) + " m weit") : StepResult.pending();
    }

    private static StepResult homeAndLand(SelfTest.StepContext ctx, int ticks, int maxTicks) {
        Flight.track(ctx);
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        Vec3 home = (Vec3) ctx.state().get(HOME);
        double distance = CamFlights.horizontal(drone.get().position(), home);
        double height = drone.get().getY() - Flight.surface();
        double speed = drone.get().getDeltaMovement().length() * 20.0;
        if (ticks % 40 == 0) {
            ctx.log(String.format(Locale.GERMANY, "t=%.0f s: %.1f m vom Homepoint, Höhe %.1f m, %s", ticks / 20.0, distance, height, Flight.describe()));
        }
        if (height < 0.4) {
            int since = (int) ctx.state().computeIfAbsent(LANDED, key -> ticks);
            if (ticks - since >= 40 && distance <= HOME_TOLERANCE) {
                return StepResult.pass("gelandet " + Flight.format(distance) + " m vom Homepoint nach " + Flight.format(ticks / 20.0) + " s");
            }
            if (ticks - since >= 40) {
                return StepResult.fail("landet " + Flight.format(distance) + " m vom Homepoint (Grenze " + Flight.format(HOME_TOLERANCE) + " m)");
            }
        } else {
            ctx.state().remove(LANDED);
        }
        return ticks > maxTicks ? StepResult.fail("nicht am Homepoint gelandet: " + Flight.format(distance) + " m, Höhe " + Flight.format(height) + " m, "
                + Flight.format(speed) + " m/s, " + Flight.describe()) : StepResult.pending();
    }

    private static StepResult countdown(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (!CamOsdSource.flightLeftMeasured()) {
            if (ticks % 200 == 0) {
                ctx.log("noch keine Messung nach " + ticks / 20 + " s, Restflugzeit " + HudText.duration(CamOsdSource.last().flightSecondsLeft()) + ", "
                        + Flight.describe() + ", Höhe " + Flight.format(Flight.height()) + " m");
            }
            return ticks > 20 * 120 ? StepResult.fail("Restflugzeit wird nach 120 s noch nicht gemessen") : StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        List<double[]> samples = (List<double[]>) ctx.state().computeIfAbsent(SAMPLES, key -> new ArrayList<double[]>());
        if (ticks % 20 == 0) {
            samples.add(new double[]{ticks / 20.0, CamOsdSource.last().flightSecondsLeft()});
        }
        if (samples.size() < 41) {
            return StepResult.pending();
        }
        double worstRise = 0.0;
        for (int i = 1; i < samples.size(); i++) {
            worstRise = Math.max(worstRise, samples.get(i)[1] - samples.get(i - 1)[1]);
        }
        double first = samples.getFirst()[1];
        double last = samples.getLast()[1];
        double elapsed = samples.getLast()[0] - samples.getFirst()[0];
        double drop = first - last;
        double bench = CamOsdSource.benchSeconds(drone.get());
        String line = "Restflugzeit " + HudText.duration(first) + " → " + HudText.duration(last) + " in " + Flight.format(elapsed) + " s (Abnahme "
                + Flight.format(drop) + " s, größter Anstieg " + Flight.format(worstRise) + " s), Werkbank-Schätzung " + HudText.duration(bench);
        if (worstRise > 1.5) {
            return StepResult.fail("zählt nicht monoton herunter: " + line);
        }
        if (drop < 0.4 * elapsed || drop > 2.5 * elapsed) {
            return StepResult.fail("zählt nicht plausibel herunter: " + line);
        }
        if (!(last < 0.35 * bench)) {
            return StepResult.fail("Restflugzeit passt nicht zu Akkulaufzeit 10 %: " + line);
        }
        return StepResult.pass(line);
    }

    private static StepResult lowBatteryReturn(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        Vec3 home = (Vec3) ctx.state().get(HOME);
        double distance = CamFlights.horizontal(drone.get().position(), home);
        double height = drone.get().getY() - Flight.surface();
        double left = CamOsdSource.last().flightSecondsLeft();
        double sink = -drone.get().getDeltaMovement().y * 20.0;
        ctx.state().put(FAST, Math.max((double) ctx.state().getOrDefault(FAST, 0.0), height < 3.0 ? sink : 0.0));
        double min = Math.min((double) ctx.state().getOrDefault(LOWEST, Double.MAX_VALUE), height > 0.5 ? distance : Double.MAX_VALUE);
        ctx.state().put(LOWEST, min);
        if (ticks % 40 == 0) {
            ctx.log(String.format(Locale.GERMANY, "t=%.0f s: Restflugzeit %s, %.1f m vom Homepoint, Höhe %.1f m, %s", ticks / 20.0,
                    HudText.duration(left), distance, height, Flight.describe()));
        }
        if (min <= 3.0) {
            return StepResult.pass("kehrt bei knappem Akku zurück: " + Flight.format(min) + " m vom Homepoint, Restflugzeit " + HudText.duration(left));
        }
        if (height < 0.4 && ticks > 40) {
            double fast = (double) ctx.state().get(FAST);
            if (fast < 2.5) {
                return StepResult.pass("landet bei knappem Akku weich (" + Flight.format(fast) + " m/s) " + Flight.format(distance)
                        + " m vom Homepoint, Restflugzeit " + HudText.duration(left));
            }
            return StepResult.fail("fällt bei leerem Akku (" + Flight.format(fast) + " m/s Sinken) " + Flight.format(distance)
                    + " m vom Homepoint statt Rückkehr oder Landung, Restflugzeit " + HudText.duration(left) + ", " + Flight.describe());
        }
        return ticks > 20 * 150 ? StepResult.fail("weder Rückkehr noch Landung nach 150 s: " + Flight.format(distance) + " m vom Homepoint, Höhe "
                + Flight.format(height) + " m, Restflugzeit " + HudText.duration(left) + ", " + Flight.describe()) : StepResult.pending();
    }

    private static StepResult pause(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.hold(CamControl.PAUSE, ticks < 4);
        if (ticks < 8) {
            return StepResult.pending();
        }
        Optional<NavStatus> status = Pilot.status();
        if (status.isPresent() && status.get().function() == NavLogic.Function.HOLD) {
            return StepResult.pass("Pause: Funktion HOLD, Anzeige " + status.get().label());
        }
        return ticks > 60 ? StepResult.fail("Pause schaltet nicht auf Halten: " + Flight.describe()) : StepResult.pending();
    }

    private static KeyMapping tiltKey(boolean up) {
        return (up ? CamKeys.increase(CamControl.GIMBAL_TILT) : CamKeys.decrease(CamControl.GIMBAL_TILT))
                .orElseThrow(() -> new IllegalStateException("Taste für die Gimbal-Neigung fehlt"));
    }

    private static StepResult tripodHold(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Vec3> now = Flight.position();
        Optional<Entity> drone = Pilot.drone();
        if (now.isEmpty() || drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks < TRIPOD_REFERENCE_TICKS) {
            ctx.state().put(START, ((Vec3) ctx.state().getOrDefault(START, Vec3.ZERO)).add(now.get()));
            if (ticks == 0) {
                ctx.state().put(TILT, GimbalClient.tilt(drone.get()));
            }
            return StepResult.pending();
        }
        if (ticks == TRIPOD_REFERENCE_TICKS) {
            ctx.state().put(START, ((Vec3) ctx.state().get(START)).scale(1.0 / TRIPOD_REFERENCE_TICKS));
            ctx.state().put(MAX, 0.0);
            ctx.state().put(LOWEST, 0.0);
        }
        if (ticks == TRIPOD_CALM_TICKS) {
            ctx.onServer(server -> Arena.gameRule(server, Arena.WIND, WINDY));
            ctx.log("Wind jetzt Windig");
        }
        Vec3 start = (Vec3) ctx.state().get(START);
        double horizontal = CamFlights.horizontal(now.get(), start);
        double vertical = Math.abs(now.get().y - start.y);
        double max = Math.max((double) ctx.state().get(MAX), horizontal);
        double maxVertical = Math.max((double) ctx.state().get(LOWEST), vertical);
        ctx.state().put(MAX, max);
        ctx.state().put(LOWEST, maxVertical);
        if (ticks % 20 == 0) {
            ctx.log(String.format(Locale.ROOT, "t=%d s pos=%.2f/%.2f/%.2f waagrecht %.2f m, senkrecht %.2f m, Funktion %s", ticks / 20, now.get().x,
                    now.get().y, now.get().z, horizontal, vertical, Pilot.status().map(s -> s.function().name()).orElse("?")));
        }
        if (max > TRIPOD_LIMIT || maxVertical > TRIPOD_LIMIT) {
            return StepResult.fail("Luftstativ weicht nach " + Flight.format(ticks / 20.0) + " s waagrecht " + Flight.format(max) + " m, senkrecht "
                    + Flight.format(maxVertical) + " m ab (Grenze " + Flight.format(TRIPOD_LIMIT) + " m je Richtung), Wind "
                    + (ticks >= TRIPOD_CALM_TICKS ? "Windig" : "Mild") + ", " + Flight.describe());
        }
        int tiltFrom = TRIPOD_CALM_TICKS + 20 * 60;
        if (ticks >= tiltFrom && ticks < tiltFrom + 30) {
            Pilot.hold(tiltKey(false), true);
        } else if (ticks == tiltFrom + 30) {
            Pilot.hold(tiltKey(false), false);
            double before = (double) ctx.state().get(TILT);
            double after = GimbalClient.tilt(drone.get());
            ctx.state().put(MOVED, before - after);
        }
        if (ticks < TRIPOD_TICKS) {
            return StepResult.pending();
        }
        double moved = (double) ctx.state().getOrDefault(MOVED, 0.0);
        if (moved < 10.0) {
            return StepResult.fail("Gimbal folgt der Neigungsachse beim Luftstativ nicht (" + Flight.format(moved) + "°)");
        }
        if (Pilot.status().map(s -> s.function() != NavLogic.Function.HOLD).orElse(true)) {
            return StepResult.fail("Halten endet von selbst: " + Flight.describe());
        }
        return StepResult.pass("hält 3 min (1 min Wind Mild, 2 min Windig) waagrecht auf " + Flight.format(max) + " m, senkrecht auf "
                + Flight.format(maxVertical) + " m genau (Grenze " + Flight.format(TRIPOD_LIMIT) + " m), Gimbal neigt dabei um " + Flight.format(moved) + "°");
    }

    private static StepResult resetTilt(SelfTest.StepContext ctx) {
        Pilot.releaseKeys();
        double axis = PropwashClientApi.input().axis(CamControl.GIMBAL_TILT.id());
        if (Math.abs(axis) < 0.02 || !PropwashClientApi.input().flying()) {
            Pilot.hold(tiltKey(true), false);
            Pilot.hold(tiltKey(false), false);
            return StepResult.pass();
        }
        Pilot.hold(tiltKey(true), axis < 0.0);
        Pilot.hold(tiltKey(false), axis > 0.0);
        return ctx.tick() > 300 ? StepResult.fail("Gimbal-Achse bleibt bei " + Flight.format(axis)) : StepResult.pending();
    }

    private static StepResult afterExit(SelfTest.StepContext ctx, int ticks, DroneSettings.ExitBehaviour exit) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        if (PropwashClientApi.input().flying()) {
            return ticks > 200 ? StepResult.fail("FPV endet nicht") : StepResult.pending();
        }
        Vec3 before = (Vec3) ctx.state().get(BEFORE);
        Vec3 now = drone.get().position();
        double lowest = Math.min((double) ctx.state().get(LOWEST), now.y);
        ctx.state().put(LOWEST, lowest);
        double horizontal = CamFlights.horizontal(now, before);
        double height = now.y - Flight.surface();
        if (ticks % 40 == 0) {
            ctx.log(String.format(Locale.GERMANY, "%s t=%.0f s: %.2f m vom Ausstiegspunkt, Höhe %.1f m, tiefster Punkt %.2f m unter dem Ausstieg", exit,
                    ticks / 20.0, horizontal, height, before.y - lowest));
        }
        switch (exit) {
            case HOVER -> {
                double drop = before.y - lowest;
                if (horizontal > EXIT_STAND || drop > EXIT_DROP) {
                    return StepResult.fail("schweben: " + Flight.format(horizontal) + " m versetzt, " + Flight.format(drop) + " m abgesackt (Grenzen "
                            + Flight.format(EXIT_STAND) + " m / " + Flight.format(EXIT_DROP) + " m)");
                }
                if (ticks < 200) {
                    return StepResult.pending();
                }
                StepResult flight = CamFlights.serverFlight(ctx, ticks, "hold");
                if (!flight.done()) {
                    return flight;
                }
                if (flight.failed()) {
                    return flight;
                }
                return StepResult.pass("schweben: steht 10 s auf " + Flight.format(horizontal) + " m, abgesackt " + Flight.format(Math.max(0.0, drop))
                        + " m; " + flight.message());
            }
            case LAND -> {
                double drop = ticks <= 20 ? before.y - lowest : 0.0;
                if (ticks <= 20 && drop > LAND_FIRST_SECOND) {
                    return StepResult.fail("landen: fällt beim Wechsel " + Flight.format(drop) + " m in 1 s (Grenze " + Flight.format(LAND_FIRST_SECOND)
                            + " m, kein freier Fall)");
                }
                if (horizontal > 1.5) {
                    return StepResult.fail("landen: driftet " + Flight.format(horizontal) + " m vom Ausstiegspunkt weg");
                }
                if (height < 0.4) {
                    int since = (int) ctx.state().computeIfAbsent(LANDED, key -> ticks);
                    if (ticks - since >= 40) {
                        return StepResult.pass("landen: setzt " + Flight.format(horizontal) + " m neben dem Ausstiegspunkt auf nach "
                                + Flight.format(ticks / 20.0) + " s");
                    }
                }
                return ticks > 20 * 60 ? StepResult.fail("landen: nicht gelandet, Höhe " + Flight.format(height) + " m") : StepResult.pending();
            }
            case HOME -> {
                Vec3 home = (Vec3) ctx.state().get(HOME);
                double distance = CamFlights.horizontal(now, home);
                if (ticks == 40) {
                    ctx.state().put(PROBLEMS, before.y - lowest);
                }
                if (height < 0.4 && distance <= HOME_TOLERANCE + 1.0) {
                    int since = (int) ctx.state().computeIfAbsent(LANDED, key -> ticks);
                    if (ticks - since >= 40) {
                        double drop = (double) ctx.state().getOrDefault(PROBLEMS, 0.0);
                        String line = "heim: fliegt zurück und landet " + Flight.format(distance) + " m vom Homepoint nach " + Flight.format(ticks / 20.0)
                                + " s, Absacken beim Wechsel " + Flight.format(drop) + " m";
                        return drop > EXIT_DROP ? StepResult.fail(line + " (Grenze " + Flight.format(EXIT_DROP) + " m)") : StepResult.pass(line);
                    }
                }
                return ticks > 20 * 90 ? StepResult.fail("heim: nicht am Homepoint gelandet (" + Flight.format(distance) + " m, Höhe " + Flight.format(height)
                        + " m)") : StepResult.pending();
            }
        }
        return StepResult.fail("unbekannte Einstellung");
    }

    private static void bindCourseLock(boolean on) {
        KeyMapping key = CamKeys.key(CamControl.COURSE_LOCK).orElseThrow(() -> new IllegalStateException("Taste für Course Lock fehlt"));
        if (on && savedCourseKey == null) {
            savedCourseKey = key.saveString();
            key.setKey(InputConstants.getKey(COURSE_TEST_KEY));
        } else if (!on && savedCourseKey != null) {
            key.setKey(InputConstants.getKey(savedCourseKey));
            savedCourseKey = null;
        }
        KeyMapping.resetMapping();
    }

    private static boolean locked() {
        return Pilot.status().map(NavStatus::function).filter(f -> f == NavLogic.Function.COURSE_LOCK).isPresent();
    }

    private static StepResult courseLock(SelfTest.StepContext ctx, int ticks, boolean on) {
        Flight.track(ctx);
        if (ticks % 40 == 0 && locked() != on) {
            KeyMapping key = CamKeys.key(CamControl.COURSE_LOCK).orElseThrow(() -> new IllegalStateException("Taste für Course Lock fehlt"));
            KeyMapping.click(InputConstants.getKey(key.saveString()));
        }
        if (locked() == on && ticks >= 4) {
            return StepResult.pass("Course Lock " + (on ? "an" : "aus") + ", " + Flight.describe());
        }
        return ticks > 130 ? StepResult.fail("Course Lock schaltet nicht " + (on ? "ein" : "aus") + ": " + Flight.describe()) : StepResult.pending();
    }

    private static StepResult intoWall(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.hold(Pilot.PITCH_FORWARD, true);
        double speed = Flight.horizontalSpeed(ctx, 4);
        if (Double.isFinite(speed) && speed > 5.0) {
            ctx.state().put(FAST, Math.max((double) ctx.state().getOrDefault(FAST, 0.0), speed));
        }
        double wall = CovCamAKit.wallDistance(ctx.state());
        double fast = (double) ctx.state().getOrDefault(FAST, 0.0);
        boolean hit = Double.isFinite(wall) && wall < 1.2 && fast > 5.0 && Double.isFinite(speed) && speed < 2.0;
        boolean disarmed = ticks > 20 && !Pilot.armed();
        if (hit || disarmed && fast > 5.0) {
            Pilot.hold(Pilot.PITCH_FORWARD, false);
            ctx.state().put(CRASHED, "Crash bei " + Flight.format(fast) + " m/s, Abstand " + Flight.format(wall) + " m, scharf " + Pilot.armed());
            return StepResult.pass((String) ctx.state().get(CRASHED));
        }
        return ticks > 20 * 20 ? StepResult.fail("kein Crash an der Wand (Abstand " + Flight.format(wall) + " m, Tempo " + Flight.format(speed)
                + " m/s, " + Flight.describe() + ")") : StepResult.pending();
    }

    private static StepResult afterCrash(SelfTest.StepContext ctx, int ticks, String when) {
        Flight.track(ctx);
        Pilot.releaseKeys();
        if (ticks < 60) {
            return StepResult.pending();
        }
        Optional<NavStatus> status = Pilot.status();
        Optional<Entity> drone = Pilot.drone();
        SmartState smart = drone.map(d -> PropwashApi.droneData().get(d, CamDroneData.smart())).orElse(SmartState.NONE);
        boolean function = status.map(s -> s.function() != NavLogic.Function.NONE).orElse(false);
        boolean mission = status.map(s -> s.missionState() == NavLogic.MissionState.RUNNING).orElse(false);
        String seen = "Funktion " + status.map(s -> s.function().name()).orElse("?") + ", Mission " + status.map(s -> s.missionState().name()).orElse("?")
                + ", Drohnen-Daten " + smart.function();
        if (function || mission || smart.function() != SmartState.Function.NONE) {
            String previous = (String) ctx.state().getOrDefault(CRASHED + ".problems", "");
            ctx.state().put(CRASHED + ".problems", (previous.isEmpty() ? "" : previous + "; ") + when + " noch aktiv: " + seen);
            return StepResult.pass(when + " noch aktiv: " + seen);
        }
        return StepResult.pass(when + ": " + seen);
    }

    private static StepResult rearm(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        if (!PropwashClientApi.input().flying()) {
            return StepResult.fail("FPV nach dem Crash beendet");
        }
        boolean low = Pilot.steer(0.0);
        if (Pilot.armed()) {
            int since = (int) ctx.state().computeIfAbsent(REARMED, key -> ticks);
            String flipped = (String) ctx.state().get(REARM_FLIPPED);
            return ticks - since >= 10 ? StepResult.pass((flipped == null ? "" : flipped + ", ") + "wieder scharf nach " + Flight.format(since / 20.0)
                    + " s (" + armState() + ")") : StepResult.pending();
        }
        ctx.state().remove(REARMED);
        double tilt = Pilot.drone().flatMap(d -> PropwashApi.drones().view(d)).map(CovCamAFlightSteps::tilt).orElse(0.0);
        if (tilt > FLIP_TILT) {
            ctx.state().remove(REARM_LOW);
            if (ticks % 40 == 0) {
                ctx.state().putIfAbsent(REARM_FLIPPED, "nach dem Crash mit Neigung " + Flight.format(tilt) + "° aufgerichtet");
                Pilot.click(FLIP_KEY);
            }
            return ticks > 400 ? StepResult.fail("liegt nach dem Crash schief und lässt sich nicht aufrichten (" + armState() + ")") : StepResult.pending();
        }
        if (!low) {
            ctx.state().remove(REARM_LOW);
            return ticks > 300 ? StepResult.fail("Gas geht nach dem Crash nicht ganz herunter (" + armState() + ")") : StepResult.pending();
        }
        int lowSince = (int) ctx.state().computeIfAbsent(REARM_LOW, key -> ticks);
        if ((ticks - lowSince) % 40 == 0) {
            Pilot.click(Pilot.ARM);
        }
        return ticks > 400 ? StepResult.fail("lässt sich nach dem Crash nicht wieder scharf schalten (" + armState() + ")") : StepResult.pending();
    }

    private static StepResult afterRearm(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        String when = "nach erneutem Scharfschalten";
        if (!Pilot.armed()) {
            return StepResult.fail(when + ": nicht mehr scharf (" + armState() + ")");
        }
        if (!PropwashClientApi.input().switchActive(CamControl.COURSE_LOCK.id())) {
            return StepResult.fail(when + ": Schalter Course Lock steht nicht mehr, die Prüfung hätte keine Aussage (" + armState() + ")");
        }
        Optional<NavStatus> status = Pilot.status();
        SmartState smart = Pilot.drone().map(d -> PropwashApi.droneData().get(d, CamDroneData.smart())).orElse(SmartState.NONE);
        boolean function = status.map(s -> s.function() != NavLogic.Function.NONE).orElse(false);
        boolean mission = status.map(s -> s.missionState() == NavLogic.MissionState.RUNNING).orElse(false);
        String seen = "Funktion " + status.map(s -> s.function().name()).orElse("?") + ", Mission " + status.map(s -> s.missionState().name()).orElse("?")
                + ", Drohnen-Daten " + smart.function();
        if ((function || mission || smart.function() != SmartState.Function.NONE) && !ctx.state().containsKey(REARM_SEEN)) {
            ctx.state().put(REARM_SEEN, "nach " + Flight.format(ticks / 20.0) + " s " + seen);
        }
        if (ticks < REARM_WATCH_TICKS) {
            return StepResult.pending();
        }
        String watched = seen + ", Schalter Course Lock weiter an, " + Flight.format(REARM_WATCH_TICKS / 20.0) + " s beobachtet";
        String active = (String) ctx.state().get(REARM_SEEN);
        if (active != null) {
            String line = when + " wieder aktiv: " + active + " (" + watched + ")";
            String previous = (String) ctx.state().getOrDefault(CRASHED + ".problems", "");
            ctx.state().put(CRASHED + ".problems", (previous.isEmpty() ? "" : previous + "; ") + line);
            return StepResult.pass(line);
        }
        String line = when + ": " + watched;
        ctx.state().put(CRASHED, ctx.state().get(CRASHED) + "; " + line);
        return StepResult.pass(line);
    }

    private static String armState() {
        Optional<DroneView> view = Pilot.drone().flatMap(d -> PropwashApi.drones().view(d));
        String body = view.map(v -> "Neigung " + Flight.format(tilt(v)) + "°, Tempo " + Flight.format(v.speed()) + " m/s, ").orElse("");
        return body + "Gas " + Flight.format(PropwashClientApi.input().throttle()) + ", scharf " + Pilot.armed() + ", " + Flight.describe();
    }

    private static double tilt(DroneView view) {
        return Math.toDegrees(Math.acos(Math.clamp(view.up().y, -1.0, 1.0)));
    }
}
