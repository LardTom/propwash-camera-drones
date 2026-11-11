package de.lardtom.camdrones.pad;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.camdrones.core.RepoFiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LandingPadFilesTest {
    private static final Path RESOURCES = Path.of("common/src/main/resources");
    private static final String ID = "camdrones:landing_pad";

    private static JsonObject read(String path) throws IOException {
        Path file = RepoFiles.root().resolve(RESOURCES).resolve(path);
        assertTrue(Files.isRegularFile(file), path + " fehlt");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject lang(String language) throws IOException {
        Path file = RepoFiles.root().resolve("common/src/lang/pad/" + language + ".json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void idMatchesTheModelsOfTheAssetPipeline() throws IOException {
        assertEquals(ID, PadBlocks.LANDING_PAD_ID.toString());
        JsonObject state = read("assets/camdrones/blockstates/landing_pad.json");
        assertEquals("camdrones:block/landing_pad", state.getAsJsonObject("variants").getAsJsonObject("").get("model").getAsString());
        JsonObject item = read("assets/camdrones/items/landing_pad.json");
        assertEquals("camdrones:block/landing_pad", item.getAsJsonObject("model").get("model").getAsString());
        JsonObject model = read("assets/camdrones/models/block/landing_pad.json");
        JsonArray elements = model.getAsJsonArray("elements");
        assertEquals(1, elements.size());
        JsonArray from = elements.get(0).getAsJsonObject().getAsJsonArray("from");
        JsonArray to = elements.get(0).getAsJsonObject().getAsJsonArray("to");
        assertEquals(List.of(0.0, 0.0, 0.0), List.of(from.get(0).getAsDouble(), from.get(1).getAsDouble(), from.get(2).getAsDouble()));
        assertEquals(List.of(16.0, LandingPadBlock.HEIGHT_PIXELS, 16.0), List.of(to.get(0).getAsDouble(), to.get(1).getAsDouble(), to.get(2).getAsDouble()));
        assertEquals(LandingPadBlock.HEIGHT_PIXELS / 16.0, LandingPadBlock.HEIGHT, 1e-9);
        for (String texture : List.of("top", "side", "bottom")) {
            assertTrue(Files.isRegularFile(RepoFiles.root().resolve(RESOURCES).resolve("assets/camdrones/textures/block/landing_pad_" + texture + ".png")), texture);
        }
    }

    @Test
    void breakingDropsThePadAndAPickaxeIsTheRightTool() throws IOException {
        JsonObject table = read("data/camdrones/loot_table/blocks/landing_pad.json");
        assertEquals("minecraft:block", table.get("type").getAsString());
        JsonArray pools = table.getAsJsonArray("pools");
        assertEquals(1, pools.size());
        JsonObject pool = pools.get(0).getAsJsonObject();
        assertEquals(1, pool.get("rolls").getAsInt());
        assertEquals("minecraft:survives_explosion", pool.getAsJsonObject("condition").get("type").getAsString());
        assertEquals(ID, pool.getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("camdrones:blocks/landing_pad", table.get("random_sequence").getAsString());
        JsonObject tag = read("data/minecraft/tags/block/mineable/pickaxe.json");
        assertTrue(tag.getAsJsonArray("values").toString().contains("\"" + ID + "\""));
        assertTrue(!tag.has("replace") || !tag.get("replace").getAsBoolean(), "Tag darf Vanilla nicht ersetzen");
    }

    @Test
    void nameAndTooltipInBothLanguages() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            JsonObject lang = lang(language);
            assertTrue(lang.has("block.camdrones.landing_pad"), language);
            assertTrue(lang.has(LandingPadItem.TOOLTIP_KEY), language);
            assertEquals(2, lang.size(), language);
        }
        assertEquals("Landeplatz", lang("de_de").get("block.camdrones.landing_pad").getAsString());
        assertEquals("Landing Pad", lang("en_us").get("block.camdrones.landing_pad").getAsString());
    }
}
