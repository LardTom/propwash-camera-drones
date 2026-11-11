package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.hud.CamOsd;
import de.lardtom.camdrones.client.hud.CamOsdSource;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.logic.nav.CineMode;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.NormalMode;
import de.lardtom.camdrones.logic.nav.SportMode;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.osd.OsdText;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;

import java.util.List;
import java.util.Optional;

public final class FlightModeSteps {
    public static final String KEY = "flug: Flugmodus-Taste von Propwash schaltet N → C → S";
    public static final String SWITCH = "flug: Flugmodus-Schalter der Funke wählt N, C und S";
    public static final String MENU = "tasten: K-Menü nennt die Flugmodus-Taste für N, C und S";
    public static final String FLIGHT_MODE_KEY = "key.propwash.drone_flight_mode";
    public static final int COMPACT_NUMBER = 381;
    public static final int TOUR_NUMBER = 382;
    public static final int CINE_NUMBER = 383;
    public static final int SWITCH_NUMBER = 384;
    public static final int MENU_NUMBER = 385;
    public static final String MODES_TAB = "modes";
    public static final String PAGE_KEY = "propwash.input.modes.page.addons";
    public static final int MODE_CHANNEL = 5;
    public static final float SWITCH_ANGLE = 1.0F;
    public static final float SWITCH_HORIZON = 0.0F;
    public static final float SWITCH_ACRO = -1.0F;
    public static final double HEIGHT = 5.0;
    public static final int PRESS_TICKS = 3;
    public static final int SETTLE_TICKS = 4;
    public static final int WAIT_TICKS = 80;
    private static final String BEFORE = "flightmode.before";
    private static final int STEP_TICKS = 20 * 150;

    private FlightModeSteps() {
    }

    public static void register() {
        SelfTest.clientStep(COMPACT_NUMBER, KEY + " – " + CamPresets.COMPACT.path(), STEP_TICKS, key(CamPresets.COMPACT, 90));
        SelfTest.clientStep(TOUR_NUMBER, KEY + " – " + CamPresets.TOUR.path(), STEP_TICKS, key(CamPresets.TOUR, 102));
        SelfTest.clientStep(CINE_NUMBER, KEY + " – " + CamPresets.CINE.path(), STEP_TICKS, key(CamPresets.CINE, 114));
        SelfTest.clientStep(SWITCH_NUMBER, SWITCH, STEP_TICKS, radio(), FlightModeSteps::stopRadio);
        SelfTest.clientStep(MENU_NUMBER, MENU, STEP_TICKS, menu());
    }

    private static Script key(CamPresets.Entry entry, int dx) {
        return Script.create()
                .append(Pilot.launch(entry, dx, 70, 0.0F))
                .append(Flight.takeoff(HEIGHT))
                .then("nach dem Start N (Tastatur in Angle)", (ctx, ticks) -> expect(ctx, ticks, NavLogic.StickMode.NORMAL, "Start"))
                .then("Flugmodus-Taste: C", (ctx, ticks) -> press(ctx, ticks, NavLogic.StickMode.CINE))
                .then("Flugmodus-Taste: S", (ctx, ticks) -> press(ctx, ticks, NavLogic.StickMode.SPORT))
                .then("Flugmodus-Taste: N", (ctx, ticks) -> press(ctx, ticks, NavLogic.StickMode.NORMAL))
                .then("Flugmodus-Taste: wieder C", (ctx, ticks) -> press(ctx, ticks, NavLogic.StickMode.CINE));
    }

    private static Script radio() {
        return Script.create()
                .append(Pilot.launch(CamPresets.TOUR, 126, 70, 0.0F))
                .append(Flight.gpsFix())
                .then("vor der Funke N (Tastatur in Angle)", (ctx, ticks) -> expect(ctx, ticks, NavLogic.StickMode.NORMAL, "Tastatur"))
                .then("Test-Funke anschließen", (ctx, ticks) -> PropwashClientApi.simulateRadio(false) ? StepResult.pass()
                        : StepResult.fail("Test-Funke von Propwash nicht verfügbar"))
                .then("Schalter unten (Acro): S", (ctx, ticks) -> position(ctx, ticks, SWITCH_ACRO, NavLogic.StickMode.SPORT, "unten"))
                .then("Schalter Mitte (Horizon): C", (ctx, ticks) -> position(ctx, ticks, SWITCH_HORIZON, NavLogic.StickMode.CINE, "Mitte"))
                .then("Schalter oben (Angle): N", (ctx, ticks) -> position(ctx, ticks, SWITCH_ANGLE, NavLogic.StickMode.NORMAL, "oben"))
                .then("Schalter unten (Acro): S", (ctx, ticks) -> position(ctx, ticks, SWITCH_ACRO, NavLogic.StickMode.SPORT, "unten"))
                .then("Schalter oben (Angle): N", (ctx, ticks) -> position(ctx, ticks, SWITCH_ANGLE, NavLogic.StickMode.NORMAL, "oben"))
                .then("Schalter Mitte (Horizon): C", (ctx, ticks) -> position(ctx, ticks, SWITCH_HORIZON, NavLogic.StickMode.CINE, "Mitte"));
    }

    private static Script menu() {
        Script script = Script.create().append(CovCamBKit.wide());
        for (String language : List.of(Shots.ENGLISH, Shots.GERMAN)) {
            script.run("Sprache " + language, () -> Shots.language(Minecraft.getInstance(), language))
                    .then("Tab Modi öffnen", (ctx, ticks) -> OsdSteps.openTab(ctx, ticks, MODES_TAB))
                    .then("Seite Add-ons", FlightModeSteps::addonsPage)
                    .then("Nächster Flugmodus (" + language + ")", (ctx, ticks) -> describes(ctx, ticks, language))
                    .run("Tab schließen", () -> Minecraft.getInstance().gui.setScreen(null));
        }
        return script.run("Sprache zurück", () -> Shots.restoreLanguage(Minecraft.getInstance()));
    }

