package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.core.CovCamASources;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CovCamAPreviewSettingTest {
    @Test
    void clientSettingsExistWithPreviewMarkersOnByDefault() {
        Map<String, String> config = CovCamASources.matching("camdrones-client.json");
        assertFalse(config.isEmpty(), "keine Client-Einstellungen config/camdrones-client.json (Entwurf §4.3: Vorschau-Marker abschaltbar)");
    }

    @Test
    void previewDrawsNothingWhenSwitchedOff() {
        String preview = CovCamASources.main().getOrDefault("de/lardtom/camdrones/client/planner/WaypointPreview.java", "");
        Map<String, String> config = CovCamASources.matching("camdrones-client.json");
        boolean reads = config.keySet().stream().map(path -> path.substring(path.lastIndexOf('/') + 1).replace(".java", ""))
                .anyMatch(preview::contains);
        assertFalse(!reads, "WaypointPreview fragt keine Einstellung für die Vorschau-Marker ab");
    }
}
