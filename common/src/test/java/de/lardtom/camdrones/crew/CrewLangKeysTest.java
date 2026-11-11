package de.lardtom.camdrones.crew;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.core.RepoFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrewLangKeysTest {
    private static final Pattern ARG = Pattern.compile("%(\\d+\\$)?s");

    private static JsonObject lang(String language) throws IOException {
        return JsonParser.parseString(Files.readString(RepoFiles.root().resolve("common/src/lang/crew/" + language + ".json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static int args(String text) {
        Matcher m = ARG.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    @Test
    void everyUsedKeyExistsInBothLanguagesAndNothingElse() throws IOException {
        Set<String> used = new TreeSet<>(CrewTexts.all());
        assertEquals(used, new TreeSet<>(lang("en_us").keySet()));
        assertEquals(used, new TreeSet<>(lang("de_de").keySet()));
    }

    @Test
    void placeholdersMatchBetweenLanguages() throws IOException {
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        for (String key : CrewTexts.all()) {
            assertEquals(args(en.get(key).getAsString()), args(de.get(key).getAsString()), key);
            assertTrue(args(en.get(key).getAsString()) <= 2, key);
        }
    }

    @Test
    void decimalSeparatorsAreSingleCharacters() throws IOException {
        assertEquals(".", lang("en_us").get(CrewTexts.SCREEN_DECIMAL).getAsString());
        assertEquals(",", lang("de_de").get(CrewTexts.SCREEN_DECIMAL).getAsString());
    }

    @Test
    void commandsInTheInvitationMatchTheCommandTree() {
        assertEquals("/" + CrewCommands.ROOT + " " + CrewCommands.CREW + " accept", CrewService.ACCEPT_COMMAND);
        assertEquals("/" + CrewCommands.ROOT + " " + CrewCommands.CREW + " decline", CrewService.DECLINE_COMMAND);
    }

    @Test
    void commandTreeNamesEverySubcommand() throws IOException {
        String source = Files.readString(RepoFiles.root().resolve("common/src/main/java/de/lardtom/camdrones/crew/CrewCommands.java"),
                StandardCharsets.UTF_8);
        for (String literal : List.of("invite", "accept", "decline", "leave", "release")) {
            assertTrue(source.contains("Commands.literal(\"" + literal + "\")"), literal);
        }
    }
}
