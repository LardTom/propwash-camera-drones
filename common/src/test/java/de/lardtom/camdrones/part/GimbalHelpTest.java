package de.lardtom.camdrones.part;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.bench.Bench;
import de.lardtom.camdrones.bench.PartFiles;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.FrameDefinition;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GimbalHelpTest {
    private static final List<Identifier> GIMBALS = List.of(GimbalTable.COMPACT, GimbalTable.TOUR, GimbalTable.CINE);
    private static final Identifier CINE_FRAME = Identifier.fromNamespaceAndPath(PartFiles.NAMESPACE, "cine_frame");
    private static final String RULE_KEY = "gamerule.propwash.part_compatibility";

    private record Language(String code, String guide, Pattern only, String frameAfter, String frameBefore, String fitsMarker,
                            String cineGimbal) {
    }

    private static final List<Language> LANGUAGES = List.of(
            new Language("de_de", "docs/guide.de.md", Pattern.compile("\\bnur\\b"), "-Rahmen", "Rahmen ",
                    "Kamera-Rahmen haben zwei Halterungen", "-Gimbal"),
            new Language("en_us", "docs/guide.md", Pattern.compile("\\bonly\\b", Pattern.CASE_INSENSITIVE), " frame", "Frame ",
                    "Camera frames have two mounts", " gimbal"));

    private static JsonObject lang(Language language) throws IOException {
        return JsonParser.parseString(Files.readString(RepoFiles.root().resolve("common/src/lang/parts/" + language.code() + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String propwashText(Language language, String key) throws IOException {
        String path = "assets/propwash/lang/" + language.code() + ".json";
        try (InputStream in = GimbalHelpTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " fehlt");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(json.has(key), language.code() + " fehlt " + key);
            return json.get(key).getAsString();
        }
    }

    private static Map<Identifier, String> frameWords(JsonObject lang) {
        Map<Identifier, String> out = new LinkedHashMap<>();
        for (Object value : PartFiles.registerAll().values()) {
            if (value instanceof FrameDefinition frame) {
                String key = "camdrones.frame_short." + frame.id().getPath();
                assertTrue(lang.has(key), key);
                out.put(frame.id(), lang.get(key).getAsString().split(" ")[0]);
            }
        }
        assertEquals(3, out.size(), out.toString());
        return out;
    }

    private static Set<Identifier> framesOf(Identifier gimbal) {
        PartFiles.registerAll();
        AccessoryDefinition definition = (AccessoryDefinition) PropwashApi.parts().get(gimbal).orElseThrow(() -> new AssertionError(gimbal.toString()));
        assertFalse(definition.frames().isEmpty(), gimbal + ": frames leer");
        return Set.copyOf(definition.frames());
    }

    private static Set<Identifier> framesNamedIn(String line, Map<Identifier, String> words, Language language) {
        Set<Identifier> out = new HashSet<>();
        for (Map.Entry<Identifier, String> entry : words.entrySet()) {
            String word = Pattern.quote(entry.getValue());
            Pattern named = Pattern.compile("(?i)\\b" + word + "(?:\\s+Folding)?" + Pattern.quote(language.frameAfter())
                    + "|" + Pattern.quote(language.frameBefore().trim()) + "\\s+" + word + "\\b");
            if (named.matcher(line).find()) {
                out.add(entry.getKey());
            }
        }
        return out;
    }

    private static String paragraph(String text, String marker) {
        int at = text.indexOf(marker);
        assertTrue(at >= 0, "Absatz fehlt: " + marker);
        int start = text.lastIndexOf("\n\n", at);
        int end = text.indexOf("\n\n", at);
        return text.substring(start < 0 ? 0 : start, end < 0 ? text.length() : end).replace('\n', ' ');
    }

    private static String gimbalSection(String text) {
        int start = text.indexOf("\n### Gimbal\n");
        assertTrue(start >= 0, "Abschnitt ### Gimbal fehlt");
        int end = text.indexOf("\n#", start + 1);
        return text.substring(start, end < 0 ? text.length() : end);
    }

    @Test
    void gimbalHelpNeverLimitsAGimbalToFewerFramesThanItsFramesList() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Language language : LANGUAGES) {
            JsonObject lang = lang(language);
            Map<Identifier, String> words = frameWords(lang);
            for (Identifier gimbal : GIMBALS) {
                Set<Identifier> frames = framesOf(gimbal);
                String prefix = "camdrones.help." + gimbal.getPath() + ".";
                for (String key : lang.keySet()) {
                    if (!key.startsWith(prefix)) {
                        continue;
                    }
                    String line = lang.get(key).getAsString();
                    Set<Identifier> named = framesNamedIn(line, words, language);
                    if (!frames.containsAll(named) || (!named.isEmpty() && language.only().matcher(line).find() && !named.equals(frames))) {
                        wrong.add(language.code() + " " + key + " = „" + line + "“ nennt " + named + ", frames " + frames);
                    }
                }
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    void cineGimbalFitsTheCompactButIsTooHeavyForItAndCostsTheTourAboutAThird() {
        BuildStats tour = Bench.stats(CamPresets.TOUR.build());
        for (CamPresets.Entry entry : CamPresets.ALL) {
            BuildSpec spec = entry.build().withAccessory(AccessorySlot.FRONT, GimbalTable.CINE);
            assertTrue(Bench.check(spec).buildable(), entry.path() + " + Kino-Gimbal: " + Bench.check(spec).problems());
            BuildStats stats = Bench.stats(spec);
            assertEquals(entry == CamPresets.COMPACT, stats.warnings().contains("cannot_hover"), entry.path() + ": " + stats.warnings());
            if (entry == CamPresets.TOUR) {
                double share = stats.hoverFlightTimeMin() / tour.hoverFlightTimeMin();
                assertTrue(share > 0.6 && share < 0.75, "Tour mit Kino-Gimbal schwebt " + share + " der Zeit");
            }
        }
    }

    @Test
    void guideNamesEveryFrameOfTheFramesListAndTheRuleThatLiftsIt() throws IOException {
        Set<Identifier> frames = new HashSet<>();
        for (Identifier gimbal : GIMBALS) {
            frames.addAll(framesOf(gimbal));
        }
        for (Language language : LANGUAGES) {
            Map<Identifier, String> words = frameWords(lang(language));
            String text = Files.readString(RepoFiles.root().resolve(language.guide()), StandardCharsets.UTF_8);
            String fits = paragraph(text, language.fitsMarker());
            for (Identifier frame : frames) {
                assertTrue(fits.contains(words.get(frame)), language.guide() + ": " + frame + " fehlt in „" + fits + "“");
            }
            String rule = propwashText(language, RULE_KEY);
            assertTrue(fits.contains(rule), language.guide() + ": Welt-Regel „" + rule + "“ fehlt in „" + fits + "“");
        }
    }

    @Test
    void guideGimbalSectionCallsTheCineGimbalByItsName() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Language language : LANGUAGES) {
            String cine = frameWords(lang(language)).get(CINE_FRAME);
            String section = gimbalSection(Files.readString(RepoFiles.root().resolve(language.guide()), StandardCharsets.UTF_8));
            Matcher bare = Pattern.compile("(?i)\\b" + Pattern.quote(cine) + "\\b(?!" + Pattern.quote(language.cineGimbal()) + ")").matcher(section);
            while (bare.find()) {
                int lineStart = section.lastIndexOf('\n', bare.start()) + 1;
                int lineEnd = section.indexOf('\n', bare.end());
                wrong.add(language.guide() + ": „" + section.substring(lineStart, lineEnd < 0 ? section.length() : lineEnd).trim() + "“");
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }
}
