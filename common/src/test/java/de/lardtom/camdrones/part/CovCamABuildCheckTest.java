package de.lardtom.camdrones.part;

import de.lardtom.camdrones.core.CovCamASources;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CovCamABuildCheckTest {
    @Test
    void workbenchHasACameraDroneCheckButton() {
        Map<String, String> extensions = CovCamASources.matching("implements WorkbenchExtension");
        assertFalse(extensions.isEmpty(), "keine WorkbenchExtension im Add-on, der Knopf „Kamera-Drohne prüfen“ (Entwurf §3.1) fehlt");
        Map<String, String> registered = CovCamASources.matching("registerWorkbenchExtension(");
        assertFalse(registered.isEmpty(), "WorkbenchExtension " + extensions.keySet() + " wird nicht angemeldet");
    }
}
