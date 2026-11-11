package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.input.CamKeyDefaults;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CovCamBKeySteps {
    public static final String ADDONS_PAGE = "tasten: Seite Add-ons im Tab Modi";
    public static final String DEFAULTS = "tasten: jede Standardtaste löst genau ihre Funktion aus";
    public static final String PROPWASH_KEYS = "tasten: Propwash-Tasten lösen keine Kamera-Funktion aus";
    public static final String LETTERS = "tasten: Belegung auf Buchstaben";
    public static final String SERVER_SWITCHES = "tasten: Schalter melden jeden Wechsel genau einmal an den Server";
    public static final String MODES_TAB = "modes";
    public static final String PAGE_KEY = "propwash.input.modes.page.addons";
    public static final String AXIS_KEY = "propwash.input.modes.axis";
    public static final List<String> DESIGN = List.of("mode_normal", "mode_cine", "mode_sport", "mode_cycle", "pause", "mission_start",
            "waypoint_add", "follow_me", "poi_here", "course_lock", "gimbal_lock", "gimbal_recenter", "gimbal_tilt", "gimbal_pan", "zoom");
    public static final List<Integer> PROPWASH_NUMPAD = List.of(InputConstants.KEY_NUMPAD0, InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2,
            InputConstants.KEY_NUMPAD3, InputConstants.KEY_NUMPAD4, InputConstants.KEY_NUMPAD5, InputConstants.KEY_NUMPAD6, InputConstants.KEY_NUMPAD7,
            InputConstants.KEY_NUMPAD8, InputConstants.KEY_NUMPAD9, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_ADD);
    public static final List<Integer> CANDIDATES = List.of(InputConstants.KEY_B, InputConstants.KEY_C, InputConstants.KEY_G, InputConstants.KEY_H,
            InputConstants.KEY_I, InputConstants.KEY_N, InputConstants.KEY_O, InputConstants.KEY_P, InputConstants.KEY_V, InputConstants.KEY_X,
            InputConstants.KEY_J, InputConstants.KEY_L, InputConstants.KEY_K, InputConstants.KEY_M, InputConstants.KEY_U, InputConstants.KEY_Y,
            InputConstants.KEY_Z, InputConstants.KEY_R, InputConstants.KEY_T, InputConstants.KEY_F, InputConstants.KEY_Q, InputConstants.KEY_E,
            InputConstants.KEY_SEMICOLON, InputConstants.KEY_APOSTROPHE, InputConstants.KEY_LBRACKET, InputConstants.KEY_RBRACKET,
            InputConstants.KEY_MINUS, InputConstants.KEY_EQUALS, InputConstants.KEY_BACKSLASH, InputConstants.KEY_GRAVE);
    private static final String NOTES = "covcamb.notes";
    private static final String INDEX = "covcamb.index";
    private static final String BEFORE = "covcamb.before";
    private static final String DURING = "covcamb.during";
    private static final String DOWN = "covcamb.down";
    private static final String LABEL = "covcamb.label";
    private static final String MARK = "covcamb.mark";
    private static final String SEEN = "covcamb.seen";
    private static final String CONFLICT = "covcamb.conflict";
    private static final int HOLD = 3;
    private static final int STEP_TICKS = 20 * 180;

    private CovCamBKeySteps() {
    }

    record Press(InputConstants.Key key, CamControl control, CamKeyDefaults.Role role) {
    }

    static void addonsPage() {
        Script script = Script.create().append(CovCamBKit.wide());
        for (String language : List.of(Shots.ENGLISH, Shots.GERMAN)) {
            script.run("Sprache " + language, () -> Shots.language(Minecraft.getInstance(), language))
                    .then("Tab Modi öffnen", (ctx, ticks) -> OsdSteps.openTab(ctx, ticks, MODES_TAB))
                    .then("Seite Add-ons", CovCamBKeySteps::addonsPageOpen)
                    .then("Texte der Seite " + language, (ctx, ticks) -> addonsTexts(ctx, ticks, language))
                    .append(Shots.enabled() ? Script.create().then("Bildschirmfoto", (ctx, ticks) -> Shots.shot(ctx, ticks, CovCamBKit.PHOTO
                            + "modes_addons_" + language)) : Script.create())
                    .run("Tab schließen", () -> Minecraft.getInstance().gui.setScreen(null));
        }
        script.run("Sprache zurück", () -> Shots.restoreLanguage(Minecraft.getInstance()))
                .then("beide Sprachen", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx))));
        SelfTest.clientStep(501, ADDONS_PAGE, STEP_TICKS, script);
    }

    @SuppressWarnings("unchecked")
    private static List<String> notes(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(NOTES, key -> new ArrayList<String>());
    }

    private static StepResult addonsPageOpen(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Tab Modi geschlossen");
        }
        String page = I18n.get(PAGE_KEY);
        if (ticks % 10 == 2) {
            SelfTestRunner.clickWidget(screen, page);
        }
        LayoutCheck.Frame frame = LayoutCheck.capture(mc, screen);
        String first = I18n.get(CamControl.MODE_NORMAL.nameKey());
        if (ticks >= 6 && frame.shows(first)) {
            return StepResult.pass();
        }
        return ticks > 80 ? StepResult.fail("Seite \"" + page + "\" zeigt die Kamera-Funktionen nicht: " + frame.values()) : StepResult.pending();
    }

    private static StepResult addonsTexts(SelfTest.StepContext ctx, int ticks, String language) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Tab Modi geschlossen");
        }
        if (ticks < 4) {
            return StepResult.pending();
        }
        LayoutCheck.Frame frame = LayoutCheck.capture(mc, screen);
        List<String> problems = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String path : DESIGN) {
            CamControl control = CamControl.byPath(path).orElse(null);
            if (control == null) {
                problems.add(path + " nicht angemeldet");
                continue;
            }
            String name = I18n.get(control.nameKey());
            names.add(name);
            if (name.equals(control.nameKey())) {
                problems.add("kein Text für " + control.nameKey());
            } else if (!frame.shows(name) && !shownShortened(frame, name)) {
                problems.add("\"" + name + "\" fehlt");
            }
        }
        for (String value : frame.values()) {
            if (value.contains("camdrones.")) {
                problems.add("roher Schlüssel \"" + value + "\"");
            }
        }
        String axisLabel = I18n.get(AXIS_KEY);
        int axes = 0;
        for (CamControl control : CamControl.axes()) {
            String name = I18n.get(control.nameKey());
            Optional<LayoutCheck.Box> row = frame.find(name);
            if (row.isEmpty()) {
                row = shortened(frame, name);
            }
            if (row.isEmpty()) {
                continue;
            }
            LayoutCheck.Box box = row.get();
            MouseButtonEvent click = new MouseButtonEvent(box.left() + 2.0, (box.top() + box.bottom()) / 2.0,
                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
            screen.mouseClicked(click, false);
            screen.mouseReleased(click);
            LayoutCheck.Frame selected = LayoutCheck.capture(mc, screen);
            if (selected.shows(axisLabel)) {
                axes++;
            } else {
                problems.add("Achse \"" + name + "\" ohne \"" + axisLabel + "\"");
            }
        }
        if (!problems.isEmpty()) {
            if (ticks < 40) {
                return StepResult.pending();
            }
            ctx.log("Texte der Seite: " + frame.values());
            return StepResult.fail(language + ": " + String.join(", ", problems));
        }
        notes(ctx).add(language + ": " + names.size() + " Funktionen aus dem Entwurf mit Namen (" + String.join(", ", names) + "), " + axes
                + " Achsen mit \"" + axisLabel + "\", kein roher Schlüssel");
        return StepResult.pass();
    }

    private static boolean shownShortened(LayoutCheck.Frame frame, String name) {
        return shortened(frame, name).isPresent();
    }

    private static Optional<LayoutCheck.Box> shortened(LayoutCheck.Frame frame, String name) {
        for (LayoutCheck.Text text : frame.texts()) {
            String value = text.value();
            if (text.visible() != null && value.endsWith("…") && value.length() > 6 && name.startsWith(value.substring(0, value.length() - 1))) {
                return Optional.of(text.visible());
            }
        }
        return Optional.empty();
    }

    static List<Press> defaults() {
        List<Press> out = new ArrayList<>();
        for (CamKeyDefaults.Entry entry : CamKeyDefaults.ALL) {
            if (entry.bound()) {
                out.add(new Press(CovCamBKit.key(entry.defaultKey()), entry.control(), entry.role()));
            }
        }
        return out;
    }

    static void defaultKeys() {
        SelfTest.clientStep(502, DEFAULTS, STEP_TICKS, Script.create()
                .append(Pilot.launch(CamPresets.TOUR, -24, -60, 0.0F))
                .append(Flight.takeoff(5.0))
                .then("Standardtasten einzeln", (ctx, ticks) -> sweep(ctx, ticks, defaults(), true)));
    }

    static void propwashKeys() {
        SelfTest.clientStep(503, PROPWASH_KEYS, STEP_TICKS, Script.create()
                .append(Pilot.launch(CamPresets.TOUR, -36, -60, 0.0F))
                .then("Ziffernblock 0–9, Enter und + je zweimal", (ctx, ticks) -> sweep(ctx, ticks, propwashPresses(), false))
                .append(Flight.takeoff(3.0))
                .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx)) + "; danach steigt die Drohne normal (Propwash-Schalter "
                        + "durch den zweiten Druck zurück)")));
    }

    private static List<Press> propwashPresses() {
        List<Press> out = new ArrayList<>();
        for (int code : PROPWASH_NUMPAD) {
            out.add(new Press(CovCamBKit.key(code), null, null));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static StepResult sweep(SelfTest.StepContext ctx, int ticks, List<Press> presses, boolean own) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(INDEX, 0);
        if (index >= presses.size()) {
            return StepResult.pass(String.join("; ", notes(ctx)));
        }
        if (!PropwashClientApi.input().flying()) {
            return StepResult.fail("FPV-Ansicht verlassen bei Taste " + (index + 1));
        }
        Press press = presses.get(index);
        int start = (int) state.computeIfAbsent(MARK, key -> ticks);
        int t = ticks - start;
        String name = press.key().getDisplayName().getString();
        if (t == 0) {
            state.put(BEFORE, CovCamBKit.controls());
            state.put(LABEL, Pilot.status().map(NavStatus::label).orElse(""));
            List<KeyMapping> bound = CovCamBKit.boundTo(press.key());
            state.put(DOWN, bound);
            CovCamBKit.down(press.key(), true);
            return StepResult.pending();
        }
        if (t == HOLD - 1) {
            state.put(DURING, CovCamBKit.controls());
            state.put(SEEN, CovCamBKit.propwashDown());
        }
        if (t == HOLD) {
            CovCamBKit.down(press.key(), false);
        }
        if (!own && t == HOLD + 6) {
            CovCamBKit.down(press.key(), true);
        }
        if (!own && t == 2 * HOLD + 6) {
            CovCamBKit.down(press.key(), false);
        }
        if (t < (own ? HOLD + 12 : 2 * HOLD + 18)) {
            return StepResult.pending();
        }
        Map<CamControl, String> before = (Map<CamControl, String>) state.get(BEFORE);
        Map<CamControl, String> during = (Map<CamControl, String>) state.get(DURING);
        Map<CamControl, String> after = CovCamBKit.controls();
        List<CamControl> changed = CovCamBKit.changed(before, during);
        for (CamControl control : CovCamBKit.changed(before, after)) {
            if (!changed.contains(control) && !control.axis()) {
                changed.add(control);
            }
        }
        Set<String> propwash = (Set<String>) state.get(SEEN);
        List<KeyMapping> bound = (List<KeyMapping>) state.get(DOWN);
        List<String> boundNames = bound.stream().map(KeyMapping::getName).toList();
        String label = (String) state.get(LABEL);
        String labelAfter = Pilot.status().map(NavStatus::label).orElse("");
        if (own) {
            List<CamControl> want = List.of(press.control());
            if (!changed.equals(want)) {
                return StepResult.fail(name + ": geändert " + changed + " statt nur " + press.control() + " (vorher " + before + ", gedrückt " + during + ")");
            }
            if (!propwash.isEmpty()) {
                return StepResult.fail(name + ": löst auch Propwash-Tasten aus: " + propwash);
            }
            if (bound.size() != 1) {
                return StepResult.fail(name + ": auf " + boundNames + " belegt");
            }
            String effect = effect(press, before, during, label, labelAfter);
            if (effect.startsWith("FAIL ")) {
                return StepResult.fail(name + ": " + effect.substring(5));
            }
            notes(ctx).add(name + " → " + press.control().path() + effect);
        } else {
            if (!changed.isEmpty()) {
                return StepResult.fail(name + ": ändert Kamera-Funktionen " + changed);
            }
            List<String> camera = boundNames.stream().filter(n -> n.startsWith("key.camdrones.")).toList();
            if (!camera.isEmpty()) {
                return StepResult.fail(name + ": auch auf Kamera-Tasten " + camera);
            }
            List<String> wanted = boundNames.stream().filter(n -> n.startsWith("key.propwash.")).toList();
            if (wanted.isEmpty()) {
                return StepResult.fail(name + ": keine Propwash-Taste belegt (" + boundNames + ")");
            }
            if (!propwash.containsAll(wanted)) {
                return StepResult.fail(name + ": Propwash-Taste " + wanted + " nicht gedrückt (gedrückt " + propwash + ")");
            }
            notes(ctx).add(name + " → " + String.join("/", wanted.stream().map(n -> n.substring("key.propwash.".length())).toList()));
        }
        state.put(INDEX, index + 1);
        state.remove(MARK);
        return StepResult.pending();
    }

    private static String effect(Press press, Map<CamControl, String> before, Map<CamControl, String> during, String label, String labelAfter) {
        CamControl control = press.control();
        if (control.axis()) {
            double from = Double.parseDouble(before.get(control));
            double to = Double.parseDouble(during.get(control));
            boolean up = press.role() == CamKeyDefaults.Role.INCREASE;
            if (up ? to <= from : to >= from) {
                return "FAIL Achse " + control.path() + " geht von " + from + " nach " + to;
            }
            return " (Achse " + from + " → " + to + ")";
        }
        if (!"an".equals(during.get(control))) {
            return "FAIL " + control.path() + " nicht aktiv beim Drücken";
        }
        if (control == CamControl.MODE_CYCLE && label.equals(labelAfter)) {
            return "FAIL Modus bleibt " + label;
        }
        if (control == CamControl.MODE_CYCLE) {
            return " (Modus " + label + " → " + labelAfter + ")";
        }
        if (control == CamControl.PAUSE) {
            boolean holding = Pilot.status().map(s -> s.function() == NavLogic.Function.HOLD).orElse(false);
            return holding ? " (Halten)" : "FAIL Pause hält nicht: " + Pilot.status().map(NavStatus::function).orElse(null);
        }
        return "";
    }

    static void letters() {
        Script script = Script.create()
                .then("Kamera-Funktionen auf freie Tasten legen", (ctx, ticks) -> rebind(ctx, lettersOrder(), defaults().size()))
                .append(Pilot.launch(CamPresets.TOUR, -48, -60, 0.0F))
                .then("Zustand vor dem Start", (ctx, ticks) -> {
                    ctx.log("vor dem Start: " + Pilot.status().map(s -> s.function() + "/" + s.label() + "/atti " + s.atti() + "/" + s.warnings())
                            .orElse("kein Status") + ", Schalter " + CovCamBKit.controls());
                    return StepResult.pass();
                })
                .append(Flight.takeoff(5.0))
                .then("jede Taste drücken", CovCamBKeySteps::letterSweep)
                .then("absichtlich eine Propwash-Taste wählen", CovCamBKeySteps::conflict);
        if (Shots.enabled()) {
            script.server("Flug beenden", (server, state) -> Pilot.release(server))
                    .await("FPV beendet", 200, () -> !PropwashClientApi.input().flying())
                    .append(CovCamBKit.wide())
                    .then("Tastenbelegung öffnen", CovCamBKeySteps::openKeyBinds)
                    .then("Bildschirmfoto Tastenbelegung", (ctx, ticks) -> Shots.shot(ctx, ticks, CovCamBKit.PHOTO + "keybinds_letters_conflict"))
                    .run("Tastenbelegung schließen", () -> Minecraft.getInstance().gui.setScreen(null));
        }
        script.then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", notes(ctx))));
        SelfTest.clientStep(504, LETTERS, STEP_TICKS, script, CovCamBKeySteps::restoreKeys);
    }

    private static final Map<KeyMapping, InputConstants.Key> RESTORE = new LinkedHashMap<>();

    private static final String REBOUND = "covcamb.rebound";

    static List<CamKeyDefaults.Entry> lettersOrder() {
        List<CamKeyDefaults.Entry> out = new ArrayList<>();
        for (CamKeyDefaults.Entry entry : CamKeyDefaults.ALL) {
            if (entry.bound()) {
                out.add(entry);
            }
        }
        for (CamKeyDefaults.Entry entry : CamKeyDefaults.ALL) {
            if (!entry.bound() && entry.control().kind() == CamControl.Kind.SWITCH) {
                out.add(entry);
            }
        }
        for (CamKeyDefaults.Entry entry : CamKeyDefaults.ALL) {
            if (!out.contains(entry)) {
                out.add(entry);
            }
        }
        return out;
    }

    static List<CamKeyDefaults.Entry> gamepadOrder() {
        List<CamKeyDefaults.Entry> out = new ArrayList<>();
        for (CamControl control : List.of(CamControl.FOLLOW_ME, CamControl.COURSE_LOCK, CamControl.GIMBAL_RECENTER)) {
            CamKeyDefaults.find(control, CamKeyDefaults.Role.PRESS).ifPresent(out::add);
        }
        return out;
    }

    private static StepResult rebind(SelfTest.StepContext ctx, List<CamKeyDefaults.Entry> wanted, int minimum) {
        Minecraft mc = Minecraft.getInstance();
        List<KeyMapping> own = CamKeys.mappings();
        List<InputConstants.Key> free = new ArrayList<>();
        List<String> taken = new ArrayList<>();
        for (int code : CANDIDATES) {
            InputConstants.Key key = CovCamBKit.key(code);
            String user = null;
            for (KeyMapping other : mc.options.keyMappings) {
                if (!own.contains(other) && !other.isUnbound() && other.saveString().equals(key.getName())) {
                    user = other.getName();
                }
            }
            if (user == null) {
                free.add(key);
            } else if (code <= InputConstants.KEY_Z) {
                taken.add(key.getDisplayName().getString() + "=" + user.replace("key.", ""));
            }
        }
        if (free.size() < minimum) {
            return StepResult.fail("nur " + free.size() + " freie Tasten für mindestens " + minimum + " Kamera-Funktionen: " + free);
        }
        RESTORE.clear();
        List<CamKeyDefaults.Entry> rebound = new ArrayList<>();
        List<String> letters = new ArrayList<>();
        for (int i = 0; i < wanted.size() && i < free.size(); i++) {
            CamKeyDefaults.Entry entry = wanted.get(i);
            KeyMapping mapping = KeyMapping.get(entry.translationKey());
            if (mapping == null) {
                return StepResult.fail(entry.translationKey() + " fehlt");
            }
            RESTORE.put(mapping, InputConstants.getKey(mapping.saveString()));
            mapping.setKey(free.get(i));
            rebound.add(entry);
            letters.add(entry.name() + "=" + free.get(i).getDisplayName().getString());
        }
        KeyMapping.resetMapping();
        ctx.state().put(REBOUND, rebound);
        ctx.log("neu belegt: " + String.join(" ", letters) + ", Steuerung " + PropwashClientApi.input().switchActive(CamControl.MODE_NORMAL.id()));
        notes(ctx).add("freie Tasten ohne Ziffernblock: " + free.size() + " (" + String.join(" ", free.stream().map(k -> k.getDisplayName().getString())
                .toList()) + "), alle Buchstaben belegt (" + String.join(", ", taken) + "); " + rebound.size() + " von " + wanted.size()
                + " Kamera-Tasten neu belegt: " + String.join(" ", letters));
        return StepResult.pass();
    }

    @SuppressWarnings("unchecked")
    private static StepResult letterSweep(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Map<String, Object> state = ctx.state();
        List<CamKeyDefaults.Entry> entries = (List<CamKeyDefaults.Entry>) state.get(REBOUND);
        int index = (int) state.getOrDefault(INDEX, 0);
        if (index >= entries.size()) {
            state.remove(INDEX);
            notes(ctx).add("alle " + entries.size() + " neu belegten Kamera-Tasten wirken");
            return StepResult.pass();
        }
        CamKeyDefaults.Entry entry = entries.get(index);
        KeyMapping mapping = KeyMapping.get(entry.translationKey());
        if (mapping == null) {
            return StepResult.fail(entry.translationKey() + " fehlt");
        }
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        int start = (int) state.computeIfAbsent(MARK, k -> ticks);
        int t = ticks - start;
        boolean toggle = entry.control().toggle();
        if (t == 0) {
            state.put(BEFORE, CovCamBKit.controls());
            CovCamBKit.down(key, true);
            return StepResult.pending();
        }
        if (t == HOLD - 1) {
            state.put(DURING, CovCamBKit.controls());
        }
        if (t == HOLD) {
            CovCamBKit.down(key, false);
        }
        if (toggle && t == HOLD + 10) {
            CovCamBKit.down(key, true);
        }
        if (toggle && t == HOLD + 13) {
            CovCamBKit.down(key, false);
        }
        if (t < (toggle ? HOLD + 26 : HOLD + 10)) {
            return StepResult.pending();
        }
        Map<CamControl, String> before = (Map<CamControl, String>) state.get(BEFORE);
        Map<CamControl, String> during = (Map<CamControl, String>) state.get(DURING);
        Map<CamControl, String> after = CovCamBKit.controls();
        List<CamControl> changed = CovCamBKit.changed(before, during);
        String name = key.getDisplayName().getString();
        if (!changed.equals(List.of(entry.control()))) {
            return StepResult.fail(name + " (" + entry.translationKey() + "): geändert " + changed + " statt " + entry.control());
        }
        if (toggle && !before.get(entry.control()).equals(after.get(entry.control()))) {
            return StepResult.fail(name + ": Schalter " + entry.control() + " rastet beim zweiten Druck nicht aus (" + after.get(entry.control()) + ")");
        }
        if (!toggle && !entry.control().axis() && !"aus".equals(after.get(entry.control()))) {
            return StepResult.fail(name + ": Taster " + entry.control() + " bleibt nach dem Loslassen aktiv");
        }
        state.put(INDEX, index + 1);
        state.remove(MARK);
        return StepResult.pending();
    }

    private static StepResult conflict(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping arm = KeyMapping.get(Pilot.ARM);
        KeyMapping mine = CamKeys.key(CamControl.COURSE_LOCK).orElse(null);
        if (arm == null || mine == null) {
            return StepResult.fail("Taste " + Pilot.ARM + " oder Course Lock fehlt");
        }
        InputConstants.Key taken = InputConstants.getKey(arm.saveString());
        RESTORE.putIfAbsent(mine, InputConstants.getKey(mine.saveString()));
        mine.setKey(taken);
        KeyMapping.resetMapping();
        List<String> clashes = new ArrayList<>();
        for (KeyMapping other : mc.options.keyMappings) {
            if (other != mine && !other.isUnbound() && other.same(mine)) {
                clashes.add(other.getName());
            }
        }
        ctx.state().put(CONFLICT, clashes);
        if (!clashes.contains(Pilot.ARM)) {
            return StepResult.fail("Course Lock auf " + taken.getDisplayName().getString() + " zeigt keinen Konflikt mit " + Pilot.ARM + " (" + clashes + ")");
        }
        notes(ctx).add("Course Lock auf " + taken.getDisplayName().getString() + " (" + I18n.get(arm.getName()) + "): Konflikt mit " + clashes
                + " erkannt");
        return StepResult.pass();
    }

    private static StepResult openKeyBinds(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            mc.gui.setScreen(new KeyBindsScreen(null, mc.options));
            return StepResult.pending();
        }
        Screen screen = mc.gui.screen();
        if (!(screen instanceof KeyBindsScreen)) {
            return ticks > 40 ? StepResult.fail("Tastenbelegung öffnet nicht") : StepResult.pending();
        }
        String category = CamKeys.key(CamControl.COURSE_LOCK).map(k -> k.getCategory().label().getString()).orElse("");
        String course = I18n.get(CamKeys.key(CamControl.COURSE_LOCK).map(KeyMapping::getName).orElse(""));
        LayoutCheck.Frame frame = LayoutCheck.capture(mc, screen);
        if (frame.shows(course)) {
            for (int i = 0; i < 4; i++) {
                screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0.0, -1.0);
            }
            return StepResult.pass();
        }
        screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0.0, -1.0);
        return ticks > 400 ? StepResult.fail("Kategorie \"" + category + "\" mit \"" + course + "\" nicht erreicht") : StepResult.pending();
    }

    private static StepResult restoreKeys(SelfTest.StepContext ctx) {
        for (Map.Entry<KeyMapping, InputConstants.Key> entry : RESTORE.entrySet()) {
            entry.getKey().setKey(entry.getValue());
        }
        RESTORE.clear();
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        return StepResult.pass();
    }

    static void serverSwitches() {
        CovCamBKit.listen();
        SelfTest.clientStep(505, SERVER_SWITCHES, STEP_TICKS, Script.create()
                .then("Follow me, Course Lock und Gimbal zentrieren auf freie Tasten", (ctx, ticks) -> rebind(ctx, gamepadOrder(), 3))
                .append(Pilot.launch(CamPresets.TOUR, -60, -60, 0.0F))
                .append(Flight.takeoff(5.0))
                .then("je dreimal drücken", CovCamBKeySteps::serverPresses)
                .then("Gimbal zentrieren nur solange gedrückt", CovCamBKeySteps::recenterHeld), CovCamBKeySteps::restoreKeys);
    }

    private static final List<CamControl> LATCHING = List.of(CamControl.COURSE_LOCK, CamControl.FOLLOW_ME);

    @SuppressWarnings("unchecked")
    private static StepResult serverPresses(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(INDEX, 0);
        int presses = 3;
        if (index >= LATCHING.size() * presses) {
            state.remove(INDEX);
            return StepResult.pass();
        }
        CamControl control = LATCHING.get(index / presses);
        KeyMapping mapping = CamKeys.key(control).orElseThrow();
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        int start = (int) state.computeIfAbsent(MARK, k -> ticks);
        int t = ticks - start;
        if (t == 0) {
            state.put(BEFORE, PropwashClientApi.input().switchActive(control.id()));
            state.put(SEEN, CovCamBKit.switchMark());
            CovCamBKit.down(key, true);
            return StepResult.pending();
        }
        if (t == HOLD) {
            CovCamBKit.down(key, false);
        }
        if (t < HOLD + 30) {
            return StepResult.pending();
        }
        boolean before = (boolean) state.get(BEFORE);
        boolean after = PropwashClientApi.input().switchActive(control.id());
        List<CovCamBKit.SwitchEvent> events = CovCamBKit.switchEvents((int) state.get(SEEN)).stream()
                .filter(e -> e.id().equals(control.id())).toList();
        if (after == before) {
            return StepResult.fail(control.path() + " Druck " + (index % presses + 1) + ": Schalter bleibt " + (after ? "an" : "aus"));
        }
        if (events.size() != 1 || events.getFirst().active() != after) {
            return StepResult.fail(control.path() + " Druck " + (index % presses + 1) + ": " + events.size() + " Meldungen ADDON_SWITCH_CHANGED am Server ("
                    + events.stream().map(e -> e.active() ? "an" : "aus").toList() + ") statt genau einer mit " + (after ? "an" : "aus"));
        }
        if (index % presses == presses - 1) {
            notes(ctx).add(control.path() + ": 3 Drücke, 3 Wechsel, je genau ein ADDON_SWITCH_CHANGED am Server");
        }
        state.put(INDEX, index + 1);
        state.remove(MARK);
        return StepResult.pending();
    }

    private static StepResult recenterHeld(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Pilot.steer(Pilot.THROTTLE_CENTER);
        KeyMapping mapping = CamKeys.key(CamControl.GIMBAL_RECENTER).orElseThrow();
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        Map<String, Object> state = ctx.state();
        if (ticks == 0) {
            state.put(SEEN, CovCamBKit.switchMark());
            CovCamBKit.down(key, true);
            return StepResult.pending();
        }
        boolean active = PropwashClientApi.input().switchActive(CamControl.GIMBAL_RECENTER.id());
        if (ticks > 2 && ticks < 20 && !active) {
            return StepResult.fail("Gimbal zentrieren nicht aktiv, während die Taste gehalten wird (Tick " + ticks + ")");
        }
        if (ticks == 20) {
            CovCamBKit.down(key, false);
        }
        if (ticks > 26 && active) {
            return StepResult.fail("Gimbal zentrieren bleibt nach dem Loslassen aktiv");
        }
        if (ticks < 40) {
            return StepResult.pending();
        }
        long events = CovCamBKit.switchEvents((int) state.get(SEEN)).stream().filter(e -> e.id().equals(CamControl.GIMBAL_RECENTER.id())).count();
        notes(ctx).add("Gimbal zentrieren aktiv nur in den 20 Ticks mit gedrückter Taste, " + events + " Meldungen am Server (Funktion ohne Server-Sync)");
        return StepResult.pass(String.join("; ", notes(ctx)));
    }

    static Map<CamControl, InputConstants.Key> defaultsByControl() {
        Map<CamControl, InputConstants.Key> out = new EnumMap<>(CamControl.class);
        for (Press press : defaults()) {
            out.putIfAbsent(press.control(), press.key());
        }
        return out;
    }
}
