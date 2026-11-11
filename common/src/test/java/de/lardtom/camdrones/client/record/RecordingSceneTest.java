package de.lardtom.camdrones.client.record;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.bench.PartFiles;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.camdrones.client.record.model.RecordingMode;
import de.lardtom.propwash.guide.scene.Scene;
import de.lardtom.propwash.guide.scene.SceneStep;
import de.lardtom.propwash.guide.scene.TimedCue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingSceneTest {
    private static final List<String> LANGUAGES = List.of("en_us", "de_de");
    private static Scene scene;

    @BeforeAll
    static void build() {
        PartFiles.registerAll();
        scene = RecordingScene.create();
    }

    private static Map<String, String> lang(String language) throws IOException {
        Path file = RepoFiles.root().resolve("common/src/lang/rec-modes/" + language + ".json");
        JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            out.put(entry.getKey(), entry.getValue().getAsString());
        }
        return out;
    }

    private static int limit(String key) {
        if (key.endsWith(".title")) {
            return 38;
        }
        if (key.endsWith(".description")) {
            return 64;
        }
        return key.endsWith(".text") ? 150 : 90;
    }

    @Test
    void chapterInTheOwnNamespaceWithTheDesignedSteps() {
        assertEquals("camdrones:recording", scene.id());
        assertTrue(scene.keyPrefix().startsWith("camdrones.guide.scene."), scene.keyPrefix());
        assertEquals(RecordingScene.STEPS, scene.steps().stream().map(SceneStep::name).toList());
        for (SceneStep step : scene.steps()) {
            for (TimedCue timed : step.cues()) {
                assertTrue(timed.start() <= step.duration(), step.name() + " Cue nach dem Schrittende");
            }
        }
    }

    @Test
    void everyTextExistsInBothLanguagesWithinTheLimitsAndNothingIsUnused() throws IOException, IllegalAccessException {
        Set<String> used = new TreeSet<>(scene.translationKeys());
        for (RecordingMode mode : RecordingMode.values()) {
            used.add(mode.translationKey());
            used.add(mode.tooltipKey());
        }
        for (Field field : RecordTexts.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class && !field.getName().equals("PREFIX")) {
                used.add((String) field.get(null));
            }
        }
        List<String> problems = new ArrayList<>();
        Map<String, Map<String, String>> languages = new TreeMap<>();
        for (String language : LANGUAGES) {
            Map<String, String> lang = lang(language);
            languages.put(language, lang);
            for (String key : used) {
                String value = lang.get(key);
                if (value == null || value.isBlank()) {
                    problems.add(language + " fehlt " + key);
                } else if (key.startsWith("camdrones.guide.") && value.length() > limit(key)) {
                    problems.add(language + " " + key + " zu lang: " + value.length() + " > " + limit(key));
                }
            }
            Set<String> unused = new TreeSet<>(lang.keySet());
            unused.removeAll(used);
            if (!unused.isEmpty()) {
                problems.add(language + " nicht benutzt: " + unused);
            }
        }
        assertEquals(languages.get("en_us").keySet(), languages.get("de_de").keySet());
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void theLiveViewHintNamesBothModes() throws IOException {
        assertEquals("Direkte Aufnahme braucht die Live-Ansicht – Flashback-Modus nimmt im Hintergrund auf", lang("de_de").get(RecordTexts.NEEDS_LIVE_VIEW));
    }
}
