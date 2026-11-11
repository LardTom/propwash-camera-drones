package de.lardtom.camdrones.client.planner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamClientConfigTest {
    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void missingFileIsCreatedWithPreviewMarkersOn(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config").resolve(CamClientConfig.FILE_NAME);
        CamClientConfig config = CamClientConfig.load(file);
        assertTrue(config.previewMarkers());
        assertTrue(Files.isRegularFile(file), "Datei angelegt");
        assertTrue(read(file).get(CamClientConfig.PREVIEW_MARKERS).getAsBoolean());
        assertEquals("camdrones-client.json", file.getFileName().toString());
    }

    @Test
    void switchedOffStaysOffAfterReload(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(CamClientConfig.FILE_NAME);
        assertTrue(CamClientConfig.load(file).previewMarkers(false));
        assertFalse(read(file).get(CamClientConfig.PREVIEW_MARKERS).getAsBoolean());
        CamClientConfig again = CamClientConfig.load(file);
        assertFalse(again.previewMarkers());
        assertTrue(again.previewMarkers(true));
        assertTrue(CamClientConfig.load(file).previewMarkers());
    }

    @Test
    void brokenOrForeignValuesFallBackToTheDefault(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(CamClientConfig.FILE_NAME);
        Files.writeString(file, "{ kaputt", StandardCharsets.UTF_8);
        assertTrue(CamClientConfig.load(file).previewMarkers());
        assertTrue(read(file).get(CamClientConfig.PREVIEW_MARKERS).getAsBoolean(), "neu geschrieben");
        Files.writeString(file, "{\"preview_markers\": \"nein\"}", StandardCharsets.UTF_8);
        assertTrue(CamClientConfig.load(file).previewMarkers());
        Files.writeString(file, "[]", StandardCharsets.UTF_8);
        assertTrue(CamClientConfig.load(file).previewMarkers());
    }

    @Test
    void otherKeysSurviveASave(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(CamClientConfig.FILE_NAME);
        Files.writeString(file, "{\"units\": \"km/h\", \"preview_markers\": true}", StandardCharsets.UTF_8);
        CamClientConfig.load(file).previewMarkers(false);
        JsonObject saved = read(file);
        assertEquals("km/h", saved.get("units").getAsString());
        assertFalse(saved.get(CamClientConfig.PREVIEW_MARKERS).getAsBoolean());
    }
}
