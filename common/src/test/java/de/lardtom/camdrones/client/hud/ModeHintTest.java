package de.lardtom.camdrones.client.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.core.RepoFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModeHintTest {
    private static JsonObject lang(String language) throws IOException {
        return JsonParser.parseString(Files.readString(RepoFiles.root().resolve("common/src/lang/hud/" + language + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void theFirstFlightAndEveryModeChangeShowTheHintForFiveSeconds() {
        ModeHint hint = new ModeHint();
        assertEquals("", hint.update(false, "N", false, 0.0));
        assertEquals("N", hint.update(true, "N", false, 1.0), "Start des Flugs");
        assertEquals("N", hint.update(true, "N", false, 1.0 + ModeHint.SECONDS - 0.05));
        assertEquals("", hint.update(true, "N", false, 1.0 + ModeHint.SECONDS));
        assertEquals("C", hint.update(true, "C", false, 10.0), "Wechsel auf C");
        assertEquals("", hint.update(true, "HOLD", false, 11.0), "Halten verdeckt den Hinweis");
        assertEquals("", hint.update(true, "C", false, 12.0), "zurück aus dem Halten ist kein Moduswechsel");
        assertEquals("S", hint.update(true, "S", false, 20.0));
        assertEquals("ATTI", hint.update(true, "S", true, 21.0), "ohne GPS");
        assertEquals("S", hint.update(true, "S", false, 30.0), "GPS wieder da");
        assertEquals("", hint.update(false, "", false, 40.0), "Flug vorbei");
        assertEquals("S", hint.update(true, "S", false, 50.0), "nächster Flug zeigt den Hinweis wieder");
        assertEquals("", hint.update(true, "WP 3/12", false, 51.0));
        assertEquals("", hint.update(true, "S", false, 49.0), "Zeit läuft rückwärts");
    }

    @Test
    void osdShowsTheHintOnlyForCameraDronesInTheAccentTone() {
        StateBuilder state = StateBuilder.camera();
        assertNull(CamOsd.modeHint(state.build()));
        state.hint = "N";
        assertEquals(CamOsd.MODE_HINTS.get("N"), CamOsd.modeHint(state.build()).text());
        state.cameraDrone = false;
        assertNull(CamOsd.modeHint(state.build()));
    }

    @Test
    void hintTextsInBothLanguagesFitTheOsdWithoutCutting() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            JsonObject lang = lang(language);
            for (String mode : ModeHint.MODES) {
                String key = ModeHint.key(mode);
                assertTrue(lang.has(key), language + " fehlt " + key);
                String value = lang.get(key).getAsString();
                String shown = CamOsd.modeHintText(value, CamOsd.MODE_HINTS.get(mode));
                assertEquals(CamOsd.transliterate(value).toUpperCase(Locale.ROOT), shown, language + " " + key + " wird gekürzt");
                assertTrue(shown.length() <= CamOsd.MODE_HINT_CHARS && HudText.printable(shown), language + " " + key + ": " + shown);
                assertTrue(shown.startsWith(mode + ":"), language + " " + key + ": " + shown);
            }
        }
        for (String mode : ModeHint.MODES) {
            String fallback = CamOsd.MODE_HINTS.get(mode);
            assertTrue(fallback.length() <= CamOsd.MODE_HINT_CHARS && HudText.printable(fallback), fallback);
            assertEquals(fallback, CamOsd.modeHintText(mode));
        }
        assertEquals("N: GPS HAELT POS. + HORIZONT", CamOsd.modeHintText(lang("de_de").get(ModeHint.key("N")).getAsString(), "N"));
    }
}
