package de.lardtom.camdrones.client.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamOsdNamesTest {
    private static final List<String> LANGUAGES = List.of("en_us", "de_de");
    private static final int NARROWEST_LIST = 112;
    private static final int ROW_CHROME = 4 + 8 + 5 + 20;
    static final int NAME_ROOM = NARROWEST_LIST - ROW_CHROME;
    private static final String PROPWASH_ELEMENT = "propwash.osd.element.";

    private static JsonObject lang(String language) throws IOException {
        Path file = RepoFiles.root().resolve("common/src/lang/hud/" + language + ".json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject propwashLang(String language) throws IOException {
        String path = "assets/propwash/lang/" + language + ".json";
        try (InputStream in = CamOsdNamesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "Sprachdatei von Propwash fehlt im Testpfad: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<String> names(JsonObject lang) {
        List<String> out = new ArrayList<>();
        for (OsdElementSpec spec : CamOsdElements.specs(context -> CamOsdState.NONE)) {
            assertTrue(lang.has(spec.nameKey()), spec.nameKey());
            out.add(lang.get(spec.nameKey()).getAsString());
        }
        return out;
    }

    @Test
    void namesFitTheNarrowestListOfTheOsdEditorUncut() throws IOException {
        VanillaFont font = VanillaFont.get();
        assertEquals(75, NAME_ROOM);
        List<String> failures = new ArrayList<>();
        for (String language : LANGUAGES) {
            for (String name : names(lang(language))) {
                int width = font.width(name);
                if (width > NAME_ROOM) {
                    failures.add(language + " \"" + name + "\" " + width + " px");
                }
            }
        }
        assertTrue(failures.isEmpty(), "Namen breiter als die " + NAME_ROOM + " px der schmalsten Liste im OSD-Editor (GUI 320x240): " + failures);
    }

    @Test
    void namesAreDistinctFromEachOtherAndFromPropwashElements() throws IOException {
        for (String language : LANGUAGES) {
            Map<String, String> taken = new HashMap<>();
            JsonObject propwash = propwashLang(language);
            for (String key : propwash.keySet()) {
                if (key.startsWith(PROPWASH_ELEMENT) && key.indexOf('.', PROPWASH_ELEMENT.length()) < 0) {
                    taken.put(propwash.get(key).getAsString().toLowerCase(Locale.ROOT), key);
                }
            }
            assertTrue(taken.size() >= 40, language + ": Propwash-Elemente nicht gefunden");
            for (String name : names(lang(language))) {
                String before = taken.put(name.toLowerCase(Locale.ROOT), name);
                assertTrue(before == null, language + ": \"" + name + "\" gibt es schon (" + before + ")");
            }
        }
    }
}
