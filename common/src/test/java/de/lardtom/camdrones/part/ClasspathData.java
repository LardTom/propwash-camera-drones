package de.lardtom.camdrones.part;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class ClasspathData {
    private static final Map<String, Set<String>> TAGS = new HashMap<>();

    private ClasspathData() {
    }

    public static boolean exists(String resource) {
        return ClasspathData.class.getClassLoader().getResource(resource) != null;
    }

    public static Optional<JsonObject> json(String resource) {
        try (InputStream in = ClasspathData.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Map<String, JsonObject> jsonTree(String folder) {
        Map<String, JsonObject> out = new TreeMap<>();
        try {
            Enumeration<URL> roots = ClasspathData.class.getClassLoader().getResources(folder);
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                walk(url, (relative, file) -> out.putIfAbsent(relative, read(file)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    public static Map<String, JsonObject> jsonTree(Path folder) {
        Map<String, JsonObject> out = new TreeMap<>();
        if (!Files.isDirectory(folder)) {
            return out;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                out.put(folder.relativize(file).toString().replace('\\', '/'), read(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    public static boolean itemExists(String id) {
        int colon = id.indexOf(':');
        String namespace = id.substring(0, colon);
        String path = id.substring(colon + 1);
        return exists("assets/" + namespace + "/items/" + path + ".json");
    }

    public static boolean itemTagExists(String id) {
        int colon = id.indexOf(':');
        return exists("data/" + id.substring(0, colon) + "/tags/item/" + id.substring(colon + 1) + ".json");
    }

    public static synchronized Set<String> itemTag(String id) {
        Set<String> cached = TAGS.get(id);
        if (cached != null) {
            return cached;
        }
        Set<String> items = new LinkedHashSet<>();
        int colon = id.indexOf(':');
        Optional<JsonObject> tag = json("data/" + id.substring(0, colon) + "/tags/item/" + id.substring(colon + 1) + ".json");
        if (tag.isEmpty()) {
            items.add("#" + id);
        } else {
            TAGS.put(id, Set.of("#" + id));
            for (JsonElement value : tag.get().getAsJsonArray("values")) {
                String entry = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
                if (entry.startsWith("#")) {
                    items.addAll(itemTag(entry.substring(1)));
                } else {
                    items.add(entry);
                }
            }
        }
        Set<String> result = Set.copyOf(items);
        TAGS.put(id, result);
        return result;
    }

    private interface Visitor {
        void accept(String relative, Path file);
    }

    private static void walk(URL url, Visitor visitor) throws IOException {
        Path root;
        try {
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                FileSystem fs;
                try {
                    fs = FileSystems.newFileSystem(uri, Map.of());
                } catch (FileSystemAlreadyExistsException e) {
                    fs = FileSystems.getFileSystem(uri);
                }
                root = fs.provider().getPath(uri);
            } else {
                root = Path.of(uri);
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.getFileName() != null && p.getFileName().toString().endsWith(".json")).forEach(files::add);
        }
        Map<String, Path> sorted = new LinkedHashMap<>();
        for (Path file : files) {
            sorted.put(root.relativize(file).toString().replace('\\', '/'), file);
        }
        sorted.forEach(visitor::accept);
    }

    private static JsonObject read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
