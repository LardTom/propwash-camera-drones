package de.lardtom.camdrones.integration.autonomy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.propwash.api.drone.AutonomyNotReady;
import de.lardtom.propwash.api.drone.AutonomyResult;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotReadyMessageTest {
    private static final UUID SERIAL = UUID.fromString("95c60636-1a2b-4c3d-8e4f-001122334455");
    private static final String SHORT = "95C60636";
    private static final Identifier CINE = Identifier.fromNamespaceAndPath("camdrones", "cine_frame");
    private static final Identifier FREESTYLE = Identifier.fromNamespaceAndPath("propwash", "freestyle_5");
    private static final Identifier OTHER = Identifier.fromNamespaceAndPath("otheraddon", "big_frame");
    private static final Identifier UNKNOWN = Identifier.fromNamespaceAndPath("otheraddon", "secret_frame");
    private static final String CINE_KEY = "camdrones.frame_short.cine_frame";
    private static final Predicate<String> KNOWN = Set.of(CINE_KEY, "part.otheraddon.big_frame")::contains;
    private static final Map<AutonomyNotReady, Map<String, List<String>>> WORDS = Map.of(
            AutonomyNotReady.NOT_ON_GROUND, Map.of("en_us", List.of("ground"), "de_de", List.of("boden")),
            AutonomyNotReady.NO_TAKEOFF, Map.of("en_us", List.of("take-off"), "de_de", List.of("start")),
            AutonomyNotReady.NO_BATTERY, Map.of("en_us", List.of("battery"), "de_de", List.of("akku")),
            AutonomyNotReady.REMOVED, Map.of("en_us", List.of("gone"), "de_de", List.of("nicht mehr da")));

    private static JsonObject lang(String language) throws IOException {
        return JsonParser.parseString(Files.readString(RepoFiles.root().resolve("common/src/lang/autonomy/" + language + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void everyReasonFromPropwashHasItsOwnMessageInBothLanguages() throws IOException {
        Set<String> keys = new HashSet<>();
        for (AutonomyNotReady why : AutonomyNotReady.values()) {
            AutonomyStatus.Reason reason = AutonomyStatus.Reason.notReady(why)
                    .orElseThrow(() -> new AssertionError("kein Grund für " + why + ", die Meldung bleibt allgemein"));
            assertEquals(AutonomyStatus.NOT_READY, reason.status(), why.name());
            assertEquals(Optional.of(reason), AutonomyStatus.Reason.of(AutonomyStatus.NOT_READY, reason.code()), why.name());
            assertTrue(keys.add(reason.key()), "doppelter Schlüssel " + reason.key());
            for (String language : List.of("en_us", "de_de")) {
                JsonElement value = lang(language).get(reason.key());
                assertNotNull(value, language + " fehlt " + reason.key());
                String text = value.getAsString();
                String lower = text.toLowerCase(Locale.ROOT);
                assertTrue(text.startsWith("%1$s"), language + " nennt die Drohne nicht vorne: " + text);
                assertTrue(WORDS.get(why).get(language).stream().anyMatch(lower::contains), language + " nennt den Grund " + why + " nicht: " + text);
                assertFalse(lower.contains("gps"), language + ": " + text);
            }
        }
    }

    @Test
    void generalMessageNoLongerGuessesBatteryGpsOrFlight() throws IOException {
        Map<String, List<String>> guesses = Map.of("en_us", List.of("gps", "battery", "in the air"), "de_de", List.of("gps", "akku", "in der luft"));
        for (String language : guesses.keySet()) {
            String text = lang(language).get(AutonomyStatus.NOT_READY.key()).getAsString();
            for (String guess : guesses.get(language)) {
                assertFalse(text.toLowerCase(Locale.ROOT).contains(guess), language + " rät weiter „" + guess + "“: " + text);
            }
        }
    }

    @Test
    void notReadyFromPropwashTravelsWithItsReason() {
        AtomicInteger asked = new AtomicInteger();
        AutonomyServer.Reply reply = AutonomyServer.refusal(AutonomyResult.NOT_READY, AutonomyPlan.Kind.MISSION, () -> {
            asked.incrementAndGet();
            return Optional.of(AutonomyNotReady.NOT_ON_GROUND);
        });
        assertEquals(AutonomyStatus.NOT_READY, reply.status());
        assertEquals("not_on_ground", reply.detail());
        assertEquals(1, asked.get());
        assertEquals("", AutonomyServer.refusal(AutonomyResult.NOT_READY, AutonomyPlan.Kind.HOLD, Optional::empty).detail());
        assertEquals("", AutonomyServer.refusal(AutonomyResult.NOT_READY, AutonomyPlan.Kind.HOLD, () -> {
            throw new IllegalStateException("alter Stand");
        }).detail());
        assertEquals(AutonomyStatus.NO_BUDGET, AutonomyServer.refusal(AutonomyResult.NO_BUDGET, AutonomyPlan.Kind.MISSION, () -> {
            asked.incrementAndGet();
            return Optional.of(AutonomyNotReady.REMOVED);
        }).status());
        assertEquals(1, asked.get(), "nur bei NOT_READY fragt das Add-on nach dem Grund");
    }

    @Test
    void refusalNamesTheDroneByItsFrameInsteadOfTheId() {
        assertEquals(CINE_KEY, DroneNames.messageName("", Optional.of(CINE), SERIAL, KNOWN));
        assertEquals(CINE_KEY, DroneNames.messageName(null, Optional.of(CINE), SERIAL, key -> false), "eigene Rahmen ohne Nachschlagen");
        assertEquals("propwash.content.frame_short.freestyle_5", DroneNames.messageName(" ", Optional.of(FREESTYLE), SERIAL, key -> false));
        assertEquals("Burg", DroneNames.messageName(" Burg ", Optional.of(CINE), SERIAL, KNOWN));
        assertEquals("part.otheraddon.big_frame", DroneNames.messageName("", Optional.of(OTHER), SERIAL, KNOWN));
        assertEquals(SHORT, DroneNames.messageName("", Optional.of(UNKNOWN), SERIAL, KNOWN));
        assertEquals(SHORT, DroneNames.messageName("", Optional.empty(), SERIAL, KNOWN));
    }

    @Test
    void theClientTranslatesTheFrameNameAndKeepsCustomNames() {
        assertEquals(CINE_KEY, assertInstanceOf(TranslatableContents.class, DroneNames.text(CINE_KEY, KNOWN).getContents()).getKey());
        assertInstanceOf(TranslatableContents.class, DroneNames.text(CINE_KEY, key -> false).getContents(), "Server ohne Sprachdatei des Add-ons");
        assertInstanceOf(TranslatableContents.class, DroneNames.text("propwash.content.frame_short.freestyle_5", key -> false).getContents());
        assertEquals(CINE_KEY, assertInstanceOf(TranslatableContents.class,
                DroneNames.component("", Optional.of(CINE), SERIAL, key -> false).getContents()).getKey());
        assertInstanceOf(PlainTextContents.class, DroneNames.component("Burg", Optional.of(CINE), SERIAL, KNOWN).getContents());
        assertInstanceOf(PlainTextContents.class, DroneNames.component("", Optional.empty(), SERIAL, KNOWN).getContents());
        assertInstanceOf(PlainTextContents.class, DroneNames.text("Burg", KNOWN).getContents());
        assertInstanceOf(PlainTextContents.class, DroneNames.text(SHORT, KNOWN).getContents());
        Component text = AutonomyServer.text(AutonomyStatus.NOT_READY, CINE_KEY, "not_on_ground", KNOWN);
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, text.getContents());
        assertEquals("camdrones.autonomy.status.not_ready.not_on_ground", contents.getKey());
        Component drone = assertInstanceOf(Component.class, contents.getArgs()[0], "Drohnenname als übersetzbarer Text");
        assertEquals(CINE_KEY, assertInstanceOf(TranslatableContents.class, drone.getContents()).getKey());
        TranslatableContents empty = assertInstanceOf(TranslatableContents.class,
                AutonomyServer.text(AutonomyStatus.NOT_ARMABLE, CINE_KEY, "battery_empty", KNOWN).getContents());
        assertEquals("camdrones.autonomy.status.not_armable.battery_empty", empty.getKey());
        assertInstanceOf(Component.class, empty.getArgs()[0]);
    }
}
