package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.hud.HudSettingsKeys;
import de.lardtom.camdrones.client.hud.LimitChime;
import de.lardtom.camdrones.client.hud.LimitTone;
import de.lardtom.camdrones.client.hud.SpeedUnit;
import de.lardtom.camdrones.client.planner.CamClientConfig;
import de.lardtom.camdrones.client.planner.CamSettingsTab;
import de.lardtom.camdrones.client.record.model.RecordingMode;
import de.lardtom.camdrones.crew.CrewTexts;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.part.CamPresets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

public final class HudSettingsSteps {
    public static final String SETTINGS = "hud: Tempo-Einheit, Warnton-Lautstärke und Gimbal-Surren in den Einstellungen bei GUI 320×240 in EN und DE";
    public static final String LIMIT_TONE = "töne: Warnton an der Höhengrenze in der eingestellten Lautstärke";
    public static final int SETTINGS_NUMBER = 47;
    public static final int LIMIT_NUMBER = 48;
    public static final double TEST_VOLUME = 0.35;
    public static final double TEST_SERVO = 0.0;
    public static final List<String> SERVO_TIP_NOW = List.of("recenters", "zentriert");
    public static final List<String> SERVO_TIP_BEFORE = List.of("keeps moving", "solange sich der Gimbal bewegt");
    private static final int GUI_SCALE = 2;
    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 240;
    private static final int SETTLE_TICKS = 10;
    private static final int STEP_TICKS = 20 * 180;
    private static final String FINDINGS = "hudsettings.findings";
    private static final String CLEAR = "hudsettings.clear";
    private static final List<Played> PLAYED = new CopyOnWriteArrayList<>();
    private static final SoundEventListener LISTENER = HudSettingsSteps::played;
    private static boolean listening;

    record Played(String id, float volume, long time) {
    }

    private HudSettingsSteps() {
    }

    public static void register() {
        SelfTest.clientStep(SETTINGS_NUMBER, SETTINGS, STEP_TICKS, Script.create()
                .run("Standardwerte", HudSettingsSteps::defaults)
                .append(layout(Shots.ENGLISH))
                .append(layout(Shots.GERMAN))
                .then("km/h, 35 % und Gimbal-Surren 0 % wählen, Strg+S", HudSettingsSteps::chooseAndSave)
                .then("Tab wieder öffnen", (ctx, ticks) -> OsdSteps.openTab(ctx, ticks, CamSettingsTab.ID))
                .then("Tab zeigt km/h, 35 % und 0 %", (ctx, ticks) -> reopened(ctx, ticks, SpeedUnit.KILOMETRES_PER_HOUR, TEST_VOLUME))
                .then("m/s wählen und verwerfen", HudSettingsSteps::chooseAndDiscard)
                .then("Tab noch einmal öffnen", (ctx, ticks) -> OsdSteps.openTab(ctx, ticks, CamSettingsTab.ID))
                .then("Standardwerte und speichern", HudSettingsSteps::resetAndSave)
                .run("Fenster und Sprache zurück", HudSettingsSteps::restore)
                .then("Ergebnis", (ctx, ticks) -> result(ctx)), ctx -> {
                    restore();
                    defaults();
                    return StepResult.pass();
                });
        SelfTest.clientStep(LIMIT_NUMBER, LIMIT_TONE, STEP_TICKS, Script.create()
                .run("Tonliste starten, Lautstärke 35 %", () -> {
                    listen();
                    CamClientConfig.get().warningVolume(TEST_VOLUME);
                })
                .append(CovCamBKit.goTo(-120, 30))
                .append(Pilot.launch(CamPresets.TOUR, -120, 30, 0.0F))
                .server("Höhengrenze 6 m", (server, state) -> Arena.rule(server, CamWorldRules.MAX_ALTITUDE_ID, "6"))
                .append(Flight.gpsFix())
                .run("Marke", () -> PLAYED.clear())
                .then("Vollgas bis zur Höhengrenze", HudSettingsSteps::climbToLimit)
                .then("3 s gegen die Grenze drücken: genau ein Ton in 35 %", (ctx, ticks) -> pushAgainst(ctx, ticks, 1, TEST_VOLUME))
                .then("sinken, bis die Warnung " + LimitChime.REARM + " s weg ist", HudSettingsSteps::sink)
                .then("wieder bis zur Grenze", HudSettingsSteps::climbToLimit)
                .then("zweiter Ton beim zweiten Erreichen", (ctx, ticks) -> pushAgainst(ctx, ticks, 2, TEST_VOLUME))
                .run("Lautstärke 0 %", () -> CamClientConfig.get().warningVolume(0.0))
                .then("sinken", HudSettingsSteps::sink)
                .then("drittes Mal bis zur Grenze", HudSettingsSteps::climbToLimit)
                .then("stumm bei 0 %", (ctx, ticks) -> pushAgainst(ctx, ticks, 2, TEST_VOLUME))
                .then("Ergebnis", (ctx, ticks) -> {
                    Pilot.steer(Pilot.THROTTLE_CENTER);
                    return StepResult.pass("Töne an der Höhengrenze " + describe(limitTones()));
                }), ctx -> {
                    Pilot.releaseKeys();
                    CamClientConfig.get().warningVolume(CamClientConfig.WARNING_VOLUME_DEFAULT);
                    return StepResult.pass();
                });
    }

