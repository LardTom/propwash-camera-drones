package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.client.hud.CamOsd;
import de.lardtom.camdrones.client.hud.CamOsdElements;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class OsdSteps {
    public static final String OSD = "osd: Add-on-Elemente im OSD-Editor, Warnungen";
    public static final String HUD = "hud: Kamera-Ansicht";
    public static final String OSD_TAB = "osd";
    private static final String GROUP_KEY = "propwash.osd.group.addon";
    private static final String FIRST_ELEMENT_KEY = "propwash.osd.element.crosshair";
    private static final int MAX_SCROLL = 80;
    private static final List<String> HUD_TEXTS = List.of("BAT ", "SIG ", "H.S. ", "V.S. ");

    private OsdSteps() {
    }

    public static void register() {
        SelfTest.clientStep(15, OSD, 20 * 150, Script.create()
                .append(Shots.photoWindow())
                .then("OSD-Tab öffnen", (ctx, ticks) -> openTab(ctx, ticks, OSD_TAB))
                .then("Add-on-Elemente im Editor", OsdSteps::editor)
                .append(Shots.photos("osd_editor_addons", OsdSteps::scrollToAddons))
                .run("Tab schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .append(Pilot.launch(CamPresets.TOUR, -36, 8, 0.0F))
                .server("Mission an die Drohne", OsdSteps::mission)
                .append(Flight.takeoff(3.0))
                .then("Mission starten", (ctx, ticks) -> press(ctx, ticks, CamControl.MISSION_START))
                .then("Mission läuft", OsdSteps::running)
                .then("Pause-Taste", (ctx, ticks) -> press(ctx, ticks, CamControl.PAUSE))
                .then("Warnung PAUSED", OsdSteps::paused)
                .append(Shots.photos("osd_warning_paused")));
        SelfTest.clientStep(16, HUD, 20 * 120, Script.create()
                .append(Shots.photoWindow())
                .append(Pilot.launch(CamPresets.TOUR, -48, 8, 0.0F))
                .server("Kamera-Ansicht einstellen", OsdSteps::cameraView)
                .await("Einstellung am Client", 100, () -> clientView().filter(view -> view == DroneSettings.CameraView.HUD).isPresent())
                .append(Flight.takeoff(3.0))
                .then("Kamera-Ansicht gezeichnet", OsdSteps::hud)
                .append(Shots.photos("hud_camera_view")));
    }

    static StepResult openTab(SelfTest.StepContext ctx, int ticks, String tab) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            if (!PropwashClientApi.openConfigTab(tab, null)) {
                return StepResult.fail("Tab " + tab + " lässt sich nicht öffnen");
            }
            return StepResult.pending();
        }
        if (mc.gui.screen() != null && ticks > 5) {
            return StepResult.pass();
        }
        return ticks > 100 ? StepResult.fail("Tab " + tab + " öffnet nicht") : StepResult.pending();
    }

    private static StepResult editor(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("OSD-Tab geschlossen");
        }
        String group = I18n.get(GROUP_KEY);
        LayoutCheck.Frame frame = scrollList(mc, screen, group);
        List<String> missing = new ArrayList<>();
        if (!frame.shows(group)) {
            missing.add("Gruppe \"" + group + "\"");
        }
        for (CamOsdElements.Element element : CamOsdElements.ELEMENTS) {
            String name = I18n.get(OsdElementSpec.defaultNameKey(element.id()));
            if (!frame.shows(name)) {
                missing.add("\"" + name + "\"");
            }
        }
        if (!missing.isEmpty()) {
            if (ticks < 20) {
                return StepResult.pending();
            }
            ctx.log("Texte im OSD-Tab: " + frame.values());
            return StepResult.fail("fehlt im OSD-Editor: " + String.join(", ", missing));
        }
        return StepResult.pass("Gruppe \"" + group + "\" mit allen " + CamOsdElements.ELEMENTS.size() + " Elementen des Add-ons im OSD-Editor");
    }

    private static void scrollToAddons() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen != null) {
            scrollList(mc, screen, I18n.get(GROUP_KEY));
        }
    }

    static LayoutCheck.Frame scrollList(Minecraft mc, Screen screen, String wanted) {
        LayoutCheck.Frame frame = LayoutCheck.capture(mc, screen);
        Optional<LayoutCheck.Box> anchor = frame.find(I18n.get(FIRST_ELEMENT_KEY));
        if (anchor.isEmpty()) {
            return frame;
        }
        double x = (anchor.get().left() + anchor.get().right()) / 2.0;
        double y = (anchor.get().top() + anchor.get().bottom()) / 2.0;
        for (int i = 0; i < MAX_SCROLL && !frame.shows(wanted); i++) {
            screen.mouseScrolled(x, y, 0.0, -1.0);
            frame = LayoutCheck.capture(mc, screen);
        }
        for (int i = 0; i < 4 && !lastVisible(frame); i++) {
            screen.mouseScrolled(x, y, 0.0, -1.0);
            frame = LayoutCheck.capture(mc, screen);
        }
        return frame;
    }

    private static boolean lastVisible(LayoutCheck.Frame frame) {
        CamOsdElements.Element last = CamOsdElements.ELEMENTS.getLast();
        return frame.shows(I18n.get(OsdElementSpec.defaultNameKey(last.id())));
    }

    private static StepResult press(SelfTest.StepContext ctx, int ticks, CamControl control) {
        Flight.track(ctx);
        Pilot.hold(control, ticks < 4);
        return ticks >= 6 ? StepResult.pass() : StepResult.pending();
    }

    private static void mission(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        List<Waypoint> points = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            points.add(Waypoint.at(drone.getX(), drone.getY() + 4.0, drone.getZ() + 12.0 * i));
        }
        PropwashApi.droneData().set(drone, CamDroneData.mission(), MissionData.of(Mission.of(points)));
    }

    private static StepResult running(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<NavStatus> status = Pilot.status();
        if (status.isPresent() && status.get().function() == NavLogic.Function.MISSION
                && status.get().missionState() == NavLogic.MissionState.RUNNING && ticks >= 40) {
            return StepResult.pass("Mission mit 3 Wegpunkten läuft (" + status.get().label() + ")");
        }
        return ticks > 200 ? StepResult.fail("Mission startet nicht: " + status.map(s -> s.function() + "/" + s.missionState()).orElse("kein Status"))
                : StepResult.pending();
    }

    private static StepResult paused(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<NavStatus> status = Pilot.status();
        if (status.isPresent() && status.get().warns(NavOutput.Warning.PAUSED) && status.get().missionState() == NavLogic.MissionState.PAUSED) {
            return StepResult.pass("Mission angehalten, Warnung PAUSED (OSD-Text \"" + CamOsd.Warning.PAUSED.text() + "\"), Warnungen "
                    + status.get().warnings());
        }
        return ticks > 60 ? StepResult.fail("keine Warnung PAUSED: " + status.map(NavStatus::warnings).orElse(Set.of()))
                : StepResult.pending();
    }

    private static void cameraView(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        PropwashApi.droneData().set(drone, CamDroneData.settings(), DroneSettings.DEFAULT.withView(DroneSettings.CameraView.HUD));
    }

    private static Optional<DroneSettings.CameraView> clientView() {
        return Pilot.drone().map(drone -> PropwashApi.droneData().get(drone, CamDroneData.settings()).view());
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
            return StepResult.fail("Kamera-Ansicht ohne " + missing);
        }
        return StepResult.pass("Kamera-Ansicht statt OSD mit " + HUD_TEXTS.size() + " Anzeigen (" + String.join(", ", HUD_TEXTS).replace(" ,", ",")
                + ") im FPV-Bild");
    }
}
