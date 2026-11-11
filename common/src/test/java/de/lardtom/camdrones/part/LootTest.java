package de.lardtom.camdrones.part;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.lardtom.camdrones.core.RepoFiles;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LootTest {
    private static final String TABLE = "common/src/main/resources/data/camdrones/loot_table/";
    private static final double MAX_CHANCE = 0.02;

    private static JsonObject table() {
        return ClasspathData.jsonTree(RepoFiles.root().resolve(TABLE)).get(CamLoot.VILLAGE_HOUSE_TABLE + ".json");
    }

    @Test
    public void everyInjectionTargetsAnExistingVanillaChest() {
        Set<String> targets = new HashSet<>();
        for (CamLoot.Injection injection : CamLoot.injections()) {
            Identifier target = injection.target().identifier();
            assertEquals("minecraft", target.getNamespace());
            assertTrue(ClasspathData.exists("data/minecraft/loot_table/" + target.getPath() + ".json"), target.toString());
            assertTrue(targets.add(target.toString()), target + " doppelt");
            assertEquals(CamPresets.part(CamLoot.VILLAGE_HOUSE_TABLE), injection.table().identifier());
        }
        assertEquals(CamLoot.VILLAGE_HOUSES.size(), targets.size());
        assertTrue(ClasspathData.jsonTree(RepoFiles.root().resolve(TABLE)).keySet().stream().filter(name -> !name.startsWith("blocks/")).count() == 1,
                "nur eine eigene Truhen-Beutetabelle");
    }

    @Test
    public void villageChestsRarelyHoldTheCompactPreset() {
        JsonObject table = table();
        assertEquals("minecraft:chest", table.get("type").getAsString());
        int total = 0;
        int drone = 0;
        JsonObject build = null;
        JsonObject components = null;
        for (JsonElement poolElement : table.getAsJsonArray("pools")) {
            JsonObject pool = poolElement.getAsJsonObject();
            assertEquals(1, pool.get("rolls").getAsInt());
            for (JsonElement entryElement : pool.getAsJsonArray("entries")) {
                JsonObject entry = entryElement.getAsJsonObject();
                int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
                total += weight;
                if ("minecraft:item".equals(entry.get("type").getAsString())) {
                    assertEquals("propwash:drone", entry.get("name").getAsString());
                    drone += weight;
                    components = entry.getAsJsonObject("modifier").getAsJsonObject("components");
                    build = components.getAsJsonObject("propwash:drone_build");
                }
            }
        }
        assertTrue(drone > 0 && (double) drone / total <= MAX_CHANCE, "Chance " + drone + "/" + total);
        assertTrue(build != null, "kein Drohnen-Bauplan");
        BuildSpec expected = CamPresets.COMPACT.build();
        assertEquals(expected.frame().toString(), build.get("frame").getAsString());
        assertEquals(expected.stack().toString(), build.get("stack").getAsString());
        assertEquals(expected.motor().toString(), build.get("motor").getAsString());
        assertEquals(expected.prop().toString(), build.get("prop").getAsString());
        assertEquals(expected.video().toString(), build.get("video").getAsString());
        assertEquals(expected.battery().toString(), build.get("battery").getAsString());
        Map<String, JsonElement> accessories = build.getAsJsonObject("accessories").asMap();
        assertEquals(Set.of(AccessorySlot.FRONT.key()), accessories.keySet());
        assertEquals(expected.accessory(AccessorySlot.FRONT).orElseThrow().toString(), accessories.get(AccessorySlot.FRONT.key()).getAsString());
        assertEquals(CamPresets.COMPACT.translationKey(), components.getAsJsonObject("minecraft:item_name").get("translate").getAsString());
        double charge = components.get("propwash:battery_charge").getAsDouble();
        assertTrue(charge > 0.0 && charge < 1.0, "Ladestand " + charge);
    }
}
