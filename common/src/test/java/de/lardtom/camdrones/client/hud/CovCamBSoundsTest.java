package de.lardtom.camdrones.client.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.core.CovCamASources;
import de.lardtom.camdrones.core.RepoFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovCamBSoundsTest {
    static final List<String> DESIGN = List.of("planner.click", "planner.waypoint", "mission.start", "mission.done", "gimbal.calibrate",
            "warning.obstacle", "warning.limit", "warning.atti", "crew.request");
    private static final Path ASSETS = Path.of("common/src/main/resources/assets/camdrones");

    private static JsonObject sounds() throws IOException {
        return JsonParser.parseString(Files.readString(RepoFiles.root().resolve(ASSETS).resolve("sounds.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    @Test
    void everyDesignSoundIsDeclaredWithAnOggFile() throws IOException {
        JsonObject sounds = sounds();
        assertEquals(List.copyOf(DESIGN.stream().sorted().toList()), sounds.keySet().stream().sorted().toList());
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : sounds.entrySet()) {
            for (JsonElement sound : entry.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                String name = sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString();
                String path = name.substring(name.indexOf(':') + 1);
                Path file = RepoFiles.root().resolve(ASSETS).resolve("sounds").resolve(path + ".ogg");
                if (!Files.isRegularFile(file)) {
                    problems.add(entry.getKey() + ": " + file.getFileName() + " fehlt");
                    continue;
                }
                byte[] head = new byte[4];
                try (InputStream in = Files.newInputStream(file)) {
                    if (in.read(head) != 4 || !Arrays.equals(head, "OggS".getBytes(StandardCharsets.US_ASCII))) {
                        problems.add(entry.getKey() + ": " + file.getFileName() + " ist keine Ogg-Datei");
                    }
                }
            }
            String subtitle = entry.getValue().getAsJsonObject().get("subtitle").getAsString();
            if (!subtitle.equals("camdrones.subtitles." + entry.getKey())) {
                problems.add(entry.getKey() + ": Untertitel " + subtitle);
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void everyDeclaredSoundIsPlayedSomewhere() {
        Map<String, String> sources = CovCamASources.main();
        List<String> silent = new ArrayList<>();
        for (String id : DESIGN) {
            String call = "CamDrones.id(\"" + id + "\")";
            if (sources.values().stream().noneMatch(text -> text.contains(call))) {
                silent.add(id);
            }
        }
        assertTrue(silent.isEmpty(), "in sounds.json angemeldet, im Code nie gespielt: " + silent);
    }

    @Test
    void gimbalCalibrateIsPlayedByTheGimbal() {
        String call = "CamDrones.id(\"gimbal.calibrate\")";
        List<String> players = CovCamASources.matching(call).entrySet().stream()
                .filter(file -> file.getKey().startsWith("de/lardtom/camdrones/client/gimbal/") && file.getValue().contains("getSoundManager().play("))
                .map(Map.Entry::getKey)
                .toList();
        assertTrue(!players.isEmpty(), "gimbal.calibrate wird im Gimbal-Code nie gespielt (Befund cov-cam-b-6)");
    }
}
