package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.client.hud.CamOsdElements;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class OsdNameSteps {
    public static final String NAMES = "osd: Namen der Add-on-Elemente bei GUI 320×240 in EN und DE ungekürzt";
    private static final int NUMBER = 39;
    private static final int GUI_SCALE = 2;
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 240;
    private static final String GROUP_KEY = "propwash.osd.group.addon";
    private static final String ELLIPSIS = "…";
    private static final int SETTLE_TICKS = 10;
    private static final int SIZE_TICKS = 40;
    private static final int RETRY_TICKS = 40;
    private static final int MAX_SCROLL = 12;
    private static final String FINDINGS = "osdnames.findings";

    private OsdNameSteps() {
    }

    public static void register() {
        SelfTest.clientStep(NUMBER, NAMES, 20 * 90, Script.create()
                .append(language(Shots.ENGLISH))
                .append(language(Shots.GERMAN))
                .run("Tab schließen, Fenster und Sprache zurück", OsdNameSteps::restore)
                .then("Ergebnis", (ctx, ticks) -> result(ctx)), ctx -> {
                    restore();
                    return StepResult.pass();
                });
    }

    private static Script language(String code) {
        String label = code + " bei GUI " + GUI_WIDTH + "x" + GUI_HEIGHT;
        return Script.create()
                .run("Sprache " + code, () -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.gui.setScreen(null);
                    Shots.language(mc, code);
                })
                .then("Fenster " + GUI_WIDTH * GUI_SCALE + "x" + GUI_HEIGHT * GUI_SCALE + " mit GUI-Skala " + GUI_SCALE,
                        (ctx, ticks) -> Shots.window(ctx, ticks, GUI_WIDTH * GUI_SCALE, GUI_HEIGHT * GUI_SCALE, GUI_SCALE))
                .then("OSD-Tab öffnen", (ctx, ticks) -> OsdSteps.openTab(ctx, ticks, OsdSteps.OSD_TAB))
                .then("Namen ungekürzt (" + label + ")", (ctx, ticks) -> names(ctx, ticks, label));
    }

    private static StepResult names(SelfTest.StepContext ctx, int ticks, String label) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("OSD-Tab geschlossen");
        }
        if (screen.width != GUI_WIDTH || screen.height != GUI_HEIGHT) {
            return ticks > SIZE_TICKS ? StepResult.fail("GUI ist " + screen.width + "x" + screen.height + " statt " + GUI_WIDTH + "x" + GUI_HEIGHT)
                    : StepResult.pending();
        }
        if (ticks < SETTLE_TICKS) {
            return StepResult.pending();
        }
        List<String> wanted = new ArrayList<>();
        for (CamOsdElements.Element element : CamOsdElements.ELEMENTS) {
            wanted.add(I18n.get(OsdElementSpec.defaultNameKey(element.id())));
        }
        String group = I18n.get(GROUP_KEY);
        Set<String> seen = new LinkedHashSet<>();
        Set<String> shortened = new LinkedHashSet<>();
        LayoutCheck.Frame frame = OsdSteps.scrollList(mc, screen, group);
        collect(frame, wanted, seen, shortened);
        Optional<LayoutCheck.Box> pointer = anchor(frame, group, wanted);
        for (int i = 0; i < MAX_SCROLL && seen.size() < wanted.size() && !seen.contains(wanted.getLast()); i++) {
            Optional<LayoutCheck.Box> anchor = anchor(frame, group, wanted);
            if (anchor.isEmpty()) {
                break;
            }
            pointer = anchor;
            LayoutCheck.Box box = anchor.get();
            screen.mouseScrolled((box.left() + box.right()) / 2.0, (box.top() + box.bottom()) / 2.0, 0.0, -1.0);
            frame = LayoutCheck.capture(mc, screen);
            collect(frame, wanted, seen, shortened);
        }
        for (int i = 0; i < MAX_SCROLL && seen.size() < wanted.size() && pointer.isPresent(); i++) {
            LayoutCheck.Box box = anchor(frame, group, wanted).orElse(pointer.get());
            screen.mouseScrolled((box.left() + box.right()) / 2.0, (box.top() + box.bottom()) / 2.0, 0.0, 1.0);
            frame = LayoutCheck.capture(mc, screen);
            collect(frame, wanted, seen, shortened);
        }
        List<String> missing = new ArrayList<>(wanted);
        missing.removeAll(seen);
        if (missing.isEmpty()) {
            return StepResult.pass(label + ": alle " + wanted.size() + " Namen ungekürzt " + wanted);
        }
        List<String> cut = cut(missing, shortened);
        if (ticks < RETRY_TICKS && cut.isEmpty()) {
            return StepResult.pending();
        }
        ctx.log("Texte im OSD-Tab (" + label + "): " + frame.values());
        findings(ctx).add(label + ": nicht ungekürzt zu sehen " + missing + (cut.isEmpty() ? "" : ", gekürzt " + cut));
        return StepResult.pass(label + ": " + missing.size() + " Namen gekürzt oder nicht zu sehen");
    }

    private static void collect(LayoutCheck.Frame frame, List<String> wanted, Set<String> seen, Set<String> shortened) {
        List<String> values = frame.values();
        for (String name : wanted) {
            if (frame.shows(name) && values.stream().anyMatch(value -> value.strip().equals(name))) {
                seen.add(name);
            }
        }
        for (String value : values) {
            String stripped = value.strip();
            if (stripped.endsWith(ELLIPSIS) && stripped.length() > ELLIPSIS.length()) {
                shortened.add(stripped);
            }
        }
    }

    private static List<String> cut(List<String> missing, Set<String> shortened) {
        List<String> out = new ArrayList<>();
        for (String name : missing) {
            for (String value : shortened) {
                String head = value.substring(0, value.length() - ELLIPSIS.length());
                if (name.startsWith(head)) {
                    out.add("\"" + name + "\" zu \"" + value + "\"");
                    break;
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<String> findings(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(FINDINGS, key -> new ArrayList<String>());
    }

    private static StepResult result(SelfTest.StepContext ctx) {
        List<String> findings = findings(ctx);
        if (!findings.isEmpty()) {
            return StepResult.fail(String.join(" | ", findings));
        }
        return StepResult.pass("EN und DE bei GUI " + GUI_WIDTH + "x" + GUI_HEIGHT + ": alle Namen ungekürzt");
    }

    private static Optional<LayoutCheck.Box> anchor(LayoutCheck.Frame frame, String group, List<String> wanted) {
        Optional<LayoutCheck.Box> box = frame.find(group);
        if (box.isPresent()) {
            return box;
        }
        for (String name : wanted) {
            box = frame.find(name);
            if (box.isPresent()) {
                return box;
            }
        }
        return Optional.empty();
    }

    private static void restore() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        Shots.restoreWindow(mc);
        Shots.restoreLanguage(mc);
    }
}
