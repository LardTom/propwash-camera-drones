package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.CamDrones;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class PlannerItems {
    public static final Identifier FLIGHT_PLANNER_ID = CamDrones.id("flight_planner");
    public static final Identifier LINK_ID = CamDrones.id("planner_link");
    public static final Identifier MISSIONS_ID = CamDrones.id("planner_missions");
    public static final Identifier SETTINGS_ID = CamDrones.id("planner_settings");

    private static volatile Item flightPlanner;
    private static volatile DataComponentType<PlannerLink> link;
    private static volatile DataComponentType<PlannerMissions> missions;
    private static volatile DataComponentType<PlannerSettings> settings;

    private PlannerItems() {
    }

    public static synchronized int registerComponents(BiConsumer<ResourceKey<DataComponentType<?>>, DataComponentType<?>> sink) {
        if (link != null) {
            return 0;
        }
        DataComponentType<PlannerLink> linkType = DataComponentType.<PlannerLink>builder()
                .persistent(PlannerLink.CODEC).networkSynchronized(PlannerLink.STREAM_CODEC).build();
        DataComponentType<PlannerMissions> missionsType = DataComponentType.<PlannerMissions>builder()
                .persistent(PlannerCodecs.MISSIONS_CODEC).networkSynchronized(PlannerCodecs.MISSIONS_STREAM_CODEC).cacheEncoding().build();
        DataComponentType<PlannerSettings> settingsType = DataComponentType.<PlannerSettings>builder()
                .persistent(PlannerCodecs.SETTINGS_CODEC).networkSynchronized(PlannerCodecs.SETTINGS_STREAM_CODEC).build();
        sink.accept(ResourceKey.create(Registries.DATA_COMPONENT_TYPE, LINK_ID), linkType);
        sink.accept(ResourceKey.create(Registries.DATA_COMPONENT_TYPE, MISSIONS_ID), missionsType);
        sink.accept(ResourceKey.create(Registries.DATA_COMPONENT_TYPE, SETTINGS_ID), settingsType);
        link = linkType;
        missions = missionsType;
        settings = settingsType;
        return 3;
    }

    public static synchronized int registerItems(BiConsumer<ResourceKey<Item>, Item> sink) {
        if (flightPlanner != null) {
            return 0;
        }
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, FLIGHT_PLANNER_ID);
        Item item = new FlightPlannerItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).setId(key));
        sink.accept(key, item);
        flightPlanner = item;
        return 1;
    }

    public static boolean registered() {
        return flightPlanner != null && link != null;
    }

    public static Optional<Item> flightPlanner() {
        return Optional.ofNullable(flightPlanner);
    }

    public static boolean isPlanner(ItemStack stack) {
        Item item = flightPlanner;
        return item != null && stack != null && !stack.isEmpty() && stack.is(item);
    }

    public static DataComponentType<PlannerLink> link() {
        return require(link);
    }

    public static DataComponentType<PlannerMissions> missions() {
        return require(missions);
    }

    public static DataComponentType<PlannerSettings> settings() {
        return require(settings);
    }

    public static List<ItemStack> creativeStacks() {
        Item item = flightPlanner;
        return item == null ? List.of() : List.of(new ItemStack(item));
    }

    private static <T> T require(T value) {
        if (value == null) {
            throw new IllegalStateException("Flugplaner von " + CamDrones.MOD_ID + " ist noch nicht angemeldet");
        }
        return value;
    }
}
