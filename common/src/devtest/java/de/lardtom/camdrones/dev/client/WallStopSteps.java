package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.hud.AttiTone;
import de.lardtom.camdrones.client.hud.CamOsd;
import de.lardtom.camdrones.client.hud.CamOsdSource;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WallStopSteps {
    public static final String NAME = "hindernis: Vollgas gegen die Wand";
    public static final int WALL_DISTANCE = 45;
    public static final int LANE_Z = -150;
    public static final double NEAR = 4.0;
    public static final double MIN_NORMAL_PEAK = 8.0;
    public static final double MIN_CINE_SHARE = 0.7;
    public static final double WALL_TOP = 12.0;
    public static final double NEAR_TOP = 2.0;
    public static final double HEIGHT_TOLERANCE = 0.15;
    public static final double HEIGHT_THROTTLE = 0.09;
    public static final double LEVEL_ACCEPT = 0.25;
    public static final String ANGLE_LIMIT_KEY = "pid.angleLimitDeg";
    public static final double ANGLE_LIMIT_TOLERANCE = 0.05;
    public static final String ATTI_NAME = "hindernis: GPS weg vor der Wand";
    public static final double ATTI_HEIGHT = 1.0;
    public static final int ROOF_ABOVE = 7;
    public static final int ROOF_HALF = 12;
    public static final int ATTI_PUSH_TICKS = 20 * 15;
    public static final int CENTER_TICKS = 20;
    public static final double STILL_SPEED = 0.3;
    public static final int STILL_TICKS = 20;
    private static final String CLOSEST = "wallstop.closest";
    private static final String PEAK = "wallstop.peak";
    private static final String NEAR_SEEN = "wallstop.near";
    private static final String START = "wallstop.start";
    private static final String HIGHEST = "wallstop.highest";
    private static final String LEVEL_SETTLING = "wallstop.levelSettling";
    private static final String TILT = "wallstop.tilt";
    private static final String STILL_SINCE = "wallstop.stillSince";
    private static final String ATTI_WALL = "wallstop.attiWall";
    private static final String ATTI_CLOSEST = "wallstop.attiClosest";
    private static final String ATTI_MISSING = "wallstop.attiMissing";
    private static final String ATTI_STOPPED = "wallstop.attiStopped";
    private static final int ATTI_WAIT_TICKS = 20 * 40;
    private static final int CENTER_WAIT_TICKS = 20 * 5;
    private static final List<String> PLAYED = new CopyOnWriteArrayList<>();
    private static boolean listening;
    private static final int LEVEL_TICKS = 20 * 60;
    private static final int STEP_TICKS = 20 * 180;
    private static final int RUN_TICKS = 20 * 45;

    private WallStopSteps() {
    }

    public static void register() {
        step(361, CamPresets.TOUR, NavLogic.StickMode.NORMAL, 3.0, -40);
        step(362, CamPresets.TOUR, NavLogic.StickMode.NORMAL, 6.0, -24);
        step(363, CamPresets.TOUR, NavLogic.StickMode.CINE, 3.0, -8);
        step(364, CamPresets.CINE, NavLogic.StickMode.NORMAL, 3.0, 8);
        step(365, CamPresets.CINE, NavLogic.StickMode.NORMAL, 6.0, 24);
        step(366, CamPresets.CINE, NavLogic.StickMode.CINE, 3.0, 40);
        step(367, CamPresets.TOUR, NavLogic.StickMode.NORMAL, 11.0, -56);
        step(368, CamPresets.TOUR, NavLogic.StickMode.NORMAL, 11.6, -72);
        step(369, CamPresets.CINE, NavLogic.StickMode.NORMAL, 11.0, 56);
        step(370, CamPresets.CINE, NavLogic.StickMode.NORMAL, 11.6, 72);
        step(371, CamPresets.CINE, NavLogic.StickMode.NORMAL, 6.0, 88, 30.0);
        step(372, CamPresets.CINE, NavLogic.StickMode.NORMAL, 6.0, 104, 45.0);
        step(373, CamPresets.CINE, NavLogic.StickMode.NORMAL, 6.0, 120, 60.0);
        step(374, CamPresets.CINE, NavLogic.StickMode.NORMAL, 10.0, -88, 30.0);
        step(375, CamPresets.CINE, NavLogic.StickMode.NORMAL, 10.0, -104, 45.0);
        step(376, CamPresets.TOUR, NavLogic.StickMode.NORMAL, 10.0, -120, 30.0);
        atti(377, CamPresets.CINE, 136);
        atti(378, CamPresets.TOUR, -136);
    }

    private static void atti(int number, CamPresets.Entry entry, int lane) {
        Script script = Script.create()
                .server("Wind aus", (server, state) -> Arena.gameRule(server, Arena.WIND, "0"))
                .append(KeyboardDeflection.set(KeyboardDeflection.MAX_PERCENT))
                .append(Pilot.launch(entry, lane, LANE_Z, 0.0F))
                .append(Flight.takeoff(ATTI_HEIGHT))
                .append(level(ATTI_HEIGHT))
                .server("Wand " + WALL_DISTANCE + " m vor die Drohne", (server, state) -> CovCamAKit.wall(server, state, WALL_DISTANCE))
                .run("Töne mitschreiben", WallStopSteps::listen)
                .then("Vollgas bis zum Stillstand vor der Wand", WallStopSteps::still)
                .server("Dach über die Drohne: GPS weg", WallStopSteps::roof)
                .then("weiter Vollgas bis ATTI", WallStopSteps::untilAtti)
                .then("in ATTI " + ATTI_PUSH_TICKS / 20 + " s weiter Vollgas", WallStopSteps::pushInAtti)
                .then("Knüppel in die Mitte: freies ATTI", WallStopSteps::centred);
        SelfTest.clientStep(number, ATTI_NAME + " – " + entry.path() + " N " + Flight.format(ATTI_HEIGHT) + " m", STEP_TICKS, script, ctx -> {
            Pilot.releaseKeys();
            return KeyboardDeflection.restore(ctx);
        });
    }

    private static synchronized void listen() {
        PLAYED.clear();
        if (!listening) {
            listening = true;
            Minecraft.getInstance().getSoundManager().addListener(WallStopSteps::played);
        }
    }

    private static void played(SoundInstance sound, WeighedSoundEvents events, float range) {
        Identifier id = sound.getIdentifier();
        if (id != null && CamDrones.MOD_ID.equals(id.getNamespace())) {
            PLAYED.add(id.getPath());
        }
    }

    private static long attiTones() {
        return PLAYED.stream().filter(AttiTone.SOUND.getPath()::equals).count();
    }

    private static List<String> osdWarnings() {
        return CamOsd.warnings(CamOsdSource.last()).stream().map(OsdText::text).toList();
    }

    private static void roof(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        BlockPos base = BlockPos.containing(drone.getX(), Arena.ground().getY(), drone.getZ());
        Arena.fill(Arena.level(server), base.offset(-ROOF_HALF, ROOF_ABOVE, -ROOF_HALF), base.offset(ROOF_HALF, ROOF_ABOVE, ROOF_HALF),
                Blocks.STONE.defaultBlockState());
    }

    private static double closer(SelfTest.StepContext ctx, String key, double wall) {
        double closest = Math.min((double) ctx.state().getOrDefault(key, Double.MAX_VALUE), Double.isFinite(wall) ? wall : Double.MAX_VALUE);
        ctx.state().put(key, closest);
        return closest;
    }

    private static String warnings(Optional<NavStatus> status) {
        return status.map(s -> (s.atti() ? "ATTI " : "") + s.warnings()).orElse("-");
    }

    private static StepResult still(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.hold(Pilot.PITCH_FORWARD, true);
        double wall = CovCamAKit.wallDistance(ctx.state());
        double speed = Flight.horizontalSpeed(ctx, 4);
        double closest = closer(ctx, CLOSEST, wall);
        if (ticks % 20 == 0) {
            ctx.log(String.format(Locale.ROOT, "t=%.0f s Wand %.2f m, %.2f m/s, Höhe %.2f m, %s", ticks / 20.0, wall, speed, Flight.height(),
                    warnings(Pilot.status())));
        }
        if (!Pilot.armed()) {
            return StepResult.fail("Aufprall vor dem Dach: nächster Abstand " + Flight.format(closest) + " m");
        }
        if (wall < 0.0) {
            return StepResult.fail("fliegt über die Wand");
        }
        boolean early = Pilot.status().map(NavStatus::atti).orElse(false);
        if ((wall < NEAR || early) && Double.isFinite(speed) && speed < STILL_SPEED) {
            int since = (int) ctx.state().computeIfAbsent(STILL_SINCE, key -> ticks);
            if (ticks - since >= STILL_TICKS) {
                ctx.state().remove(STILL_SINCE);
                return StepResult.pass(String.format(Locale.ROOT, "steht mit Vollgas %.2f m vor der Wand, nächster Abstand %.2f m, Höhe %.2f m, %s%s", wall,
                        closest, Flight.height(), warnings(Pilot.status()), early ? " (GPS schon im Anflug weg)" : ""));
            }
        } else {
            ctx.state().remove(STILL_SINCE);
        }
        return ticks > RUN_TICKS ? StepResult.fail("steht nicht vor der Wand: noch " + Flight.format(wall) + " m, " + Flight.format(speed) + " m/s")
                : StepResult.pending();
    }

    private static StepResult untilAtti(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.hold(Pilot.PITCH_FORWARD, true);
        double wall = CovCamAKit.wallDistance(ctx.state());
        double closest = closer(ctx, CLOSEST, wall);
        if (!Pilot.armed()) {
            return StepResult.fail("Aufprall vor ATTI: nächster Abstand " + Flight.format(closest) + " m");
        }
        Optional<NavStatus> status = Pilot.status();
        if (status.map(NavStatus::atti).orElse(false)) {
            ctx.state().put(ATTI_WALL, wall);
            return StepResult.pass(String.format(Locale.ROOT, "ATTI nach %.1f s unter dem Dach, %.2f m vor der Wand, %s", ticks / 20.0, wall,
                    warnings(status)));
        }
        return ticks > ATTI_WAIT_TICKS ? StepResult.fail("kein ATTI unter dem Dach: " + warnings(status)) : StepResult.pending();
    }

    private static StepResult pushInAtti(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.hold(Pilot.PITCH_FORWARD, true);
        double wall = CovCamAKit.wallDistance(ctx.state());
        double closest = closer(ctx, ATTI_CLOSEST, wall);
        Optional<NavStatus> status = Pilot.status();
        boolean atti = status.map(NavStatus::atti).orElse(false);
        boolean off = status.map(s -> s.warns(NavOutput.Warning.OBSTACLE_OFF)).orElse(false);
        boolean stopped = status.map(s -> s.warns(NavOutput.Warning.OBSTACLE_STOP)).orElse(false);
        if (atti && !(off && stopped)) {
            ctx.state().merge(ATTI_MISSING, 1, (a, b) -> (int) a + (int) b);
        }
        if (atti && stopped) {
            ctx.state().merge(ATTI_STOPPED, 1, (a, b) -> (int) a + (int) b);
        }
        if (ticks % 20 == 0) {
            ctx.log(String.format(Locale.ROOT, "ATTI t=%.0f s Wand %.2f m, %.2f m/s, Höhe %.2f m, %s", ticks / 20.0, wall,
                    Flight.horizontalSpeed(ctx, 4), Flight.height(), warnings(status)));
        }
        double start = (double) ctx.state().getOrDefault(ATTI_WALL, wall);
        String values = String.format(Locale.ROOT, "beim GPS-Verlust %.2f m vor der Wand, danach nächster Abstand %.2f m", start, closest);
        if (!Pilot.armed()) {
            Pilot.hold(Pilot.PITCH_FORWARD, false);
            return StepResult.fail("Aufprall in ATTI: " + values);
        }
        if (closest < ObstacleGuard.STOP_DISTANCE) {
            Pilot.hold(Pilot.PITCH_FORWARD, false);
            return StepResult.fail("fliegt in ATTI zur Wand: " + values + ", " + warnings(status));
        }
        if (ticks < ATTI_PUSH_TICKS) {
            return StepResult.pending();
        }
        int missing = (int) ctx.state().getOrDefault(ATTI_MISSING, 0);
        int held = (int) ctx.state().getOrDefault(ATTI_STOPPED, 0);
        List<String> osd = osdWarnings();
        String shown = CamOsd.Warning.OBSTACLE_OFF.shown();
        long tones = attiTones();
        String text = String.format(Locale.ROOT, "%s, nach %d s Vollgas %.2f m vor der Wand; ATTI mit Halt %d Ticks, ohne Warnung %d Ticks; OSD %s; Töne %s",
                values, ATTI_PUSH_TICKS / 20, wall, held, missing, osd, PLAYED);
        if (missing > 2 || held < ATTI_PUSH_TICKS - 2) {
            return StepResult.fail("Halt oder Warnung fehlt in ATTI: " + text);
        }
        if (!osd.contains(shown)) {
            return StepResult.fail("OSD zeigt \"" + shown + "\" nicht: " + text);
        }
        if (tones != 1) {
            return StepResult.fail("Ton " + AttiTone.SOUND + " " + tones + " mal statt einmal: " + text);
        }
        return StepResult.pass(text);
    }

    private static StepResult centred(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.hold(Pilot.PITCH_FORWARD, false);
        if (!Pilot.armed()) {
            return StepResult.fail("Aufprall nach dem Loslassen");
        }
        if (ticks < CENTER_TICKS) {
            return StepResult.pending();
        }
        Optional<NavStatus> status = Pilot.status();
        boolean atti = status.map(NavStatus::atti).orElse(false);
        boolean off = status.map(s -> s.warns(NavOutput.Warning.OBSTACLE_OFF)).orElse(false);
        boolean stopped = status.map(s -> s.warns(NavOutput.Warning.OBSTACLE_STOP)).orElse(true);
        double wall = CovCamAKit.wallDistance(ctx.state());
        if (atti && off && !stopped) {
            return StepResult.pass(String.format(Locale.ROOT, "Knüppel in der Mitte: freies ATTI ohne Halt, %.2f m vor der Wand, %s", wall,
                    warnings(status)));
        }
        return ticks > CENTER_WAIT_TICKS ? StepResult.fail("nach der Mitte kein freies ATTI: " + warnings(status)) : StepResult.pending();
    }

    private static String label(NavLogic.StickMode mode) {
        return mode == NavLogic.StickMode.CINE ? "C" : "N";
    }

    private static boolean nearTop(double height) {
        return height > WALL_TOP - NEAR_TOP;
    }

    private static void step(int number, CamPresets.Entry entry, NavLogic.StickMode mode, double height, int lane) {
        step(number, entry, mode, height, lane, Double.NaN);
    }

    private static void step(int number, CamPresets.Entry entry, NavLogic.StickMode mode, double height, int lane, double angleLimit) {
        boolean limited = !Double.isNaN(angleLimit);
        String what = entry.path() + " " + label(mode) + " " + Flight.format(height) + " m"
                + (nearTop(height) ? " (Oberkante " + Flight.format(WALL_TOP - height) + " m darüber)" : "")
                + (limited ? ", Winkelgrenze " + Flight.format(angleLimit) + "°" : "");
        Script launch = limited
                ? Pilot.launch(entry.path() + " mit Winkelgrenze " + Flight.format(angleLimit) + "°",
                        entry.data().withTune(new TuneSpec(Map.of(ANGLE_LIMIT_KEY, (float) angleLimit))), lane, LANE_Z, 0.0F)
                : Pilot.launch(entry, lane, LANE_Z, 0.0F);
        Script script = Script.create()
                .server("Wind aus", (server, state) -> Arena.gameRule(server, Arena.WIND, "0"))
                .append(KeyboardDeflection.set(KeyboardDeflection.MAX_PERCENT))
                .append(launch);
        if (limited) {
            script.then("Winkelgrenze " + Flight.format(angleLimit) + "° am Client", (ctx, ticks) -> angleLimit(angleLimit));
        }
        script.append(Flight.takeoff(nearTop(height) ? height - 1.0 : height));
        if (nearTop(height)) {
            script.append(level(height));
        }
        if (mode != NavLogic.StickMode.NORMAL) {
            script.append(Flight.mode(mode));
        }
        script.server("Wand " + WALL_DISTANCE + " m vor die Drohne", (server, state) -> CovCamAKit.wall(server, state, WALL_DISTANCE))
                .then("Vollgas auf die Wand zu", (ctx, ticks) -> towards(ctx, ticks, entry.flightClass(), mode))
                .then("loslassen", Flight::brake);
        SelfTest.clientStep(number, NAME + " – " + what, STEP_TICKS, script, ctx -> {
            Pilot.releaseKeys();
            return KeyboardDeflection.restore(ctx);
        });
    }

    private static StepResult angleLimit(double expected) {
        Optional<Double> limit = Pilot.drone().flatMap(d -> PropwashApi.drones().view(d)).map(DroneView::angleLimitDeg);
        if (limit.isEmpty()) {
            return StepResult.fail("keine Drohne am Client");
        }
        if (Math.abs(limit.get() - expected) > ANGLE_LIMIT_TOLERANCE) {
            return StepResult.fail(String.format(Locale.ROOT, "Drohne meldet Winkelgrenze %.1f° statt %.1f°", limit.get(), expected));
        }
        return StepResult.pass();
    }

    private static double tilt() {
        return Pilot.drone().flatMap(d -> PropwashApi.drones().view(d))
                .map(view -> Math.toDegrees(Math.asin(Math.clamp(-view.forward().y, -1.0, 1.0)))).orElse(Double.NaN);
    }

    private static Script level(double metres) {
        return Script.create().then("Höhe auf " + Flight.format(metres) + " m", (ctx, ticks) -> {
            Flight.track(ctx);
            double error = metres - Flight.height();
            if (Double.isNaN(error)) {
                return StepResult.fail("keine Drohne am Client");
            }
            if (ticks > LEVEL_TICKS) {
                return StepResult.fail("erreicht " + Flight.format(metres) + " m nicht, Höhe " + Flight.format(Flight.height()) + " m");
            }
            boolean settling = (boolean) ctx.state().getOrDefault(LEVEL_SETTLING, false);
            if (!settling) {
                if (Math.abs(error) > HEIGHT_TOLERANCE) {
                    Pilot.steer(Pilot.THROTTLE_CENTER + Math.copySign(HEIGHT_THROTTLE, error));
                    return StepResult.pending();
                }
                if (Pilot.steer(Pilot.THROTTLE_CENTER)) {
                    ctx.state().put(LEVEL_SETTLING, true);
                }
                return StepResult.pending();
            }
            StepResult calm = Flight.calm(ctx, ticks, LEVEL_TICKS);
            if (!calm.done()) {
                return calm;
            }
            if (Math.abs(error) > LEVEL_ACCEPT) {
                ctx.state().put(LEVEL_SETTLING, false);
                return StepResult.pending();
            }
            ctx.state().remove(LEVEL_SETTLING);
            return StepResult.pass();
        });
    }

    private static double minimumPeak(FlightClass flightClass, NavLogic.StickMode mode) {
        return mode == NavLogic.StickMode.CINE ? MIN_CINE_SHARE * flightClass.cineSpeed() : MIN_NORMAL_PEAK;
    }

    private static StepResult towards(SelfTest.StepContext ctx, int ticks, FlightClass flightClass, NavLogic.StickMode mode) {
        Flight.track(ctx);
        Pilot.hold(Pilot.PITCH_FORWARD, true);
        Optional<NavStatus> status = Pilot.status();
        if (ticks == 0 && status.isPresent() && status.get().stickMode() != mode) {
            Pilot.hold(Pilot.PITCH_FORWARD, false);
            return StepResult.fail("Modus " + status.get().stickMode() + " statt " + mode);
        }
        double wall = CovCamAKit.wallDistance(ctx.state());
        double speed = Flight.horizontalSpeed(ctx, 4);
        double closest = Math.min((double) ctx.state().getOrDefault(CLOSEST, Double.MAX_VALUE), Double.isFinite(wall) ? wall : Double.MAX_VALUE);
        ctx.state().put(CLOSEST, closest);
        double peak = Math.max((double) ctx.state().getOrDefault(PEAK, 0.0), Double.isFinite(speed) ? speed : 0.0);
        ctx.state().put(PEAK, peak);
        double height = Flight.height();
        double start = (double) ctx.state().computeIfAbsent(START, key -> height);
        double highest = Math.max((double) ctx.state().getOrDefault(HIGHEST, start), Double.isFinite(height) ? height : start);
        ctx.state().put(HIGHEST, highest);
        double tilt = tilt();
        double steepest = Math.max((double) ctx.state().getOrDefault(TILT, 0.0), Double.isFinite(tilt) ? Math.abs(tilt) : 0.0);
        ctx.state().put(TILT, steepest);
        if (ticks % 10 == 0) {
            ctx.log(String.format(Locale.ROOT, "t=%.1f s Wand %.2f m, %.2f m/s, Höhe %.2f m, Sensor %s %.2f, Warnungen %s", ticks / 20.0, wall, speed,
                    Flight.height(), status.flatMap(NavStatus::nearestSensor).map(Enum::name).orElse("-"),
                    status.map(NavStatus::nearestDistance).orElse(Double.NaN), status.map(s -> s.warnings().toString()).orElse("-")));
        }
        String values = String.format(Locale.ROOT, "nächster Abstand %.2f m, Spitzentempo %.2f m/s, Höhe beim Start %.2f m, höchstens %.2f m"
                + " (Oberkante %.2f m darüber), größte Neigung %.1f°", closest, peak, start, highest, WALL_TOP - start, steepest);
        if (!Pilot.armed()) {
            Pilot.hold(Pilot.PITCH_FORWARD, false);
            return StepResult.fail("Aufprall: " + values);
        }
        if (wall < 0.0) {
            Pilot.hold(Pilot.PITCH_FORWARD, false);
            return StepResult.fail("fliegt über die Wand: " + values);
        }
        if (closest < ObstacleGuard.STOP_DISTANCE) {
            Pilot.hold(Pilot.PITCH_FORWARD, false);
            return StepResult.fail("unterschreitet den Stopp-Abstand " + Flight.format(ObstacleGuard.STOP_DISTANCE) + " m: " + values);
        }
        boolean near = (boolean) ctx.state().getOrDefault(NEAR_SEEN, false) || wall < NEAR;
        ctx.state().put(NEAR_SEEN, near);
        if (near) {
            StepResult calm = Flight.calm(ctx, ticks, RUN_TICKS);
            if (!calm.done()) {
                return calm;
            }
            Pilot.hold(Pilot.PITCH_FORWARD, false);
            if (calm.failed()) {
                return StepResult.fail("hält vor der Wand nicht an: " + values + ", steht " + Flight.format(wall) + " m vor der Wand");
            }
            double minimum = minimumPeak(flightClass, mode);
            if (peak < minimum) {
                return StepResult.fail("kein Vollgas: " + values + " (mindestens " + Flight.format(minimum) + " m/s)");
            }
            return StepResult.pass(String.format(Locale.ROOT, "steht mit Vollgas %.2f m vor der Wand, %s, Warnungen %s", wall, values,
                    status.map(s -> s.warnings().toString()).orElse("-")));
        }
        if (ticks > RUN_TICKS) {
            Pilot.hold(Pilot.PITCH_FORWARD, false);
            return StepResult.fail("erreicht die Wand nicht: " + values + ", noch " + Flight.format(wall) + " m");
        }
        return StepResult.pending();
    }
}
