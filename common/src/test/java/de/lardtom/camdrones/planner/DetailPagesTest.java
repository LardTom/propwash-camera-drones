package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.planner.model.EditorLayout;
import de.lardtom.camdrones.planner.model.EditorTexts;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.camdrones.planner.model.WaypointField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetailPagesTest {
    @Test
    void smallWindowSplitsFlightAndActionsIntoTwoPages() {
        VanillaFont font = VanillaFont.get();
        for (String language : PlannerLang.LANGUAGES) {
            EditorTexts texts = PlannerLang.of(language).texts();
            for (boolean map : new boolean[]{false, true}) {
                EditorLayout layout = EditorLayout.compute(320, 240, map, texts, font::width);
                assertTrue(layout.detailPaged(), language + " 320×240");
                assertEquals(2, layout.detailPages());
                Rect toggle = layout.pageToggle();
                assertTrue(toggle.inside(layout.detail()), "Umschalter im Detailfeld: " + toggle);
                for (int i = 0; i < EditorLayout.DETAIL_ROWS; i++) {
                    Rect row = layout.detailRows().get(i);
                    assertTrue(row.bottom() <= toggle.y(), "Zeile " + i + " über dem Umschalter");
                    int page = i >= WaypointField.ACTION.ordinal() ? 1 : 0;
                    assertEquals(page, layout.detailPage(i));
                    Rect box = EditorLayout.valueBox(row, layout.detailLabelWidth());
                    assertEquals(i, layout.detailRowAt(box.x() + 1, box.y() + 1, page));
                    assertNotEquals(i, layout.detailRowAt(box.x() + 1, box.y() + 1, 1 - page));
                }
                Rect light = EditorLayout.valueBox(layout.detailRows().get(WaypointField.LIGHT.ordinal()), layout.detailLabelWidth());
                assertEquals(WaypointField.SPEED.ordinal(), layout.detailRowAt(light.x() + 1, light.y() + 1, 0), "gleiche Stelle, erste Seite");
            }
        }
    }

    @Test
    void largerWindowsShowEveryRowAtOnce() {
        VanillaFont font = VanillaFont.get();
        for (String language : PlannerLang.LANGUAGES) {
            EditorTexts texts = PlannerLang.of(language).texts();
            for (int[] size : new int[][]{{640, 480}, {854, 480}}) {
                EditorLayout layout = EditorLayout.compute(size[0], size[1], true, texts, font::width);
                assertFalse(layout.detailPaged(), language + " " + size[0]);
                assertTrue(layout.pageToggle().isEmpty());
                Rect last = layout.detailRows().get(WaypointField.REPEAT.ordinal());
                assertTrue(last.bottom() <= layout.detail().bottom());
                assertEquals(WaypointField.REPEAT.ordinal(), layout.detailRowAt(EditorLayout.valueBox(last, layout.detailLabelWidth()).x() + 1,
                        last.y() + 1, 0));
            }
        }
    }
}
