package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.planner.model.EditorLayout;
import de.lardtom.camdrones.planner.model.EditorState;
import de.lardtom.camdrones.planner.model.EditorTexts;
import de.lardtom.camdrones.planner.model.FollowMenu;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.camdrones.planner.model.TabletState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowMeTabletTest {
    private static final List<String> STYLES = List.of("behind", "parallel", "watch");
    private static final String PREFIX = "camdrones.planner.";

    @Test
    void tabletFooterHasAFollowMeButtonAfterAuto() {
        List<String> names = Arrays.stream(EditorTexts.Footer.values()).map(Enum::name).toList();
        assertTrue(names.contains("FOLLOW"), "Fußleiste des Flugplaners: " + names);
        assertEquals(names.indexOf("AUTO") + 1, names.indexOf("FOLLOW"), "Follow me direkt nach Automatik: " + names);
    }

    @Test
    void tabletNamesButtonStylesAndStatusInBothLanguages() {
        for (String language : PlannerLang.LANGUAGES) {
            Map<String, String> file = PlannerLang.plannerFile(language);
            for (String key : List.of("button.follow", "button.follow.short", "button.follow.tooltip", "status.follow", "status.follow_switch")) {
                assertTrue(file.containsKey(PREFIX + key), language + " fehlt " + PREFIX + key);
            }
            for (String style : STYLES) {
                assertTrue(file.containsKey(PREFIX + "follow." + style), language + " fehlt Stil " + style);
                assertTrue(file.containsKey(PREFIX + "follow." + style + ".tooltip"), language + " fehlt Tooltip zu " + style);
            }
        }
    }

    @Test
    void germanStyleNamesFollowTheDesign() {
        Map<String, String> de = PlannerLang.plannerFile("de_de");
        assertEquals("Hinterher", de.get(PREFIX + "follow.behind"));
        assertEquals("Parallel", de.get(PREFIX + "follow.parallel"));
        assertEquals("Blick", de.get(PREFIX + "follow.watch"));
        assertEquals("Follow me", de.get(PREFIX + "button.follow"));
    }

    @Test
    void menuListsTheThreeStylesInDesignOrder() {
        assertEquals(List.of(FollowController.Style.BEHIND, FollowController.Style.PARALLEL, FollowController.Style.WATCH), FollowMenu.STYLES);
        assertEquals(STYLES, FollowMenu.STYLES.stream().map(style -> FollowMenu.labelKey(style).substring((PREFIX + "follow.").length())).toList());
        assertEquals(List.of("8", "5"), FollowMenu.tooltipArgs());
        EditorTexts texts = PlannerLang.of("de_de").texts();
        assertEquals(List.of("Hinterher", "Parallel", "Blick"), texts.followStyles());
        assertEquals("Follow me", texts.footer(EditorTexts.Footer.FOLLOW).full());
    }

    @Test
    void followMenuStacksAboveItsButtonAtEverySize() {
        VanillaFont font = VanillaFont.get();
        for (String language : PlannerLang.LANGUAGES) {
            EditorTexts texts = PlannerLang.of(language).texts();
            for (int[] size : new int[][]{{320, 240}, {640, 480}, {854, 480}}) {
                for (boolean map : new boolean[]{false, true}) {
                    String where = language + " " + size[0] + "×" + size[1] + (map ? " Karte" : " Liste");
                    EditorLayout layout = EditorLayout.compute(size[0], size[1], map, texts, font::width);
                    Rect button = layout.footerButton(EditorTexts.Footer.FOLLOW);
                    assertTrue(layout.footerButton(EditorTexts.Footer.AUTO).right() < button.x(), where + ": Follow me nach Automatik");
                    assertTrue(button.right() < layout.status().x(), where + ": Follow me vor der Statuszeile");
                    assertTrue(layout.status().w() >= EditorLayout.MIN_STATUS_WIDTH, where + ": Statuszeile " + layout.status().w());
                    String shown = layout.footerCompact() ? texts.footer(EditorTexts.Footer.FOLLOW).compact() : texts.footer(EditorTexts.Footer.FOLLOW).full();
                    assertTrue(font.width(shown) + 4 <= button.w(), where + ": Beschriftung " + shown);
                    List<Rect> items = layout.followItems();
                    assertEquals(3, items.size());
                    for (int i = 0; i < items.size(); i++) {
                        Rect r = items.get(i);
                        assertTrue(r.inside(layout.window()), where + ": Stil " + i + " " + r);
                        assertTrue(r.bottom() <= layout.footer().y(), where + ": Stil " + i + " über der Fußleiste");
                        assertTrue(font.width(texts.followStyles().get(i)) + 4 <= r.w(), where + ": Stil " + i + " passt");
                        assertTrue(r.x() <= button.x() && r.right() >= button.right() || r.right() == layout.window().right() - layout.pad(),
                                where + ": Stil " + i + " über dem Knopf");
                        if (i > 0) {
                            assertTrue(items.get(i - 1).bottom() <= r.y(), where + ": Stile überlappen");
                        }
                    }
                    assertTrue(layout.problems(texts, font::width).isEmpty(), where + ": " + layout.problems(texts, font::width));
                }
            }
        }
    }

    @Test
    void followMenuClosesTheOtherMenusAndTheyCloseIt() {
        EditorState editor = new EditorState(TabletState.EMPTY, FlightClass.TOUR, 0.0, 64.0, 0.0, false);
        assertFalse(editor.followMenu());
        editor.autoMenu(true);
        editor.followMenu(true);
        assertTrue(editor.followMenu());
        assertFalse(editor.autoMenu());
        editor.templateMenu(true);
        assertFalse(editor.followMenu());
        editor.followMenu(true);
        assertFalse(editor.templateMenu());
        editor.autoMenu(true);
        assertFalse(editor.followMenu());
        editor.followMenu(true);
        editor.addMission();
        assertFalse(editor.followMenu());
    }
}
