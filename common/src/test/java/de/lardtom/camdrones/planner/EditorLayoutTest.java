package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.planner.model.EditorLayout;
import de.lardtom.camdrones.planner.model.EditorTexts;
import de.lardtom.camdrones.planner.model.Rect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorLayoutTest {
    private static final int[][] SIZES = {{320, 240}, {640, 480}, {854, 480}};

    @Test
    void fontTableMatchesTheGame() {
        VanillaFont font = VanillaFont.get();
        assertTrue(font.size() > 500, "Glyphen geladen: " + font.size());
        assertEquals(4, font.width(" "));
        assertEquals(6, font.width("a"));
        assertEquals(2, font.width("i"));
        for (int cp : "äöüÄÖÜß‹›×▲▼▶⏸■−…—°„“".codePoints().toArray()) {
            assertTrue(font.knows(cp), "Glyphe fehlt in der Spielschrift: " + Character.toString(cp));
        }
    }

    @Test
    void editorFitsEverySizeLanguageAndMapState() {
        VanillaFont font = VanillaFont.get();
        List<String> failures = new ArrayList<>();
        for (String language : PlannerLang.LANGUAGES) {
            EditorTexts texts = PlannerLang.of(language).texts();
            for (int[] size : SIZES) {
                for (boolean map : new boolean[]{false, true}) {
                    EditorLayout layout = EditorLayout.compute(size[0], size[1], map, texts, font::width);
                    for (String problem : layout.problems(texts, font::width)) {
                        failures.add(language + " " + size[0] + "×" + size[1] + (map ? " Karte" : " Liste") + ": " + problem);
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void smallWindowSwapsListAndMap() {
        EditorTexts texts = PlannerLang.of("de_de").texts();
        VanillaFont font = VanillaFont.get();
        EditorLayout list = EditorLayout.compute(320, 240, false, texts, font::width);
        EditorLayout map = EditorLayout.compute(320, 240, true, texts, font::width);
        assertEquals(EditorLayout.Mode.NARROW, list.mode());
        assertTrue(list.compact());
        assertTrue(list.listShown());
        assertFalse(list.mapShown());
        assertTrue(map.mapShown());
        assertFalse(map.listShown());
        assertEquals(list.mapToggle(), map.mapToggle());
        assertTrue(list.visibleRows() >= 8, "Zeilen: " + list.visibleRows());
        assertTrue(list.footerCompact(), "bei 320 kurze Knöpfe");
    }

    @Test
    void wideWindowShowsMapListAndDetailSideBySide() {
        VanillaFont font = VanillaFont.get();
        for (String language : PlannerLang.LANGUAGES) {
            EditorTexts texts = PlannerLang.of(language).texts();
            for (int[] size : new int[][]{{640, 480}, {854, 480}}) {
                EditorLayout layout = EditorLayout.compute(size[0], size[1], true, texts, font::width);
                assertEquals(EditorLayout.Mode.WIDE, layout.mode());
                assertTrue(layout.mapShown() && layout.listShown());
                assertTrue(layout.map().right() <= layout.list().x() && layout.list().right() <= layout.detail().x());
                assertTrue(layout.map().w() >= 200 && layout.map().h() >= 250, "Karte " + layout.map());
                assertFalse(layout.footerCompact(), language + " " + size[0] + ": volle Beschriftung");
            }
        }
    }

    @Test
    void liveButtonAndExitSettingAreReachableEverywhere() {
        VanillaFont font = VanillaFont.get();
        for (String language : PlannerLang.LANGUAGES) {
            EditorTexts texts = PlannerLang.of(language).texts();
            for (int[] size : SIZES) {
                for (boolean map : new boolean[]{false, true}) {
                    String where = language + " " + size[0] + "×" + size[1] + (map ? " Karte" : " Liste");
                    EditorLayout layout = EditorLayout.compute(size[0], size[1], map, texts, font::width);
                    Rect live = layout.live();
                    assertTrue(live.inside(layout.header()), where + ": Livebild im Kopf " + live);
                    assertTrue(layout.title().right() <= live.x() && live.right() <= layout.mapToggle().x(), where + ": Livebild zwischen Titel und Karte");
                    assertTrue(font.width(texts.live()) + 4 <= live.w(), where + ": Beschriftung Livebild passt");
                    assertEquals(EditorLayout.SETTING_FIELDS, layout.settings().size());
                    Rect exit = layout.settings().get(EditorLayout.SETTING_EXIT);
                    assertTrue(exit.inside(layout.window()) && exit.bottom() <= layout.footer().y(), where + ": Verlassen sichtbar " + exit);
                    Rect box = EditorLayout.valueBox(exit, layout.settingLabelWidth());
                    assertEquals(EditorLayout.SETTING_EXIT, layout.settingAt(box.right() - 2, box.y() + 2), where);
                }
            }
        }
    }

    @Test
    void autoShotMenuStacksAboveItsButton() {
        VanillaFont font = VanillaFont.get();
        for (String language : PlannerLang.LANGUAGES) {
            EditorTexts texts = PlannerLang.of(language).texts();
            for (int[] size : SIZES) {
                for (boolean map : new boolean[]{false, true}) {
                    String where = language + " " + size[0] + "×" + size[1] + (map ? " Karte" : " Liste");
                    EditorLayout layout = EditorLayout.compute(size[0], size[1], map, texts, font::width);
                    Rect button = layout.footerButton(EditorTexts.Footer.AUTO);
                    assertTrue(layout.footerButton(EditorTexts.Footer.STOP).right() < button.x(), where + ": Automatik nach Stopp");
                    assertTrue(button.right() < layout.status().x(), where + ": Automatik vor der Statuszeile");
                    assertTrue(layout.status().w() >= 40, where + ": Statuszeile " + layout.status().w());
                    List<Rect> items = layout.autoItems();
                    assertEquals(texts.autoShots().size(), items.size());
                    assertEquals(4, items.size());
                    for (int i = 0; i < items.size(); i++) {
                        Rect r = items.get(i);
                        assertTrue(r.inside(layout.window()), where + ": Eintrag " + i + " " + r);
                        assertTrue(r.bottom() <= layout.footer().y(), where + ": Eintrag " + i + " über der Fußleiste");
                        assertTrue(r.x() <= button.x() && r.right() >= button.right() || r.right() == layout.window().right() - layout.pad(),
                                where + ": Eintrag " + i + " über dem Knopf");
                        if (i > 0) {
                            assertTrue(items.get(i - 1).bottom() <= r.y(), where + ": Einträge überlappen");
                        }
                    }
                }
            }
        }
    }

    @Test
    void valueBoxesSplitIntoDecreaseAndIncrease() {
        Rect row = new Rect(10, 20, 200, 12);
        Rect box = EditorLayout.valueBox(row, 60);
        assertEquals(new Rect(70, 20, 140, 12), box);
        assertEquals(-1, EditorLayout.stepDirection(box, 100));
        assertEquals(1, EditorLayout.stepDirection(box, 150));
        assertEquals(140 - 2 * EditorLayout.VALUE_INSET, EditorLayout.valueTextWidth(box));
    }

    @Test
    void hitTestsFindRowsAndFields() {
        EditorTexts texts = PlannerLang.of("en_us").texts();
        VanillaFont font = VanillaFont.get();
        EditorLayout layout = EditorLayout.compute(640, 480, false, texts, font::width);
        Rect first = layout.listRow(0);
        assertEquals(0, layout.listRowAt(first.x() + 2, first.y() + 2));
        assertEquals(1, layout.listRowAt(first.x() + 2, first.y() + layout.row() + 2));
        assertEquals(-1, layout.listRowAt(first.x() - 5, first.y() + 2));
        Rect detail = layout.detailRows().get(3);
        Rect box = EditorLayout.valueBox(detail, layout.detailLabelWidth());
        assertEquals(3, layout.detailRowAt(box.x() + 1, box.y() + 1));
        assertEquals(-1, layout.detailRowAt(detail.x() + 1, detail.y() + 1));
        Rect setting = layout.settings().get(EditorLayout.SETTING_GROUND);
        Rect settingBox = EditorLayout.valueBox(setting, layout.settingLabelWidth());
        assertEquals(EditorLayout.SETTING_GROUND, layout.settingAt(settingBox.right() - 2, settingBox.y() + 2));
    }
}
