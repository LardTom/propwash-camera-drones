package de.lardtom.camdrones.award;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.core.RepoFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamAdvancementFilesTest {
    static final List<String> AWARDED = List.of("crew_flight", "video", "mission");
    static final List<String> ALL = List.of("root", "crew_flight", "video", "mission");

    private static Path resources() {
        return RepoFiles.root().resolve("common/src/main/resources");
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject advancement(String id) throws IOException {
        return read(resources().resolve("data/camdrones/advancement/" + id + ".json"));
    }

    private static JsonObject lang(String language) throws IOException {
        return read(RepoFiles.root().resolve("common/src/lang/awards/" + language + ".json"));
    }

    @Test
    void threeImpossibleAdvancementsUnderTheRoot() throws IOException {
        for (String id : AWARDED) {
            JsonObject json = advancement(id);
            assertEquals("camdrones:root", json.get("parent").getAsString(), id);
            JsonObject criteria = json.getAsJsonObject("criteria");
            assertEquals(1, criteria.size(), id);
            assertEquals("minecraft:impossible", criteria.getAsJsonObject("done").get("trigger").getAsString(), id);
            assertEquals("[[\"done\"]]", json.getAsJsonArray("requirements").toString(), id);
            JsonObject display = json.getAsJsonObject("display");
            assertEquals("camdrones.advancement." + id + ".title", display.getAsJsonObject("title").get("translate").getAsString());
            assertEquals("camdrones.advancement." + id + ".description", display.getAsJsonObject("description").get("translate").getAsString());
            assertFalse(display.getAsJsonObject("icon").get("id").getAsString().isBlank(), id);
        }
    }

    @Test
    void rootOpensTheOwnTabWithAGimbalOrTheFlightPlanner() throws IOException {
        JsonObject root = advancement("root");
        assertFalse(root.has("parent"));
        JsonObject criterion = root.getAsJsonObject("criteria").getAsJsonObject("has_camera_gear");
        assertEquals("minecraft:inventory_changed", criterion.get("trigger").getAsString());
        String items = criterion.getAsJsonObject("conditions").getAsJsonArray("items").get(0).getAsJsonObject().get("items").toString();
        for (String item : List.of("camdrones:gimbal_compact", "camdrones:gimbal_tour", "camdrones:gimbal_cine", "camdrones:flight_planner")) {
            assertTrue(items.contains(item), item);
        }
        JsonObject display = root.getAsJsonObject("display");
        assertEquals("minecraft:gui/advancements/backgrounds/adventure", display.get("background").getAsString());
        assertFalse(display.get("announce_to_chat").getAsBoolean());
        assertFalse(display.get("show_toast").getAsBoolean());
    }

    @Test
    void iconsAreItemsThatExist() throws IOException {
        Set<String> known = new TreeSet<>(List.of("camdrones:flight_planner", "camdrones:lens_50", "camdrones:gimbal_tour", "camdrones:gimbal_cine",
                "minecraft:spyglass"));
        for (String id : ALL) {
            String icon = advancement(id).getAsJsonObject("display").getAsJsonObject("icon").get("id").getAsString();
            assertTrue(known.contains(icon), id + ": " + icon);
            if (icon.startsWith("camdrones:")) {
                String path = icon.substring("camdrones:".length());
                boolean recipe = Files.isRegularFile(resources().resolve("data/camdrones/recipe/" + path + ".json"));
                boolean model = Files.isRegularFile(resources().resolve("assets/camdrones/items/" + path + ".json"));
                assertTrue(recipe || model, icon);
            }
        }
    }

    @Test
    void everyTitleAndDescriptionInBothLanguages() throws IOException {
        Set<String> wanted = new TreeSet<>();
        for (String id : ALL) {
            wanted.add("camdrones.advancement." + id + ".title");
            wanted.add("camdrones.advancement." + id + ".description");
        }
        JsonObject en = lang("en_us");
        JsonObject de = lang("de_de");
        assertEquals(wanted, new TreeSet<>(en.keySet()));
        assertEquals(wanted, new TreeSet<>(de.keySet()));
        for (String key : wanted) {
            assertFalse(en.get(key).getAsString().isBlank(), key);
            assertFalse(de.get(key).getAsString().isBlank(), key);
        }
        assertEquals("Kamera-Drohnen", de.get("camdrones.advancement.root.title").getAsString());
        assertEquals("Camera Drones", en.get("camdrones.advancement.root.title").getAsString());
    }
}