    private static StepResult addonsPage(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Tab Modi geschlossen");
        }
        if (ticks % 10 == 2) {
            SelfTestRunner.clickWidget(screen, I18n.get(PAGE_KEY));
        }
        LayoutCheck.Frame frame = LayoutCheck.capture(mc, screen);
        if (ticks >= 6 && frame.shows(I18n.get(CamControl.MODE_CYCLE.nameKey()))) {
            return StepResult.pass();
        }
        return ticks > 80 ? StepResult.fail("Seite Add-ons zeigt \"" + I18n.get(CamControl.MODE_CYCLE.nameKey()) + "\" nicht: " + frame.values())
                : StepResult.pending();
    }

    private static StepResult describes(SelfTest.StepContext ctx, int ticks, String language) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Tab Modi geschlossen");
        }
        String name = I18n.get(CamControl.MODE_CYCLE.nameKey());
        String description = I18n.get(CamControl.MODE_CYCLE.descriptionKey());
        LayoutCheck.Frame frame = LayoutCheck.capture(mc, screen);
        Optional<LayoutCheck.Box> row = frame.find(name);
        if (ticks % 10 == 2 && row.isPresent()) {
            LayoutCheck.Box box = row.get();
            MouseButtonEvent click = new MouseButtonEvent(box.left() + 2.0, (box.top() + box.bottom()) / 2.0,
                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
            screen.mouseClicked(click, false);
            screen.mouseReleased(click);
        }
        String shown = String.join(" ", frame.values()).replaceAll("\\s+", " ");
        if (ticks >= 4 && shown.contains(description)) {
            return StepResult.pass(language + ": \"" + name + "\" – " + description);
        }
        if (ticks > 80) {
            ctx.log("Texte der Seite: " + frame.values());
            return StepResult.fail(language + ": Beschreibung \"" + description + "\" fehlt");
        }
        return StepResult.pending();
    }

    public static String letter(NavLogic.StickMode mode) {
        return switch (mode) {
            case NORMAL -> NormalMode.LABEL;
            case CINE -> CineMode.LABEL;
            case SPORT -> SportMode.LABEL;
        };
    }

    private static String osd() {
        OsdText text = CamOsd.mode(CamOsdSource.last());
        return text == null ? "" : text.text();
    }

    private static String shown() {
        Optional<NavStatus> status = Pilot.status();
        return status.map(s -> s.stickMode() + " (" + s.label() + ")").orElse("ohne Status") + ", OSD \"" + osd() + "\"";
    }

    private static boolean matches(NavLogic.StickMode wanted) {
        Optional<NavStatus> status = Pilot.status();
        String letter = letter(wanted);
        return status.isPresent() && status.get().stickMode() == wanted && letter.equals(status.get().label()) && letter.equals(osd());
    }

    private static StepResult expect(SelfTest.StepContext ctx, int ticks, NavLogic.StickMode wanted, String when) {
        Flight.track(ctx);
        if (matches(wanted)) {
            return StepResult.pass(when + " " + letter(wanted) + " (OSD " + osd() + ")");
        }
        return ticks > WAIT_TICKS ? StepResult.fail(when + ": erwartet " + wanted + ", ist " + shown()) : StepResult.pending();
    }

    private static StepResult press(SelfTest.StepContext ctx, int ticks, NavLogic.StickMode wanted) {
        Flight.track(ctx);
        KeyMapping mapping = KeyMapping.get(FLIGHT_MODE_KEY);
        if (mapping == null || mapping.isUnbound()) {
            return StepResult.fail("Flugmodus-Taste " + FLIGHT_MODE_KEY + " fehlt");
        }
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        if (ticks == 0) {
            ctx.state().put(BEFORE, Pilot.status().map(NavStatus::label).orElse("?"));
            KeyMapping.click(key);
            KeyMapping.set(key, true);
            return StepResult.pending();
        }
        if (ticks == PRESS_TICKS) {
            KeyMapping.set(key, false);
        }
        if (ticks < PRESS_TICKS + SETTLE_TICKS) {
            return StepResult.pending();
        }
        String from = String.valueOf(ctx.state().get(BEFORE));
        if (matches(wanted)) {
            return StepResult.pass(key.getDisplayName().getString() + ": " + from + " → " + letter(wanted) + " (OSD " + osd() + ")");
        }
        return ticks > WAIT_TICKS ? StepResult.fail(key.getDisplayName().getString() + ": erwartet " + wanted + ", Modus bleibt " + shown())
                : StepResult.pending();
    }

    private static StepResult position(SelfTest.StepContext ctx, int ticks, float value, NavLogic.StickMode wanted, String where) {
        if (ticks == 0) {
            return PropwashClientApi.setSimulatedAxis(MODE_CHANNEL, value) ? StepResult.pending()
                    : StepResult.fail("Kanal " + (MODE_CHANNEL + 1) + " der Test-Funke lässt sich nicht stellen");
        }
        if (ticks < SETTLE_TICKS) {
            return StepResult.pending();
        }
        return expect(ctx, ticks, wanted, "Schalter " + where);
    }

    private static StepResult stopRadio(SelfTest.StepContext ctx) {
        PropwashClientApi.stopSimulatedRadio();
        return StepResult.pass();
    }
}
