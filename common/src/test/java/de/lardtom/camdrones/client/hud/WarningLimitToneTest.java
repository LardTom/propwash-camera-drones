package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.core.CovCamASources;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarningLimitToneTest {
    private static final String CALL = "CamDrones.id(\"warning.limit\")";

    @Test
    void limitToneIsPlayedFromTheHud() {
        Map<String, String> callers = CovCamASources.matching(CALL);
        assertFalse(callers.isEmpty(), "warning.limit ist in sounds.json angemeldet, wird aber nirgends gespielt");
        List<String> hud = callers.keySet().stream().filter(path -> path.contains("/client/hud/")).toList();
        assertTrue(!hud.isEmpty(), "warning.limit nicht im HUD gespielt: " + callers.keySet());
        assertTrue(callers.values().stream().anyMatch(text -> text.contains("getSoundManager().play(")),
                "warning.limit angemeldet, aber kein play(): " + callers.keySet());
    }
}
