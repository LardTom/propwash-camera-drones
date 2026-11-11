package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.hud.SpeedUnit;
import de.lardtom.camdrones.client.planner.CamClientConfig;
import de.lardtom.camdrones.client.planner.CamSettingsTab;
import de.lardtom.camdrones.client.planner.PlannerScreen;
import de.lardtom.camdrones.client.school.CameraDroneScene;
import de.lardtom.camdrones.client.school.FirstMissionScene;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.model.PilotGimbalInput;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerOwners;
import de.lardtom.camdrones.planner.model.EditorLayout;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.worldrule.WorldRule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CovCamBHudSteps {
    public static final String CAMERA_VIEW = "hud: Kamera-Ansicht mit Hindernis und Aufnahme";
    public static final String UNITS = "hud: Einheit km/h und Warnton-Lautstärke";
    public static final String SOUNDS = "töne: jedes Ereignis spielt seinen Ton";
    public static final String SOUNDS_MISSING = "töne: Gimbal und Höhengrenze spielen ihren Ton";
    public static final String SCHOOL = "flugschule: Kamera-Drohne fliegen und Erste Mission durchlaufen";
    public static final String SOUND_GIMBAL = "töne: Gimbal bewegen bleibt stumm, Zentrieren spielt gimbal.calibrate";
    public static final String SOUND_GIMBAL_AFTER_STOP = "töne: Gimbal-Ton auch nach einem Flug mit Neige-Rad am unteren Anschlag";
    public static final String GIMBAL_SOUND = "gimbal.calibrate";
    public static final int GIMBAL_QUIET_TICKS = 60;
    public static final int GIMBAL_MOVE_TICKS = 30;
    public static final int GIMBAL_TAP_TICKS = 3;
    public static final int RECENTER_TICKS = 5;
    public static final double SERVO_NEAR = 2.0;
    private static final String GIMBAL_BEFORE = "covcamb.hud.gimbalBefore";
    private static final String DIAL_BEFORE = "covcamb.hud.dialBefore";
    private static final String DIAL_TILT = "covcamb.hud.dialTilt";
    private static final String DIAL_SINCE = "covcamb.hud.dialSince";
    private static final int DIAL_STILL_TICKS = 10;
    public static final String RULES_PHOTOS = "bildschirmfotos: Welt-Regeln der Kamera-Drohnen bei GUI 320×240 auf Deutsch";
    public static final String RULES_SMALL = "welt-regeln: Abschnitt bei GUI 320×240 auf Deutsch ganz lesbar";
    public static final List<String> GREEN_SOUNDS = List.of("planner.click", "planner.waypoint", "mission.start", "mission.done", "crew.request");
    public static final List<String> MISSING_SOUNDS = List.of("gimbal.calibrate", "warning.limit");
    public static final List<String> DECLARED = List.of("planner.click", "planner.waypoint", "mission.start", "mission.done", "gimbal.calibrate",
            "warning.obstacle", "warning.limit", "crew.request");
    public static final List<String> HUD_TEXTS = List.of("BAT ", "SIG ", "H.S. ", "V.S. ", "REC ");
    private static final List<Played> PLAYED = new CopyOnWriteArrayList<>();
    private static final String NOTES = "covcamb.hud.notes";
    private static final String MARK = "covcamb.hud.mark";
    private static final String STEP = "covcamb.hud.step";
    private static final String SINCE = "covcamb.hud.since";
    private static final int STEP_TICKS = 20 * 240;
    private static final SoundEventListener LISTENER = CovCamBHudSteps::played;
    private static boolean listening;

    record Played(String id, String file, boolean exists, float volume, float pitch, long time, boolean relative, String source, double x, double y,
                  double z) {
        double distanceTo(Entity entity) {
            return Math.sqrt(entity.distanceToSqr(this.x, this.y, this.z));
        }
    }

    private CovCamBHudSteps() {
    }

    @SuppressWarnings("unchecked")
    private static List<String> notes(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(NOTES, key -> new ArrayList<String>());
    }

    static void cameraView() {
        SelfTest.clientStep(531, CAMERA_VIEW, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .append(CovCamBKit.goTo(-130, -20))
                .server("Wand vor der Drohne", (server, state) -> {
                    BlockPos base = Arena.ground(-130, -20);
                    Arena.fill(Arena.level(server), base.offset(-4, 1, -7), base.offset(4, 12, -7), Blocks.STONE_BRICKS.defaultBlockState());
                })
                .append(Pilot.launch(CamPresets.TOUR, -130, -20, 180.0F))
                .server("Kamera-Ansicht einstellen", (server, state) -> PropwashApi.droneData().set(Pilot.serverDrone(server, state),
                        CamDroneData.settings(), DroneSettings.DEFAULT.withView(DroneSettings.CameraView.HUD)))
                .await("Einstellung am Client", 100, () -> Pilot.drone().map(d -> PropwashApi.droneData().get(d, CamDroneData.settings()).view())
                        .filter(view -> view == DroneSettings.CameraView.HUD).isPresent())
                .append(Flight.takeoff(3.0))
                .then("Aufnahme starten", (ctx, ticks) -> {
                    Optional<Entity> drone = Pilot.drone();
                    if (drone.isEmpty()) {
                        return StepResult.fail("Drohne verloren");
                    }
                    if (ticks == 0 && !PropwashClientApi.recording().start(drone.get())) {
                        return StepResult.fail("Aufnahme startet nicht");
                    }
                    return ticks >= 30 ? StepResult.pass() : StepResult.pending();
                })
                .then("Hindernis voraus", CovCamBHudSteps::obstacle)
                .then("Kamera-Ansicht mit Hindernis und Aufnahme", CovCamBHudSteps::hudTexts)
                .append(Shots.photos(CovCamBKit.PHOTO + "hud_camera_view_obstacle_rec"))
                .then("Aufnahme beenden", (ctx, ticks) -> {
                    PropwashClientApi.recording().stop();
                    return StepResult.pass(String.join("; ", notes(ctx)));
                }), ctx -> {
                    PropwashClientApi.recording().stop();
                    return StepResult.pass();
                });
    }

    private static StepResult obstacle(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Optional<NavStatus> status = Pilot.status();
        boolean warn = status.map(s -> s.warns(NavOutput.Warning.OBSTACLE) || s.warns(NavOutput.Warning.OBSTACLE_STOP)).orElse(false);
        Pilot.hold(Pilot.PITCH_FORWARD, !warn && ticks < 200);
        if (warn && ticks >= 10) {
            Pilot.hold(Pilot.PITCH_FORWARD, false);
            return StepResult.pass("Hindernis in " + CovCamBKit.f(status.get().nearestDistance()) + " m");
        }
        return ticks > 260 ? StepResult.fail("keine Hindernis-Warnung vor der Wand: " + status.map(NavStatus::warnings).orElse(null)) : StepResult.pending();
    }

    private static StepResult hudTexts(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        if (ticks < 10) {
            return StepResult.pending();
        }
        Minecraft mc = Minecraft.getInstance();
        LayoutCheck.Frame frame;
        try {
            frame = LayoutCheck.capture(mc, g -> mc.gui.hud.extractRenderState(g, mc.getDeltaTracker()), List.of());
        } catch (RuntimeException e) {
            return StepResult.fail("HUD lässt sich nicht zeichnen: " + e);
        }
        List<String> missing = new ArrayList<>();
        for (String text : HUD_TEXTS) {
            if (!frame.shows(text)) {
                missing.add(text.trim());
            }
        }
        if (!missing.isEmpty()) {
            if (ticks < 60) {
                return StepResult.pending();
            }
            ctx.log("Texte im HUD: " + frame.values());
            return StepResult.fail("Kamera-Ansicht ohne " + missing);
        }
        String rec = frame.values().stream().filter(v -> v.startsWith("REC ")).findFirst().orElse("");
        notes(ctx).add("Kamera-Ansicht mit " + String.join(", ", HUD_TEXTS.stream().map(String::trim).toList()) + " und Hindernis-Warnung, Aufnahme \""
                + rec + "\"");
        return StepResult.pass();
    }

    static void units() {
        SelfTest.clientStep(532, UNITS, STEP_TICKS, Script.create()
                .then("Einstellungen des Add-ons", (ctx, ticks) -> {
                    if (ticks == 0 && !PropwashClientApi.openConfigTab(CamSettingsTab.ID, null)) {
                        return StepResult.fail("kein Tab " + CamSettingsTab.ID);
                    }
                    return ticks >= 10 ? StepResult.pass() : StepResult.pending();
                })
                .then("Einheit km/h und Lautstärke im Tab", CovCamBHudSteps::unitControls)
                .then("Einheit auf km/h", (ctx, ticks) -> clickUntil(ctx, ticks, "km/h"))
                .then("speichern (Strg+S)", CovCamBHudSteps::saveUnits)
                .append(CovCamBKit.goTo(-142, -20))
                .append(Pilot.launch(CamPresets.TOUR, -142, -20, 0.0F))
                .server("Kamera-Ansicht", (server, state) -> PropwashApi.droneData().set(Pilot.serverDrone(server, state), CamDroneData.settings(),
                        DroneSettings.DEFAULT.withView(DroneSettings.CameraView.HUD)))
                .append(Flight.takeoff(3.0))
                .then("Kamera-Ansicht zeigt km/h", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    LayoutCheck.Frame frame = LayoutCheck.capture(mc, g -> mc.gui.hud.extractRenderState(g, mc.getDeltaTracker()), List.of());
                    boolean kmh = frame.values().stream().anyMatch(v -> v.toLowerCase(Locale.ROOT).contains("km/h"));
                    return kmh ? StepResult.pass() : ticks > 60 ? StepResult.fail("HUD ohne km/h: " + frame.values()) : StepResult.pending();
                })
                .append(Shots.photos(CovCamBKit.PHOTO + "hud_kmh")), ctx -> {
                    Minecraft.getInstance().gui.setScreen(null);
                    CamClientConfig.get().speedUnit(CamClientConfig.SPEED_UNIT_DEFAULT);
                    return StepResult.pass();
                });
    }

    private static StepResult saveUnits(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            Screen screen = mc.gui.screen();
            if (screen == null) {
                return StepResult.fail("Einstellungen geschlossen");
            }
            screen.keyPressed(new KeyEvent(InputConstants.KEY_S, 0, InputConstants.MOD_CONTROL));
            return StepResult.pending();
        }
        SpeedUnit unit = CamClientConfig.get().speedUnit();
        if (mc.gui.screen() == null && unit == SpeedUnit.KILOMETRES_PER_HOUR) {
            return StepResult.pass("gespeichert: " + CamClientConfig.SPEED_UNIT + "=" + unit.symbol());
        }
        return ticks > 40 ? StepResult.fail("nicht gespeichert (" + SelfTestRunner.screenName(mc) + ", " + unit + ")") : StepResult.pending();
    }

    private static StepResult unitControls(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Einstellungen geschlossen");
        }
        LayoutCheck.Frame frame = LayoutCheck.capture(mc, screen);
        List<String> texts = new ArrayList<>(frame.values());
        for (AbstractWidget widget : KeyboardDeflection.widgets(screen)) {
            texts.add(widget.getMessage().getString());
        }
        boolean unit = texts.stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains("km/h") || t.toLowerCase(Locale.ROOT).contains("m/s"));
        boolean volume = texts.stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains("lautstärke") || t.toLowerCase(Locale.ROOT).contains("volume"));
        if (!unit || !volume) {
            return StepResult.fail("im Tab \"" + I18n.get("camdrones.planner.settings.title") + "\" " + (unit ? "" : "keine Einheit (m/s, km/h)")
                    + (unit || volume ? "" : " und ") + (volume ? "" : "keine Warnton-Lautstärke") + ": " + texts);
        }
        return StepResult.pass();
    }

    private static StepResult clickUntil(SelfTest.StepContext ctx, int ticks, String wanted) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Einstellungen geschlossen");
        }
        for (AbstractWidget widget : KeyboardDeflection.widgets(screen)) {
            String text = widget.getMessage().getString().toLowerCase(Locale.ROOT);
            if (text.contains(wanted)) {
                return StepResult.pass();
            }
            if (ticks % 10 == 0 && text.contains("m/s")) {
                CovCamAKit.click(screen, widget);
            }
        }
        return ticks > 60 ? StepResult.fail("Einheit lässt sich nicht auf " + wanted + " stellen") : StepResult.pending();
    }

    static synchronized void listen() {
        if (listening) {
            return;
        }
        listening = true;
        Minecraft.getInstance().getSoundManager().addListener(LISTENER);
    }

    private static void played(SoundInstance sound, WeighedSoundEvents events, float range) {
        Identifier id = sound.getIdentifier();
        if (id == null || !CamDrones.MOD_ID.equals(id.getNamespace())) {
            return;
        }
        String file = sound.getSound() == null ? "" : sound.getSound().getPath().toString();
        boolean exists = sound.getSound() != null && Minecraft.getInstance().getResourceManager().getResource(sound.getSound().getPath()).isPresent();
        PLAYED.add(new Played(id.getPath(), file, exists, sound.getVolume(), sound.getPitch(), System.nanoTime(), sound.isRelative(),
                sound.getSource().name(), sound.getX(), sound.getY(), sound.getZ()));
    }

    static void sounds() {
        SelfTest.clientStep(533, SOUNDS, STEP_TICKS, soundScript(GREEN_SOUNDS), ctx -> Script.server(ctx, ctx.tick(), (server, state) -> {
            CrewService.book().decline(Arena.player(server).getUUID());
            CovCamBKit.leave(server);
        }));
    }

    static void soundsMissing() {
        SelfTest.clientStep(536, SOUNDS_MISSING, STEP_TICKS, Script.create()
                .then("Tonliste starten", (ctx, ticks) -> {
                    listen();
                    ctx.state().put(MARK, PLAYED.size());
                    return StepResult.pass();
                })
                .append(CovCamBKit.goTo(-154, -20))
                .append(Pilot.launch(CamPresets.TOUR, -154, -20, 0.0F))
                .server("Höhengrenze 6 m", (server, state) -> Arena.rule(server, CamWorldRules.MAX_ALTITUDE_ID, "6"))
                .append(Flight.gpsFix())
                .then("Gimbal zentrieren", (ctx, ticks) -> {
                    Flight.track(ctx);
                    pressRecenter(ticks);
                    return ticks >= 40 ? StepResult.pass() : StepResult.pending();
                })
                .then("Vollgas bis zur Höhengrenze", (ctx, ticks) -> {
                    Flight.track(ctx);
                    Pilot.steer(1.0);
                    boolean limit = Pilot.status().map(s -> s.warns(NavOutput.Warning.ALTITUDE_LIMIT)).orElse(false);
                    return limit && ticks > 40 ? StepResult.pass() : ticks > 300 ? StepResult.fail("keine Höhengrenze") : StepResult.pending();
                })
                .then("Töne von Gimbal und Höhengrenze", (ctx, ticks) -> {
                    Pilot.steer(Pilot.THROTTLE_CENTER);
                    List<Played> since = since((int) ctx.state().get(MARK));
                    List<String> missing = new ArrayList<>();
                    for (String id : MISSING_SOUNDS) {
                        if (since.stream().noneMatch(p -> p.id().equals(id))) {
                            missing.add(id);
                        }
                    }
                    if (missing.isEmpty()) {
                        return StepResult.pass();
                    }
                    return ticks > 60 ? StepResult.fail("kein Ton " + missing + " beim Zentrieren des Gimbals und an der Höhengrenze (gespielt "
                            + since.stream().map(Played::id).distinct().toList() + ")") : StepResult.pending();
                }), CovCamBHudSteps::centerTilt);
    }

    private static List<Played> since(int mark) {
        List<Played> all = new ArrayList<>(PLAYED);
        return mark >= all.size() ? List.of() : List.copyOf(all.subList(mark, all.size()));
    }

    private static Script soundScript(List<String> wanted) {
        return Script.create()
                .then("Tonliste starten", (ctx, ticks) -> {
                    listen();
                    ctx.state().put(MARK, PLAYED.size());
                    return StepResult.pass();
                })
                .append(CovCamBKit.goTo(-150, 10))
                .append(CamFlights.groundDrone(CamPresets.TOUR, -150, 10, 0.0F))
                .server("Flugplaner in die Hand", (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PlannerItems.flightPlanner().orElseThrow()));
                })
                .await("Flugplaner am Client", 100, () -> Minecraft.getInstance().player != null
                        && PlannerItems.isPlanner(Minecraft.getInstance().player.getMainHandItem()))
                .then("Koppeln (Klick)", (ctx, ticks) -> CovCamAKit.use(ctx, ticks, CamFlights.clientDrone(ctx.state()).orElseThrow(), true))
                .run("Fenster zu", () -> Minecraft.getInstance().gui.setScreen(null))
                .sleep(10)
                .server("Wegpunkt per Block-Klick", (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    BlockPos block = Arena.ground(-156, 16);
                    ItemStack stack = player.getMainHandItem();
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(block).add(0.0, 0.5, 0.0), Direction.UP, block, false);
                    stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                })
                .sleep(10)
                .then("Editor öffnen", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (ticks == 0 && mc.player != null && mc.gameMode != null) {
                        mc.player.setXRot(-80.0F);
                        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    }
                    return mc.gui.screen() instanceof PlannerScreen ? StepResult.pass() : ticks > 100 ? StepResult.fail("Editor öffnet nicht")
                            : StepResult.pending();
                })
                .then("Klick im Editor", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (!(mc.gui.screen() instanceof PlannerScreen screen)) {
                        return StepResult.fail("Editor geschlossen");
                    }
                    if (ticks == 2) {
                        List<Rect> settings = screen.layout().settings();
                        if (settings.isEmpty()) {
                            return StepResult.fail("keine Einstellung im Editor");
                        }
                        Rect value = EditorLayout.valueBox(settings.getFirst(), screen.layout().settingLabelWidth());
                        MouseButtonEvent event = new MouseButtonEvent(value.x() + value.w() - 3.0, value.centerY(),
                                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                        screen.mouseClicked(event, false);
                        screen.mouseReleased(event);
                    }
                    return ticks >= 10 ? StepResult.pass() : StepResult.pending();
                })
                .run("Editor schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .server("kurze Mission an die Drohne", (server, state) -> {
                    Entity drone = Pilot.serverDrone(server, state);
                    double y = drone.getY() + 3.0;
                    CamFlights.mission(server, state, Mission.of(List.of(Waypoint.at(drone.getX(), y, drone.getZ() + 4.0),
                            Waypoint.at(drone.getX() + 4.0, y, drone.getZ() + 4.0))).withEnd(Mission.End.LAND));
                })
                .then("Mission starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Mission gestartet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.MISSION_STARTED, 100))
                .then("Mission beendet", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.MISSION_DONE, 20 * 60))
                .server("Crew-Anfrage von " + JoinEnv.GUEST_NAME, (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    ServerLevel level = player.level();
                    ServerPlayer guest = CovCamBKit.guest(server, level, player.position().add(3.0, 0.0, 0.0));
                    BlockPos ground = Arena.ground(-140, 4);
                    Entity drone = CovCamAKit.place(guest, PropwashApi.builds().createDrone(Pilot.preset(CamPresets.CINE).data()), ground, 0.0F);
                    PlannerOwners.claim(drone, guest.getUUID());
                    state.put("covcamb.verdict", String.valueOf(CrewService.invite(guest, drone, player)));
                })
                .sleep(20)
                .then("Töne auswerten", (ctx, ticks) -> evaluate(ctx, ticks, wanted));
    }

    private static StepResult evaluate(SelfTest.StepContext ctx, int ticks, List<String> wanted) {
        if (ticks < 20) {
            return StepResult.pending();
        }
        List<Played> played = since((int) ctx.state().get(MARK));
        List<String> lines = new ArrayList<>();
        lines.add("# Gespielte Ton-Ereignisse des Add-ons im Selbsttest (" + SOUNDS + ")");
        lines.add("# Id, Datei, Datei vorhanden, Lautstärke, Tonhöhe, Zeit ab dem ersten Ton in s");
        long first = played.isEmpty() ? 0L : played.getFirst().time();
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (Played p : played) {
            lines.add(String.format(Locale.ROOT, "%s %s %s %.2f %.2f %.2f", "camdrones:" + p.id(), p.file(), p.exists() ? "ja" : "NEIN", p.volume(), p.pitch(),
                    (p.time() - first) / 1.0E9));
            counts.merge(p.id(), 1, Integer::sum);
            if (!p.exists()) {
                problems.add("Datei fehlt: " + p.file());
            }
            if (!DECLARED.contains(p.id())) {
                problems.add("Id nicht in sounds.json: " + p.id());
            }
        }
        for (String id : wanted) {
            if (!counts.containsKey(id)) {
                problems.add("kein Ton " + id);
            }
        }
        int tones = threeTones();
        lines.add("# mission_start.ogg: " + tones + " Töne");
        if (tones != 3) {
            problems.add("mission.start hat " + tones + " statt 3 Töne");
        }
        lines.add("# Crew-Einladung: " + ctx.state().get("covcamb.verdict"));
        CovCamBKit.write("sound_events.txt", lines);
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join(", ", problems) + " (gespielt " + counts + ")");
        }
        return StepResult.pass(counts.size() + " Ereignisse mit eigenem Ton " + counts + ", alle Dateien vorhanden, mission.start mit 3 Tönen, Liste in "
                + CovCamBKit.OUT + "/sound_events.txt");
    }

    static int threeTones() {
        Identifier file = Identifier.fromNamespaceAndPath(CamDrones.MOD_ID, "sounds/audio/mission_start.ogg");
        byte[] bytes;
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(file)) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            return -1;
        }
        ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            data.put(bytes).flip();
            IntBuffer channels = stack.mallocInt(1);
            IntBuffer rate = stack.mallocInt(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(data, channels, rate);
            if (pcm == null) {
                return -2;
            }
            try {
                return bursts(pcm, channels.get(0), rate.get(0));
            } finally {
                LibCStdlib.free(pcm);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static int bursts(ShortBuffer pcm, int channels, int rate) {
        int window = Math.max(1, rate / 100);
        int frames = pcm.limit() / Math.max(1, channels);
        List<Double> levels = new ArrayList<>();
        double peak = 0.0;
        for (int start = 0; start + window <= frames; start += window) {
            double sum = 0.0;
            for (int i = start; i < start + window; i++) {
                double v = pcm.get(i * channels) / 32768.0;
                sum += v * v;
            }
            double rms = Math.sqrt(sum / window);
            levels.add(rms);
            peak = Math.max(peak, rms);
        }
        double threshold = peak * 0.25;
        int count = 0;
        int quiet = 0;
        boolean loud = false;
        for (double level : levels) {
            if (level >= threshold) {
                if (!loud && (count == 0 || quiet >= 3)) {
                    count++;
                }
                loud = true;
                quiet = 0;
            } else {
                loud = false;
                quiet++;
            }
        }
        return count;
    }

    static void soundGimbal() {
        SelfTest.clientStep(538, SOUND_GIMBAL, STEP_TICKS, gimbalSound(), CovCamBHudSteps::servoCleanup);
    }

    static void soundGimbalAfterStop() {
        SelfTest.clientStep(539, SOUND_GIMBAL_AFTER_STOP, STEP_TICKS, Script.create()
                .append(CovCamBKit.goTo(-116, 40))
                .append(Pilot.launch(CamPresets.TOUR, -116, 40, 0.0F))
                .then("Neige-Rad an den unteren Anschlag", CovCamBHudSteps::dialToStop)
                .server("FPV verlassen", (server, state) -> Pilot.release(server))
                .await("Flug beendet", 200, () -> !PropwashClientApi.input().flying())
                .server("erste Drohne entfernen", (server, state) -> Pilot.serverDrone(server, state).discard())
                .append(gimbalSound()), CovCamBHudSteps::servoCleanup);
    }

    private static StepResult dialToStop(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        KeyMapping down = CamKeys.decrease(CamControl.GIMBAL_TILT).orElseThrow();
        Pilot.hold(down, ticks < GIMBAL_MOVE_TICKS + 15);
        if (ticks < GIMBAL_MOVE_TICKS + 20) {
            return StepResult.pending();
        }
        double axis = PropwashClientApi.input().axis(CamControl.GIMBAL_TILT.id());
        if (axis > -1.0 + PilotGimbalInput.PICKUP) {
            return StepResult.fail("Neige-Rad bleibt bei " + Flight.format(axis) + " statt am unteren Anschlag");
        }
        return StepResult.pass();
    }

    private static Script gimbalSound() {
        return Script.create()
                .then("Tonliste starten", (ctx, ticks) -> {
                    listen();
                    return StepResult.pass();
                })
                .append(CovCamBKit.goTo(-136, 40))
                .append(Pilot.launch(CamPresets.TOUR, -136, 40, 0.0F))
                .append(Flight.gpsFix())
                .then("Neige-Rad auf Mitte", CovCamBHudSteps::centerDial)
                .then("Gimbal ruhig", CovCamBHudSteps::gimbalQuiet)
                .then("Gimbal " + GIMBAL_MOVE_TICKS + " Ticks neigen: stumm", (ctx, ticks) -> gimbalSilent(ctx, ticks,
                        CamKeys.decrease(CamControl.GIMBAL_TILT).orElseThrow(), GIMBAL_MOVE_TICKS, 5.0, "Neigen"))
                .then("Gimbal wieder ruhig", CovCamBHudSteps::gimbalQuiet)
                .then("kurz antippen (" + GIMBAL_TAP_TICKS + " Ticks): stumm", (ctx, ticks) -> gimbalSilent(ctx, ticks,
                        CamKeys.increase(CamControl.GIMBAL_TILT).orElseThrow(), GIMBAL_TAP_TICKS, 2.0, "Antippen"))
                .then("zentrieren: ein Ton an der Drohne", CovCamBHudSteps::recenterTone)
                .then("nach dem Zentrieren ruhig", CovCamBHudSteps::gimbalQuiet)
                .run("Gimbal-Surren 0 %", () -> CamClientConfig.get().servoVolume(0.0))
                .then("bei 0 % zentrieren: stumm", CovCamBHudSteps::recenterSilent)
                .run("Gimbal-Surren zurück", () -> CamClientConfig.get().servoVolume(CamClientConfig.SERVO_VOLUME_DEFAULT))
                .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx))));
    }

    private static StepResult centerDial(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        KeyMapping up = CamKeys.increase(CamControl.GIMBAL_TILT).orElseThrow();
        KeyMapping down = CamKeys.decrease(CamControl.GIMBAL_TILT).orElseThrow();
        double axis = PropwashClientApi.input().axis(CamControl.GIMBAL_TILT.id());
        if (ticks == 0) {
            ctx.state().put(DIAL_BEFORE, axis);
        }
        boolean centered = Math.abs(axis) < 0.02;
        Pilot.hold(up, !centered && axis < 0.0);
        Pilot.hold(down, !centered && axis > 0.0);
        double tilt = GimbalClient.tilt(drone.get());
        double last = (double) ctx.state().getOrDefault(DIAL_TILT, tilt);
        ctx.state().put(DIAL_TILT, tilt);
        if (ticks == 0 || !centered || Math.abs(tilt - last) > 0.1) {
            ctx.state().put(DIAL_SINCE, ticks);
        }
        if (ticks - (int) ctx.state().get(DIAL_SINCE) >= DIAL_STILL_TICKS) {
            notes(ctx).add("Neige-Rad vorher " + Flight.format((double) ctx.state().get(DIAL_BEFORE)) + ", jetzt " + Flight.format(axis)
                    + " (Gimbal " + Flight.format(tilt) + "°)");
            return StepResult.pass();
        }
        return ticks > 300 ? StepResult.fail("Neige-Rad bleibt bei " + Flight.format(axis) + ", Gimbal bei " + Flight.format(tilt) + "°")
                : StepResult.pending();
    }

    private static StepResult servoCleanup(SelfTest.StepContext ctx) {
        CamClientConfig.get().servoVolume(CamClientConfig.SERVO_VOLUME_DEFAULT);
        return centerTilt(ctx);
    }

    private static StepResult gimbalSilent(SelfTest.StepContext ctx, int ticks, KeyMapping key, int hold, double moved, String what) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks == 0) {
            ctx.state().put(MARK, PLAYED.size());
            ctx.state().put(GIMBAL_BEFORE, GimbalClient.tilt(drone.get()));
        }
        Pilot.hold(key, ticks < hold);
        if (ticks < hold + GIMBAL_QUIET_TICKS) {
            return StepResult.pending();
        }
        double before = (double) ctx.state().get(GIMBAL_BEFORE);
        double after = GimbalClient.tilt(drone.get());
        if (!(Math.abs(after - before) >= moved)) {
            return StepResult.fail(what + ": Gimbal neigt sich nicht (" + Flight.format(before) + "° auf " + Flight.format(after) + "°)");
        }
        int tones = gimbalTones(ctx).size();
        if (tones > 0) {
            return StepResult.fail(what + " " + Flight.format(before) + "° auf " + Flight.format(after) + "°: " + tones + "-mal " + GIMBAL_SOUND);
        }
        notes(ctx).add(what + " " + Flight.format(before) + "° auf " + Flight.format(after) + "° ohne Ton");
        return StepResult.pass();
    }

    private static void pressRecenter(int ticks) {
        KeyMapping mapping = CamKeys.key(CamControl.GIMBAL_RECENTER).orElseThrow();
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        if (ticks == 0) {
            CovCamBKit.down(key, true);
        } else if (ticks == RECENTER_TICKS) {
            CovCamBKit.down(key, false);
        }
    }

    private static StepResult recenterSilent(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks == 0) {
            ctx.state().put(MARK, PLAYED.size());
            ctx.state().put(GIMBAL_BEFORE, GimbalClient.tilt(drone.get()));
        }
        pressRecenter(ticks);
        if (ticks < RECENTER_TICKS + GIMBAL_QUIET_TICKS) {
            return StepResult.pending();
        }
        double before = (double) ctx.state().get(GIMBAL_BEFORE);
        double after = GimbalClient.tilt(drone.get());
        if (!(Math.abs(after - before) >= 5.0)) {
            return StepResult.fail("Zentrieren bei 0 %: Gimbal bewegt sich nicht (" + Flight.format(before) + "° auf " + Flight.format(after) + "°)");
        }
        int tones = gimbalTones(ctx).size();
        if (tones > 0) {
            return StepResult.fail("Zentrieren bei 0 %: " + tones + "-mal " + GIMBAL_SOUND);
        }
        notes(ctx).add("Zentrieren bei 0 % " + Flight.format(before) + "° auf " + Flight.format(after) + "° ohne Ton");
        return StepResult.pass();
    }

    private static StepResult centerTilt(SelfTest.StepContext ctx) {
        KeyMapping up = CamKeys.increase(CamControl.GIMBAL_TILT).orElseThrow();
        KeyMapping down = CamKeys.decrease(CamControl.GIMBAL_TILT).orElseThrow();
        double axis = PropwashClientApi.input().axis(CamControl.GIMBAL_TILT.id());
        if (Math.abs(axis) < 0.02 || !PropwashClientApi.input().flying()) {
            Pilot.hold(up, false);
            Pilot.hold(down, false);
            return StepResult.pass();
        }
        Pilot.hold(up, axis < 0.0);
        Pilot.hold(down, axis > 0.0);
        return ctx.tick() > 300 ? StepResult.fail("Gimbal-Achse bleibt bei " + Flight.format(axis)) : StepResult.pending();
    }

    private static List<Played> gimbalTones(SelfTest.StepContext ctx) {
        return since((int) ctx.state().get(MARK)).stream().filter(p -> p.id().equals(GIMBAL_SOUND)).toList();
    }

    private static StepResult gimbalQuiet(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        if (ticks == 0) {
            ctx.state().put(MARK, PLAYED.size());
        }
        if (ticks < GIMBAL_QUIET_TICKS) {
            return StepResult.pending();
        }
        int tones = gimbalTones(ctx).size();
        if (tones > 0) {
            return StepResult.fail(GIMBAL_SOUND + " " + tones + "-mal, obwohl der Gimbal ruht");
        }
        notes(ctx).add("ruhig " + GIMBAL_QUIET_TICKS + " Ticks ohne Ton");
        return StepResult.pass();
    }

    private static StepResult recenterTone(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks == 0) {
            ctx.state().put(MARK, PLAYED.size());
            ctx.state().put(GIMBAL_BEFORE, GimbalClient.tilt(drone.get()));
        }
        pressRecenter(ticks);
        if (ticks < RECENTER_TICKS + GIMBAL_QUIET_TICKS) {
            return StepResult.pending();
        }
        double before = (double) ctx.state().get(GIMBAL_BEFORE);
        double after = GimbalClient.tilt(drone.get());
        if (!(Math.abs(after - before) >= 5.0)) {
            return StepResult.fail("Gimbal zentriert nicht (" + Flight.format(before) + "° auf " + Flight.format(after) + "°)");
        }
        List<Played> tones = gimbalTones(ctx);
        if (tones.size() != 1) {
            return StepResult.fail(GIMBAL_SOUND + " " + tones.size() + "-mal beim Zentrieren von " + Flight.format(before) + "° auf "
                    + Flight.format(after) + "° statt einmal (gespielt " + since((int) ctx.state().get(MARK)).stream().map(Played::id).distinct().toList() + ")");
        }
        Played first = tones.getFirst();
        if (!first.exists() || first.volume() <= 0.0F) {
            return StepResult.fail(GIMBAL_SOUND + " ohne hörbare Datei: " + first.file() + ", Lautstärke " + first.volume());
        }
        if (first.relative() || "UI".equals(first.source()) || first.distanceTo(drone.get()) > SERVO_NEAR) {
            return StepResult.fail(GIMBAL_SOUND + " nicht an der Drohne: relativ " + first.relative() + ", Quelle " + first.source() + ", "
                    + Flight.format(first.distanceTo(drone.get())) + " m neben der Drohne");
        }
        if (Math.abs(first.volume() - CamClientConfig.SERVO_VOLUME_DEFAULT) > 0.005) {
            return StepResult.fail(GIMBAL_SOUND + " in Lautstärke " + Flight.format(first.volume()) + " statt " + CamClientConfig.SERVO_VOLUME_DEFAULT);
        }
        notes(ctx).add("Zentrieren " + Flight.format(before) + "° auf " + Flight.format(after) + "°: einmal " + GIMBAL_SOUND + " ("
                + first.file() + ", Lautstärke " + Flight.format(first.volume()) + ", Quelle " + first.source() + ", "
                + Flight.format(first.distanceTo(drone.get())) + " m neben der Drohne)");
        return StepResult.pass();
    }

    static void school() {
        Script script = Script.create().append(Shots.photoWindow());
        for (String language : List.of(Shots.ENGLISH, Shots.GERMAN)) {
            script.run("Sprache " + language, () -> Shots.language(Minecraft.getInstance(), language));
            script.append(chapter(CameraDroneScene.ID, CameraDroneScene.STEPS, language));
            script.append(chapter(FirstMissionScene.ID, FirstMissionScene.STEPS, language));
        }
        script.run("Sprache zurück", () -> Shots.restoreLanguage(Minecraft.getInstance()))
                .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx))));
        SelfTest.clientStep(534, SCHOOL, STEP_TICKS * 2, script);
    }

    private static Script chapter(String id, List<String> steps, String language) {
        String name = id.substring(id.indexOf(':') + 1);
        Script script = Script.create()
                .then("Kapitel " + name + " öffnen", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (ticks == 0) {
                        ctx.state().put(STEP, 0);
                        return PropwashClientApi.openFlightSchoolScene(id, null) ? StepResult.pending() : StepResult.fail("Kapitel " + id + " fehlt");
                    }
                    return mc.gui.screen() != null && ticks > 10 ? StepResult.pass() : ticks > 60 ? StepResult.fail("Kapitel öffnet nicht")
                            : StepResult.pending();
                })
                .then("Kapitel " + name + " Schritt für Schritt", (ctx, ticks) -> walk(ctx, ticks, id, steps, language));
        script.run("Kapitel schließen", () -> Minecraft.getInstance().gui.setScreen(null)).sleep(5);
        return script;
    }

    private static StepResult walk(SelfTest.StepContext ctx, int ticks, String id, List<String> steps, String language) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Kapitel geschlossen");
        }
        String name = id.substring(id.indexOf(':') + 1);
        Map<String, Object> state = ctx.state();
        int step = (int) state.getOrDefault(STEP, 0);
        int since = (int) state.computeIfAbsent(SINCE, key -> ticks);
        int t = ticks - since;
        String finished = I18n.get("propwash.guide.player.finished");
        LayoutCheck.Frame frame = LayoutCheck.capture(mc, screen);
        for (String value : frame.values()) {
            if (value.contains("camdrones.") || value.contains("propwash.guide.")) {
                return StepResult.fail(name + " (" + language + "): roher Schlüssel \"" + value + "\"");
            }
        }
        if (step >= steps.size()) {
            if (frame.shows(finished)) {
                state.remove(SINCE);
                notes(ctx).add(name + " (" + language + "): " + steps.size() + " Schritte erkannt, Ende \"" + finished + "\" erreicht");
                return StepResult.pass();
            }
            return t > 100 ? StepResult.fail(name + ": Ende nicht erreicht (" + frame.values() + ")") : StepResult.pending();
        }
        String title = I18n.get("camdrones.guide.scene." + name + ".step." + steps.get(step) + ".title");
        if (t < 30) {
            return StepResult.pending();
        }
        if (!frame.shows(title)) {
            return t > 120 ? StepResult.fail(name + " Schritt " + (step + 1) + ": Titel \"" + title + "\" nicht im Bild (" + frame.values() + ")")
                    : StepResult.pending();
        }
        if (Shots.enabled() && !state.containsKey(STEP + ".shot" + step)) {
            state.put(STEP + ".shot" + step, Shots.capture(CovCamBKit.PHOTO + "school_" + name + "_" + String.format(Locale.ROOT, "%02d", step + 1) + "_"
                    + language));
            return StepResult.pending();
        }
        if (state.get(STEP + ".shot" + step) instanceof java.util.concurrent.CompletableFuture<?> shot && !shot.isDone()) {
            return StepResult.pending();
        }
        state.remove(STEP + ".shot" + step);
        screen.keyPressed(new KeyEvent(InputConstants.KEY_RIGHT, 0, 0));
        state.put(STEP, step + 1);
        state.put(SINCE, ticks);
        return StepResult.pending();
    }

    static void rulesPhotos() {
        SelfTest.clientStep(535, RULES_PHOTOS, STEP_TICKS, rulesScript(false));
    }

    static void rulesSmall() {
        SelfTest.clientStep(537, RULES_SMALL, STEP_TICKS, rulesScript(true));
    }

    private static Script rulesScript(boolean strict) {
        return Script.create()
                .append(CovCamBKit.gui(320, 240))
                .run("Sprache Deutsch", () -> Shots.language(Minecraft.getInstance(), Shots.GERMAN))
                .then("Tab Welt-Regeln öffnen", (ctx, ticks) -> OsdSteps.openTab(ctx, ticks, RulesSteps.TAB))
                .then("zum Abschnitt des Add-ons", (ctx, ticks) -> {
                    RulesSteps.scrollToSection();
                    return ticks >= 10 ? StepResult.pass() : StepResult.pending();
                })
                .then("Bildschirmfoto Anfang", (ctx, ticks) -> strict ? StepResult.pass()
                        : Shots.shot(ctx, ticks, CovCamBKit.PHOTO + "world_rules_camdrones_de_320x240_01"))
                .then("zur letzten Regel", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    Screen screen = mc.gui.screen();
                    if (screen == null) {
                        return StepResult.fail("Tab geschlossen");
                    }
                    List<WorldRule<?>> rules = CamWorldRules.registered();
                    scrollTo(mc, screen, I18n.get(rules.getLast().nameKey()));
                    return ticks >= 5 ? StepResult.pass() : StepResult.pending();
                })
                .then("Bildschirmfoto Ende", (ctx, ticks) -> strict ? StepResult.pass()
                        : Shots.shot(ctx, ticks, CovCamBKit.PHOTO + "world_rules_camdrones_de_320x240_02"))
                .then("Namen und Werte vollständig", (ctx, ticks) -> {
                    StepResult result = rulesReadable(ctx, ticks);
                    if (!strict && result.failed()) {
                        notes(ctx).add("für die Bildmappe: " + result.message());
                        return StepResult.pass();
                    }
                    return result;
                })
                .run("Tab schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx))));
    }

    private static Optional<LayoutCheck.Text> scrollTo(Minecraft mc, Screen screen, String text) {
        for (int i = 0; i < 200; i++) {
            screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0.0, 1.0);
        }
        for (int i = 0; i < 200; i++) {
            LayoutCheck.Frame frame = LayoutCheck.capture(mc, screen);
            Optional<LayoutCheck.Text> shown = frame.texts().stream().filter(t -> t.visible() != null && (t.value().contains(text)
                    || t.value().endsWith("…") && t.value().length() > 4 && text.startsWith(t.value().substring(0, t.value().length() - 1)))).findFirst();
            if (shown.isPresent() && shown.get().visible().top() >= 0 && shown.get().visible().bottom() <= screen.height) {
                return shown;
            }
            screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0.0, -1.0);
        }
        return Optional.empty();
    }

    private static StepResult rulesReadable(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Tab geschlossen");
        }
        if (screen.width != 320 || screen.height != 240) {
            return StepResult.fail("GUI " + screen.width + "×" + screen.height + " statt 320×240");
        }
        if (ticks < 5) {
            return StepResult.pending();
        }
        List<String> wanted = new ArrayList<>();
        wanted.add(RulesSteps.sectionTitle());
        for (WorldRule<?> rule : CamWorldRules.registered()) {
            wanted.add(I18n.get(rule.nameKey()));
        }
        List<String> problems = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (String text : wanted) {
            Optional<LayoutCheck.Text> shown = scrollTo(mc, screen, text);
            if (shown.isEmpty()) {
                problems.add("\"" + text + "\" nicht gefunden");
                continue;
            }
            LayoutCheck.Text found = shown.get();
            if (!found.value().contains(text)) {
                problems.add("\"" + text + "\" gekürzt auf \"" + found.value() + "\"");
            } else if (found.visible().width() < found.full().width() - LayoutCheck.SIDE_SLACK || found.visible().right() > screen.width) {
                problems.add("\"" + text + "\" nur teilweise sichtbar");
            } else {
                seen.add(text);
            }
        }
        RulesSteps.scrollToSection();
        LayoutCheck.Frame frame = LayoutCheck.capture(mc, screen);
        List<String> audit = LayoutCheck.audit(LayoutCheck.Box.of(0, 0, screen.width, screen.height), frame).stream()
                .filter(finding -> wanted.stream().anyMatch(finding::contains)).toList();
        problems.addAll(audit);
        notes(ctx).add("GUI 320×240 auf Deutsch: Abschnitt \"" + RulesSteps.sectionTitle() + "\", " + seen.size() + " von " + wanted.size()
                + " Namen ganz lesbar");
        if (!problems.isEmpty()) {
            ctx.log("Texte: " + frame.values());
            return StepResult.fail(String.join(", ", problems));
        }
        return StepResult.pass();
    }
}
