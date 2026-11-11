package de.lardtom.camdrones.planner;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.camdrones.planner.model.EditorCatalog;
import de.lardtom.camdrones.planner.model.EditorTexts;
import de.lardtom.camdrones.planner.model.FieldText;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

final class PlannerLang implements EditorCatalog.Translator {
    static final List<String> LANGUAGES = List.of("en_us", "de_de");

    private final String language;
    private final Map<String, String> entries;

    private PlannerLang(String language, Map<String, String> entries) {
        this.language = language;
        this.entries = entries;
    }

    static PlannerLang of(String language) {
        Map<String, String> map = new TreeMap<>();
        try (Stream<Path> modules = Files.list(RepoFiles.root().resolve("common/src/lang"))) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path file = module.resolve(language + ".json");
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    map.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new PlannerLang(language, map);
    }

    static Map<String, String> plannerFile(String language) {
        Map<String, String> map = new TreeMap<>();
        try {
            Path file = RepoFiles.root().resolve("common/src/lang/planner/" + language + ".json");
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                map.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return map;
    }

    String language() {
        return this.language;
    }

    boolean has(String key) {
        return this.entries.containsKey(key);
    }

    char decimal() {
        String value = this.entries.getOrDefault(FieldText.PREFIX + "decimal", ".");
        return value.length() == 1 ? value.charAt(0) : '.';
    }

    EditorTexts texts() {
        return EditorCatalog.texts(this, this.decimal());
    }

    @Override
    public String text(String key, List<String> args) {
        String template = this.entries.get(key);
        if (template == null) {
            throw new IllegalArgumentException("Sprachschlüssel fehlt (" + this.language + "): " + key);
        }
        return String.format(Locale.ROOT, template, args.toArray());
    }
}
