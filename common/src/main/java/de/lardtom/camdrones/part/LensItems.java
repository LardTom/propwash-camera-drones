package de.lardtom.camdrones.part;

import de.lardtom.camdrones.CamDrones;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class LensItems {
    public static final Lens LENS_24 = new Lens("lens_24", 24, 140.0);
    public static final Lens LENS_50 = new Lens("lens_50", 50, 160.0);
    public static final Lens LENS_85 = new Lens("lens_85", 85, 190.0);
    public static final List<Lens> ALL = List.of(LENS_24, LENS_50, LENS_85);
    public static final int STACK_SIZE = 16;

    private static final Map<Identifier, Item> ITEMS = new LinkedHashMap<>();

    private LensItems() {
    }

    public static synchronized int register(BiConsumer<ResourceKey<Item>, Item> sink) {
        int count = 0;
        for (Lens lens : ALL) {
            if (ITEMS.containsKey(lens.id())) {
                continue;
            }
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, lens.id());
            Item item = new Item(new Item.Properties()
                    .stacksTo(STACK_SIZE)
                    .rarity(Rarity.RARE)
                    .component(DataComponents.LORE, new ItemLore(List.of(Component.translatable(lens.hintKey())
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false)))))
                    .setId(key));
            sink.accept(key, item);
            ITEMS.put(lens.id(), item);
            count++;
        }
        return count;
    }

    public static synchronized Optional<Item> item(Lens lens) {
        return Optional.ofNullable(ITEMS.get(lens.id()));
    }

    public static synchronized List<ItemStack> stacks() {
        List<ItemStack> out = new ArrayList<>();
        for (Lens lens : ALL) {
            Item item = ITEMS.get(lens.id());
            if (item != null) {
                out.add(new ItemStack(item));
            }
        }
        return out;
    }

    public static synchronized Optional<Lens> lensOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        for (Lens lens : ALL) {
            Item item = ITEMS.get(lens.id());
            if (item != null && stack.is(item)) {
                return Optional.of(lens);
            }
        }
        return Optional.empty();
    }

    public record Lens(String path, int focalMm, double grams) {
        public Identifier id() {
            return CamDrones.id(this.path);
        }

        public String translationKey() {
            return "item." + CamDrones.MOD_ID + "." + this.path;
        }

        public String hintKey() {
            return CamDrones.MOD_ID + ".lens.hint." + this.path;
        }
    }
}
