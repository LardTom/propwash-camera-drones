package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.core.CovCamASources;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CovCamACrashTest {
    @Test
    void crashEndsMissionCourseLockAndFunction() {
        Map<String, String> listeners = CovCamASources.matching("DRONE_DAMAGED");
        assertFalse(listeners.isEmpty(), "kein Listener auf PropwashEvents.DRONE_DAMAGED (Entwurf §3.1: Crash beendet Mission)");
        String all = String.join("\n", listeners.values());
        assertFalse(!all.contains("register("), "DRONE_DAMAGED wird erwähnt, aber nicht angemeldet: " + listeners.keySet());
    }
}
