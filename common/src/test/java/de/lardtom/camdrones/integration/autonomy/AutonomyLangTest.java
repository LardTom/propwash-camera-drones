package de.lardtom.camdrones.integration.autonomy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.command.CamCommands;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutonomyLangTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("%(\\d+\\$)?s");
    private static final Pattern KEY_USE = Pattern.compile("\\b(KEY|CONSENT_KEY|AutonomyServer\\.CONSENT_KEY) \\+ \"([a-z_.]+)\"");
    private static final Map<String, List<String>> HOME_WORDS = Map.of("de_de", List.of("heimflug", "fliegt heim"), "en_us", List.of("home"));
    private static final Map<String, List<String>> LAND_WORDS = Map.of("de_de", List.of("landung", "landet"), "en_us",
            List.of("landing", "lands"));
    private static final Set<AutonomyEnd> SAYS_LANDING = EnumSet.of(AutonomyEnd.ERROR);
    private static final List<String> SOURCES = List.of("command/CamCommands.java", "integration/autonomy/AutonomyServer.java",
            "integration/autonomy/client/AutonomyClient.java");

    private static JsonObject read(String language) throws IOException {
        Path file = RepoFiles.root().resolve("common/src/lang/autonomy/" + language + ".json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        int plain = 0;
        while (matcher.find()) {
            found.add(matcher.group(1) == null ? (++plain) + "$" : matcher.group(1));
        }
        return found;
    }

    static Set<String> usedKeys() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (AutonomyStatus status : AutonomyStatus.values()) {
            keys.add(status.key());
        }
        for (AutonomyStatus.Reason reason : AutonomyStatus.Reason.values()) {
            keys.add(reason.key());
        }
        for (NavLogic.MissionState state : NavLogic.MissionState.values()) {
            keys.add(CamCommands.KEY + "state." + state.name().toLowerCase(Locale.ROOT));
        }
        keys.add(AutonomyServer.FOLLOWED_KEY);
        Path base = RepoFiles.root().resolve("common/src/main/java/de/lardtom/camdrones");
        for (String source : SOURCES) {
            Matcher matcher = KEY_USE.matcher(Files.readString(base.resolve(source), StandardCharsets.UTF_8));
            while (matcher.find()) {
                String prefix = matcher.group(1).endsWith("CONSENT_KEY") ? AutonomyServer.CONSENT_KEY : CamCommands.KEY;
                if (!matcher.group(2).endsWith(".") && (source.startsWith("command/") || !matcher.group(1).equals("KEY"))) {
                    keys.add(prefix + matcher.group(2));
                }
            }
        }
        return keys;
    }

    @Test
    void everyUsedKeyExistsInBothLanguages() throws IOException {
        JsonObject en = read("en_us");
        JsonObject de = read("de_de");
        Set<String> used = usedKeys();
        assertTrue(used.size() > AutonomyStatus.values().length + 10, "Schlüssel gefunden: " + used.size());
        List<String> missing = new ArrayList<>();
        for (String key : used) {
            if (!en.has(key) || !de.has(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "fehlt: " + missing);
        assertEquals(new TreeSet<>(en.keySet()), new TreeSet<>(de.keySet()));
        List<String> unused = new ArrayList<>();
        for (String key : en.keySet()) {
            if (!used.contains(key)) {
                unused.add(key);
            }
        }
        assertTrue(unused.isEmpty(), "unbenutzt: " + unused);
    }

    @Test
    void placeholdersMatchBetweenLanguages() throws IOException {
        JsonObject en = read("en_us");
        JsonObject de = read("de_de");
        for (String key : en.keySet()) {
            assertEquals(placeholders(en.get(key).getAsString()), placeholders(de.get(key).getAsString()), key);
        }
    }

    @Test
    void budgetMessageSaysNoBudgetAndConsentMentionsTheStopCommand() throws IOException {
        JsonObject de = read("de_de");
        JsonObject en = read("en_us");
        assertTrue(de.get(AutonomyStatus.NO_BUDGET.key()).getAsString().contains("kein Budget"));
        assertTrue(en.get(AutonomyStatus.NO_BUDGET.key()).getAsString().contains("no budget"));
        for (JsonObject lang : List.of(de, en)) {
            assertTrue(lang.get(AutonomyServer.FOLLOWED_KEY).getAsString().contains("/camdrones follow stop"));
            assertTrue(lang.get(AutonomyServer.CONSENT_KEY + "question").getAsString().contains("/camdrones follow stop"));
            assertFalse(lang.toString().contains("/propwash"), "nur der eigene Befehl");
        }
    }

    enum FollowUp {
        NONE,
        HOME_OR_LAND,
        LAND
    }

    static FollowUp propwashFollowUp(String reason) {
        return switch (reason) {
            case "PILOT", "LANDED", "REPLACED" -> FollowUp.NONE;
            case "BATTERY", "TIMEOUT", "OUT_OF_RANGE", AutonomyEndTest.TERRAIN_LIMIT -> FollowUp.HOME_OR_LAND;
            case "OWNER_OFFLINE", "STOPPED", "ERROR" -> FollowUp.LAND;
            default -> throw new AssertionError("Folge in Propwash nach " + reason + " fehlt in der Tabelle");
        };
    }

    private static boolean mentions(String text, List<String> words) {
        String lower = text.toLowerCase(Locale.ROOT);
        return words.stream().anyMatch(lower::contains);
    }

    @Test
    void endMessagesPromiseOnlyWhatPropwashDoesAfterwards() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (String language : List.of("en_us", "de_de")) {
            JsonObject lang = read(language);
            for (AutonomyEnd reason : AutonomyEnd.values()) {
                String text = lang.get(AutonomyStatus.ended(reason).key()).getAsString();
                boolean home = mentions(text, HOME_WORDS.get(language));
                boolean land = mentions(text, LAND_WORDS.get(language));
                FollowUp next = propwashFollowUp(reason.name());
                boolean ok = switch (next) {
                    case NONE -> !home;
                    case HOME_OR_LAND -> home && land;
                    case LAND -> !home && (land || !SAYS_LANDING.contains(reason));
                };
                if (!ok) {
                    wrong.add(language + " " + reason + " (Propwash danach: " + next + "): " + text);
                }
            }
        }
        assertTrue(wrong.isEmpty(), "Meldung passt nicht zum Verhalten: " + wrong);
    }

    @Test
    void worldEdgeTellsThePilotWhyAndThatTheDroneFliesHomeOrLands() throws IOException {
        String key = AutonomyStatus.ENDED_TERRAIN_LIMIT.key();
        assertEquals(FollowUp.HOME_OR_LAND, propwashFollowUp(AutonomyEndTest.TERRAIN_LIMIT));
        for (String language : List.of("en_us", "de_de")) {
            JsonObject lang = read(language);
            assertTrue(lang.has(key), language + " " + key);
            String text = lang.get(key).getAsString();
            assertTrue(mentions(text, HOME_WORDS.get(language)) && mentions(text, LAND_WORDS.get(language)), language + ": " + text);
        }
        assertTrue(read("de_de").get(key).getAsString().contains("Weltrand"));
        assertTrue(read("de_de").get(key).getAsString().contains("kein neues Gelände"));
        assertTrue(read("en_us").get(key).getAsString().contains("world edge"));
        assertTrue(read("en_us").get(key).getAsString().contains("new terrain"));
    }

    @Test
    void outOfRangeTellsTheOwnerTheDroneFliesHomeOrLands() throws IOException {
        String key = AutonomyStatus.ENDED_OUT_OF_RANGE.key();
        assertEquals("%1$s: außer Funkreichweite – Heimflug bzw. Landung", read("de_de").get(key).getAsString());
        assertEquals("%1$s: out of radio range – returning home or landing", read("en_us").get(key).getAsString());
        assertEquals(FollowUp.HOME_OR_LAND, propwashFollowUp(AutonomyEnd.OUT_OF_RANGE.name()));
    }
}
