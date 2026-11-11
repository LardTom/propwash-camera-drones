package de.lardtom.camdrones.part;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;

public final class CamLoot {
    public static final String VILLAGE_HOUSE_TABLE = "inject/village_house";
    public static final List<String> VILLAGE_HOUSES = List.of("chests/village/village_plains_house", "chests/village/village_desert_house",
            "chests/village/village_savanna_house", "chests/village/village_snowy_house", "chests/village/village_taiga_house");

    private CamLoot() {
    }

    public record Injection(ResourceKey<LootTable> target, ResourceKey<LootTable> table) {
    }

    public static List<Injection> injections() {
        ResourceKey<LootTable> table = ResourceKey.create(Registries.LOOT_TABLE, CamDrones.id(VILLAGE_HOUSE_TABLE));
        return VILLAGE_HOUSES.stream()
                .map(target -> new Injection(ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace(target)), table))
                .toList();
    }

    public static int register() {
        List<Injection> all = injections();
        for (Injection injection : all) {
            PropwashApi.loot().inject(injection.target(), injection.table());
        }
        return all.size();
    }
}
