package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.planner.model.EditorCatalog;
import de.lardtom.camdrones.planner.model.EditorLayout;
import de.lardtom.camdrones.planner.model.EditorTexts;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.camdrones.planner.model.WaypointField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EveryFieldTest {
    private static final FlightClass TOUR = FlightClass.TOUR;

    private static Waypoint step(WaypointField field, Waypoint w, int steps, boolean big) {
        return field.step(w, steps, big, 0, TOUR);
    }

    private static Waypoint action() {
        return Waypoint.at(0.0, 70.0, 0.0).withAccessory(Waypoint.Accessory.ACTION_1, false);
    }

    @Test
    void repeatCyclesOnceEachWaypointAndEveryXTicks() {
        Waypoint once = action();
        assertEquals(Waypoint.Repeat.ONCE, once.repeatMode());
        Waypoint each = step(WaypointField.REPEAT, once, 1, false);
        assertEquals(Waypoint.Repeat.EACH, each.repeatMode());
        Waypoint timed = step(WaypointField.REPEAT, each, 1, false);
        assertEquals(Waypoint.Repeat.EVERY, timed.repeatMode());
        assertEquals(MissionLimits.DEFAULT_EVERY_TICKS, timed.everyTicks());
        assertEquals(Waypoint.Repeat.ONCE, step(WaypointField.REPEAT, timed, 1, false).repeatMode());
        assertEquals(Waypoint.Repeat.EVERY, step(WaypointField.REPEAT, once, -1, false).repeatMode(), "rückwärts von „nur hier“ zum Takt");
        assertEquals(FieldText.PREFIX + "value.repeat.off", WaypointField.REPEAT.value(once, ',', 0.0).key());
        assertEquals(FieldText.PREFIX + "value.repeat.on", WaypointField.REPEAT.value(each, ',', 0.0).key());
        assertEquals(FieldText.PREFIX + "value.repeat.every", WaypointField.REPEAT.value(timed, ',', 0.0).key());
    }

    @Test
    void intervalStepsInTicksAndStaysInsideTheLimits() {
        Waypoint timed = action().withRepeat(Waypoint.Repeat.EVERY);
        assertTrue(WaypointField.EVERY.enabled(timed));
        assertFalse(WaypointField.EVERY.enabled(action()));
        assertFalse(WaypointField.EVERY.enabled(action().withRepeat(Waypoint.Repeat.EACH)));
        assertEquals(action(), step(WaypointField.EVERY, action(), 1, false), "ohne Takt kein Schritt");
        assertTrue(WaypointField.EVERY.task());
        assertEquals(21, step(WaypointField.EVERY, timed, 1, false).everyTicks());
        assertEquals(19, step(WaypointField.EVERY, timed, -1, false).everyTicks());
        assertEquals(40, step(WaypointField.EVERY, timed, 1, true).everyTicks(), "Umschalt: 20 Ticks = 1 s");
        assertEquals(1, WaypointField.EVERY_STEP_TICKS);
        assertEquals(MissionLimits.TICKS_PER_SECOND, WaypointField.EVERY_BIG_STEP_TICKS);
        Waypoint fastest = timed.withEveryTicks(MissionLimits.MIN_EVERY_TICKS);
        assertEquals(MissionLimits.MIN_EVERY_TICKS, step(WaypointField.EVERY, fastest, -1, false).everyTicks());
        assertEquals(MissionLimits.MIN_EVERY_TICKS, step(WaypointField.EVERY, fastest, -1, true).everyTicks());
        Waypoint slow = timed.withEveryTicks(MissionLimits.MAX_EVERY_TICKS - 5);
        assertEquals(MissionLimits.MAX_EVERY_TICKS, step(WaypointField.EVERY, slow, 1, true).everyTicks());
        assertEquals(MissionLimits.MAX_EVERY_TICKS, step(WaypointField.EVERY, slow.withEveryTicks(MissionLimits.MAX_EVERY_TICKS), 1, false).everyTicks());
    }

    @Test
    void changingTheActionKeepsTheTimerAndNoActionDropsIt() {
        Waypoint timed = action().withRepeat(Waypoint.Repeat.EVERY).withEveryTicks(7);
        Waypoint second = step(WaypointField.ACCESSORY, timed, 1, false);
        assertEquals(Waypoint.Accessory.ACTION_2, second.accessory());
        assertEquals(Waypoint.Repeat.EVERY, second.repeatMode());
        assertEquals(7, second.everyTicks());
        Waypoint none = step(WaypointField.ACCESSORY, timed, -1, false);
        assertEquals(Waypoint.Accessory.NONE, none.accessory());
        assertEquals(Waypoint.Repeat.ONCE, none.repeatMode());
        assertEquals(0, none.everyTicks());
        assertFalse(WaypointField.REPEAT.enabled(none));
        assertEquals(7, step(WaypointField.LIGHT, timed, 1, false).everyTicks(), "Licht lässt den Takt stehen");
    }

    @Test
    void intervalShowsTicksAndRepeatShowsTheSeconds() {
        Waypoint timed = action().withRepeat(Waypoint.Repeat.EVERY);
        FieldText twenty = WaypointField.EVERY.value(timed, ',', 0.0);
        assertEquals(FieldText.PREFIX + "value.every", twenty.key());
        assertEquals(List.of("20"), twenty.args());
        FieldText second = WaypointField.REPEAT.value(timed, ',', 0.0);
        assertEquals(List.of("1"), second.args());
        assertEquals(List.of("0,25"), WaypointField.REPEAT.value(timed.withEveryTicks(5), ',', 0.0).args());
        assertEquals(List.of("1.5"), WaypointField.REPEAT.value(timed.withEveryTicks(30), '.', 0.0).args());
        assertEquals(List.of("59,95"), WaypointField.REPEAT.value(timed.withEveryTicks(1199), ',', 0.0).args());
        assertEquals(List.of("1200"), WaypointField.EVERY.value(timed.withEveryTicks(1200), ',', 0.0).args());
        assertEquals(List.of("60"), WaypointField.REPEAT.value(timed.withEveryTicks(1200), ',', 0.0).args());
        FieldText one = WaypointField.EVERY.value(timed.withEveryTicks(1), ',', 0.0);
        assertEquals(FieldText.PREFIX + "value.every.one", one.key());
        assertEquals(List.of("0,05"), WaypointField.REPEAT.value(timed.withEveryTicks(1), ',', 0.0).args());
        assertEquals("0,05", WaypointField.seconds(1, ','));
        assertEquals(FieldText.PREFIX + "value.none", WaypointField.EVERY.value(action(), ',', 0.0).key());
        for (String language : PlannerLang.LANGUAGES) {
            PlannerLang lang = PlannerLang.of(language);
            boolean german = language.equals("de_de");
            assertEquals(german ? "20 Ticks" : "20 ticks", lang.text(twenty.key(), twenty.args()));
            assertEquals(german ? "alle 1 s" : "every 1 s", lang.text(second.key(), second.args()));
            assertEquals(german ? "1 Tick" : "1 tick", lang.text(one.key(), one.args()));
            assertTrue(lang.has(FieldText.PREFIX + "support.every_limit"), language);
            assertTrue(lang.has(WaypointField.EVERY.labelKey()) && lang.has(WaypointField.EVERY.tooltipKey()), language);
        }
        assertEquals("höchstens so oft, wie das zubehör erlaubt",
                PlannerLang.of("de_de").text(FieldText.PREFIX + "support.every_limit", List.of()).toLowerCase(Locale.ROOT));
        assertEquals("at most as often as the accessory allows",
                PlannerLang.of("en_us").text(FieldText.PREFIX + "support.every_limit", List.of()).toLowerCase(Locale.ROOT));
    }

    @Test
    void timedValuesAreNoWiderThanTheWidestValueBefore() {
        VanillaFont font = VanillaFont.get();
        Waypoint base = action();
        for (String language : PlannerLang.LANGUAGES) {
            PlannerLang lang = PlannerLang.of(language);
            char decimal = lang.text(FieldText.PREFIX + "decimal", List.of()).charAt(0);
            int before = Math.max(font.width(lang.text(FieldText.PREFIX + "value.accessory.end_repeat", List.of())),
                    font.width(lang.text(FieldText.PREFIX + "value.accessory.repeated", List.of("4", "50"))));
            for (int ticks = MissionLimits.MIN_EVERY_TICKS; ticks <= MissionLimits.MAX_EVERY_TICKS; ticks++) {
                Waypoint w = base.withRepeat(Waypoint.Repeat.EVERY).withEveryTicks(ticks);
                for (WaypointField field : List.of(WaypointField.REPEAT, WaypointField.EVERY)) {
                    FieldText value = field.value(w, decimal, 0.0);
                    int width = font.width(lang.text(value.key(), value.args()));
                    assertTrue(width <= before, language + " " + ticks + " Ticks, " + field + ": " + width + " > " + before);
                }
            }
            FieldText timed = EditorCatalog.timedAccessory(4);
            assertTrue(font.width(lang.text(timed.key(), timed.args())) <= before, language + " Aktion 4 im Takt");
        }
    }

    @Test
    void timedTextsReadWellAndEndAtTheLastWaypoint() {
        PlannerLang en = PlannerLang.of("en_us");
        PlannerLang de = PlannerLang.of("de_de");
        String key = FieldText.PREFIX + "support.timed";
        assertEquals("Continues every 10 ticks (0.5 s) from waypoint 2", en.text(key, List.of("2", "10", "0.5")));
        assertEquals("Läuft im Takt von Wegpunkt 2 weiter: alle 10 Ticks (0,5 s)", de.text(key, List.of("2", "10", "0,5")));
        for (WaypointField field : List.of(WaypointField.REPEAT, WaypointField.EVERY)) {
            String english = en.text(field.tooltipKey(), List.of());
            String german = de.text(field.tooltipKey(), List.of());
            assertTrue(english.contains("last waypoint") && !english.contains("end of the mission"), field + ": " + english);
            assertTrue(german.contains("letzten Wegpunkt") && !german.contains("Ende der Mission"), field + ": " + german);
        }
    }

    @Test
    void intervalRowSitsOnTheActionsPageAndFitsEveryWindow() {
        VanillaFont font = VanillaFont.get();
        for (String language : PlannerLang.LANGUAGES) {
            EditorTexts texts = PlannerLang.of(language).texts();
            EditorLayout small = EditorLayout.compute(320, 240, true, texts, font::width);
            assertTrue(small.detailPaged());
            assertEquals(1, small.detailPage(WaypointField.EVERY.ordinal()));
            Rect every = small.detailRows().get(WaypointField.EVERY.ordinal());
            assertTrue(every.bottom() <= small.pageToggle().y(), language + ": Takt über dem Umschalter");
            assertTrue(small.problems(texts, font::width).isEmpty(), language + " 320×240: " + small.problems(texts, font::width));
            for (int[] size : new int[][]{{640, 480}, {854, 480}}) {
                EditorLayout layout = EditorLayout.compute(size[0], size[1], true, texts, font::width);
                assertFalse(layout.detailPaged(), language + " " + size[0]);
                Rect last = layout.detailRows().get(WaypointField.EVERY.ordinal());
                assertTrue(last.bottom() <= layout.detail().bottom());
                assertTrue(layout.problems(texts, font::width).isEmpty(), language + " " + size[0] + ": " + layout.problems(texts, font::width));
            }
        }
    }
}
