package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.planner.model.AutoShotMenu;
import de.lardtom.camdrones.planner.model.FollowMenu;
import de.lardtom.camdrones.planner.model.EditorCatalog;
import de.lardtom.camdrones.planner.model.ExitSetting;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.MissionField;
import de.lardtom.camdrones.planner.model.MissionTemplates;
import de.lardtom.camdrones.planner.model.UploadCheck;
import de.lardtom.camdrones.planner.model.WaypointField;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerLangKeysTest {
    private static final Pattern PREFIXED = Pattern.compile("PREFIX \\+ \"([a-z_.]+)\"");
    private static final Pattern STATUS = Pattern.compile("Status\\.(?:info|error)\\(\"([a-z_.]+)\"");
    private static final Pattern FIELD_TEXT = Pattern.compile("FieldText\\.of\\(\"([a-z_.]+)\"");
    private static final List<String> SOURCES = List.of("common/src/main/java/de/lardtom/camdrones/planner",
            "common/src/main/java/de/lardtom/camdrones/client/planner");

    private static Set<String> expectedKeys() throws IOException {
        Set<String> keys = new TreeSet<>();
        keys.add("item.camdrones.flight_planner");
        for (String dir : SOURCES) {
            try (Stream<Path> walk = Files.walk(RepoFiles.root().resolve(dir))) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    for (Pattern pattern : List.of(PREFIXED, STATUS, FIELD_TEXT)) {
                        Matcher m = pattern.matcher(text);
                        while (m.find()) {
                            String key = m.group(1);
                            if (!key.endsWith(".")) {
                                keys.add(FieldText.PREFIX + key);
                            }
                        }
                    }
                }
            }
        }
        for (WaypointField field : WaypointField.values()) {
            keys.add(field.labelKey());
            keys.add(field.tooltipKey());
        }
        for (MissionField field : MissionField.values()) {
            keys.add(field.labelKey());
            keys.add(field.tooltipKey());
        }
        keys.addAll(EditorCatalog.fieldLabelKeys());
        keys.addAll(EditorCatalog.settingLabelKeys());
        keys.add(EditorCatalog.GROUND_KEY + ".tooltip");
        keys.add(EditorCatalog.RECORD_KEY + ".tooltip");
        for (UploadCheck.Result result : UploadCheck.Result.values()) {
            keys.add(result.messageKey());
        }
        for (ExitSetting.Result result : ExitSetting.Result.values()) {
            keys.add(result.messageKey());
        }
        keys.add(ExitSetting.TOOLTIP_KEY);
        for (MissionTemplates.Template template : MissionTemplates.Template.values()) {
            keys.add(template.labelKey());
            keys.add(template.tooltipKey());
        }
        for (Waypoint.Action action : Waypoint.Action.values()) {
            keys.add(FieldText.PREFIX + "value.action." + action.name().toLowerCase(Locale.ROOT));
        }
        for (AutoShots.Shot shot : AutoShotMenu.SHOTS) {
            keys.add(AutoShotMenu.labelKey(shot));
            keys.add(AutoShotMenu.tooltipKey(shot));
        }
        for (FollowController.Style style : FollowMenu.STYLES) {
            keys.add(FollowMenu.labelKey(style));
            keys.add(FollowMenu.tooltipKey(style));
        }
        for (String button : List.of("send", "start", "pause", "stop", "auto", "follow", "template", "save")) {
            keys.add(FieldText.PREFIX + "button." + button);
            keys.add(FieldText.PREFIX + "button." + button + ".short");
            keys.add(FieldText.PREFIX + "button." + button + ".tooltip");
        }
        for (String command : List.of("start", "pause", "stop")) {
            keys.add(FieldText.PREFIX + "status.command." + command);
        }
        for (char decimal : new char[]{'.', ','}) {
            EditorCatalog.fieldValueSamples(decimal).forEach(text -> keys.add(text.key()));
            EditorCatalog.settingValueSamples(decimal).forEach(text -> keys.add(text.key()));
        }
        return keys;
    }

    @Test
    void everyUsedKeyExistsInBothLanguages() throws IOException {
        Set<String> expected = expectedKeys();
        assertTrue(expected.size() > 120, "Schlüssel: " + expected.size());
        for (String language : PlannerLang.LANGUAGES) {
            PlannerLang lang = PlannerLang.of(language);
            Set<String> missing = new TreeSet<>();
            for (String key : expected) {
                if (!lang.has(key)) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(), language + " fehlt: " + missing);
        }
    }

    @Test
    void plannerFilesHaveNoUnusedKeysAndMatchPlaceholders() throws IOException {
        Set<String> expected = expectedKeys();
        Map<String, String> en = PlannerLang.plannerFile("en_us");
        Map<String, String> de = PlannerLang.plannerFile("de_de");
        assertEquals(en.keySet(), de.keySet());
        String sources = sources();
        Set<String> unused = new TreeSet<>();
        for (String key : en.keySet()) {
            String bare = key.startsWith(FieldText.PREFIX) ? key.substring(FieldText.PREFIX.length()) : key;
            if (!expected.contains(key) && !sources.contains("\"" + bare + "\"")) {
                unused.add(key);
            }
        }
        assertTrue(unused.isEmpty(), "nicht benutzt: " + unused);
        for (String key : en.keySet()) {
            assertEquals(count(en.get(key)), count(de.get(key)), "Platzhalter in " + key);
            assertTrue(key.startsWith("camdrones.planner.") || key.startsWith("item.camdrones."), key);
        }
    }

    @Test
    void decimalSeparatorPerLanguage() {
        assertEquals('.', PlannerLang.of("en_us").decimal());
        assertEquals(',', PlannerLang.of("de_de").decimal());
    }

    private static String sources() throws IOException {
        StringBuilder out = new StringBuilder();
        for (String dir : SOURCES) {
            try (Stream<Path> walk = Files.walk(RepoFiles.root().resolve(dir))) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    out.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
                }
            }
        }
        return out.toString();
    }

    private static int count(String text) {
        return text.replace("%%", "").split("%s", -1).length - 1;
    }
}
