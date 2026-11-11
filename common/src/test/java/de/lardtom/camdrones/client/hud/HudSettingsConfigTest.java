package de.lardtom.camdrones.client.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.client.planner.CamClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudSettingsConfigTest {
    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void newFileHoldsMetresPerSecondAndTheOldVolume(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(CamClientConfig.FILE_NAME);
        CamClientConfig config = CamClientConfig.load(file);
        assertEquals(SpeedUnit.METRES_PER_SECOND, config.speedUnit());
        assertEquals(0.6, config.warningVolume(), 1.0E-9);
        assertEquals(ObstacleTone.VOLUME, (float) config.warningVolume());
        JsonObject json = read(file);
        assertEquals("m/s", json.get(CamClientConfig.SPEED_UNIT).getAsString());
        assertEquals(0.6, json.get(CamClientConfig.WARNING_VOLUME).getAsDouble(), 1.0E-9);
    }

    @Test
    void chosenUnitAndVolumeSurviveAReload(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(CamClientConfig.FILE_NAME);
        CamClientConfig config = CamClientConfig.load(file);
        assertTrue(config.speedUnit(SpeedUnit.KILOMETRES_PER_HOUR));
        assertTrue(config.warningVolume(0.333));
        assertEquals("km/h", read(file).get(CamClientConfig.SPEED_UNIT).getAsString());
        CamClientConfig again = CamClientConfig.load(file);
        assertEquals(SpeedUnit.KILOMETRES_PER_HOUR, again.speedUnit());
        assertEquals(0.33, again.warningVolume(), 1.0E-9);
        assertTrue(again.warningVolume(0.0));
        assertEquals(0.0, CamClientConfig.load(file).warningVolume(), 1.0E-9);
        assertTrue(again.warningVolume(7.0));
        assertEquals(1.0, CamClientConfig.load(file).warningVolume(), 1.0E-9);
    }

    @Test
    void oldFileWithOnlyPreviewMarkersIsCompletedAndKeepsItsValue(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(CamClientConfig.FILE_NAME);
        Files.writeString(file, "{\"preview_markers\": false, \"other\": 3}", StandardCharsets.UTF_8);
        CamClientConfig config = CamClientConfig.load(file);
        assertFalse(config.previewMarkers());
        JsonObject json = read(file);
        assertFalse(json.get(CamClientConfig.PREVIEW_MARKERS).getAsBoolean());
        assertEquals("m/s", json.get(CamClientConfig.SPEED_UNIT).getAsString());
        assertEquals(0.6, json.get(CamClientConfig.WARNING_VOLUME).getAsDouble(), 1.0E-9);
        assertEquals(3, json.get("other").getAsInt());
    }

    @Test
    void brokenValuesFallBackToTheDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(CamClientConfig.FILE_NAME);
        for (String broken : new String[]{"{\"speed_unit\": \"mph\", \"warning_volume\": 3}", "{\"speed_unit\": 1, \"warning_volume\": \"laut\"}",
                "{\"speed_unit\": \"km/h\", \"warning_volume\": -0.5}"}) {
            Files.writeString(file, broken, StandardCharsets.UTF_8);
            CamClientConfig config = CamClientConfig.load(file);
            assertEquals(0.6, config.warningVolume(), 1.0E-9, broken);
            assertEquals(0.6, read(file).get(CamClientConfig.WARNING_VOLUME).getAsDouble(), 1.0E-9, broken);
            assertEquals(broken.contains("km/h") ? SpeedUnit.KILOMETRES_PER_HOUR : SpeedUnit.METRES_PER_SECOND, config.speedUnit(), broken);
        }
    }

    @Test
    void volumeIsClampedAndRoundedToWholePercent() {
        assertEquals(0.0, CamClientConfig.clampVolume(-1.0), 1.0E-9);
        assertEquals(1.0, CamClientConfig.clampVolume(1.5), 1.0E-9);
        assertEquals(0.46, CamClientConfig.clampVolume(0.456), 1.0E-9);
        assertEquals(CamClientConfig.WARNING_VOLUME_DEFAULT, CamClientConfig.clampVolume(Double.NaN), 1.0E-9);
    }
}