    private static void defaults() {
        CamClientConfig config = CamClientConfig.get();
        config.speedUnit(CamClientConfig.SPEED_UNIT_DEFAULT);
        config.warningVolume(CamClientConfig.WARNING_VOLUME_DEFAULT);
        config.servoVolume(CamClientConfig.SERVO_VOLUME_DEFAULT);
        config.recordingMode(RecordingMode.DIRECT);
    }

    private static Script layout(String code) {
        String label = code + " bei GUI " + GUI_WIDTH + "x" + GUI_HEIGHT;
        return Script.create()
                .run("Sprache " + code, () -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.gui.setScreen(null);
                    Shots.language(mc, code);
                })
                .then("Fenster " + GUI_WIDTH * GUI_SCALE + "x" + GUI_HEIGHT * GUI_SCALE + " mit GUI-Skala " + GUI_SCALE,
                        (ctx, ticks) -> Shots.window(ctx, ticks, GUI_WIDTH * GUI_SCALE, GUI_HEIGHT * GUI_SCALE, GUI_SCALE))
                .then("Tab Kamera-Drohnen öffnen", (ctx, ticks) -> OsdSteps.openTab(ctx, ticks, CamSettingsTab.ID))
                .then("Einheit und Lautstärke ganz zu sehen (" + label + ")", (ctx, ticks) -> layoutCheck(ctx, ticks, label))
                .then("Bildschirmfoto", (ctx, ticks) -> Shots.shot(ctx, ticks, "hud_settings_" + GUI_WIDTH + "x" + GUI_HEIGHT
                        + (code.equals(Shots.ENGLISH) ? "" : "_de")));
    }

    private static List<String> labels() {
        return List.of(I18n.get(HudSettingsKeys.TITLE), I18n.get(HudSettingsKeys.UNIT), I18n.get(HudSettingsKeys.VOLUME), I18n.get(CrewTexts.SERVO_VOLUME),
                I18n.get("camdrones.planner.settings.preview"), I18n.get("camdrones.planner.title"));
    }

    private static StepResult layoutCheck(SelfTest.StepContext ctx, int ticks, String label) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        CamSettingsTab tab = CamSettingsTab.active();
        if (screen == null || tab == null || tab.unitButton() == null || tab.volumeSlider() == null || tab.servoSlider() == null) {
            return ticks > 60 ? StepResult.fail("Tab " + CamSettingsTab.ID + " ohne Einheit, Lautstärke oder Gimbal-Surren") : StepResult.pending();
        }
        if (screen.width != GUI_WIDTH || screen.height != GUI_HEIGHT) {
            return ticks > 40 ? StepResult.fail("GUI ist " + screen.width + "x" + screen.height) : StepResult.pending();
        }
        if (ticks < SETTLE_TICKS) {
            return StepResult.pending();
        }
        LayoutCheck.Frame frame = LayoutCheck.capture(mc, screen);
        List<String> values = frame.values().stream().map(String::strip).toList();
        List<String> problems = new ArrayList<>();
        for (String text : labels()) {
            if (!frame.shows(text) || !values.contains(text)) {
                problems.add("\"" + text + "\" nicht ganz zu sehen");
            }
        }
        String unit = tab.unitButton().getMessage().getString();
        String volume = tab.volumeSlider().getMessage().getString();
        String servo = tab.servoSlider().getMessage().getString();
        if (!servo.equals(CamSettingsTab.volumeValue(CamClientConfig.SERVO_VOLUME_DEFAULT * 100.0).getString())) {
            problems.add("Gimbal-Surren zeigt \"" + servo + "\" statt 15 %");
        }
        String tip = I18n.get(CrewTexts.SERVO_VOLUME_TOOLTIP);
        if (!SERVO_TIP_NOW.stream().anyMatch(tip::contains) || SERVO_TIP_BEFORE.stream().anyMatch(tip::contains)) {
            problems.add("Tooltip Gimbal-Surren nennt nicht den Ton beim Zentrieren und bei der Übernahme: \"" + tip + "\"");
        }
        if (!unit.equals(I18n.get(SpeedUnit.METRES_PER_SECOND.key()))) {
            problems.add("Einheit zeigt \"" + unit + "\" statt m/s");
        }
        if (!volume.equals(CamSettingsTab.volumeValue(CamClientConfig.WARNING_VOLUME_DEFAULT * 100.0).getString())) {
            problems.add("Lautstärke zeigt \"" + volume + "\" statt 60 %");
        }
        for (AbstractWidget widget : List.of(tab.unitButton(), tab.volumeSlider(), tab.servoSlider())) {
            if (!widget.visible || widget.getX() < 0 || widget.getY() < 0 || widget.getRight() > screen.width || widget.getBottom() > screen.height) {
                problems.add("\"" + widget.getMessage().getString() + "\" außerhalb des Fensters");
            }
        }
        List<String> mine = new ArrayList<>(labels());
        mine.add(unit);
        mine.add(volume);
        mine.add(servo);
        for (String line : LayoutCheck.audit(mc, screen)) {
            if (mine.stream().anyMatch(line::contains)) {
                problems.add(line);
            }
        }
        if (!problems.isEmpty()) {
            ctx.log("Texte im Tab (" + label + "): " + frame.values());
            findings(ctx).add(label + ": " + String.join("; ", problems));
            return StepResult.pass(label + ": " + problems.size() + " Befunde");
        }
        return StepResult.pass(label + ": " + String.join(", ", labels()) + ", \"" + unit + "\", \"" + volume + "\", \"" + servo + "\" ganz zu sehen");
    }

    private static StepResult chooseAndSave(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        CamSettingsTab tab = CamSettingsTab.active();
        if (ticks == 0) {
            if (screen == null || tab == null || tab.unitButton() == null || tab.volumeSlider() == null || tab.servoSlider() == null) {
                return StepResult.fail("Einstellungen geschlossen");
            }
            CovCamAKit.click(screen, tab.unitButton());
            if (tab.speedUnit() != SpeedUnit.KILOMETRES_PER_HOUR || !tab.unitButton().getMessage().getString().equals("km/h")) {
                return StepResult.fail("Klick auf die Einheit zeigt \"" + tab.unitButton().getMessage().getString() + "\"");
            }
            tab.volumeSlider().select(TEST_VOLUME * 100.0);
            if (Math.abs(tab.warningVolume() - TEST_VOLUME) > 1.0E-9) {
                return StepResult.fail("Regler steht auf " + tab.warningVolume());
            }
            tab.servoSlider().select(TEST_SERVO * 100.0);
            if (Math.abs(tab.servoVolume() - TEST_SERVO) > 1.0E-9) {
                return StepResult.fail("Regler Gimbal-Surren steht auf " + tab.servoVolume());
            }
            if (CamClientConfig.get().speedUnit() != SpeedUnit.METRES_PER_SECOND) {
                return StepResult.fail("vor dem Speichern schon gespeichert");
            }
            screen.keyPressed(new KeyEvent(InputConstants.KEY_S, 0, InputConstants.MOD_CONTROL));
            return StepResult.pending();
        }
        CamClientConfig config = CamClientConfig.get();
        if (mc.gui.screen() == null && config.speedUnit() == SpeedUnit.KILOMETRES_PER_HOUR && Math.abs(config.warningVolume() - TEST_VOLUME) < 1.0E-9
                && Math.abs(config.servoVolume() - TEST_SERVO) < 1.0E-9) {
            return StepResult.pass("gespeichert: speed_unit=km/h, warning_volume=" + config.warningVolume() + ", gimbal_servo_volume=" + config.servoVolume());
        }
        return ticks > 40 ? StepResult.fail("nicht gespeichert (" + SelfTestRunner.screenName(mc) + ", " + config.speedUnit() + ", " + config.warningVolume() + ")")
                : StepResult.pending();
    }

    private static StepResult reopened(SelfTest.StepContext ctx, int ticks, SpeedUnit unit, double volume) {
        CamSettingsTab tab = CamSettingsTab.active();
        if (tab == null || tab.unitButton() == null || tab.volumeSlider() == null) {
            return ticks > 100 ? StepResult.fail("Tab öffnet nicht") : StepResult.pending();
        }
        String shownUnit = tab.unitButton().getMessage().getString();
        String shownVolume = tab.volumeSlider().getMessage().getString();
        String wantedVolume = CamSettingsTab.volumeValue(volume * 100.0).getString();
        String shownServo = tab.servoSlider() == null ? "" : tab.servoSlider().getMessage().getString();
        String wantedServo = CamSettingsTab.volumeValue(TEST_SERVO * 100.0).getString();
        if (shownUnit.equals(I18n.get(unit.key())) && shownVolume.equals(wantedVolume) && shownServo.equals(wantedServo)) {
            return StepResult.pass("\"" + shownUnit + "\", \"" + shownVolume + "\", \"" + shownServo + "\"");
        }
        return ticks > 100 ? StepResult.fail("Tab zeigt \"" + shownUnit + "\", \"" + shownVolume + "\" statt " + unit.symbol() + ", " + wantedVolume)
                : StepResult.pending();
    }

    private static StepResult chooseAndDiscard(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        CamSettingsTab tab = CamSettingsTab.active();
        if (ticks == 0) {
            if (screen == null || tab == null || tab.unitButton() == null) {
                return StepResult.fail("Einstellungen geschlossen");
            }
            CovCamAKit.click(screen, tab.unitButton());
            if (tab.speedUnit() != SpeedUnit.METRES_PER_SECOND) {
                return StepResult.fail("Klick auf die Einheit zeigt \"" + tab.unitButton().getMessage().getString() + "\"");
            }
            return StepResult.pending();
        }
        if (screen != null && ticks % 10 == 0 && ticks <= 40) {
            screen.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
        }
        if (screen == null && ticks > 5) {
            SpeedUnit stored = CamClientConfig.get().speedUnit();
            return stored == SpeedUnit.KILOMETRES_PER_HOUR ? StepResult.pass("verworfen, gespeichert bleibt km/h")
                    : StepResult.fail("Verwerfen hat " + stored + " gespeichert");
        }
        return ticks > 80 ? StepResult.fail("Fenster schließt nicht mit Esc (" + SelfTestRunner.screenName(mc) + ")") : StepResult.pending();
    }

    private static StepResult resetAndSave(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        CamSettingsTab tab = CamSettingsTab.active();
        if (ticks == 0) {
            if (screen == null || tab == null) {
                return StepResult.fail("Tab öffnet nicht");
            }
            tab.resetDefaults();
            if (tab.speedUnit() != SpeedUnit.METRES_PER_SECOND || Math.abs(tab.warningVolume() - CamClientConfig.WARNING_VOLUME_DEFAULT) > 1.0E-9
                    || Math.abs(tab.servoVolume() - CamClientConfig.SERVO_VOLUME_DEFAULT) > 1.0E-9) {
                return StepResult.fail("Standardwerte: " + tab.speedUnit() + ", " + tab.warningVolume() + ", " + tab.servoVolume());
            }
            screen.keyPressed(new KeyEvent(InputConstants.KEY_S, 0, InputConstants.MOD_CONTROL));
            return StepResult.pending();
        }
        CamClientConfig config = CamClientConfig.get();
        if (screen == null && config.speedUnit() == SpeedUnit.METRES_PER_SECOND && Math.abs(config.warningVolume() - CamClientConfig.WARNING_VOLUME_DEFAULT) < 1.0E-9
                && Math.abs(config.servoVolume() - CamClientConfig.SERVO_VOLUME_DEFAULT) < 1.0E-9) {
            return StepResult.pass("Standardwerte gespeichert: m/s, " + config.warningVolume() + ", " + config.servoVolume());
        }
        return ticks > 60 ? StepResult.fail("Standardwerte nicht gespeichert: " + config.speedUnit() + ", " + config.warningVolume()) : StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private static List<String> findings(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(FINDINGS, key -> new ArrayList<String>());
    }

    private static StepResult result(SelfTest.StepContext ctx) {
        List<String> findings = findings(ctx);
        return findings.isEmpty() ? StepResult.pass("EN und DE bei GUI " + GUI_WIDTH + "x" + GUI_HEIGHT + " ganz zu sehen, Speichern und Verwerfen wirken")
                : StepResult.fail(String.join(" | ", findings));
    }

    private static void restore() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        Shots.restoreWindow(mc);
        Shots.restoreLanguage(mc);
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
        if (id != null && CamDrones.MOD_ID.equals(id.getNamespace())) {
            PLAYED.add(new Played(id.getPath(), sound.getVolume(), System.nanoTime()));
        }
    }

    private static List<Played> limitTones() {
        return PLAYED.stream().filter(p -> p.id().equals(LimitTone.SOUND.getPath())).toList();
    }

    private static String describe(List<Played> tones) {
        return tones.size() + " " + tones.stream().map(p -> String.format(Locale.ROOT, "%.2f", p.volume())).toList();
    }

    private static boolean limited() {
        return Pilot.status().map(s -> s.warns(NavOutput.Warning.ALTITUDE_LIMIT)).orElse(false);
    }

    private static StepResult climbToLimit(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(1.0);
        if (limited() && ticks > 5) {
            return StepResult.pass("Höhengrenze nach " + ticks + " Ticks, " + Flight.format(Flight.height()) + " m");
        }
        return ticks > 400 ? StepResult.fail("keine Höhengrenze, " + Flight.format(Flight.height()) + " m") : StepResult.pending();
    }

    private static StepResult pushAgainst(SelfTest.StepContext ctx, int ticks, int expected, double volume) {
        Flight.track(ctx);
        Pilot.steer(1.0);
        if (ticks < 60) {
            return StepResult.pending();
        }
        List<Played> tones = limitTones();
        if (tones.size() != expected) {
            return StepResult.fail("Töne an der Höhengrenze " + describe(tones) + ", erwartet " + expected + " (alle Töne "
                    + PLAYED.stream().map(Played::id).distinct().toList() + ")");
        }
        for (Played tone : tones) {
            if (Math.abs(tone.volume() - volume) > 0.005) {
                return StepResult.fail("Ton in Lautstärke " + tone.volume() + " statt " + volume);
            }
        }
        return StepResult.pass("Töne an der Höhengrenze " + describe(tones));
    }

    private static StepResult sink(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(0.2);
        if (ticks == 0) {
            ctx.state().remove(CLEAR);
        }
        if (limited()) {
            ctx.state().remove(CLEAR);
        } else {
            ctx.state().putIfAbsent(CLEAR, ticks);
        }
        Object since = ctx.state().get(CLEAR);
        if (since != null && ticks - (int) since >= (int) Math.ceil(LimitChime.REARM * 20.0) + 10) {
            return StepResult.pass("ohne Warnung seit " + (ticks - (int) since) + " Ticks, " + Flight.format(Flight.height()) + " m");
        }
        return ticks > 300 ? StepResult.fail("Warnung bleibt beim Sinken") : StepResult.pending();
    }
}
