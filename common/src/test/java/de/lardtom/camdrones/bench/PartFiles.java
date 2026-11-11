package de.lardtom.camdrones.bench;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.part.CellChemistry;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class PartFiles {
    public static final String NAMESPACE = "camdrones";
    public static final String ROOT = "data/" + NAMESPACE + "/propwash_parts";
    public static final List<String> ORDER = List.of("chemistry", "frame", "stack", "motor", "prop", "video", "battery", "accessory");

    private static Map<Identifier, Object> registered;

    private PartFiles() {
    }

    public static synchronized Map<Identifier, Object> registerAll() {
        if (registered != null) {
            return registered;
        }
        Map<Identifier, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Object> entry : parseAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof CellChemistry chemistry) {
                out.put(entry.getKey(), PropwashApi.parts().registerChemistry(chemistry));
            } else if (value instanceof PartDefinition definition) {
                out.put(entry.getKey(), PropwashApi.parts().register(definition));
            }
        }
        registered = out;
        return out;
    }

    public static Map<Identifier, Object> parseAll() {
        Path resources = root();
        Map<Identifier, Object> out = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (String category : ORDER) {
            Path dir = resources.resolve(category);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            for (Path file : jsonFiles(dir)) {
                String name = file.getFileName().toString();
                Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, name.substring(0, name.length() - ".json".length()));
                try {
                    out.put(id, parse(category, id, read(file)));
                } catch (RuntimeException e) {
                    errors.add(category + "/" + name + ": " + e.getMessage());
                }
            }
        }
        try (Stream<Path> dirs = Files.list(resources)) {
            for (Path dir : dirs.toList()) {
                if (!ORDER.contains(dir.getFileName().toString())) {
                    errors.add("unbekannter Kategorie-Ordner: " + dir.getFileName());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Fehlerhafte Bauteil-Dateien:\n" + String.join("\n", errors));
        }
        return out;
    }

    public static Map<Identifier, JsonObject> rawFiles(String category) {
        Map<Identifier, JsonObject> out = new LinkedHashMap<>();
        Path dir = root().resolve(category);
        if (!Files.isDirectory(dir)) {
            return out;
        }
        for (Path file : jsonFiles(dir)) {
            String name = file.getFileName().toString();
            out.put(Identifier.fromNamespaceAndPath(NAMESPACE, name.substring(0, name.length() - ".json".length())), read(file));
        }
        return out;
    }

    public static Path root() {
        URL url = PartFiles.class.getClassLoader().getResource(ROOT);
        if (url == null) {
            throw new IllegalStateException(ROOT + " fehlt im Klassenpfad");
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Object parse(String category, Identifier id, JsonObject json) {
        if ("chemistry".equals(category)) {
            return PropwashApi.parts().chemistryFromJson(id, json);
        }
        PartKind kind = PartKind.byKey(category).orElseThrow(() -> new IllegalArgumentException("unbekannte Kategorie " + category));
        return PropwashApi.parts().fromJson(kind, id, json);
    }

    private static List<Path> jsonFiles(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonObject read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
