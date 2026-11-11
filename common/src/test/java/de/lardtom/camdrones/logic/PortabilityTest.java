package de.lardtom.camdrones.logic;

import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortabilityTest {
    private static final List<String> FORBIDDEN_PREFIXES = List.of("net.minecraft.", "com.mojang.", "de.lardtom.propwash.",
            "net.fabricmc.", "net.neoforged.", "org.slf4j.", "com.google.gson.");
    private static final String OWN_PACKAGE = "de.lardtom.camdrones.";
    private static final String LOGIC_PACKAGE = "de.lardtom.camdrones.logic.";
    private static final List<String> TEST_PREFIXES = List.of("java.", "javax.tools.", "org.junit.", LOGIC_PACKAGE);
    private static final String BACKPORT_RELEASE = "21";
    private static final String LINE_COMMENT = "/" + "/";
    private static final String BLOCK_START = "/" + "*";
    private static final String BLOCK_END = "*" + "/";

    static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle")) && Files.isDirectory(dir.resolve("common"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Projektwurzel nicht gefunden");
    }

    private static List<Path> sources(String relative) throws IOException {
        Path base = root().resolve(relative);
        try (Stream<Path> walk = Files.walk(base)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static List<String> imports(Path source) throws IOException {
        List<String> found = new ArrayList<>();
        for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import ")) {
                found.add(trimmed.substring(7).replace("static ", "").replace(";", "").trim());
            }
        }
        return found;
    }

    @Test
    void logicImportsOnlyJavaAndItself() throws IOException {
        Path logic = root().resolve("common/src/main/java/de/lardtom/camdrones/logic");
        List<Path> sources = sources("common/src/main/java/de/lardtom/camdrones/logic");
        assertFalse(sources.isEmpty(), "logic ist leer");
        List<String> hits = new ArrayList<>();
        for (Path source : sources) {
            for (String target : imports(source)) {
                boolean foreign = FORBIDDEN_PREFIXES.stream().anyMatch(target::startsWith);
                boolean ownOutsideLogic = target.startsWith(OWN_PACKAGE) && !target.startsWith(LOGIC_PACKAGE);
                boolean onlyJava = target.startsWith("java.") || target.startsWith(LOGIC_PACKAGE);
                if (foreign || ownOutsideLogic || !onlyJava) {
                    hits.add(logic.relativize(source) + ": " + target);
                }
            }
        }
        assertTrue(hits.isEmpty(), "logic muss ohne Minecraft, Loader und Propwash auskommen: " + hits);
    }

    @Test
    void logicTestsTravelWithThePackage() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path source : sources("common/src/test/java/de/lardtom/camdrones/logic")) {
            for (String target : imports(source)) {
                if (TEST_PREFIXES.stream().noneMatch(target::startsWith)) {
                    hits.add(source.getFileName() + ": " + target);
                }
            }
        }
        assertTrue(hits.isEmpty(), "Tests in logic dürfen nur Java, JUnit und logic nutzen: " + hits);
    }

    @Test
    void logicCompilesForTheBackportRelease() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "kein Java-Compiler in der Test-Laufzeit");
        Path out = Files.createTempDirectory("camdrones-logic");
        try {
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromPaths(sources("common/src/main/java/de/lardtom/camdrones/logic"));
                List<String> options = List.of("--release", BACKPORT_RELEASE, "-Xlint:all", "-Werror", "-proc:none", "-d", out.toString());
                boolean ok = compiler.getTask(null, files, diagnostics, options, null, units).call();
                List<String> messages = new ArrayList<>();
                for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                    messages.add(d.getKind() + " " + d.getSource() + ":" + d.getLineNumber() + " " + d.getMessage(null));
                }
                assertTrue(ok && messages.isEmpty(), "logic baut nicht mit --release " + BACKPORT_RELEASE + ": " + messages);
            }
        } finally {
            try (Stream<Path> walk = Files.walk(out)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    @Test
    void logicHasNoComments() throws IOException {
        List<String> hits = new ArrayList<>();
        List<Path> all = new ArrayList<>(sources("common/src/main/java/de/lardtom/camdrones/logic"));
        all.addAll(sources("common/src/test/java/de/lardtom/camdrones/logic"));
        for (Path source : all) {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String trimmed = lines.get(i).trim();
                if (trimmed.startsWith(LINE_COMMENT) || trimmed.startsWith(BLOCK_START) || trimmed.contains(" " + LINE_COMMENT)
                        || trimmed.endsWith(BLOCK_END)) {
                    hits.add(source.getFileName() + ":" + (i + 1));
                }
            }
        }
        assertTrue(hits.isEmpty(), "Kommentare in logic: " + hits);
    }
}
