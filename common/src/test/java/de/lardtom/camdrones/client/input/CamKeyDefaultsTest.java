package de.lardtom.camdrones.client.input;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.camdrones.integration.CamControl;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamKeyDefaultsTest {
    private static final Set<Integer> PROPWASH_KEYPAD = Set.of(InputConstants.KEY_NUMPAD0, InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2,
            InputConstants.KEY_NUMPAD3, InputConstants.KEY_NUMPAD4, InputConstants.KEY_NUMPAD5, InputConstants.KEY_NUMPAD6,
            InputConstants.KEY_NUMPAD7, InputConstants.KEY_NUMPAD8, InputConstants.KEY_NUMPAD9, InputConstants.KEY_NUMPADENTER,
            InputConstants.KEY_ADD);
    private static final Map<String, Integer> DESIGN = Map.of(
            "mode_cycle", 84,
            "pause", 86,
            "mission_start", 85,
            "waypoint_add", InputConstants.KEY_INSERT,
            "gimbal_recenter", 99,
            "pilot_view", InputConstants.KEY_DELETE,
            "gimbal_tilt_up", InputConstants.KEY_PAGEUP,
            "gimbal_tilt_down", InputConstants.KEY_PAGEDOWN);

    @Test
    void defaultsAreExactlyTheDesignKeys() {
        for (CamKeyDefaults.Entry entry : CamKeyDefaults.ALL) {
            assertEquals(DESIGN.getOrDefault(entry.name(), CamKeyDefaults.UNBOUND), entry.defaultKey(), entry.name());
        }
        assertEquals(DESIGN.size(), CamKeyDefaults.ALL.stream().filter(CamKeyDefaults.Entry::bound).count());
    }

    @Test
    void boundDefaultsUseOnlyFreeKeysAndNeverTwice() {
        Set<Integer> seen = new HashSet<>();
        for (CamKeyDefaults.Entry entry : CamKeyDefaults.ALL) {
            if (!entry.bound()) {
                continue;
            }
            assertTrue(CamKeyDefaults.ALLOWED.contains(entry.defaultKey()), entry.name() + " nutzt keine freie Taste");
            assertFalse(PROPWASH_KEYPAD.contains(entry.defaultKey()), entry.name() + " liegt auf einer Ziffernblock-Taste von Propwash");
            assertTrue(seen.add(entry.defaultKey()), entry.name() + " doppelt");
        }
        assertEquals(CamKeyDefaults.ALLOWED, seen, "jede freie Taste aus E21 ist genau einmal vergeben");
    }

    @Test
    void everyControlHasKeysWithUniqueNamesAndTexts() throws IOException {
        Set<String> names = new HashSet<>();
        for (CamControl control : CamControl.values()) {
            if (control.axis()) {
                assertTrue(CamKeyDefaults.find(control, CamKeyDefaults.Role.INCREASE).isPresent(), control.path());
                assertTrue(CamKeyDefaults.find(control, CamKeyDefaults.Role.DECREASE).isPresent(), control.path());
                assertTrue(CamKeyDefaults.find(control, CamKeyDefaults.Role.PRESS).isEmpty(), control.path());
            } else {
                assertTrue(CamKeyDefaults.find(control, CamKeyDefaults.Role.PRESS).isPresent(), control.path());
            }
        }
        assertEquals(CamControl.switches().size() + 2 * CamControl.axes().size(), CamKeyDefaults.ALL.size());
        for (String language : List.of("en_us", "de_de")) {
            JsonObject lang = JsonParser.parseString(Files.readString(RepoFiles.root().resolve("common/src/lang/core/" + language + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(lang.has(CamKeys.CATEGORY.toLanguageKey("key.category")), language);
            for (CamKeyDefaults.Entry entry : CamKeyDefaults.ALL) {
                assertTrue(lang.has(entry.translationKey()), language + ": " + entry.translationKey());
            }
        }
        for (CamKeyDefaults.Entry entry : CamKeyDefaults.ALL) {
            assertTrue(names.add(entry.translationKey()), entry.translationKey());
            assertTrue(entry.translationKey().startsWith("key.camdrones."), entry.translationKey());
        }
        assertEquals(InputConstants.KEY_PAGEUP, CamKeyDefaults.defaultKey(CamControl.GIMBAL_TILT, CamKeyDefaults.Role.INCREASE));
        assertEquals(CamKeyDefaults.UNBOUND, CamKeyDefaults.defaultKey(CamControl.ZOOM, CamKeyDefaults.Role.INCREASE));
    }
}
