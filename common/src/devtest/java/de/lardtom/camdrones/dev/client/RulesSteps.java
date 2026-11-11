package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.worldrule.WorldRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class RulesSteps {
    public static final String NAME = "welt-regeln: Abschnitt „Kamera-Drohnen“, Höhengrenze greift";
    public static final String TAB = "world_rules";
    public static final int LIMIT = 6;
    public static final double LIMIT_TOLERANCE = 1.0;
    private static final String HOME = "regeln.home";
    private static final String TOP = "regeln.top";
    private static final int MAX_SCROLL = 120;

    private RulesSteps() {
    }

    public static void register() {
        SelfTest.clientStep(17, NAME, 20 * 150, Script.create()
                .append(Shots.photoWindow())
                .then("Tab Welt-Regeln öffnen", (ctx, ticks) -> OsdSteps.openTab(ctx, ticks, TAB))
                .then("Abschnitt des Add-ons", RulesSteps::section)
                .append(Shots.photos("world_rules_camdrones", RulesSteps::scrollToSection))
                .run("Tab schließen", () -> Minecraft.getInstance().gui.setScreen(null))
                .server("Höhengrenze " + LIMIT + " m", (server, state) -> Arena.rule(server, CamWorldRules.MAX_ALTITUDE_ID, String.valueOf(LIMIT)))
                .await("Höhengrenze am Client", 100, () -> Minecraft.getInstance().level != null
                        && CamWorldRules.maxAltitude(Minecraft.getInstance().level) == LIMIT)
                .append(Pilot.launch(CamPresets.TOUR, 36, 8, 0.0F))
                .append(Flight.gpsFix())
                .then("Vollgas nach oben", RulesSteps::climb)
                .append(Shots.photos("osd_alt_limit"))
                .then("Gas in die Mitte", (ctx, ticks) -> Pilot.throttleTo(ctx, ticks, Pilot.THROTTLE_CENTER)));
    }

    private static List<String> wanted() {
        List<String> out = new ArrayList<>();
        out.add(sectionTitle());
        for (WorldRule<?> rule : CamWorldRules.registered()) {
            out.add(I18n.get(rule.nameKey()));
        }
        return out;
    }

    static String sectionTitle() {
        return CamDrones.MOD_NAME.replaceFirst("^Propwash:\\s*", "");
    }

    private static boolean visible(LayoutCheck.Frame frame, List<String> texts) {
        for (String text : texts) {
            if (!frame.shows(text)) {
                return false;
            }
        }
        return true;
    }

    static void scrollToSection() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return;
        }
        List<String> texts = wanted();
        for (int i = 0; i < MAX_SCROLL && !visible(LayoutCheck.capture(mc, screen), texts); i++) {
            screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0.0, -1.0);
        }
    }

    private static StepResult section(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Tab geschlossen");
        }
        if (ticks < 5) {
            return StepResult.pending();
        }
        scrollToSection();
        LayoutCheck.Frame frame = LayoutCheck.capture(mc, screen);
        List<String> missing = new ArrayList<>();
        for (String text : wanted()) {
            if (!frame.shows(text)) {
                missing.add("\"" + text + "\"");
            }
        }
        if (!missing.isEmpty()) {
            ctx.log("Texte im Tab Welt-Regeln: " + frame.values());
            return StepResult.fail("nicht sichtbar: " + String.join(", ", missing));
        }
        return StepResult.pass("Abschnitt \"" + sectionTitle() + "\" mit " + CamWorldRules.registered().size() + " Regeln im Tab Welt-Regeln");
    }

    private static StepResult climb(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Double> y = Flight.position().map(p -> p.y);
        if (y.isEmpty()) {
            return StepResult.fail("keine Drohne");
        }
        double home = (double) ctx.state().computeIfAbsent(HOME, key -> y.get());
        double top = Math.max((double) ctx.state().getOrDefault(TOP, home), y.get());
        ctx.state().put(TOP, top);
        Pilot.steer(1.0);
        double above = top - home;
        if (above > LIMIT + LIMIT_TOLERANCE) {
            return StepResult.fail("steigt " + Flight.format(above) + " m über den Start (Grenze " + LIMIT + " m)");
        }
        if (ticks < 160) {
            return StepResult.pending();
        }
        Optional<NavStatus> status = Pilot.status();
        boolean warned = status.map(s -> s.warns(NavOutput.Warning.ALTITUDE_LIMIT)).orElse(false);
        if (!warned) {
            return StepResult.fail("keine Warnung ALT LIMIT bei " + Flight.format(above) + " m: " + status.map(NavStatus::warnings).orElse(Set.of()));
        }
        if (above < LIMIT - 2.0 * LIMIT_TOLERANCE) {
            return StepResult.fail("bleibt bei " + Flight.format(above) + " m, Grenze " + LIMIT + " m");
        }
        return StepResult.pass("mit Vollgas höchstens " + Flight.format(above) + " m über dem Start bei Grenze " + LIMIT + " m, Warnung ALT LIMIT");
    }
}
