package de.lardtom.camdrones.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangFilesTest {
    private static final List<String> ASCII_UMLAUT_WORDS = List.of("fuer", "ueber", "koennen", "moeglich", "zurueck", "hoehe",
            "waehlen", "aendern", "groesse", "schliessen", "gross", "naechste", "spaeter", "pruefen", "fluege");

    private static List<Path> moduleDirs() throws IOException {
        try (Stream<Path> dirs = Files.list(RepoFiles.root().resolve("common/src/lang"))) {
            return dirs.filter(Files::isDirectory).sorted().toList();
        }
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void germanAndEnglishHaveTheSameKeys() throws IOException {
        List<Path> modules = moduleDirs();
        assertTrue(!modules.isEmpty(), "keine Sprachmodule");
        for (Path module : modules) {
            Set<String> de = new TreeSet<>(read(module.resolve("de_de.json")).keySet());
            Set<String> en = new TreeSet<>(read(module.resolve("en_us.json")).keySet());
            assertEquals(en, de, module.getFileName().toString());
            for (String key : de) {
                assertTrue(key.startsWith("camdrones.") || key.startsWith("item.camdrones.") || key.startsWith("block.camdrones.")
                        || key.startsWith("key.camdrones.") || key.startsWith("key.category.camdrones.") || key.startsWith("gamerule.camdrones.") || key.startsWith("part.camdrones."), key);
            }
        }
    }

    @Test
    void germanUsesRealUmlauts() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path module : moduleDirs()) {
            JsonObject de = read(module.resolve("de_de.json"));
            for (String key : de.keySet()) {
                String value = de.get(key).getAsString().toLowerCase(Locale.ROOT);
                for (String word : ASCII_UMLAUT_WORDS) {
                    if (value.matches(".*\\b" + word + "\\b.*")) {
                        hits.add(key + ": " + word);
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(), "Umschreibung statt Umlaut: " + hits);
    }
}
