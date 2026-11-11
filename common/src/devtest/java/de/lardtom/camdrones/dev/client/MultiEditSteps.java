package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.planner.PlannerScreen;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.model.EditorLayout;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.PlannerEntry;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.camdrones.planner.model.TabletState;
import de.lardtom.camdrones.planner.model.WaypointField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MultiEditSteps {
    public static final int NUMBER = 709;
    public static final String NAME = "planer: mehrere Wegpunkte auf einmal ändern – Auswahl mit Strg/Cmd, Umschalt und Strg/Cmd+A, gemischte Werte „–“, "
            + "ein Wert für alle, mit Alt jeder um den Schritt, Doppelklick zum Eintippen";
    public static final double[] HEIGHTS = {6.0, 9.0, 13.0, 20.0};
    public static final double TYPED = 12.0;
    public static final double BIG_STEP = 5.0;
    public static final double STEP = 1.0;
    public static final String MIXED = "–";
    public static final int LEAD = 2;
    public static final int OUTSIDE = 3;
    public static final String PHOTO_SELECT = "planner_multi_select";
    public static final String PHOTO_ENTRY = "planner_multi_entry";
    private static final int STEP_TICKS = 20 * 60;
    private static final int SHORTCUT = InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER;
    private static final int KEY_A = 'a';
    private static final int KEY_RETURN = '\r';
    private static final int KEY_ESCAPE = 27;
    private static final String MAP = "multi.map";
    private static final String START_Y = "multi.start";
    private static final String SERVER_HEIGHTS = "multi.server";
    private static final double EPSILON = 0.05;

    private MultiEditSteps() {
    }

    public static void register() {
        SelfTest.clientStep(NUMBER, NAME, STEP_TICKS, Script.create()
                .append(Shots.photoWindow())
                .server("Tablet mit vier Wegpunkten auf 6, 9, 13 und 20 m über dem Spieler", MultiEditSteps::giveTablet)
                .await("Tablet am Client", 100, () -> held().isPresent())
                .run("Editor öffnen", () -> Minecraft.getInstance().gameMode.useItem(Minecraft.getInstance().player, InteractionHand.MAIN_HAND))
                .await("Editor offen", 100, () -> screen().isPresent())
                .then("Liste zu sehen", MultiEditSteps::listShown)
                .then("Strg/Cmd-Klick: Wegpunkt 1 und 3 gewählt", MultiEditSteps::toggle)
                .then("Umschalt-Klick: Wegpunkt 1 bis 3, Strg/Cmd+A: alle vier", MultiEditSteps::range)
                .then("gemischte Höhe zeigt „–“, gleiches Warten den Wert", MultiEditSteps::mixed)
                .append(Shots.photos(PHOTO_SELECT))
                .then("Alt+Umschalt-Klick rechts auf Höhe: jeder Wegpunkt 5 m höher, Abstände bleiben", MultiEditSteps::eachStep)
                .then("Klick rechts ohne Alt: alle auf den Wert von Wegpunkt 3 plus 1 m", MultiEditSteps::sameStep)
                .then("Doppelklick auf die Höhe öffnet die Texteingabe, Esc bricht ab", MultiEditSteps::escape)
                .then("Doppelklick auf die Höhe öffnet die Texteingabe mit Hinweis", MultiEditSteps::open)
                .append(Shots.photos(PHOTO_ENTRY))
                .then("Höhe 12 m tippen, Enter: Wegpunkt 1 bis 3 auf 12 m", MultiEditSteps::type)
                .then("mit Alt +5 m: Wegpunkt 1 bis 3 auf 17 m, Wegpunkt 4 bleibt", MultiEditSteps::altPlusFive)
                .then("Doppelklick auf eine Listenzeile zentriert weiter die Karte", MultiEditSteps::center)
                .then("Karte wie vorher", MultiEditSteps::restoreMap)
                .run("Editor schließen, speichert die ganze Mission", () -> screen().ifPresent(PlannerScreen::onClose))
                .then("Tablet am Client und am Server: 17, 17, 17 und 20 m", MultiEditSteps::saved),
                Script.create()
                        .run("Editor zu", () -> {
                            if (screen().isPresent()) {
                                Minecraft.getInstance().gui.setScreen(null);
                            }
                        })
                        .server("leere Hand", (server, state) -> Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)));
    }

    private static void giveTablet(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Vec3 spawn = Arena.spawn();
        player.teleportTo(player.level(), spawn.x, spawn.y, spawn.z, Set.<Relative>of(), 0.0F, -60.0F, true);
        List<Waypoint> waypoints = new ArrayList<>();
        for (int i = 0; i < HEIGHTS.length; i++) {
            waypoints.add(Waypoint.at(spawn.x + 8.0 * i, player.getY() + HEIGHTS[i], spawn.z + 20.0 + 4.0 * i));
        }
        ItemStack tablet = new ItemStack(PlannerItems.flightPlanner().orElseThrow(() -> new IllegalStateException("Tablet fehlt")));
        PlannerTablets.write(tablet, new TabletState(List.of(PlannerEntry.of("Mehrfach", Mission.of(waypoints))), 0,
                TabletState.DEFAULT_GROUND_HEIGHT));
        player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
    }

    private static Optional<ItemStack> held() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return Optional.empty();
        }
        ItemStack stack = mc.player.getMainHandItem();
        return PlannerItems.isPlanner(stack) ? Optional.of(stack) : Optional.empty();
    }

    private static Optional<PlannerScreen> screen() {
        return Minecraft.getInstance().gui.screen() instanceof PlannerScreen screen ? Optional.of(screen) : Optional.empty();
    }

    private static StepResult listShown(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        ctx.state().putIfAbsent(MAP, screen.state().mapExpanded());
        ctx.state().putIfAbsent(START_Y, screen.state().startY());
        if (!screen.layout().listShown()) {
            if (!showList(screen)) {
                return StepResult.fail("Knopf für die Karte fehlt");
            }
            return ticks > 40 ? StepResult.fail("Liste nicht zu sehen") : StepResult.pending();
        }
        screen.detailPage(0);
        return StepResult.pass("Liste bei " + screen.width + "×" + screen.height + (screen.layout().mapShown() ? " neben der Karte" : " ohne Karte")
                + ", Start auf " + format(screen.state().startY()));
    }

    private static boolean showList(PlannerScreen screen) {
        if (!screen.state().mapExpanded()) {
            return true;
        }
        return SelfTestRunner.clickWidget(screen, I18n.get(FieldText.PREFIX + "map.hide"));
    }

    private static StepResult toggle(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        clickRow(screen, 0, 0, false);
        clickRow(screen, 2, SHORTCUT, false);
        String title = detailTitle(screen);
        String wanted = I18n.get(FieldText.PREFIX + "detail.waypoints", 2);
        if (!title.equals(wanted)) {
            return StepResult.fail("Titel „" + title + "“ statt „" + wanted + "“");
        }
        clickRow(screen, 2, SHORTCUT, false);
        String single = detailTitle(screen);
        String one = I18n.get(FieldText.PREFIX + "detail.waypoint", 1, HEIGHTS.length);
        if (!single.equals(one)) {
            return StepResult.fail("nach zweitem Strg/Cmd-Klick „" + single + "“ statt „" + one + "“");
        }
        return StepResult.pass("„" + wanted + "“, zweiter Klick nimmt Wegpunkt 3 wieder heraus: „" + single + "“");
    }

    private static StepResult range(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        List<String> seen = new ArrayList<>();
        clickRow(screen, 0, 0, false);
        clickRow(screen, 2, InputConstants.MOD_SHIFT, false);
        seen.add(detailTitle(screen));
        screen.keyPressed(new KeyEvent(InputConstants.KEY_A, KEY_A, SHORTCUT));
        seen.add(detailTitle(screen));
        clickRow(screen, 0, 0, false);
        clickRow(screen, 2, InputConstants.MOD_SHIFT, false);
        seen.add(detailTitle(screen));
        List<String> wanted = List.of(I18n.get(FieldText.PREFIX + "detail.waypoints", 3), I18n.get(FieldText.PREFIX + "detail.waypoints", 4),
                I18n.get(FieldText.PREFIX + "detail.waypoints", 3));
        return seen.equals(wanted) ? StepResult.pass(String.join(" → ", seen)) : StepResult.fail("Titel " + seen + " statt " + wanted);
    }

    private static StepResult mixed(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        String height = value(screen, WaypointField.HEIGHT);
        String wait = value(screen, WaypointField.WAIT);
        String none = I18n.get(FieldText.PREFIX + "value.wait_none");
        List<String> problems = new ArrayList<>();
        if (!height.equals(MIXED)) {
            problems.add("Höhe „" + height + "“ statt „" + MIXED + "“");
        }
        if (!wait.equals(none)) {
            problems.add("Warten „" + wait + "“ statt „" + none + "“");
        }
        return problems.isEmpty() ? StepResult.pass("Höhe „" + height + "“, Warten „" + wait + "“") : StepResult.fail(String.join(", ", problems));
    }

    private static StepResult eachStep(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        double[] before = heights(screen);
        clickValue(screen, WaypointField.HEIGHT, true, InputConstants.MOD_ALT | InputConstants.MOD_SHIFT, false);
        double[] wanted = {before[0] + BIG_STEP, before[1] + BIG_STEP, before[2] + BIG_STEP, before[OUTSIDE]};
        return compare(screen, wanted);
    }

    private static StepResult sameStep(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        double[] before = heights(screen);
        clickValue(screen, WaypointField.HEIGHT, true, 0, false);
        double lead = before[LEAD] + STEP;
        return compare(screen, new double[]{lead, lead, lead, before[OUTSIDE]});
    }

    private static StepResult escape(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        double[] before = heights(screen);
        doubleClickValue(screen, WaypointField.HEIGHT);
        Optional<EditBox> field = editField(screen, WaypointField.HEIGHT);
        if (field.isEmpty()) {
            return StepResult.fail("keine Texteingabe nach Doppelklick auf die Höhe (Fokus: " + focused(screen) + ")");
        }
        if (!same(heights(screen), before)) {
            return StepResult.fail("Doppelklick hat die Höhe verändert: " + describe(heights(screen)) + " statt " + describe(before));
        }
        String shown = field.get().getValue();
        screen.charTyped(new CharacterEvent('9'));
        screen.charTyped(new CharacterEvent('9'));
        screen.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, KEY_ESCAPE, 0));
        if (screen().isEmpty()) {
            return StepResult.fail("Esc hat den Editor geschlossen statt die Eingabe abzubrechen");
        }
        if (editField(screen, WaypointField.HEIGHT).isPresent()) {
            return StepResult.fail("Texteingabe nach Esc noch offen");
        }
        if (!same(heights(screen), before)) {
            return StepResult.fail("Esc hat übernommen: " + describe(heights(screen)) + " statt " + describe(before));
        }
        return StepResult.pass("Feld zeigte „" + shown + "“, nach „99“ und Esc unverändert " + describe(before));
    }

    private static StepResult open(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        double[] before = heights(screen);
        doubleClickValue(screen, WaypointField.HEIGHT);
        Optional<EditBox> field = editField(screen, WaypointField.HEIGHT);
        if (field.isEmpty()) {
            return StepResult.fail("keine Texteingabe nach Doppelklick auf die Höhe");
        }
        if (!same(heights(screen), before)) {
            return StepResult.fail("Doppelklick hat die Höhe verändert: " + describe(heights(screen)) + " statt " + describe(before));
        }
        String all = I18n.get(FieldText.PREFIX + "entry.all", 3);
        LayoutCheck.Frame frame = LayoutCheck.capture(Minecraft.getInstance(), screen);
        if (!frame.tooltip().stream().map(LayoutCheck.Text::value).toList().contains(all) && !frame.shows(all)) {
            return StepResult.fail("Hinweis „" + all + "“ fehlt unter der Texteingabe: " + frame.tooltip().stream().map(LayoutCheck.Text::value).toList());
        }
        return StepResult.pass("Feld „" + field.get().getValue() + "“, Hinweis „" + all + "“");
    }

    private static StepResult type(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        double[] before = heights(screen);
        if (editField(screen, WaypointField.HEIGHT).isEmpty()) {
            return StepResult.fail("Texteingabe nicht mehr offen");
        }
        screen.keyPressed(new KeyEvent(InputConstants.KEY_A, KEY_A, SHORTCUT));
        screen.charTyped(new CharacterEvent('1'));
        screen.charTyped(new CharacterEvent('x'));
        screen.charTyped(new CharacterEvent('2'));
        screen.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, KEY_RETURN, 0));
        if (editField(screen, WaypointField.HEIGHT).isPresent()) {
            return StepResult.fail("Texteingabe nach Enter noch offen");
        }
        return compare(screen, new double[]{TYPED, TYPED, TYPED, before[OUTSIDE]});
    }

    private static StepResult altPlusFive(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        double[] before = heights(screen);
        clickValue(screen, WaypointField.HEIGHT, true, InputConstants.MOD_ALT | InputConstants.MOD_SHIFT, false);
        return compare(screen, new double[]{TYPED + BIG_STEP, TYPED + BIG_STEP, TYPED + BIG_STEP, before[OUTSIDE]});
    }

    private static StepResult center(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        if (screen.layout().mapShown()) {
            if (!SelfTestRunner.clickWidget(screen, I18n.get(FieldText.PREFIX + "map.hide"))) {
                return StepResult.fail("Knopf „Karte aus“ fehlt");
            }
            return StepResult.pending();
        }
        Waypoint target = screen.state().mission().waypoint(OUTSIDE);
        screen.state().view().center(target.x() - 100.0, target.z() - 100.0);
        clickRow(screen, OUTSIDE, 0, false);
        clickRow(screen, OUTSIDE, 0, true);
        double dx = screen.state().view().centerX() - target.x();
        double dz = screen.state().view().centerZ() - target.z();
        String title = detailTitle(screen);
        String wanted = I18n.get(FieldText.PREFIX + "detail.waypoint", OUTSIDE + 1, HEIGHTS.length);
        if (Math.abs(dx) > EPSILON || Math.abs(dz) > EPSILON) {
            return StepResult.fail("Karte nicht auf Wegpunkt 4 zentriert: " + format(dx) + " / " + format(dz) + " m daneben");
        }
        if (!title.equals(wanted)) {
            return StepResult.fail("Titel „" + title + "“ statt „" + wanted + "“");
        }
        return StepResult.pass("Karte auf Wegpunkt 4, Titel „" + title + "“");
    }

    private static StepResult restoreMap(SelfTest.StepContext ctx, int ticks) {
        Optional<PlannerScreen> open = screen();
        if (open.isEmpty()) {
            return StepResult.fail("Editor geschlossen");
        }
        PlannerScreen screen = open.get();
        boolean wanted = (boolean) ctx.state().getOrDefault(MAP, false);
        if (screen.state().mapExpanded() != wanted) {
            String toggle = I18n.get(FieldText.PREFIX + (screen.state().mapExpanded() ? "map.hide" : "map.show"));
            if (!SelfTestRunner.clickWidget(screen, toggle)) {
                return StepResult.fail("Knopf \"" + toggle + "\" fehlt");
            }
        }
        return StepResult.pass("Karte " + (wanted ? "an" : "aus"));
    }

    private static StepResult saved(SelfTest.StepContext ctx, int ticks) {
        double start = (double) ctx.state().getOrDefault(START_Y, 0.0);
        double[] wanted = {TYPED + BIG_STEP, TYPED + BIG_STEP, TYPED + BIG_STEP, HEIGHTS[OUTSIDE]};
        if (ticks % 5 == 0) {
            ctx.onServer(server -> {
                ItemStack item = Arena.player(server).getMainHandItem();
                Mission mission = PlannerItems.isPlanner(item) ? PlannerTablets.read(item).activeMission() : Mission.empty();
                ctx.state().put(SERVER_HEIGHTS, heights(mission, start));
            });
        }
        double[] client = heights(held().map(stack -> PlannerTablets.read(stack).activeMission()).orElse(Mission.empty()), start);
        Object server = ctx.state().get(SERVER_HEIGHTS);
        if (same(client, wanted) && server instanceof double[] seen && same(seen, wanted)) {
            return StepResult.pass("Tablet am Client " + describe(client) + ", am Server " + describe(seen));
        }
        if (ticks > 200) {
            return StepResult.fail("Tablet am Client " + describe(client) + ", am Server " + (server instanceof double[] seen ? describe(seen) : "unbekannt")
                    + " statt " + describe(wanted));
        }
        return StepResult.pending();
    }

    private static double[] heights(Mission mission, double start) {
        double[] out = new double[mission.size()];
        for (int i = 0; i < mission.size(); i++) {
            out[i] = mission.waypoint(i).y() - start;
        }
        return out;
    }

    private static void clickRow(PlannerScreen screen, int index, int modifiers, boolean doubleClick) {
        EditorLayout layout = screen.layout();
        Rect row = layout.listRow(index - screen.state().listScroll());
        click(screen, row.x() + 12, row.centerY(), modifiers, doubleClick);
    }

    private static Rect valueBox(PlannerScreen screen, WaypointField field) {
        EditorLayout layout = screen.layout();
        return EditorLayout.valueBox(layout.detailRows().get(field.ordinal()), layout.detailLabelWidth());
    }

    private static void clickValue(PlannerScreen screen, WaypointField field, boolean right, int modifiers, boolean doubleClick) {
        Rect box = valueBox(screen, field);
        click(screen, right ? box.right() - 3 : box.x() + 3, box.centerY(), modifiers, doubleClick);
    }

    private static void doubleClickValue(PlannerScreen screen, WaypointField field) {
        Rect box = valueBox(screen, field);
        click(screen, box.centerX(), box.centerY(), 0, false);
        click(screen, box.centerX(), box.centerY(), 0, true);
    }

    private static void click(PlannerScreen screen, double x, double y, int modifiers, boolean doubleClick) {
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, modifiers));
        screen.mouseClicked(event, doubleClick);
        screen.mouseReleased(event);
    }

    private static Optional<EditBox> editField(PlannerScreen screen, WaypointField field) {
        Rect box = valueBox(screen, field);
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox edit && edit.visible && edit.isFocused() && box.contains(edit.getX() + edit.getWidth() / 2.0,
                    edit.getY() + edit.getHeight() / 2.0)) {
                return Optional.of(edit);
            }
        }
        return Optional.empty();
    }

    private static String focused(PlannerScreen screen) {
        GuiEventListener focus = screen.getFocused();
        return focus == null ? "keiner" : focus.getClass().getSimpleName();
    }

    private static String detailTitle(PlannerScreen screen) {
        Rect title = screen.layout().detailTitle();
        return textIn(screen, LayoutCheck.Box.of(title.x(), title.y(), title.w(), title.h()));
    }

    private static String value(PlannerScreen screen, WaypointField field) {
        Rect box = valueBox(screen, field);
        int inset = EditorLayout.VALUE_INSET;
        return textIn(screen, LayoutCheck.Box.of(box.x() + inset - 2, box.y(), box.w() - 2 * inset + 4, box.h()));
    }

    private static String textIn(PlannerScreen screen, LayoutCheck.Box area) {
        List<String> found = new ArrayList<>();
        for (LayoutCheck.Text text : LayoutCheck.capture(Minecraft.getInstance(), screen).texts()) {
            if (area.contains(text.full(), 2)) {
                found.add(text.value());
            }
        }
        return String.join(" ", found);
    }

    private static double[] heights(PlannerScreen screen) {
        return heights(screen.state().mission(), screen.state().startY());
    }

    private static StepResult compare(PlannerScreen screen, double[] wanted) {
        double[] seen = heights(screen);
        return same(seen, wanted) ? StepResult.pass("Höhen " + describe(seen)) : StepResult.fail("Höhen " + describe(seen) + " statt " + describe(wanted));
    }

    private static boolean same(double[] a, double[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (Math.abs(a[i] - b[i]) > EPSILON) {
                return false;
            }
        }
        return true;
    }

    private static String describe(double[] values) {
        List<String> parts = new ArrayList<>();
        for (double value : values) {
            parts.add(format(value));
        }
        return String.join(" / ", parts) + " m";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
