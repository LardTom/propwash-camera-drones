package de.lardtom.camdrones.client.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.core.RepoFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudSettingsKeysTest {
    private static JsonObject lang(String language) throws IOException {
        return JsonParser.parseString(Files.readString(RepoFiles.root().resolve("common/src/lang/hud/" + language + ".json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    @Test
    void everySettingsKeyExistsInEnglishAndGerman() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            JsonObject lang = lang(language);
            List<String> missing = new ArrayList<>();
            for (String key : HudSettingsKeys.all()) {
                if (!lang.has(key) || lang.get(key).getAsString().isBlank()) {
                    missing.add(key);
                }
            }
            assertEquals(List.of(), missing, language);
        }
    }

    @Test
    void labelsNameTheSettingAndUnitsStayTheirSymbols() throws IOException {
        JsonObject de = lang("de_de");
        JsonObject en = lang("en_us");
        assertTrue(de.get(HudSettingsKeys.VOLUME).getAsString().contains("Lautstärke"));
        assertTrue(en.get(HudSettingsKeys.VOLUME).getAsString().toLowerCase(Locale.ROOT).contains("volume"));
        for (SpeedUnit unit : SpeedUnit.values()) {
            assertEquals(unit.symbol(), de.get(unit.key()).getAsString());
            assertEquals(unit.symbol(), en.get(unit.key()).getAsString());
        }
        assertEquals("%s %%", de.get(HudSettingsKeys.VOLUME_VALUE).getAsString());
        assertEquals("%s%%", en.get(HudSettingsKeys.VOLUME_VALUE).getAsString());
    }
}
