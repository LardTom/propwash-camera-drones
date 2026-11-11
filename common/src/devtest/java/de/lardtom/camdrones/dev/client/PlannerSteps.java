package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.planner.PlannerScreen;
import de.lardtom.camdrones.client.planner.WaypointPreview;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.model.EditorCatalog;
import de.lardtom.camdrones.planner.model.EditorLayout;
import de.lardtom.camdrones.planner.model.EditorTexts;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.MissionTemplates;
import de.lardtom.camdrones.planner.model.PlannerEntry;
import de.lardtom.camdrones.planner.model.TabletState;
import de.lardtom.camdrones.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class PlannerSteps {
    public static final String EDITOR = "planer: Editor bei 320×240, 640×480, 854×480 in EN und DE ganz sichtbar";
    public static final String CLICKS = "planer: Wegpunkte per Block-Klick, Vorschau-Linie";
    public static final int[][] SIZES = {{320, 240}, {640, 480}, {854, 480}};
    public static final int GUI_SCALE = 2;
    public static final String REPORT_FILE = "camdrones-ui-scale.txt";
    private static final String COMBO = "planer.combo";
    private static final String PHASE = "planer.phase";
    private static final String SINCE = "planer.since";
    private static final String FINDINGS = "planer.findings";
    private static final String CHECKED = "planer.checked";
    private static final String TOOLTIPS = "planer.tooltips";
    private static final String REPORT = "planer.report";
    private static final int[][] CLICKED = {{-3, 3}, {0, 4}, {3, 3}};

    private PlannerSteps() {
    }

    private record Combo(String language, int width, int height, boolean map) {
        String photo() {
            return "planner_editor_" + this.width + "x" + this.height + (this.map ? "_map" : "_list") + (Shots.ENGLISH.equals(this.language) ? "" : "_de");
        }

        String label() {
            return this.language + " " + this.width + "x" + this.height + (this.map ? " mit Karte" : " ohne Karte");
        }
    }

    private static final List<Combo> COMBOS = combos();

    private static List<Combo> combos() {
        List<Combo> out = new ArrayList<>();
        for (String language : List.of(Shots.ENGLISH, Shots.GERMAN)) {
            for (int[] size : SIZES) {
                out.add(new Combo(language, size[0], size[1], true));
                out.add(new Combo(language, size[0], size[1], false));
            }
        }
        return List.copyOf(out);
    }

    public static void register() {
        SelfTest.clientStep(9, EDITOR, 20 * 240, Script.create()
                .server("Tablet mit Mission", PlannerSteps::giveTablet)
                .await("Tablet am Client", 100, () -> held().isPresent())
                .run("Editor öffnen", () -> Minecraft.getInstance().gameMode.useItem(Minecraft.getInstance().player, InteractionHand.MAIN_HAND))
                .await("Editor offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof PlannerScreen)
                .then("Fenstergrößen und Sprachen", PlannerSteps::sweep));
        SelfTest.clientStep(10, CLICKS, 20 * 120, Script.create()
                .append(Shots.photoWindow())
                .server("leeres Tablet", PlannerSteps::giveEmptyTablet)
                .await("Tablet am Client", 100, () -> held().isPresent())
                .then("Blöcke anklicken", PlannerSteps::click)
                .then("Wegpunkte im Tablet", PlannerSteps::waypoints)
                .server("zurücktreten", PlannerSteps::stepBack)
                .then("Vorschau-Linie", PlannerSteps::preview)
                .append(Shots.photos("planner_waypoints_preview")));
    }

    private static Optional<ItemStack> held() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return Optional.empty();
        }
        ItemStack stack = mc.player.getMainHandItem();
        return PlannerItems.isPlanner(stack) ? Optional.of(stack) : Optional.empty();
    }

    static Mission sampleMission() {
        Vec3 spawn = Arena.spawn();
        return MissionTemplates.apply(MissionTemplates.Template.CIRCLE, Mission.empty(),
                new MissionTemplates.Anchor(spawn.x, spawn.y, spawn.z + 30.0, 0.0)).orElseThrow();
    }

    private static void giveTablet(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ItemStack tablet = new ItemStack(PlannerItems.flightPlanner().orElseThrow(() -> new IllegalStateException("Tablet fehlt")));
        TabletState missions = new TabletState(List.of(PlannerEntry.of("Turm von oben", sampleMission()), PlannerEntry.empty("Leer")), 0,
                TabletState.DEFAULT_GROUND_HEIGHT);
        PlannerTablets.write(tablet, missions);
        player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
        player.teleportTo(player.level(), Arena.spawn().x, Arena.spawn().y, Arena.spawn().z, Set.<Relative>of(), 0.0F, -60.0F, true);
    }

    private static void giveEmptyTablet(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ItemStack tablet = new ItemStack(PlannerItems.flightPlanner().orElseThrow(() -> new IllegalStateException("Tablet fehlt")));
        player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
        player.teleportTo(player.level(), Arena.spawn().x, Arena.spawn().y, Arena.spawn().z, Set.<Relative>of(), 0.0F, 40.0F, true);
    }

    private static StepResult sweep(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(COMBO, 0);
        @SuppressWarnings("unchecked")
        List<String> findings = (List<String>) state.computeIfAbsent(FINDINGS, key -> new ArrayList<String>());
        @SuppressWarnings("unchecked")
        List<String> report = (List<String>) state.computeIfAbsent(REPORT, key -> new ArrayList<String>());
        if (index >= COMBOS.size()) {
            Shots.restoreLanguage(mc);
            writeReport(report);
            if (!findings.isEmpty()) {
                return StepResult.fail(findings.size() + " Befunde: " + String.join(" | ", findings));
            }
            return StepResult.pass(state.getOrDefault(CHECKED, 0) + " Ansichten (3 Größen × Karte an/aus × EN/DE) ohne Befund, "
                    + state.getOrDefault(TOOLTIPS, 0) + " Tooltips ganz im Bild");
        }
        Combo combo = COMBOS.get(index);
        int phase = (int) state.getOrDefault(PHASE, 0);
        int since = (int) state.computeIfAbsent(SINCE, key -> ticks);
        int elapsed = ticks - since;
        if (!(mc.gui.screen() instanceof PlannerScreen screen)) {
            return StepResult.fail("Editor geschlossen bei " + combo.label());
        }
        switch (phase) {
            case 0 -> {
                if (!combo.language().equals(mc.getLanguageManager().getSelected())) {
                    Shots.language(mc, combo.language());
                }
                StepResult sized = Shots.window(ctx, elapsed, combo.width() * GUI_SCALE, combo.height() * GUI_SCALE, GUI_SCALE);
                if (!sized.done()) {
                    return sized;
                }
                if (sized.failed()) {
                    return sized;
                }
                next(state, ticks, 1);
            }
            case 1 -> {
                if (screen.width != combo.width() || screen.height != combo.height()) {
                    return elapsed > 40 ? StepResult.fail("GUI ist " + screen.width + "x" + screen.height + " statt " + combo.width() + "x"
                            + combo.height()) : StepResult.pending();
                }
                if (screen.state().mapExpanded() != combo.map()) {
                    String toggle = I18n.get(FieldText.PREFIX + (screen.state().mapExpanded() ? "map.hide" : "map.show"));
                    if (!SelfTestRunner.clickWidget(screen, toggle)) {
                        return StepResult.fail("Knopf \"" + toggle + "\" fehlt bei " + combo.label());
                    }
                }
                next(state, ticks, 2);
            }
            case 2 -> {
                if (elapsed < 4) {
                    return StepResult.pending();
                }
                if (screen.state().mapExpanded() != combo.map()) {
                    return StepResult.fail("Karte lässt sich nicht umschalten bei " + combo.label());
                }
                Shots.parkMouse(mc);
                int[] tooltips = new int[1];
                List<String> found = audit(mc, screen, tooltips);
                state.put(TOOLTIPS, (int) state.getOrDefault(TOOLTIPS, 0) + tooltips[0]);
                for (String finding : found) {
                    findings.add(combo.label() + ": " + finding);
                }
                report.add(combo.label() + " (GUI " + screen.width + "x" + screen.height + ", " + tooltips[0] + " Tooltips): "
                        + (found.isEmpty() ? "ganz sichtbar" : String.join(" | ", found)));
                state.put(CHECKED, (int) state.getOrDefault(CHECKED, 0) + 1);
                ctx.log(combo.label() + ": " + (found.isEmpty() ? "ganz sichtbar" : found.size() + " Befunde"));
                next(state, ticks, 3);
            }
            case 3 -> {
                StepResult shot = Shots.shot(ctx, elapsed, combo.photo());
                if (!shot.done()) {
                    return shot;
                }
                if (shot.failed()) {
                    return shot;
                }
                state.put(COMBO, index + 1);
                next(state, ticks, 0);
            }
            default -> {
                return StepResult.fail("unbekannte Phase " + phase);
            }
        }
        return StepResult.pending();
    }

    private static void writeReport(List<String> lines) {
        try {
            Files.writeString(Services.PLATFORM.gameDir().resolve(REPORT_FILE), String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            CamDrones.LOG.warn("[Kamera-Selbsttest] {} nicht geschrieben: {}", REPORT_FILE, e.toString());
        }
    }

    private static void next(Map<String, Object> state, int ticks, int phase) {
        state.put(PHASE, phase);
        state.put(SINCE, ticks + 1);
    }

    static List<String> audit(Minecraft mc, PlannerScreen screen, int[] tooltips) {
        EditorLayout shown = screen.layout();
        List<String> out = new ArrayList<>(LayoutCheck.audit(LayoutCheck.Box.of(0, 0, screen.width, screen.height), LayoutCheck.capture(mc, screen)));
        String separator = I18n.get(FieldText.PREFIX + "decimal");
        char decimal = separator.length() == 1 ? separator.charAt(0) : '.';
        EditorTexts texts = EditorCatalog.texts((key, args) -> I18n.get(key, args.toArray()), decimal);
        for (String problem : shown.problems(texts, mc.font::width)) {
            out.add("Layout: " + problem);
        }
        out.addAll(LayoutCheck.tooltips(mc, screen, tooltips));
        return out;
    }

    private static StepResult click(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        int index = ticks / 12;
        if (ticks % 12 == 0 && index < CLICKED.length) {
            BlockPos pos = Arena.ground(CLICKED[index][0], CLICKED[index][1]);
            Vec3 top = Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(top, Direction.UP, pos, false));
            return StepResult.pending();
        }
        return index >= CLICKED.length ? StepResult.pass() : StepResult.pending();
    }

    private static StepResult waypoints(SelfTest.StepContext ctx, int ticks) {
        Optional<ItemStack> stack = held();
        if (stack.isEmpty()) {
            return StepResult.fail("Tablet nicht mehr in der Hand");
        }
        TabletState tablet = PlannerTablets.read(stack.get());
        Mission mission = tablet.activeMission();
        if (mission.size() < CLICKED.length) {
            return ticks > 120 ? StepResult.fail(mission.size() + " von " + CLICKED.length + " Wegpunkten im Tablet") : StepResult.pending();
        }
        if (mission.size() > CLICKED.length) {
            return StepResult.fail(mission.size() + " Wegpunkte statt " + CLICKED.length);
        }
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < CLICKED.length; i++) {
            Waypoint w = mission.waypoints().get(i);
            BlockPos pos = Arena.ground(CLICKED[i][0], CLICKED[i][1]);
            double expectedY = pos.getY() + 1.0 + tablet.groundHeight();
            if (Math.abs(w.x() - (pos.getX() + 0.5)) > 0.6 || Math.abs(w.z() - (pos.getZ() + 0.5)) > 0.6 || Math.abs(w.y() - expectedY) > 0.6) {
                problems.add("Wegpunkt " + (i + 1) + " bei " + Flight.format(w.x()) + "/" + Flight.format(w.y()) + "/" + Flight.format(w.z())
                        + " statt über " + pos.toShortString() + " in " + tablet.groundHeight() + " m");
            }
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        return StepResult.pass(CLICKED.length + " Wegpunkte per Klick, je " + tablet.groundHeight() + " m über dem Block");
    }

    private static void stepBack(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Vec3 spawn = Arena.spawn();
        player.teleportTo(player.level(), spawn.x + 0.5, spawn.y, spawn.z - 9.0, Set.<Relative>of(), 0.0F, -25.0F, true);
    }

    private static StepResult preview(SelfTest.StepContext ctx, int ticks) {
        if (ticks < 10) {
            return StepResult.pending();
        }
        int drawn = WaypointPreview.lastDrawn();
        if (drawn != CLICKED.length) {
            return ticks > 100 ? StepResult.fail("Vorschau zeigt " + drawn + " statt " + CLICKED.length + " Wegpunkte") : StepResult.pending();
        }
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen != null) {
            return StepResult.fail("Fenster offen: " + screen.getClass().getSimpleName());
        }
        return StepResult.pass("Vorschau mit " + drawn + " Wegpunkten und Linie, solange das Tablet in der Hand ist");
    }
}
