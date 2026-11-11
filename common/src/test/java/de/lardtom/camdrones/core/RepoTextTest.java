package de.lardtom.camdrones.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoTextTest {
    private static final Set<String> FORBIDDEN_TOKEN_HASHES = Set.of(
            "384585e6cc15335fd566dba3455fdf90f3c6183ab9f09027a2b0bf4a656cfd93",
            "30452f10d5fcecdbb9428d237916efc65d04be6bb20a6eee810dfc49c8f3b387",
            "5daf812b4cad7b20870fd9040a42ee6895547dc467a2b65a193b5e8eb66b1dc5",
            "d4690300ba7d6a12b9b5b77a7a13edfbfd7eb0b5aaa8c5672eb052abb9672b6e",
            "933e3d170e5134b1ad902e62476f7769a367011f2be071afeec826346f91105b",
            "c857d09db23e6822e3600bc06ad8d58f92ed62bc8efd81c753f77048662cb97d",
            "c70eca6b0f88f44d81a41311647e50fda1ac454ec04ffd442b0eb4743a993131");
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");

    @Test
    void noForbiddenNamesInRepository() throws IOException {
        List<String> hits = new ArrayList<>();
        Path root = RepoFiles.root();
        for (Path file : RepoFiles.textFiles()) {
            String text = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            Matcher matcher = TOKEN.matcher(text);
            while (matcher.find()) {
                if (FORBIDDEN_TOKEN_HASHES.contains(sha256(matcher.group()))) {
                    hits.add(root.relativize(file) + " @" + matcher.start());
                }
            }
        }
        assertTrue(hits.isEmpty(), "Verbotene Namen gefunden: " + hits);
    }

    @Test
    void scansTheWholeProject() {
        List<Path> files = RepoFiles.textFiles();
        Path root = RepoFiles.root();
        assertTrue(files.contains(root.resolve("docs/design.md")), "docs/design.md fehlt im Scan");
        assertTrue(files.contains(root.resolve("gradle.properties")), "gradle.properties fehlt im Scan");
        assertFalse(files.isEmpty());
    }

    private static String sha256(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
