package de.lardtom.camdrones.client.gimbal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.client.planner.CamClientConfig;
import de.lardtom.camdrones.core.RepoFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GimbalServoSoundTest {
    private static final String CLIENT = "common/src/main/java/de/lardtom/camdrones/client/gimbal/GimbalClient.java";
    private static final String SERVO_KEY = "gimbal_servo_volume";
    private static final Path LANG = Path.of("common/src/lang/crew");
    private static final String TOOLTIP_KEY = "camdrones.crew.servo.volume.tooltip";

    private static String source() throws IOException {
        return Files.readString(RepoFiles.root().resolve(CLIENT), StandardCharsets.UTF_8);
    }

    @Test
    void servoSoundsAtTheDroneInsteadOfAsUiSound() throws IOException {
        String source = source();
        assertFalse(source.contains("SimpleSoundInstance.forUI(CALIBRATE"), "Servo-Surren als UI-Ton ohne Ort");
        assertTrue(source.contains("new EntityBoundSoundInstance(CALIBRATE"), "Servo-Surren nicht an der Drohne");
    }

    @Test
    void sliderTooltipNamesRecenterAndTakeOverInsteadOfMoving() throws IOException {
        JsonObject en = JsonParser.parseString(Files.readString(RepoFiles.root().resolve(LANG).resolve("en_us.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonObject de = JsonParser.parseString(Files.readString(RepoFiles.root().resolve(LANG).resolve("de_de.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        String english = en.get(TOOLTIP_KEY).getAsString();
        String german = de.get(TOOLTIP_KEY).getAsString();
        assertTrue(english.contains("recenters") && english.contains("takes over") && !english.contains("keeps moving"), english);
        assertTrue(german.contains("zentriert") && german.contains("übernimmt") && !german.contains("solange sich der Gimbal bewegt"), german);
    }

    @Test
    void newConfigHoldsAQuietServoVolume(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(CamClientConfig.FILE_NAME);
        CamClientConfig.load(file);
        JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(json.has(SERVO_KEY), "kein eigener Regler für das Servo-Surren in " + json.keySet());
        assertEquals(0.15, json.get(SERVO_KEY).getAsDouble(), 1.0E-9);
    }
}
