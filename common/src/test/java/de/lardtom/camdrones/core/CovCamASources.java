package de.lardtom.camdrones.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class CovCamASources {
    private CovCamASources() {
    }

    public static Map<String, String> main() {
        Path root = RepoFiles.root().resolve("common/src/main/java");
        Map<String, String> out = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                out.put(root.relativize(file).toString().replace('\\', '/'), Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    public static Map<String, String> matching(String needle) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> file : main().entrySet()) {
            if (file.getValue().contains(needle)) {
                out.put(file.getKey(), file.getValue());
            }
        }
        return out;
    }
}
