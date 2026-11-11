package de.lardtom.camdrones.crew;

import de.lardtom.camdrones.core.RepoFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CrewModelPortabilityTest {
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(static\\s+)?([\\w.]+)", Pattern.MULTILINE);
    private static final Pattern COMMENT = Pattern.compile("(^|\\s)/[/*]", Pattern.MULTILINE);
    private static final List<String> ALLOWED = List.of("java.", "de.lardtom.camdrones.logic.", "de.lardtom.camdrones.crew.model.",
            "de.lardtom.camdrones.client.gimbal.model.", "de.lardtom.camdrones.planner.model.");
    private static final String MAIN = "common/src/main/java/de/lardtom/camdrones/";

    @Test
    void modelClassesUseOnlyJavaAndLogic() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String dir : List.of("crew/model", "client/gimbal/model")) {
            try (Stream<Path> walk = Files.walk(RepoFiles.root().resolve(MAIN + dir))) {
                files.addAll(walk.filter(p -> p.toString().endsWith(".java")).toList());
            }
        }
        files.add(RepoFiles.root().resolve(MAIN + "crew/client/CrewLayout.java"));
        List<String> bad = new ArrayList<>();
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = IMPORT.matcher(text);
            while (m.find()) {
                String name = m.group(2);
                if (ALLOWED.stream().noneMatch(name::startsWith)) {
                    bad.add(file.getFileName() + ": " + name);
                }
            }
        }
        assertTrue(files.size() >= 10, "Modellklassen gefunden: " + files.size());
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }

    @Test
    void ownSourcesHaveNoComments() throws IOException {
        List<Path> roots = List.of(RepoFiles.root().resolve(MAIN + "crew"), RepoFiles.root().resolve(MAIN + "client/gimbal"),
                RepoFiles.root().resolve(MAIN + "net/CrewPackets.java"), RepoFiles.root().resolve("common/src/test/java/de/lardtom/camdrones/crew"),
                RepoFiles.root().resolve("common/src/test/java/de/lardtom/camdrones/client/gimbal"));
        List<String> bad = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(file, StandardCharsets.UTF_8).replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
                    if (COMMENT.matcher(text).find()) {
                        bad.add(file.getFileName().toString());
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "Kommentare: " + bad);
    }
}
