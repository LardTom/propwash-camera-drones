package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.core.CovCamASources;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CovCamAExitSettingTest {
    @Test
    void exitBehaviourCanBeChosenInThePlanner() {
        Map<String, String> callers = CovCamASources.matching("withExit(").entrySet().stream()
                .filter(e -> !e.getKey().endsWith("integration/data/DroneSettings.java"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertFalse(callers.isEmpty(), "Verhalten beim Verlassen der FPV-Ansicht (schweben, heim, landen) lässt sich nirgends einstellen: kein Aufruf von "
                + "DroneSettings.withExit außerhalb von DroneSettings");
    }
}
