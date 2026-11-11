package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.planner.CamClientConfig;
import de.lardtom.camdrones.client.planner.CamSettingsTab;
import de.lardtom.camdrones.client.planner.MissionService;
import de.lardtom.camdrones.client.planner.MissionServiceScreen;
import de.lardtom.camdrones.client.planner.PlannerClient;
import de.lardtom.camdrones.client.planner.PlannerScreen;
import de.lardtom.camdrones.client.planner.WaypointPreview;
import de.lardtom.camdrones.crew.CrewTexts;
import de.lardtom.camdrones.crew.client.CrewScreen;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.ExitSettings;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerLink;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.model.EditorLayout;
import de.lardtom.camdrones.planner.model.EditorState;
import de.lardtom.camdrones.planner.model.ExitSetting;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.PlannerEntry;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.camdrones.planner.model.TabletState;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.ui.client.theme.ThemedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CovCamAPlannerSteps {
    public static final String PAIR = "planer: koppeln";
    public static final String SERVICE = "wartung: Knöpfe Mission und Crew";
    public static final String PREVIEW = "planer: Vorschau-Marker abschaltbar";
    public static final String LIVE = "planer: Livebild";
    public static final String EXIT = "planer: Verhalten beim Verlassen einstellen";
    public static final String CLIENT_CONFIG = "camdrones-client.json";
    private static final String NOTE = "covcama.note";
    private static final String SHOT = "covcama.shot";
    private static final String STAGE = "covcama.stage";
    private static final String EXIT_SEEN = "covcama.exit";
    private static final int STEP_TICKS = 20 * 120;

    private CovCamAPlannerSteps() {
    }

    static void pair() {
        SelfTest.clientStep(340, PAIR, STEP_TICKS, pairing(true, -20));
    }

    static void pairPlain() {
        SelfTest.clientStep(344, PAIR + " per Rechtsklick ohne Schleichen", STEP_TICKS, pairing(false, -32));
    }

    private static Script pairing(boolean sneak, int dx) {
        return Script.create()
                .server("Tour abstellen, Flugplaner in die Hand", (server, state) -> {
                    standNear(server, state, dx, -100);
                    Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PlannerItems.flightPlanner().orElseThrow()));
                })
                .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() && held().isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne oder Flugplaner fehlt am Client") : StepResult.pending())
                .run("Meldung leeren", CovCamAKit::clearOverlay)
                .then((sneak ? "Schleichen + " : "") + "Rechtsklick mit dem Flugplaner auf die Drohne", (ctx, ticks) -> CovCamAKit.use(ctx, ticks,
                        drone(ctx), sneak))
                .then("Kopplung", (ctx, ticks) -> paired(ctx, ticks, sneak))
                .run("Fenster schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .server("leere Hand", (server, state) -> Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY))
                .sleep(10)
                .then("Schleichen + Rechtsklick mit leerer Hand", (ctx, ticks) -> CovCamAKit.use(ctx, ticks, drone(ctx), true))
                .then("Wartungsmenü", CovCamAPlannerSteps::serviceOpen)
                .run("Wartung schließen", () -> Minecraft.getInstance().gui.setScreen(null));
    }

    static void service() {
        SelfTest.clientStep(341, SERVICE, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .server("Tour abstellen", (server, state) -> standNear(server, state, -8, -100))
                .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne fehlt am Client") : StepResult.pending())
                .then("Schleichen + Rechtsklick", (ctx, ticks) -> CovCamAKit.use(ctx, ticks, drone(ctx), true))
                .then("Wartungsmenü", CovCamAPlannerSteps::serviceOpen)
                .append(Shots.photos(CovCamAKit.PHOTO + "service_buttons"))
                .then("Knöpfe Mission und Crew", CovCamAPlannerSteps::serviceButtons)
                .run("Fenster schließen", () -> Minecraft.getInstance().gui.setScreen(null)));
    }

    static void preview() {
        SelfTest.clientStep(342, PREVIEW, STEP_TICKS, Script.create()
                .server("Flugplaner mit Mission", (server, state) -> givePlanner(server))
                .await("Flugplaner am Client", 100, () -> held().isPresent())
                .sleep(20)
                .then("Marker in der Welt bei eingeschalteter Vorschau", (ctx, ticks) -> {
                    int drawn = WaypointPreview.lastDrawn();
                    return drawn > 0 ? StepResult.pass(drawn + " Marker in der Welt")
                            : ticks > 40 ? StepResult.fail("keine Marker in der Welt bei eingeschalteter Vorschau") : StepResult.pending();
                })
                .then("Einstellungen des Add-ons öffnen", (ctx, ticks) -> {
                    if (ticks == 0 && !PropwashClientApi.openConfigTab(CamSettingsTab.ID, null)) {
                        return StepResult.fail("kein Einstellungs-Tab " + CamSettingsTab.ID);
                    }
                    CamSettingsTab tab = CamSettingsTab.active();
                    Screen screen = Minecraft.getInstance().gui.screen();
                    if (tab != null && tab.previewButton() != null && screen != null && tab.previewButton().visible) {
                        return StepResult.pass("Tab \"" + tab.title().getString() + "\" offen, Vorschau-Marker " + tab.previewButton().getMessage().getString());
                    }
                    return ticks > 60 ? StepResult.fail("Tab oder Schalter für die Vorschau-Marker nicht sichtbar (" + SelfTestRunner.screenName(Minecraft.getInstance()) + ")")
                            : StepResult.pending();
                })
                .append(Shots.photos(CovCamAKit.PHOTO + "settings_preview"))
                .then("Vorschau-Marker ausschalten und speichern (Strg+S)", (ctx, ticks) -> {
                    Screen screen = Minecraft.getInstance().gui.screen();
                    CamSettingsTab tab = CamSettingsTab.active();
                    if (ticks == 0) {
                        if (screen == null || tab == null || tab.previewButton() == null) {
                            return StepResult.fail("Einstellungen geschlossen");
                        }
                        CovCamAKit.click(screen, tab.previewButton());
                        if (tab.previewMarkers()) {
                            return StepResult.fail("Schalter bleibt an");
                        }
                        screen.keyPressed(new KeyEvent(InputConstants.KEY_S, 0, InputConstants.MOD_CONTROL));
                        return StepResult.pending();
                    }
                    Path config = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(CLIENT_CONFIG);
                    boolean closed = Minecraft.getInstance().gui.screen() == null;
                    if (closed && !CamClientConfig.get().previewMarkers()) {
                        return StepResult.pass("gespeichert: " + CamClientConfig.get().file().getFileName() + " (" + (Files.exists(config) ? "Datei da" : "Datei fehlt")
                                + "), preview_markers=false");
                    }
                    return ticks > 40 ? StepResult.fail("nicht gespeichert (Fenster " + SelfTestRunner.screenName(Minecraft.getInstance()) + ", preview_markers="
                            + CamClientConfig.get().previewMarkers() + ")") : StepResult.pending();
                })
                .sleep(10)
                .then("keine Marker in der Welt, Editor mit allen Punkten", (ctx, ticks) -> {
                    int drawn = WaypointPreview.lastDrawn();
                    int points = Minecraft.getInstance().player == null ? 0
                            : PlannerClient.tabletState(Minecraft.getInstance().player, held().orElseThrow()).activeMission().size();
                    if (drawn == 0 && points > 0 && ticks >= 10) {
                        return StepResult.pass("0 Marker in der Welt, " + points + " Punkte im Tablet");
                    }
                    return drawn > 0 ? StepResult.fail(drawn + " Marker in der Welt bei ausgeschalteter Vorschau") : StepResult.pending();
                })
                .run("Editor öffnen", () -> Minecraft.getInstance().gameMode.useItem(Minecraft.getInstance().player, InteractionHand.MAIN_HAND))
                .await("Editor offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof PlannerScreen)
                .then("Editor zeigt die Punkte", (ctx, ticks) -> {
                    PlannerScreen screen = (PlannerScreen) Minecraft.getInstance().gui.screen();
                    int rows = screen.state().rowCount();
                    return rows > 0 ? StepResult.pass(rows + " Zeilen in der Liste des Editors") : StepResult.fail("Editor ohne Punkte");
                })
                .run("Editor schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .run("Vorschau-Marker wieder an", () -> CamClientConfig.get().previewMarkers(true))
                .sleep(10)
                .then("Marker wieder da", (ctx, ticks) -> WaypointPreview.lastDrawn() > 0 ? StepResult.pass(WaypointPreview.lastDrawn() + " Marker")
                        : ticks > 40 ? StepResult.fail("Marker kommen nicht zurück") : StepResult.pending()),
                ctx -> {
                    CamClientConfig.get().previewMarkers(true);
                    return StepResult.pass();
                });
    }

    static void live() {
        SelfTest.clientStep(343, LIVE, STEP_TICKS, Script.create()
                .server("gekoppelte Tour, Brille auf, Flugplaner in die Hand", (server, state) -> {
                    standNear(server, state, 8, -100);
                    CamFlights.pair(server, state);
                    linkedPlanner(server, state);
                    Arena.player(server).setItemSlot(EquipmentSlot.HEAD, Arena.stack(Pilot.GOGGLES));
                })
                .await("Flugplaner am Client", 100, () -> held().flatMap(PlannerTablets::link).isPresent())
                .then("Seriennummer der Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state())
                        .filter(drone -> CamFlights.serial(ctx.state()).equals(PlannerTablets.serialOf(drone))).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Seriennummer fehlt am Client") : StepResult.pending())
                .run("Editor öffnen", () -> Minecraft.getInstance().gameMode.useItem(Minecraft.getInstance().player, InteractionHand.MAIN_HAND))
                .await("Editor offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof PlannerScreen)
                .then("Knopf Livebild im Planer", (ctx, ticks) -> {
                    Screen screen = Minecraft.getInstance().gui.screen();
                    Optional<AbstractWidget> live = screen == null ? Optional.empty() : CovCamAKit.widget(screen, I18n.get(FieldText.PREFIX + "live"));
                    if (live.isPresent() && live.get().active) {
                        ctx.state().put(NOTE, live.get());
                        return StepResult.pass("Knopf \"" + live.get().getMessage().getString() + "\" im Kopf, Knöpfe " + CovCamAKit.labels(screen));
                    }
                    return ticks > 20 ? StepResult.fail("kein Knopf für das Livebild im Planer, Knöpfe " + CovCamAKit.labels(screen)) : StepResult.pending();
                })
                .append(Shots.photos(CovCamAKit.PHOTO + "live_button"))
                .then("abgestellte Drohne: Meldung statt schwarzem Bild", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (ticks == 0) {
                        CovCamAKit.click(mc.gui.screen(), (AbstractWidget) ctx.state().get(NOTE));
                        return StepResult.pending();
                    }
                    if (!(mc.gui.screen() instanceof PlannerScreen screen)) {
                        return StepResult.fail("Editor geschlossen, Kamera " + mc.getCameraEntity());
                    }
                    Optional<String> key = screen.state().status().filter(EditorState.Status::error).map(status -> status.text().key());
                    if (key.filter((FieldText.PREFIX + "live.unavailable")::equals).isPresent() && mc.getCameraEntity() == mc.player) {
                        return StepResult.pass("Meldung \"" + I18n.get(key.get()) + "\"");
                    }
                    return ticks > 40 ? StepResult.fail("keine Meldung live.unavailable (Status " + key.orElse("-") + ")") : StepResult.pending();
                })
                .then("Halten am Server starten", (ctx, ticks) -> CamFlights.send(ctx,
                        MissionCommand.Request.of(CamFlights.serial(ctx.state()), MissionCommand.Command.HOLD)))
                .then("Flug ohne Pilot am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "hold"))
                .then("Drohne schwebt", (ctx, ticks) -> {
                    Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
                    double height = drone.map(d -> d.getY() - CamFlights.surface()).orElse(0.0);
                    if (height > 2.0 && ticks >= 60) {
                        return StepResult.pass(String.format(Locale.ROOT, "Drohne %.1f m über dem Boden", height));
                    }
                    return ticks > 400 ? StepResult.fail(String.format(Locale.ROOT, "Drohne steigt nicht (%.1f m über dem Boden)", height))
                            : StepResult.pending();
                })
                .then("Livebild öffnen", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (ticks == 0) {
                        if (!(mc.gui.screen() instanceof PlannerScreen screen)) {
                            return StepResult.fail("Editor geschlossen");
                        }
                        CovCamAKit.click(screen, (AbstractWidget) ctx.state().get(NOTE));
                        return StepResult.pending();
                    }
                    Entity camera = mc.getCameraEntity();
                    Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
                    if (drone.isPresent() && camera == drone.get() && mc.gui.screen() == null) {
                        return StepResult.pass("Kamera-Entity ist die Drohne, Editor geschlossen");
                    }
                    return ticks > 200 ? StepResult.fail("Livebild zeigt nicht die Drohne (Kamera " + camera + ", Fenster " + SelfTestRunner.screenName(mc) + ")")
                            : StepResult.pending();
                })
                .append(Shots.photos(CovCamAKit.PHOTO + "live_view")));
    }

    static void exitSetting() {
        SelfTest.clientStep(345, EXIT, STEP_TICKS, Script.create()
                .server("gekoppelte Tour, Flugplaner in die Hand", (server, state) -> {
                    standNear(server, state, 14, -100);
                    CamFlights.pair(server, state);
                    linkedPlanner(server, state);
                })
                .await("Flugplaner am Client", 100, () -> held().flatMap(PlannerTablets::link).isPresent())
                .then("Seriennummer der Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state())
                        .filter(drone -> CamFlights.serial(ctx.state()).equals(PlannerTablets.serialOf(drone))).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Seriennummer fehlt am Client") : StepResult.pending())
                .run("Editor öffnen", () -> Minecraft.getInstance().gameMode.useItem(Minecraft.getInstance().player, InteractionHand.MAIN_HAND))
                .await("Editor offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof PlannerScreen)
                .then("Verlassen steht auf schweben", (ctx, ticks) -> shownExit(ExitSetting.HOVER, ticks))
                .append(Shots.photos(CovCamAKit.PHOTO + "exit_setting"))
                .then("Klick rechts: heim", (ctx, ticks) -> clickExit(ctx, ticks, 1, ExitSetting.HOME))
                .then("Server: heim an der Drohne", (ctx, ticks) -> serverExit(ctx, ticks, ExitSetting.HOME))
                .then("Klick rechts: landen", (ctx, ticks) -> clickExit(ctx, ticks, 1, ExitSetting.LAND))
                .then("Server: landen an der Drohne", (ctx, ticks) -> serverExit(ctx, ticks, ExitSetting.LAND))
                .then("Klick rechts: schweben", (ctx, ticks) -> clickExit(ctx, ticks, 1, ExitSetting.HOVER))
                .then("Server: schweben an der Drohne", (ctx, ticks) -> serverExit(ctx, ticks, ExitSetting.HOVER))
                .then("Klick links: landen", (ctx, ticks) -> clickExit(ctx, ticks, -1, ExitSetting.LAND))
                .then("Server: landen an der Drohne", (ctx, ticks) -> serverExit(ctx, ticks, ExitSetting.LAND))
                .run("Editor schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .server("leere Hand", (server, state) -> Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY))
                .sleep(10)
                .then("Schleichen + Rechtsklick mit leerer Hand", (ctx, ticks) -> CovCamAKit.use(ctx, ticks, drone(ctx), true))
                .then("Wartungsmenü", CovCamAPlannerSteps::serviceOpen)
                .then("Knopf Mission", (ctx, ticks) -> {
                    Screen screen = Minecraft.getInstance().gui.screen();
                    Optional<AbstractWidget> mission = screen == null ? Optional.empty() : CovCamAKit.widget(screen, I18n.get(MissionService.BUTTON_KEY));
                    if (mission.isEmpty()) {
                        return StepResult.fail("kein Knopf Mission, Knöpfe " + (screen == null ? "-" : CovCamAKit.labels(screen)));
                    }
                    CovCamAKit.click(screen, mission.get());
                    return StepResult.pass();
                })
                .await("Fenster Mission", 100, () -> Minecraft.getInstance().gui.screen() instanceof MissionServiceScreen)
                .then("Fenster Mission zeigt landen", (ctx, ticks) -> {
                    MissionServiceScreen screen = (MissionServiceScreen) Minecraft.getInstance().gui.screen();
                    Optional<ExitSetting> shown = screen.shownExit();
                    return shown.filter(ExitSetting.LAND::equals).isPresent() ? StepResult.pass("Fenster Mission: Verlassen " + I18n.get(ExitSetting.LAND.value().key()))
                            : ticks > 40 ? StepResult.fail("Fenster Mission zeigt " + shown) : StepResult.pending();
                })
                .append(Shots.photos(CovCamAKit.PHOTO + "mission_window"))
                .run("Fenster schließen", () -> Minecraft.getInstance().gui.setScreen(null)));
    }

    private static StepResult shownExit(ExitSetting wanted, int ticks) {
        if (!(Minecraft.getInstance().gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        Optional<ExitSetting> shown = screen.shownExit();
        if (shown.filter(wanted::equals).isPresent()) {
            return StepResult.pass("Editor zeigt " + I18n.get(wanted.value().key()));
        }
        return ticks > 60 ? StepResult.fail("Editor zeigt " + shown.map(ExitSetting::key).orElse("—") + " statt " + wanted.key()) : StepResult.pending();
    }

    private static StepResult clickExit(SelfTest.StepContext ctx, int ticks, int direction, ExitSetting wanted) {
        if (!(Minecraft.getInstance().gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        if (ticks == 0) {
            Rect box = EditorLayout.valueBox(screen.layout().settings().get(EditorLayout.SETTING_EXIT), screen.layout().settingLabelWidth());
            double x = direction > 0 ? box.right() - 4 : box.x() + 4;
            MouseButtonEvent event = new MouseButtonEvent(x, box.centerY(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
            screen.mouseClicked(event, false);
            screen.mouseReleased(event);
            return StepResult.pending();
        }
        Optional<EditorState.Status> status = screen.state().status();
        boolean saved = status.filter(s -> !s.error() && s.text().key().equals(ExitSetting.Result.OK.messageKey())).isPresent();
        if (saved && screen.shownExit().filter(wanted::equals).isPresent()) {
            return StepResult.pass("Editor zeigt " + I18n.get(wanted.value().key()) + ", Meldung \"" + I18n.get(status.get().text().key()) + "\"");
        }
        if (status.filter(EditorState.Status::error).isPresent()) {
            return StepResult.fail("Fehler " + status.get().text().key());
        }
        return ticks > 60 ? StepResult.fail("Editor zeigt " + screen.shownExit() + ", Status " + status.map(s -> s.text().key()).orElse("-"))
                : StepResult.pending();
    }

    private static StepResult serverExit(SelfTest.StepContext ctx, int ticks, ExitSetting wanted) {
        StepResult read = Script.server(ctx, ticks, (server, state) -> state.put(EXIT_SEEN,
                ExitSettings.read(Pilot.serverDrone(server, state)).map(ExitSetting::name).orElse("-")));
        if (!read.done() || read.failed()) {
            return read;
        }
        Object seen = ctx.state().remove(EXIT_SEEN);
        if (wanted.name().equals(seen)) {
            return StepResult.pass("camdrones:settings an der Drohne: exit=" + seen);
        }
        return ticks > 100 ? StepResult.fail("camdrones:settings an der Drohne: exit=" + seen + " statt " + wanted.name()) : StepResult.pending();
    }

    private static void linkedPlanner(MinecraftServer server, Map<String, Object> state) {
        ItemStack planner = new ItemStack(PlannerItems.flightPlanner().orElseThrow());
        planner.set(PlannerItems.link(), PlannerLink.of(CamFlights.serial(state), "", CamPresets.TOUR.flightClass()));
        Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, planner);
    }

    private static void standNear(MinecraftServer server, Map<String, Object> state, int dx, int dz) {
        Entity drone = CovCamAKit.spawnPreset(server, state, CamPresets.TOUR, Arena.ground(dx, dz), 0.0F);
        ServerPlayer player = Arena.player(server);
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Vec3 stand = drone.position().add(0.0, 0.0, -2.2);
        player.teleportTo(player.level(), stand.x, stand.y, stand.z, Set.<Relative>of(), 0.0F, 20.0F, true);
    }

    private static Entity drone(SelfTest.StepContext ctx) {
        return CamFlights.clientDrone(ctx.state()).orElseThrow(() -> new IllegalStateException("Drohne fehlt am Client"));
    }

    private static Optional<ItemStack> held() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return Optional.empty();
        }
        ItemStack stack = mc.player.getMainHandItem();
        return PlannerItems.isPlanner(stack) ? Optional.of(stack) : Optional.empty();
    }

    private static StepResult paired(SelfTest.StepContext ctx, int ticks, boolean sneak) {
        Minecraft mc = Minecraft.getInstance();
        Optional<String> key = CovCamAKit.overlayKey();
        Optional<PlannerLink> link = held().flatMap(PlannerTablets::link);
        if (key.filter((FieldText.PREFIX + "pair.done")::equals).isPresent() && link.isPresent()) {
            String text = CovCamAKit.overlayText();
            String serial = link.get().shortSerial();
            String name = link.get().name();
            boolean named = !serial.isEmpty() && text.contains(serial) || !name.isEmpty() && text.contains(name);
            return named ? StepResult.pass("Meldung \"" + text + "\", Tablet trägt die Seriennummer " + link.get().serial())
                    : StepResult.fail("Meldung \"" + text + "\" ohne Namen der Drohne");
        }
        if (ticks < 40) {
            return StepResult.pending();
        }
        return StepResult.fail((sneak ? "Schleichen + " : "") + "Rechtsklick mit dem Flugplaner koppelt nicht (Meldung \"" + CovCamAKit.overlayText() + "\" "
                + key.orElse("ohne Schlüssel") + ", Fenster " + SelfTestRunner.screenName(mc) + ", Tablet gekoppelt " + link.isPresent() + ")");
    }

    private static StepResult serviceOpen(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen != null && !(screen instanceof PlannerScreen) && ticks >= 10) {
            boolean crew = CovCamAKit.widget(screen, I18n.get(CrewTexts.BUTTON)).isPresent();
            String name = screen.getClass().getSimpleName();
            if (crew || name.toLowerCase(Locale.ROOT).contains("service")) {
                return StepResult.pass("Wartungsmenü offen (" + name + ", Knöpfe " + CovCamAKit.labels(screen) + ")");
            }
            return StepResult.fail("anderes Fenster offen: " + name + " " + CovCamAKit.labels(screen));
        }
        return ticks > 100 ? StepResult.fail("Wartungsmenü öffnet nicht (" + SelfTestRunner.screenName(mc) + ")") : StepResult.pending();
    }

    private static StepResult serviceButtons(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Wartungsmenü geschlossen");
        }
        int stage = (int) ctx.state().getOrDefault(STAGE, 0);
        if (ticks > 300) {
            return StepResult.fail("Stufe " + stage + " hängt, Fenster " + screen.getClass().getSimpleName());
        }
        switch (stage) {
            case 0 -> {
                List<String> labels = CovCamAKit.labels(screen);
                Optional<AbstractWidget> crew = CovCamAKit.widget(screen, I18n.get(CrewTexts.BUTTON));
                Optional<AbstractWidget> mission = CovCamAKit.widget(screen, I18n.get(MissionService.BUTTON_KEY));
                if (crew.isEmpty() || mission.isEmpty()) {
                    return StepResult.fail("Kopf des Wartungsmenüs ohne " + (mission.isEmpty() ? "„Mission“" : "") + (crew.isEmpty() ? " „Crew“" : "")
                            + ", Knöpfe " + labels);
                }
                ctx.state().put(SHOT, screen);
                ctx.state().put(NOTE, labels.toString());
                CovCamAKit.click(screen, mission.get());
                ctx.state().put(STAGE, 1);
                return StepResult.pending();
            }
            case 1 -> {
                if (screen instanceof MissionServiceScreen missionScreen && screen instanceof ThemedScreen) {
                    Script.note(ctx, "Mission öffnet " + screen.getClass().getSimpleName() + " (" + missionScreen.getTitle().getString() + ")");
                    missionScreen.onClose();
                    ctx.state().put(STAGE, 2);
                }
                return StepResult.pending();
            }
            case 2 -> {
                if (screen != ctx.state().get(SHOT)) {
                    return StepResult.pending();
                }
                Optional<AbstractWidget> crew = CovCamAKit.widget(screen, I18n.get(CrewTexts.BUTTON));
                if (crew.isEmpty()) {
                    return StepResult.fail("nach Zurück kein Knopf Crew");
                }
                CovCamAKit.click(screen, crew.get());
                ctx.state().put(STAGE, 3);
                return StepResult.pending();
            }
            default -> {
                if (screen instanceof CrewScreen && screen instanceof ThemedScreen) {
                    ctx.state().remove(STAGE);
                    return StepResult.pass("Knöpfe Mission und Crew im Wartungsmenü " + ctx.state().get(NOTE) + ", beide öffnen ein Fenster im Propwash-Stil "
                            + "(MissionServiceScreen, CrewScreen)");
                }
                return StepResult.pending();
            }
        }
    }

    private static void givePlanner(MinecraftServer server) {
        ServerPlayer player = Arena.player(server);
        ItemStack tablet = new ItemStack(PlannerItems.flightPlanner().orElseThrow());
        PlannerTablets.write(tablet, new TabletState(List.of(PlannerEntry.of("Vorschau", PlannerSteps.sampleMission())), 0,
                TabletState.DEFAULT_GROUND_HEIGHT));
        player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
        Vec3 spawn = Arena.spawn();
        player.teleportTo(player.level(), spawn.x, spawn.y, spawn.z, Set.<Relative>of(), 0.0F, 10.0F, true);
    }
}
