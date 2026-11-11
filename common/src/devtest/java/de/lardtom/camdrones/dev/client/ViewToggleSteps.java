package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.planner.MissionService;
import de.lardtom.camdrones.client.planner.MissionServiceScreen;
import de.lardtom.camdrones.client.planner.PlannerScreen;
import de.lardtom.camdrones.crew.CrewTexts;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerLink;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.ViewSettings;
import de.lardtom.camdrones.planner.model.EditorLayout;
import de.lardtom.camdrones.planner.model.EditorState;
import de.lardtom.camdrones.planner.model.ExitSetting;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.camdrones.planner.model.ViewSetting;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ViewToggleSteps {
    public static final String SERVICE = "wartung: Ansicht OSD/Kamera umschalten";
    public static final String EDITOR = "planer: Ansicht im Editor umschalten";
    private static final List<String> HUD_TEXTS = List.of("BAT ", "SIG ", "H.S. ", "V.S. ");
    private static final String SEEN = "view.seen";
    private static final int STEP_TICKS = 20 * 150;

    private ViewToggleSteps() {
    }

    public static void register() {
        SelfTest.clientStep(403, SERVICE, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .server("Tour abstellen und koppeln, leere Hand", (server, state) -> {
                    Pilot.spawn(server, state, CamPresets.TOUR, Arena.ground(84, 76), 0.0F);
                    CamFlights.pair(server, state);
                    Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                })
                .then("Seriennummer der Drohne am Client", ViewToggleSteps::serialOnClient)
                .then("Schleichen + Rechtsklick mit leerer Hand", (ctx, ticks) -> CovCamAKit.use(ctx, ticks, drone(ctx), true))
                .then("Wartungsmenü", ViewToggleSteps::serviceOpen)
                .then("Knopf Mission", ViewToggleSteps::clickMission)
                .await("Fenster Mission", 100, () -> Minecraft.getInstance().gui.screen() instanceof MissionServiceScreen)
                .then("Ansicht steht auf OSD", (ctx, ticks) -> serviceShows(ticks, ViewSetting.OSD))
                .then("Klick rechts: Kamera", (ctx, ticks) -> clickService(ticks, 1, ViewSetting.CAMERA))
                .then("Server: Kamera an der Drohne", (ctx, ticks) -> serverView(ctx, ticks, ViewSetting.CAMERA))
                .append(Shots.photos(SurvivalCraftSteps.PHOTO + "view_setting_service"))
                .then("Klick links: OSD", (ctx, ticks) -> clickService(ticks, -1, ViewSetting.OSD))
                .then("Server: OSD an der Drohne", (ctx, ticks) -> serverView(ctx, ticks, ViewSetting.OSD))
                .then("Klick links: Kamera", (ctx, ticks) -> clickService(ticks, -1, ViewSetting.CAMERA))
                .then("Server: Kamera an der Drohne", (ctx, ticks) -> serverView(ctx, ticks, ViewSetting.CAMERA))
                .then("Verlassen bleibt schweben", (ctx, ticks) -> {
                    MissionServiceScreen screen = (MissionServiceScreen) Minecraft.getInstance().gui.screen();
                    return screen.shownExit().filter(ExitSetting.HOVER::equals).isPresent() ? StepResult.pass("Verlassen unverändert schweben")
                            : StepResult.fail("Verlassen geändert: " + screen.shownExit());
                })
                .run("Fenster schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .sleep(10)
                .server("Funke binden", Pilot::bind)
                .sleep(14)
                .server("Flug starten", Pilot::start)
                .await("FPV-Ansicht am Client", 200, () -> PropwashClientApi.input().flying() && PropwashClientApi.input().drone().isPresent())
                .sleep(20)
                .then("scharf schalten", Pilot::arm)
                .then("Kompass prüfen", Pilot::compass)
                .append(Flight.takeoff(3.0))
                .then("Kamera-Ansicht statt OSD im FPV", ViewToggleSteps::hud)
                .append(Shots.photos(SurvivalCraftSteps.PHOTO + "view_setting_hud")));
        SelfTest.clientStep(404, EDITOR, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .server("Tour abstellen und koppeln, gekoppelter Flugplaner", (server, state) -> {
                    Pilot.spawn(server, state, CamPresets.TOUR, Arena.ground(92, 76), 0.0F);
                    CamFlights.pair(server, state);
                    ItemStack planner = new ItemStack(PlannerItems.flightPlanner().orElseThrow());
                    planner.set(PlannerItems.link(), PlannerLink.of(CamFlights.serial(state), "", CamPresets.TOUR.flightClass()));
                    Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, planner);
                })
                .then("Seriennummer der Drohne am Client", ViewToggleSteps::serialOnClient)
                .await("Flugplaner am Client", 100, () -> held().flatMap(PlannerTablets::link).isPresent())
                .run("Editor öffnen", () -> Minecraft.getInstance().gameMode.useItem(Minecraft.getInstance().player, InteractionHand.MAIN_HAND))
                .await("Editor offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof PlannerScreen)
                .then("Ansicht im Editor neben Verlassen", ViewToggleSteps::editorField)
                .then("Ansicht steht auf OSD", (ctx, ticks) -> editorShows(ticks, ViewSetting.OSD))
                .then("Klick rechts: Kamera", (ctx, ticks) -> clickEditor(ticks, 1, ViewSetting.CAMERA))
                .then("Server: Kamera an der Drohne", (ctx, ticks) -> serverView(ctx, ticks, ViewSetting.CAMERA))
                .append(Shots.photos(SurvivalCraftSteps.PHOTO + "view_setting_editor"))
                .then("Klick rechts: OSD", (ctx, ticks) -> clickEditor(ticks, 1, ViewSetting.OSD))
                .then("Server: OSD an der Drohne", (ctx, ticks) -> serverView(ctx, ticks, ViewSetting.OSD))
                .run("Editor schließen", () -> Minecraft.getInstance().gui.setScreen(null)));
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

    private static StepResult serialOnClient(SelfTest.StepContext ctx, int ticks) {
        boolean seen = CamFlights.clientDrone(ctx.state()).filter(drone -> CamFlights.serial(ctx.state()).equals(PlannerTablets.serialOf(drone))).isPresent();
        return seen ? StepResult.pass() : ticks > 100 ? StepResult.fail("Seriennummer fehlt am Client") : StepResult.pending();
    }

    private static StepResult serviceOpen(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen != null && ticks >= 10) {
            boolean crew = CovCamAKit.widget(screen, I18n.get(CrewTexts.BUTTON)).isPresent();
            if (crew || screen.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("service")) {
                return StepResult.pass("Wartungsmenü offen, Knöpfe " + CovCamAKit.labels(screen));
            }
            return StepResult.fail("anderes Fenster offen: " + screen.getClass().getSimpleName());
        }
        return ticks > 100 ? StepResult.fail("Wartungsmenü öffnet nicht (" + SelfTestRunner.screenName(mc) + ")") : StepResult.pending();
    }

    private static StepResult clickMission(SelfTest.StepContext ctx, int ticks) {
        Screen screen = Minecraft.getInstance().gui.screen();
        Optional<AbstractWidget> mission = screen == null ? Optional.empty() : CovCamAKit.widget(screen, I18n.get(MissionService.BUTTON_KEY));
        if (mission.isEmpty()) {
            return StepResult.fail("kein Knopf Mission, Knöpfe " + (screen == null ? "-" : CovCamAKit.labels(screen)));
        }
        CovCamAKit.click(screen, mission.get());
        return StepResult.pass();
    }

    private static StepResult serviceShows(int ticks, ViewSetting wanted) {
        if (!(Minecraft.getInstance().gui.screen() instanceof MissionServiceScreen screen)) {
            return StepResult.fail("Fenster Mission geschlossen");
        }
        Optional<ViewSetting> shown = screen.shownView();
        if (shown.filter(wanted::equals).isPresent()) {
            return StepResult.pass("Fenster Mission: " + I18n.get(ViewSetting.LABEL_KEY) + " " + I18n.get(wanted.value().key()));
        }
        return ticks > 60 ? StepResult.fail("Fenster Mission zeigt " + shown + " statt " + wanted) : StepResult.pending();
    }

    private static void press(Screen screen, Rect box, int direction) {
        double x = direction > 0 ? box.right() - 4 : box.x() + 4;
        MouseButtonEvent event = new MouseButtonEvent(x, box.centerY(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static StepResult clickService(int ticks, int direction, ViewSetting wanted) {
        if (!(Minecraft.getInstance().gui.screen() instanceof MissionServiceScreen screen)) {
            return StepResult.fail("Fenster Mission geschlossen");
        }
        if (ticks == 0) {
            press(screen, screen.layout().viewValue(), direction);
            return StepResult.pending();
        }
        String status = screen.status() == null ? "" : screen.status().getString();
        boolean saved = status.equals(I18n.get(ViewSetting.messageKey(ExitSetting.Result.OK)));
        if (saved && screen.shownView().filter(wanted::equals).isPresent()) {
            return StepResult.pass("Fenster Mission zeigt " + I18n.get(wanted.value().key()) + ", Meldung \"" + status + "\"");
        }
        return ticks > 80 ? StepResult.fail("Fenster Mission zeigt " + screen.shownView() + ", Meldung \"" + status + "\"") : StepResult.pending();
    }

    private static StepResult editorField(SelfTest.StepContext ctx, int ticks) {
        if (!(Minecraft.getInstance().gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        EditorLayout layout = screen.layout();
        Rect view = layout.settings().get(EditorLayout.SETTING_VIEW);
        Rect exit = layout.settings().get(EditorLayout.SETTING_EXIT);
        List<String> problems = new ArrayList<>();
        if (view.isEmpty() || !view.inside(layout.window())) {
            problems.add("Feld Ansicht außerhalb des Fensters " + view);
        }
        if (view.overlaps(exit)) {
            problems.add("Ansicht überlappt Verlassen");
        }
        return problems.isEmpty() ? StepResult.pass("Feld Ansicht bei " + screen.width + "×" + screen.height + " sichtbar, " + layout.settings().size() + " Einstellungen")
                : StepResult.fail(String.join("; ", problems));
    }

    private static StepResult editorShows(int ticks, ViewSetting wanted) {
        if (!(Minecraft.getInstance().gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        Optional<ViewSetting> shown = screen.shownView();
        if (shown.filter(wanted::equals).isPresent()) {
            return StepResult.pass("Editor zeigt " + I18n.get(wanted.value().key()));
        }
        return ticks > 60 ? StepResult.fail("Editor zeigt " + shown + " statt " + wanted) : StepResult.pending();
    }

    private static StepResult clickEditor(int ticks, int direction, ViewSetting wanted) {
        if (!(Minecraft.getInstance().gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen");
        }
        if (ticks == 0) {
            press(screen, EditorLayout.valueBox(screen.layout().settings().get(EditorLayout.SETTING_VIEW), screen.layout().settingLabelWidth()), direction);
            return StepResult.pending();
        }
        Optional<EditorState.Status> status = screen.state().status();
        boolean saved = status.filter(s -> !s.error() && s.text().key().equals(ViewSetting.messageKey(ExitSetting.Result.OK))).isPresent();
        if (saved && screen.shownView().filter(wanted::equals).isPresent()) {
            return StepResult.pass("Editor zeigt " + I18n.get(wanted.value().key()) + ", Meldung \"" + I18n.get(status.get().text().key()) + "\"");
        }
        if (status.filter(EditorState.Status::error).isPresent()) {
            return StepResult.fail("Fehler " + status.get().text().key());
        }
        return ticks > 80 ? StepResult.fail("Editor zeigt " + screen.shownView() + ", Status " + status.map(s -> s.text().key()).orElse("-"))
                : StepResult.pending();
    }

    private static StepResult serverView(SelfTest.StepContext ctx, int ticks, ViewSetting wanted) {
        StepResult read = Script.server(ctx, ticks, (MinecraftServer server, Map<String, Object> state) -> {
            Entity drone = Pilot.serverDrone(server, state);
            DroneSettings settings = PropwashApi.droneData().get(drone, CamDroneData.settings());
            state.put(SEEN, settings == null ? "-" : settings.view().name());
        });
        if (!read.done() || read.failed()) {
            return read;
        }
        Object seen = ctx.state().remove(SEEN);
        String expected = ViewSettings.cameraView(wanted).name();
        if (expected.equals(seen)) {
            return StepResult.pass("camdrones:settings an der Drohne: view=" + seen);
        }
        return ticks > 100 ? StepResult.fail("camdrones:settings an der Drohne: view=" + seen + " statt " + expected) : StepResult.pending();
    }

    private static StepResult hud(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
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
            return StepResult.fail("Kamera-Ansicht ohne " + missing + " nach dem Umschalten in der Wartung");
        }
        return StepResult.pass("nach dem Umschalten in der Wartung zeigt das FPV-Bild die Kamera-Ansicht (" + String.join(", ", HUD_TEXTS).replace(" ,", ",").trim() + ")");
    }
}
