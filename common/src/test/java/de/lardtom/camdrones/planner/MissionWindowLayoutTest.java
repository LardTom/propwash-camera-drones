package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.planner.model.ExitSetting;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.MissionWindowLayout;
import de.lardtom.camdrones.planner.model.ViewSetting;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionWindowLayoutTest {
    private static final int[][] SIZES = {{320, 240}, {640, 480}, {854, 480}};

    private static MissionWindowLayout.Widths widths(PlannerLang lang, VanillaFont font) {
        int value = font.width(lang.text(ExitSetting.value(Optional.empty())));
        for (ExitSetting setting : ExitSetting.values()) {
            value = Math.max(value, font.width(lang.text(setting.value())));
        }
        int view = font.width(lang.text(ViewSetting.value(Optional.empty())));
        for (ViewSetting setting : ViewSetting.values()) {
            view = Math.max(view, font.width(lang.text(setting.value())));
        }
        String p = FieldText.PREFIX;
        return new MissionWindowLayout.Widths(font.width(lang.text(ExitSetting.LABEL_KEY)), value, font.width(lang.text(p + "button.start")),
                font.width(lang.text(p + "button.pause")), font.width(lang.text(p + "button.stop")), font.width(lang.text(p + "service.close")),
                font.width(lang.text(ViewSetting.LABEL_KEY)), view);
    }

    @Test
    void missionWindowFitsEverySizeAndLanguage() {
        VanillaFont font = VanillaFont.get();
        List<String> failures = new ArrayList<>();
        for (String language : PlannerLang.LANGUAGES) {
            PlannerLang lang = PlannerLang.of(language);
            MissionWindowLayout.Widths texts = widths(lang, font);
            for (int[] size : SIZES) {
                MissionWindowLayout layout = MissionWindowLayout.compute(size[0], size[1], texts);
                for (String problem : layout.problems(size[0], size[1], texts)) {
                    failures.add(language + " " + size[0] + "×" + size[1] + ": " + problem);
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void exitValueSitsRightOfItsLabel() {
        VanillaFont font = VanillaFont.get();
        MissionWindowLayout layout = MissionWindowLayout.compute(320, 240, widths(PlannerLang.of("de_de"), font));
        assertTrue(layout.exitValue().x() >= layout.exitRow().x() + layout.exitLabelWidth());
        assertTrue(layout.exitValue().right() == layout.exitRow().right());
        assertTrue(layout.stop().right() <= layout.window().right() - MissionWindowLayout.PAD);
    }

    @Test
    void viewRowSitsBelowTheExitRowAndAboveTheButtons() {
        VanillaFont font = VanillaFont.get();
        for (String language : PlannerLang.LANGUAGES) {
            for (int[] size : SIZES) {
                MissionWindowLayout layout = MissionWindowLayout.compute(size[0], size[1], widths(PlannerLang.of(language), font));
                String where = language + " " + size[0] + "×" + size[1];
                assertTrue(layout.viewRow().y() >= layout.exitRow().bottom(), where);
                assertTrue(layout.start().y() >= layout.viewRow().bottom(), where);
                assertTrue(layout.viewValue().x() == layout.exitValue().x(), where);
                assertTrue(layout.viewValue().right() == layout.viewRow().right(), where);
                assertTrue(!layout.viewValue().contains(layout.exitValue().centerX(), layout.exitValue().centerY()), where);
            }
        }
    }
}
