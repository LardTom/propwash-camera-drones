package de.lardtom.camdrones.integration.autonomy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.core.RepoFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoBudgetMessageTest {
    static final Map<String, List<String>> RULES = Map.of(
            "de_de", List.of("Autonome Flüge je Spieler gleichzeitig", "Autonome Flüge gesamt"),
            "en_us", List.of("Autonomous flights per player at once", "Autonomous flights in total"));
    static final Map<String, String> SAYS = Map.of("de_de", "kein Budget", "en_us", "no budget");
    static final Map<String, List<String>> FIXED_COUNT = Map.of(
            "de_de", List.of("einer je spieler", "ein flug ohne pilot je spieler", "nur ein flug ohne pilot", "läuft schon"),
            "en_us", List.of("one per player", "one flight without pilot per player", "only one flight without pilot", "already running"));
    static final Map<String, String> GUIDES = Map.of("de_de", "docs/guide.de.md", "en_us", "docs/guide.md");

    private static JsonObject lang(String language) throws IOException {
        Path file = RepoFiles.root().resolve("common/src/lang/autonomy/" + language + ".json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static List<String> fixedCount(String language, String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return FIXED_COUNT.get(language).stream().filter(lower::contains).toList();
    }

    @Test
    void propwashKeepsItsActionBarMessageWithTheRuleValue() {
        assertFalse(AutonomyStatus.overlay(AutonomyStatus.NO_BUDGET, ""), "Propwash nennt den Wert der Welt-Regel dort selbst");
        assertFalse(AutonomyStatus.overlay(AutonomyStatus.NO_BUDGET, "1"));
        assertTrue(AutonomyStatus.NO_BUDGET.error());
        assertTrue(AutonomyStatus.NO_BUDGET.chat());
        assertTrue(AutonomyStatus.overlay(AutonomyStatus.DISABLED, ""));
        assertTrue(AutonomyStatus.overlay(AutonomyStatus.BUSY, ""));
    }

    @Test
    void chatMessageNamesBothWorldRulesAndNoFixedCount() throws IOException {
        for (String language : RULES.keySet()) {
            String text = lang(language).get(AutonomyStatus.NO_BUDGET.key()).getAsString();
            assertTrue(text.contains(SAYS.get(language)), language + ": " + text);
            for (String rule : RULES.get(language)) {
                assertTrue(text.contains(rule), language + " nennt die Welt-Regel „" + rule + "“ nicht: " + text);
            }
            assertTrue(fixedCount(language, text).isEmpty(), language + " nennt eine feste Anzahl: " + fixedCount(language, text));
            assertFalse(text.replace("%1$s", "").matches(".*\\d.*"), language + " nennt eine Zahl, die das Add-on nicht kennt: " + text);
        }
    }

    @Test
    void guideNamesTheWorldRulesInsteadOfOneFlightPerPlayer() throws IOException {
        for (String language : GUIDES.keySet()) {
            String text = Files.readString(RepoFiles.root().resolve(GUIDES.get(language)), StandardCharsets.UTF_8);
            assertTrue(fixedCount(language, text).isEmpty(), GUIDES.get(language) + " nennt eine feste Anzahl: " + fixedCount(language, text));
            for (String rule : RULES.get(language)) {
                assertTrue(text.contains("*" + rule + "*"), GUIDES.get(language) + " nennt die Welt-Regel „" + rule + "“ nicht");
            }
        }
    }
}
