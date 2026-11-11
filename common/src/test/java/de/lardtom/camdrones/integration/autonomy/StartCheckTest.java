package de.lardtom.camdrones.integration.autonomy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.core.RepoFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartCheckTest {
    private static final StartCheck.Battery FULL = StartCheck.Battery.charged(1.0);

    private static JsonObject read(String language) throws IOException {
        return JsonParser.parseString(Files.readString(RepoFiles.root().resolve("common/src/lang/autonomy/" + language + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Optional<StartCheck.Refusal> ground(StartCheck.Battery battery, double sky) {
        return StartCheck.check(false, AutonomyPlan.Kind.MISSION, battery, sky);
    }

    @Test
    void gpsFixNeedsTheSameSkyAsThePilotsReceiver() {
        assertEquals(18, StartCheck.satellites(1.0));
        assertEquals(0, StartCheck.satellites(0.0));
        assertTrue(StartCheck.gpsFix(1.0));
        assertFalse(StartCheck.gpsFix(0.0), "unter einer Steindecke");
        assertEquals(0.449, StartCheck.FIX_SKY_VIEW, 0.001);
        assertTrue(StartCheck.gpsFix(StartCheck.FIX_SKY_VIEW + 1.0E-6));
        assertFalse(StartCheck.gpsFix(StartCheck.FIX_SKY_VIEW - 0.01));
        assertTrue(StartCheck.gpsFix(0.6), "halb offener Himmel reicht");
    }

    @Test
    void startUnderARoofIsRefusedForMissingGpsFix() {
        StartCheck.Refusal refusal = ground(FULL, 0.0).orElseThrow();
        assertEquals(AutonomyStatus.NOT_READY, refusal.status());
        assertEquals(AutonomyStatus.Reason.GPS, refusal.reason());
        assertTrue(ground(FULL, 1.0).isEmpty());
        assertEquals(AutonomyStatus.Reason.GPS, StartCheck.check(true, AutonomyPlan.Kind.HOLD, StartCheck.Battery.UNKNOWN, 0.1).orElseThrow().reason());
    }

    @Test
    void batteryProblemsAreNamedBeforeTheDroneArms() {
        assertEquals(StartCheck.Refusal.of(AutonomyStatus.Reason.BATTERY_EMPTY), ground(StartCheck.Battery.charged(0.0), 1.0).orElseThrow());
        assertEquals(AutonomyStatus.NOT_ARMABLE, ground(StartCheck.Battery.charged(0.02), 1.0).orElseThrow().status());
        assertEquals(StartCheck.Refusal.of(AutonomyStatus.Reason.NO_BATTERY), ground(StartCheck.Battery.NONE, 1.0).orElseThrow());
        StartCheck.Refusal low = ground(StartCheck.Battery.charged(0.10), 1.0).orElseThrow();
        assertEquals(AutonomyStatus.Reason.BATTERY_LOW, low.reason());
        assertEquals(AutonomyStatus.NOT_READY, low.status());
        assertTrue(ground(StartCheck.Battery.charged(StartCheck.RESERVE_CHARGE), 1.0).isEmpty());
        assertEquals(AutonomyStatus.Reason.BATTERY_EMPTY, ground(StartCheck.Battery.charged(0.0), 0.0).orElseThrow().reason(),
                "ohne Akku hilft auch freier Himmel nicht – der Akku zuerst");
    }

    @Test
    void unknownBatteryOrFlightsInTheAirLeaveTheBatteryToPropwash() {
        assertTrue(ground(StartCheck.Battery.UNKNOWN, 1.0).isEmpty());
        assertTrue(StartCheck.check(true, AutonomyPlan.Kind.MISSION, StartCheck.Battery.charged(0.0), 1.0).isEmpty());
        assertEquals(StartCheck.Battery.UNKNOWN, StartCheck.Battery.charged(Double.NaN));
    }

    @Test
    void landingAndReturnHomeAreNeverRefused() {
        for (AutonomyPlan.Kind kind : List.of(AutonomyPlan.Kind.LAND, AutonomyPlan.Kind.HOME)) {
            assertFalse(StartCheck.needsChecks(kind));
            assertTrue(StartCheck.check(false, kind, StartCheck.Battery.NONE, 0.0).isEmpty(), kind.name());
        }
        for (AutonomyPlan.Kind kind : List.of(AutonomyPlan.Kind.MISSION, AutonomyPlan.Kind.HOLD, AutonomyPlan.Kind.FOLLOW, AutonomyPlan.Kind.ORBIT,
                AutonomyPlan.Kind.AUTO_SHOT)) {
            assertTrue(StartCheck.needsChecks(kind), kind.name());
        }
    }

    @Test
    void reasonsTravelAsDetailAndOnlyMatchTheirStatus() {
        for (AutonomyStatus.Reason reason : AutonomyStatus.Reason.values()) {
            assertEquals(Optional.of(reason), AutonomyStatus.Reason.of(reason.status(), reason.code()));
            assertTrue(reason.status().error());
            assertEquals(reason.status().key() + "." + reason.code(), reason.key());
        }
        assertTrue(AutonomyStatus.Reason.of(AutonomyStatus.NOT_ARMABLE, "gps").isEmpty());
        assertTrue(AutonomyStatus.Reason.of(AutonomyStatus.NOT_READY, "").isEmpty());
        assertTrue(AutonomyStatus.Reason.of(AutonomyStatus.MISSION_STARTED, "1").isEmpty());
    }

    @Test
    void propwashsOwnArmingReasonStaysInTheActionBar() {
        assertFalse(AutonomyStatus.overlay(AutonomyStatus.NOT_ARMABLE, ""), "Propwash nennt den Grund dort selbst");
        assertTrue(AutonomyStatus.overlay(AutonomyStatus.NOT_ARMABLE, AutonomyStatus.Reason.BATTERY_EMPTY.code()));
        assertTrue(AutonomyStatus.overlay(AutonomyStatus.NOT_READY, AutonomyStatus.Reason.GPS.code()));
        assertTrue(AutonomyStatus.overlay(AutonomyStatus.MISSION_STARTED, "1"));
    }

    @Test
    void messagesNameTheReason() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            JsonObject lang = read(language);
            String gps = lang.get(AutonomyStatus.Reason.GPS.key()).getAsString().toLowerCase(Locale.ROOT);
            assertTrue(gps.contains("gps"), language + ": " + gps);
            for (AutonomyStatus.Reason reason : List.of(AutonomyStatus.Reason.NO_BATTERY, AutonomyStatus.Reason.BATTERY_EMPTY,
                    AutonomyStatus.Reason.BATTERY_LOW)) {
                String text = lang.get(reason.key()).getAsString().toLowerCase(Locale.ROOT);
                assertTrue(text.contains("battery") || text.contains("akku"), language + ": " + text);
                assertFalse(text.contains("gps"), language + ": " + text);
            }
        }
    }
}
