package de.lardtom.camdrones.dev.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.model.CameraActions;
import de.lardtom.camdrones.client.planner.CamClientConfig;
import de.lardtom.camdrones.client.planner.CamSettingsTab;
import de.lardtom.camdrones.client.record.FlashbackLink;
import de.lardtom.camdrones.client.record.GimbalPathRecorder;
import de.lardtom.camdrones.client.record.RecordTexts;
import de.lardtom.camdrones.client.record.RecordingRouter;
import de.lardtom.camdrones.client.record.RecordingScene;
import de.lardtom.camdrones.client.record.model.RecordingMode;
import de.lardtom.camdrones.client.record.model.RecordingRoute;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.platform.Services;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.CameraChunkResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class RecModeSteps {
    public static final int SETTINGS_NUMBER = 801;
    public static final int BACKGROUND_NUMBER = 802;
    public static final int DIRECT_NUMBER = 803;
    public static final int HINT_NUMBER = 804;
    public static final int SCHOOL_NUMBER = 805;
    public static final String SETTINGS = "aufnahme: Aufnahmemodus im Tab Kamera-Drohnen, Flashback-Wert nur mit Flashback";
    public static final String BACKGROUND = "aufnahme: Flashback-Modus nimmt im Hintergrund auf, Drohne weit weg mit Kamera-Chunks";
    public static final String DIRECT = "aufnahme: Direkt-Modus nimmt in der Live-Ansicht ein Video auf";
    public static final String HINT = "aufnahme: Direkt-Modus ohne Live-Ansicht zeigt den Hinweis und nimmt nichts auf";
    public static final String SCHOOL = "aufnahme: Flugschul-Kapitel Aufnahmen";
    public static final int TABLET_SLOT = 8;
    public static final int MIN_FAR_BLOCKS = 300;
    public static final int MIN_SAMPLES = 40;
    private static final String FAR = "rec.far";
    private static final String FAR_SAMPLES = "rec.farSamples";
    private static final String BEFORE = "rec.before";
    private static final String HINTS = "rec.hints";
    private static final String PHOTOS = "rec.photos";
    private static final String RECORDINGS = "rec.recordings";
    private static final String HANDOVER = "rec.handover";
    private static final String SAVED = "rec.saved";
    private static final String CHUNKS = "rec.chunks";
    private static final String CLEANED = "rec.cleaned";
    private static final String FLIGHT_CHECK = "rec.flightCheck";
    private static final String CLEANED_AT = "rec.cleanedAt";
    private static final int STEP_TICKS = 20 * 300;

    private RecModeSteps() {
    }

    public static void register() {
        CamClientConfig.get().recordingMode(RecordingMode.DIRECT);
        SelfTest.clientStep(SETTINGS_NUMBER, SETTINGS, 20 * 60, Script.create()
                .run("ohne eigene Wahl", () -> CamClientConfig.get().resetRecordingMode())
                .then("Tab Kamera-Drohnen öffnen", (ctx, ticks) -> OsdSteps.openTab(ctx, ticks, CamSettingsTab.ID))
                .then("Standard und Flashback-Wert prüfen", RecModeSteps::defaults)
                .then("Bildschirmfoto", (ctx, ticks) -> Shots.shot(ctx, ticks, FlashbackLink.installed() ? "record_mode_flashback" : "record_mode_needs_flashback"))
                .then("Werte wählen und speichern", RecModeSteps::chooseAndSave), RecModeSteps::restore);
        if (FlashbackLink.installed()) {
            SelfTest.clientStep(BACKGROUND_NUMBER, BACKGROUND, STEP_TICKS, Script.create()
                    .server("Kino abstellen, koppeln, Flugplaner ins Inventar, Mission mit Aufnahme weit weg", RecModeSteps::farMission)
                    .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                            : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending())
                    .run("Flashback-Modus wählen", () -> CamClientConfig.get().recordingMode(RecordingMode.FLASHBACK))
                    .then("Zähler merken", (ctx, ticks) -> {
                        ctx.state().put(PHOTOS, GimbalPathRecorder.INSTANCE.photos());
                        ctx.state().put(RECORDINGS, GimbalPathRecorder.INSTANCE.recordings());
                        return StepResult.pass("Flashback " + FlashbackLink.version().orElse("?") + ", Status " + FlashbackLink.status());
                    })
                    .then("Mission ohne Pilot und ohne Live-Ansicht starten", (ctx, ticks) -> CamFlights.send(ctx,
                            new MissionCommand.Request(CamFlights.serial(ctx.state()), MissionCommand.Command.START, 0)))
                    .then("Aufnahme startet im Hintergrund", RecModeSteps::backgroundStarted)
                    .then("Drohne weit weg, Kamera-Chunks angemeldet, Pfad wächst", RecModeSteps::farAway)
                    .then("Foto und Ende der Aufnahme weit weg", RecModeSteps::handedOver)
                    .then("Lage nach der Mission freigegeben, Flashback beendet", RecModeSteps::released)
                    .then("Szenen in Flashbacks Editorzustand", RecModeSteps::editorState)
                    .then("Flug weit weg gelandet", RecModeSteps::landed), RecModeSteps::cleanupFar);
        } else {
            CamDrones.LOG.info("[Kamera-Selbsttest] {} nicht angemeldet: Flashback fehlt im mods-Ordner", BACKGROUND);
        }
        SelfTest.clientStep(DIRECT_NUMBER, DIRECT, 20 * 120, Script.create()
                .append(CamFlights.groundDrone(CamPresets.CINE, 70, -120, 0.0F))
                .server("Mission zum Schweben ohne Aufnahme", RecModeSteps::hoverMission)
                .run("Direkt-Modus wählen", () -> CamClientConfig.get().recordingMode(RecordingMode.DIRECT))
                .then("Mission ohne Pilot starten", (ctx, ticks) -> CamFlights.send(ctx,
                        new MissionCommand.Request(CamFlights.serial(ctx.state()), MissionCommand.Command.START, 0)))
                .then("Flug ohne Pilot am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("Livebild öffnen", RecModeSteps::watch)
                .then("Aufnahme im Livebild starten", RecModeSteps::directStart)
                .sleep(60)
                .then("Aufnahme beenden", (ctx, ticks) -> {
                    CamFlights.clientDrone(ctx.state()).ifPresent(drone -> GimbalClient.camera(drone).stop());
                    return StepResult.pass();
                })
                .then("Video im Aufnahme-Ordner", RecModeSteps::videoSaved), RecModeSteps::cleanupView);
        SelfTest.clientStep(HINT_NUMBER, HINT, 20 * 120, Script.create()
                .server("Kino abstellen, koppeln, Flugplaner ins Inventar, Mission mit Aufnahme", RecModeSteps::nearMission)
                .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending())
                .run("Direkt-Modus wählen", () -> CamClientConfig.get().recordingMode(RecordingMode.DIRECT))
                .then("Zähler merken", (ctx, ticks) -> {
                    ctx.state().put(HINTS, RecordingRouter.hints());
                    ctx.state().put(BEFORE, files());
                    return StepResult.pass();
                })
                .then("Mission ohne Live-Ansicht starten", (ctx, ticks) -> CamFlights.send(ctx,
                        new MissionCommand.Request(CamFlights.serial(ctx.state()), MissionCommand.Command.START, 0)))
                .then("Hinweis kommt, keine Aufnahme", RecModeSteps::hinted), RecModeSteps::cleanupHint);
        SelfTest.clientStep(SCHOOL_NUMBER, SCHOOL, 20 * 30, Script.create()
                .then("Kapitel öffnen", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (ticks == 0) {
                        return PropwashClientApi.openFlightSchoolScene(RecordingScene.ID, null) ? StepResult.pending()
                                : StepResult.fail("Kapitel " + RecordingScene.ID + " nicht angemeldet");
                    }
                    return mc.gui.screen() != null && ticks > 20 ? StepResult.pass("Kapitel „" + I18n.get("camdrones.guide.scene.recording.title")
                            + "“ offen (" + SelfTestRunner.screenName(mc) + ")") : ticks > 100 ? StepResult.fail("Kapitel öffnet nicht") : StepResult.pending();
                })
                .then("Bildschirmfoto", (ctx, ticks) -> Shots.shot(ctx, ticks, "school_recording"))
                .run("schließen", () -> Minecraft.getInstance().gui.setScreen(null)));
    }

    private static StepResult defaults(SelfTest.StepContext ctx, int ticks) {
        CamSettingsTab tab = CamSettingsTab.active();
        if (tab == null || tab.flashbackButton() == null || tab.directButton() == null) {
            return ticks > 60 ? StepResult.fail("Tab ohne Aufnahmemodus") : StepResult.pending();
        }
        boolean installed = FlashbackLink.installed();
        RecordingMode wanted = RecordingMode.defaultFor(installed);
        List<String> problems = new ArrayList<>();
        if (tab.recordingMode() != wanted) {
            problems.add("Standard " + tab.recordingMode() + " statt " + wanted);
        }
        if (tab.flashbackButton().active != installed) {
            problems.add("Flashback-Wert " + (installed ? "ausgegraut, obwohl Flashback da ist" : "wählbar ohne Flashback"));
        }
        if (!tab.directButton().active) {
            problems.add("Direkte Aufnahme nicht wählbar");
        }
        Component hint = CamSettingsTab.modeHint(installed, FlashbackLink.status());
        if (!installed && (hint == null || !hint.getString().equals(I18n.get(RecordTexts.NEEDS_FLASHBACK)))) {
            problems.add("Hinweis ohne Flashback fehlt");
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        return StepResult.pass("Flashback " + (installed ? "installiert" : "fehlt") + ": Standard „" + I18n.get(wanted.translationKey())
                + "“, Flashback-Wert " + (installed ? "wählbar" : "ausgegraut mit Hinweis „" + hint.getString() + "“"));
    }

    private static StepResult chooseAndSave(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        CamSettingsTab tab = CamSettingsTab.active();
        boolean installed = FlashbackLink.installed();
        RecordingMode wanted = installed ? RecordingMode.FLASHBACK : RecordingMode.DIRECT;
        if (ticks == 0) {
            if (screen == null || tab == null || tab.flashbackButton() == null || tab.directButton() == null) {
                return StepResult.fail("Einstellungen geschlossen");
            }
            CovCamAKit.click(screen, tab.directButton());
            if (tab.recordingMode() != RecordingMode.DIRECT) {
                return StepResult.fail("Klick auf „Direkte Aufnahme“ wählt " + tab.recordingMode());
            }
            CovCamAKit.click(screen, tab.flashbackButton());
            if (tab.recordingMode() != wanted) {
                return StepResult.fail("Klick auf „Flashback-Aufnahme“ wählt " + tab.recordingMode() + " statt " + wanted);
            }
            screen.keyPressed(new KeyEvent(InputConstants.KEY_S, 0, InputConstants.MOD_CONTROL));
            return StepResult.pending();
        }
        Optional<RecordingMode> saved = CamClientConfig.get().chosenRecordingMode();
        if (mc.gui.screen() == null && saved.isPresent()) {
            return saved.get() == wanted ? StepResult.pass("gespeichert: " + saved.get().key() + (installed ? "" : " (Flashback-Klick ohne Wirkung)"))
                    : StepResult.fail("gespeichert " + saved.get() + " statt " + wanted);
        }
        return ticks > 60 ? StepResult.fail("Strg+S speichert den Aufnahmemodus nicht (" + saved + ")") : StepResult.pending();
    }

    private static StepResult restore(SelfTest.StepContext ctx) {
        Minecraft.getInstance().gui.setScreen(null);
        CamClientConfig.get().recordingMode(RecordingMode.DIRECT);
        return StepResult.pass();
    }

    private static int farBlocks() {
        int chunks = Minecraft.getInstance().options.getEffectiveRenderDistance();
        return Math.max(MIN_FAR_BLOCKS, (chunks + 4) * 16);
    }

    private static void giveTablet(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Optional<net.minecraft.world.item.Item> item = PlannerItems.flightPlanner();
        if (item.isEmpty()) {
            throw new IllegalStateException("Flugplaner fehlt");
        }
        ItemStack tablet = new ItemStack(item.get());
        if (!PlannerTablets.pairDrone(player, tablet, Pilot.serverDrone(server, state))) {
            throw new IllegalStateException("Flugplaner lässt sich nicht koppeln");
        }
        player.getInventory().setItem(TABLET_SLOT, tablet);
    }

    private static void farMission(MinecraftServer server, Map<String, Object> state) {
        Pilot.spawn(server, state, CamPresets.CINE, Arena.ground(-30, 40), 0.0F);
        Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        CamFlights.pair(server, state);
        giveTablet(server, state);
        Entity drone = Pilot.serverDrone(server, state);
        ServerPlayer player = Arena.player(server);
        double y = drone.getY() + 12.0;
        int far = (int) state.computeIfAbsent(FAR, key -> farBlocks());
        double dirX = drone.getX() - player.getX();
        double dirZ = drone.getZ() - player.getZ();
        double length = Math.max(1.0, Math.sqrt(dirX * dirX + dirZ * dirZ));
        dirX /= length;
        dirZ /= length;
        double[] along = {4.0, 12.0, far, far + 12.0, far + 20.0};
        Waypoint.Action[] actions = {Waypoint.Action.NONE, Waypoint.Action.RECORD_START, Waypoint.Action.PHOTO, Waypoint.Action.RECORD_STOP,
                Waypoint.Action.NONE};
        List<Waypoint> waypoints = new ArrayList<>();
        for (int i = 0; i < along.length; i++) {
            waypoints.add(Waypoint.at(drone.getX() + dirX * along[i], y, drone.getZ() + dirZ * along[i]).withAction(actions[i]));
        }
        CamFlights.mission(server, state, Mission.of(waypoints).withEnd(Mission.End.LAND).withBaseSpeed(15.0));
    }

    private static void hoverMission(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        double y = drone.getY() + 6.0;
        CamFlights.mission(server, state, Mission.of(List.of(Waypoint.at(drone.getX(), y, drone.getZ() + 2.0),
                Waypoint.at(drone.getX(), y, drone.getZ() + 6.0))).withEnd(Mission.End.HOVER).withBaseSpeed(3.0));
    }

    private static void nearMission(MinecraftServer server, Map<String, Object> state) {
        Pilot.spawn(server, state, CamPresets.CINE, Arena.ground(40, -60), 0.0F);
        Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        CamFlights.pair(server, state);
        giveTablet(server, state);
        Entity drone = Pilot.serverDrone(server, state);
        double y = drone.getY() + 6.0;
        CamFlights.mission(server, state, Mission.of(List.of(Waypoint.at(drone.getX(), y, drone.getZ() + 4.0),
                Waypoint.at(drone.getX(), y, drone.getZ() + 10.0).withAction(Waypoint.Action.RECORD_START),
                Waypoint.at(drone.getX(), y, drone.getZ() + 16.0).withAction(Waypoint.Action.PHOTO),
                Waypoint.at(drone.getX(), y, drone.getZ() + 22.0).withAction(Waypoint.Action.RECORD_STOP))).withEnd(Mission.End.HOVER).withBaseSpeed(4.0));
    }

    private static StepResult backgroundStarted(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        Minecraft mc = Minecraft.getInstance();
        if (RecordingRouter.liveView(mc, drone.get())) {
            return StepResult.fail("Spieler ist in der Live-Ansicht der Drohne");
        }
        GimbalPathRecorder recorder = GimbalPathRecorder.INSTANCE;
        if (recorder.recording(drone.get().getId()) && FlashbackLink.recording()) {
            return StepResult.pass("Wegpunkt-Aufnahme läuft als Flashback-Kamerapfad, Flashback nimmt auf (" + (recorder.startedFlashback()
                    ? "vom Add-on gestartet" : "lief schon") + "), Route ohne Live-Ansicht " + RecordingRouter.route(false));
        }
        return ticks > 20 * 60 ? StepResult.fail("keine Aufnahme im Hintergrund (Ergebnis " + recorder.lastResult() + ", Flashback "
                + FlashbackLink.status() + ", nimmt auf " + FlashbackLink.recording() + ", Problem " + FlashbackLink.problem() + ")") : StepResult.pending();
    }

    private static StepResult farAway(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        Minecraft mc = Minecraft.getInstance();
        GimbalPathRecorder recorder = GimbalPathRecorder.INSTANCE;
        if (drone.isEmpty() || mc.player == null) {
            return ticks > 20 * 90 ? StepResult.fail("Drohne nicht mehr am Client") : StepResult.pending();
        }
        int id = drone.get().getId();
        double distance = Math.sqrt(Math.pow(drone.get().getX() - mc.player.getX(), 2) + Math.pow(drone.get().getZ() - mc.player.getZ(), 2));
        int view = mc.options.getEffectiveRenderDistance() * 16;
        Optional<CameraChunkResult.Status> answer = recorder.chunks().answer(id);
        int radius = recorder.chunks().radius(id);
        boolean accepted = answer.filter(a -> a == CameraChunkResult.Status.OPENED || a == CameraChunkResult.Status.UPDATED).isPresent();
        if (!recorder.recording(id)) {
            return StepResult.fail("Aufnahme endet vor dem Ziel (" + Flight.format(distance) + " Blöcke)");
        }
        if (distance > view + 16 && recorder.chunks().active().contains(id) && accepted && radius > 0) {
            int samples = recorder.samples(id);
            Integer first = (Integer) ctx.state().get(FAR_SAMPLES);
            if (first == null) {
                ctx.state().put(FAR_SAMPLES, samples);
                ctx.state().put(CHUNKS, "Antwort " + answer.get() + ", Radius " + radius + " Chunks bei " + Flight.format(distance) + " Blöcken (Sichtweite "
                        + view + ")");
                return StepResult.pending();
            }
            if (samples >= first + 20) {
                return StepResult.pass("Drohne " + Flight.format(distance) + " Blöcke weg (Sichtweite " + view + "), Kamera-Chunks: " + ctx.state().get(CHUNKS)
                        + ", Pfad wächst weit weg von " + first + " auf " + samples + " Proben");
            }
            return ticks > 20 * 120 ? StepResult.fail("Pfad wächst weit weg nicht: " + first + " → " + samples) : StepResult.pending();
        }
        return ticks > 20 * 120 ? StepResult.fail("Drohne " + Flight.format(distance) + " Blöcke weg, Sichtweite " + view + ", angemeldet "
                + recorder.chunks().active() + ", Antwort " + answer.map(Enum::name).orElse("-") + ", Radius " + radius) : StepResult.pending();
    }

    private static StepResult handedOver(SelfTest.StepContext ctx, int ticks) {
        GimbalPathRecorder recorder = GimbalPathRecorder.INSTANCE;
        int photos = recorder.photos() - (int) ctx.state().get(PHOTOS);
        Optional<GimbalPathRecorder.Handover> handover = recorder.lastHandover().filter(h -> !h.photo());
        boolean stopped = recorder.recordings() > (int) ctx.state().get(RECORDINGS) && !recorder.recordingAny();
        if (photos >= 1 && stopped && handover.isPresent()) {
            GimbalPathRecorder.Handover h = handover.get();
            ctx.state().put(HANDOVER, h);
            if (h.sceneIndex() < 0 || h.samples() < MIN_SAMPLES || h.keyframes() < 2 || !h.far()) {
                return StepResult.fail("Übergabe unvollständig: " + h);
            }
            return StepResult.pass("Szene „" + h.scene() + "“ (Index " + h.sceneIndex() + "): " + h.samples() + " Proben, " + h.keyframes()
                    + " Kamera-Keyframes, " + h.fovKeyframes() + " Sichtfeld-Keyframes, Ticks " + h.startTick() + "–" + h.endTick() + ", weit weg aufgenommen; "
                    + photos + " Foto");
        }
        return ticks > 20 * 90 ? StepResult.fail("Foto " + photos + ", beendet " + stopped + ", Übergabe " + recorder.lastHandover().orElse(null))
                : StepResult.pending();
    }

    private static StepResult released(SelfTest.StepContext ctx, int ticks) {
        GimbalPathRecorder recorder = GimbalPathRecorder.INSTANCE;
        pressSave(ctx);
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        boolean done = drone.isEmpty() || CamFlights.clientMissionStatus(ctx.state()).map(s -> s.state() == NavLogic.MissionState.DONE).orElse(true);
        if (done && recorder.chunks().active().isEmpty() && !FlashbackLink.recording()) {
            return StepResult.pass("keine Kamera-Chunks mehr angemeldet, Flashback-Aufnahme beendet" + (ctx.state().containsKey(SAVED) ? " und gespeichert" : ""));
        }
        return ticks > 20 * 90 ? StepResult.fail("Mission fertig " + done + ", angemeldet " + recorder.chunks().active() + ", Flashback nimmt auf "
                + FlashbackLink.recording()) : StepResult.pending();
    }

    private static void pressSave(SelfTest.StepContext ctx) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen != null && screen.getClass().getSimpleName().equals("SaveReplayScreen") && !ctx.state().containsKey(SAVED)) {
            if (SelfTestRunner.pressButton(screen, I18n.get("flashback.save_replay"))) {
                ctx.state().put(SAVED, true);
            }
        }
    }

    private static StepResult editorState(SelfTest.StepContext ctx, int ticks) {
        pressSave(ctx);
        GimbalPathRecorder.Handover h = (GimbalPathRecorder.Handover) ctx.state().get(HANDOVER);
        Path file = Services.PLATFORM.gameDir().resolve(FlashbackLink.MOD_ID).resolve("editor_states").resolve(h.replay() + ".json");
        if (!Files.isRegularFile(file)) {
            return ticks > 20 * 90 ? StepResult.fail("kein Editorzustand " + file) : StepResult.pending();
        }
        int cameraKeys = -1;
        int scenes = 0;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement scene : root.getAsJsonArray("scenes")) {
                scenes++;
                JsonObject s = scene.getAsJsonObject();
                if (!s.has("name") || !h.scene().equals(s.get("name").getAsString())) {
                    continue;
                }
                for (JsonElement track : s.getAsJsonArray("keyframeTracks")) {
                    JsonObject t = track.getAsJsonObject();
                    if ("CAMERA".equals(t.get("keyframeType").getAsString())) {
                        cameraKeys = t.getAsJsonObject("keyframesByTick").size();
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            return ticks > 20 * 90 ? StepResult.fail("Editorzustand nicht lesbar: " + e) : StepResult.pending();
        }
        if (cameraKeys != h.keyframes() || scenes < 2) {
            return ticks > 20 * 90 ? StepResult.fail(file.getFileName() + ": " + scenes + " Szenen, Kamera-Keyframes der Szene „" + h.scene() + "“ " + cameraKeys
                    + " statt " + h.keyframes()) : StepResult.pending();
        }
        return StepResult.pass(file.getFileName() + ": " + scenes + " Szenen, „" + h.scene() + "“ mit " + cameraKeys + " Kamera-Keyframes");
    }

    private static StepResult landed(SelfTest.StepContext ctx, int ticks) {
        Object pending = ctx.state().get(FLIGHT_CHECK);
        if (pending instanceof CompletableFuture<?> future) {
            if (!future.isDone()) {
                return ticks > 20 * 120 ? StepResult.fail("Server antwortet nicht") : StepResult.pending();
            }
            ctx.state().remove(FLIGHT_CHECK);
            if (Boolean.FALSE.equals(future.getNow(null))) {
                return StepResult.pass("Flug ohne Pilot weit weg beendet, Drohne gelandet");
            }
        }
        if (ticks % 10 == 0) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            UUID id = (UUID) ctx.state().get(Pilot.DRONE);
            ctx.onServer(server -> {
                Entity drone = Arena.level(server).getEntity(id);
                future.complete(drone != null && PropwashApi.drones().autonomy().active(drone).isPresent());
            });
            ctx.state().put(FLIGHT_CHECK, future);
        }
        return ticks > 20 * 120 ? StepResult.fail("Flug ohne Pilot weit weg läuft noch") : StepResult.pending();
    }

    private static StepResult cleanupFar(SelfTest.StepContext ctx) {
        pressSave(ctx);
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen != null && screen.getClass().getSimpleName().equals("SaveReplayScreen") && ctx.tick() < 200) {
            return StepResult.pending();
        }
        if (!ctx.state().containsKey(CLEANED)) {
            AtomicBoolean done = new AtomicBoolean();
            ctx.state().put(CLEANED, done);
            GimbalPathRecorder.INSTANCE.stopAll(mc);
            CamClientConfig.get().recordingMode(RecordingMode.DIRECT);
            MinecraftServer server = mc.getSingleplayerServer();
            if (server == null) {
                done.set(true);
            } else {
                server.execute(() -> {
                    try {
                        CamFlights.stopFlights(server);
                        Arena.player(server).getInventory().setItem(TABLET_SLOT, ItemStack.EMPTY);
                        Entity drone = Arena.level(server).getEntity((UUID) ctx.state().get(Pilot.DRONE));
                        if (drone != null) {
                            if (PropwashApi.drones().autonomy().active(drone).isPresent()) {
                                PropwashApi.drones().autonomy().stop(drone);
                            }
                            drone.discard();
                        }
                    } finally {
                        done.set(true);
                    }
                });
            }
            ctx.state().put(CLEANED_AT, ctx.tick());
            return StepResult.pending();
        }
        if (FlashbackLink.recording() && ctx.tick() < 200) {
            if (ctx.tick() % 40 == 20) {
                FlashbackLink.finishRecording();
            }
            return StepResult.pending();
        }
        AtomicBoolean done = (AtomicBoolean) ctx.state().get(CLEANED);
        if (!done.get() || ctx.tick() - (int) ctx.state().get(CLEANED_AT) < 40) {
            return ctx.tick() < 300 ? StepResult.pending() : StepResult.pass("Aufräumen am Server ohne Antwort");
        }
        return StepResult.pass();
    }

    private static StepResult watch(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() == drone.get() && !PropwashClientApi.input().flying()) {
            return StepResult.pass("Livebild zeigt die Kino");
        }
        if (ticks % 20 == 0) {
            PropwashClientApi.spectate(drone.get());
        }
        return ticks > 300 ? StepResult.fail("Livebild zeigt nicht die Drohne") : StepResult.pending();
    }

    private static StepResult directStart(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        if (RecordingRouter.route(true) != RecordingRoute.DIRECT) {
            return StepResult.fail("Route in der Live-Ansicht " + RecordingRouter.route(true));
        }
        ctx.state().put(BEFORE, files());
        CameraActions.Camera camera = GimbalClient.camera(drone.get());
        if (!camera.start()) {
            return ticks > 100 ? StepResult.fail("Aufnahme startet im Livebild nicht") : StepResult.pending();
        }
        if (GimbalPathRecorder.INSTANCE.recordingAny()) {
            return StepResult.fail("Direkt-Modus schreibt einen Flashback-Pfad");
        }
        return PropwashClientApi.recording().recording() ? StepResult.pass("Propwash nimmt das Gimbal-Bild direkt auf")
                : StepResult.fail("Aufnahme gemeldet, aber Propwash nimmt nicht auf");
    }

    @SuppressWarnings("unchecked")
    private static StepResult videoSaved(SelfTest.StepContext ctx, int ticks) {
        List<Path> videos = created((Set<String>) ctx.state().get(BEFORE), "propwash_", ".mp4");
        if (!videos.isEmpty() && !PropwashClientApi.recording().recording()) {
            try {
                long size = Files.size(videos.getFirst());
                if (size > 0) {
                    return StepResult.pass(videos.getFirst().getFileName() + " (" + size + " Bytes) unter " + recordings());
                }
            } catch (IOException ignored) {
            }
        }
        return ticks > 20 * 60 ? StepResult.fail("kein neues Video unter " + recordings()) : StepResult.pending();
    }

    private static StepResult cleanupView(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (PropwashClientApi.recording().recording()) {
            PropwashClientApi.recording().stop();
        }
        mc.setCameraEntity(mc.player);
        Optional.ofNullable(mc.getSingleplayerServer()).ifPresent(s -> s.execute(() -> CamFlights.stopFlights(s)));
        return StepResult.pass();
    }

    @SuppressWarnings("unchecked")
    private static StepResult hinted(SelfTest.StepContext ctx, int ticks) {
        int hints = RecordingRouter.hints() - (int) ctx.state().get(HINTS);
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        boolean done = CamFlights.clientMissionStatus(ctx.state()).map(s -> s.state() == NavLogic.MissionState.DONE).orElse(false);
        if (PropwashClientApi.recording().recording() || GimbalPathRecorder.INSTANCE.recordingAny()) {
            return StepResult.fail("ohne Live-Ansicht läuft trotzdem eine Aufnahme");
        }
        if (drone.isPresent() && RecordingRouter.liveView(Minecraft.getInstance(), drone.get())) {
            return StepResult.fail("Spieler ist in der Live-Ansicht");
        }
        if (hints >= 1 && done) {
            List<Path> made = created((Set<String>) ctx.state().get(BEFORE), "propwash_", "");
            if (!made.isEmpty()) {
                return StepResult.fail("ohne Live-Ansicht entstanden " + made);
            }
            return StepResult.pass(hints + " Hinweis „" + I18n.get(RecordTexts.NEEDS_LIVE_VIEW) + "“, Mission fertig, keine Datei unter " + recordings());
        }
        return ticks > 20 * 90 ? StepResult.fail("Hinweise " + hints + ", Mission fertig " + done) : StepResult.pending();
    }

    private static StepResult cleanupHint(SelfTest.StepContext ctx) {
        Optional.ofNullable(Minecraft.getInstance().getSingleplayerServer()).ifPresent(s -> s.execute(() -> {
            CamFlights.stopFlights(s);
            Arena.player(s).getInventory().setItem(TABLET_SLOT, ItemStack.EMPTY);
        }));
        return StepResult.pass();
    }

    private static Path recordings() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("propwash").resolve("recordings");
    }

    private static Set<String> files() {
        Set<String> out = new HashSet<>();
        Path dir = recordings();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(p -> out.add(p.getFileName().toString()));
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    private static List<Path> created(Set<String> before, String prefix, String suffix) {
        List<Path> out = new ArrayList<>();
        Path dir = recordings();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> {
                String name = p.getFileName().toString();
                return !before.contains(name) && name.startsWith(prefix) && name.endsWith(suffix) && !name.startsWith(".");
            }).forEach(out::add);
        } catch (IOException e) {
            return out;
        }
        return out;
    }
}
