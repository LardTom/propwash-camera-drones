package de.lardtom.camdrones.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class RepoFiles {
    private static final Set<String> SKIPPED_DIRS = Set.of("build", "runs", "run", "logs", "release", "bin", "out");
    private static final Set<String> TEXT_SUFFIXES = Set.of(".java", ".json", ".md", ".gradle", ".properties", ".toml", ".mcmeta", ".py", ".sh", ".txt", ".gitignore", ".gitattributes");

    private RepoFiles() {
    }

    public static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle")) && Files.isDirectory(dir.resolve("common"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Projektwurzel nicht gefunden");
    }

    public static List<Path> textFiles() {
        Path root = root();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> root.relativize(p).getNameCount() > 0)
                    .filter(p -> !skipped(root.relativize(p)))
                    .filter(RepoFiles::isText)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean skipped(Path relative) {
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            String name = relative.getName(i).toString();
            if (SKIPPED_DIRS.contains(name) || name.startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isText(Path file) {
        String name = file.getFileName().toString();
        for (String suffix : TEXT_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
