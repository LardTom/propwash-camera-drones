package de.lardtom.camdrones.client.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.client.planner.CamClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovCamBUnitsTest {
    private static List<String> keys(Path file) throws IOException {
        JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        return new ArrayList<>(json.keySet());
    }

    @Test
    void clientConfigHoldsSpeedUnitAndWarningVolume(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(CamClientConfig.FILE_NAME);
        CamClientConfig.load(file);
        List<String> keys = keys(file);
        assertTrue(keys.stream().anyMatch(k -> k.toLowerCase(Locale.ROOT).contains("unit")),
                "Einheit (m/s oder km/h) fehlt in " + CamClientConfig.FILE_NAME + ": " + keys);
        assertTrue(keys.stream().anyMatch(k -> k.toLowerCase(Locale.ROOT).contains("volume")),
                "Warnton-Lautstärke fehlt in " + CamClientConfig.FILE_NAME + ": " + keys);
    }

    @Test
    void hudTextShowsKilometresPerHourInAscii() throws ReflectiveOperationException {
        Method kmh = null;
        for (Method method : HudText.class.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getName().toLowerCase(Locale.ROOT).contains("kmh")
                    && method.getParameterCount() == 1 && method.getParameterTypes()[0] == double.class && method.getReturnType() == String.class) {
                kmh = method;
            }
        }
        assertTrue(kmh != null, "HudText ohne Anzeige in km/h");
        String text = (String) kmh.invoke(null, 10.0);
        assertTrue(text.contains("36"), "10 m/s = 36 km/h, angezeigt: " + text);
        assertTrue(HudText.printable(text), "nur ASCII im OSD: " + text);
        assertFalse(text.length() > 8, "zu lang für das OSD: " + text);
    }

    @Test
    void obstacleToneVolumeFollowsTheSetting() {
        boolean found = false;
        for (Method method : ObstacleBeeper.class.getMethods()) {
            if (method.getName().toLowerCase(Locale.ROOT).contains("volume")) {
                found = true;
            }
        }
        for (Method method : ObstacleTone.class.getMethods()) {
            if (method.getName().toLowerCase(Locale.ROOT).contains("volume")) {
                found = true;
            }
        }
        assertTrue(found, "Warnton spielt fest mit Lautstärke " + ObstacleTone.VOLUME + ", keine Einstellung");
    }
}
