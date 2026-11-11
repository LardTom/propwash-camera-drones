package de.lardtom.camdrones.client.record;

import com.google.gson.JsonParser;
import de.lardtom.camdrones.client.planner.CamClientConfig;
import de.lardtom.camdrones.client.record.model.RecordingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordingModeConfigTest {
    @Test
    void withoutChoiceTheDefaultFollowsFlashbackAndIsNotWritten(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(CamClientConfig.FILE_NAME);
        CamClientConfig config = CamClientConfig.load(file);
        assertEquals(RecordingMode.FLASHBACK, config.recordingMode(true));
        assertEquals(RecordingMode.DIRECT, config.recordingMode(false));
        assertEquals(Optional.empty(), config.chosenRecordingMode());
        assertFalse(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().has(CamClientConfig.RECORDING_MODE));
    }

    @Test
    void choiceSurvivesReloadAndResetFollowsFlashbackAgain(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(CamClientConfig.FILE_NAME);
        assertTrue(CamClientConfig.load(file).recordingMode(RecordingMode.DIRECT));
        CamClientConfig reloaded = CamClientConfig.load(file);
        assertEquals(Optional.of(RecordingMode.DIRECT), reloaded.chosenRecordingMode());
        assertEquals(RecordingMode.DIRECT, reloaded.recordingMode(true));
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("\"recording_mode\": \"direct\""));
        assertTrue(reloaded.resetRecordingMode());
        assertEquals(RecordingMode.FLASHBACK, CamClientConfig.load(file).recordingMode(true));
    }

    @Test
    void storedFlashbackWithoutFlashbackRecordsDirectly(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(CamClientConfig.FILE_NAME);
        Files.writeString(file, "{\"recording_mode\": \"flashback\"}", StandardCharsets.UTF_8);
        CamClientConfig config = CamClientConfig.load(file);
        assertEquals(RecordingMode.FLASHBACK, config.recordingMode(true));
        assertEquals(RecordingMode.DIRECT, config.recordingMode(false));
        assertEquals(Optional.of(RecordingMode.FLASHBACK), config.chosenRecordingMode());
    }

    @Test
    void unknownValuesFallBackToTheDefault(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(CamClientConfig.FILE_NAME);
        Files.writeString(file, "{\"recording_mode\": 3, \"preview_markers\": true}", StandardCharsets.UTF_8);
        assertEquals(RecordingMode.FLASHBACK, CamClientConfig.load(file).recordingMode(true));
        Files.writeString(file, "{\"recording_mode\": \"video\"}", StandardCharsets.UTF_8);
        assertEquals(RecordingMode.DIRECT, CamClientConfig.load(file).recordingMode(false));
    }
}
