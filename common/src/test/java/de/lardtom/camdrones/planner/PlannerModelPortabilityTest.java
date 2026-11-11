package de.lardtom.camdrones.planner;

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

class PlannerModelPortabilityTest {
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(static\\s+)?([\\w.]+)", Pattern.MULTILINE);
    private static final Pattern COMMENT = Pattern.compile("(^|\\s)/[/*]", Pattern.MULTILINE);
    private static final List<String> ALLOWED = List.of("java.", "de.lardtom.camdrones.logic.", "de.lardtom.camdrones.planner.model.");

    @Test
    void editorModelsUseOnlyJavaAndLogic() throws IOException {
        Path dir = RepoFiles.root().resolve("common/src/main/java/de/lardtom/camdrones/planner/model");
        List<String> bad = new ArrayList<>();
        int files = 0;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                files++;
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = IMPORT.matcher(text);
                while (m.find()) {
                    String name = m.group(2);
                    if (ALLOWED.stream().noneMatch(name::startsWith)) {
                        bad.add(dir.relativize(file) + ": " + name);
                    }
                }
                if (COMMENT.matcher(text).find()) {
                    bad.add(dir.relativize(file) + ": Kommentar");
                }
            }
        }
        assertTrue(files >= 10, "Modellklassen gefunden: " + files);
        assertTrue(bad.isEmpty(), String.join("\n", bad));
    }
}
