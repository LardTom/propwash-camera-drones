package de.lardtom.camdrones.pad;

import de.lardtom.camdrones.CamDrones;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

public final class PadBlocks {
    public static final Identifier LANDING_PAD_ID = CamDrones.id("landing_pad");
    public static final float HARDNESS = 1.0F;
    public static final float RESISTANCE = 3.0F;

    private static volatile Block landingPad;
    private static volatile Item landingPadItem;

    private PadBlocks() {
    }

    public static BlockBehaviour.Properties properties(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(HARDNESS, RESISTANCE).sound(SoundType.STONE).setId(key);
    }

    public static synchronized int registerBlocks(BiConsumer<ResourceKey<Block>, Block> sink) {
        if (landingPad != null) {
            return 0;
        }
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, LANDING_PAD_ID);
        Block block = new LandingPadBlock(properties(key));
        sink.accept(key, block);
        landingPad = block;
        return 1;
    }

    public static synchronized int registerItems(BiConsumer<ResourceKey<Item>, Item> sink) {
        Block block = landingPad;
        if (block == null) {
            throw new IllegalStateException("Landeplatz von " + CamDrones.MOD_ID + ": Block vor dem Item anmelden");
        }
        if (landingPadItem != null) {
            return 0;
        }
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, LANDING_PAD_ID);
        Item item = new LandingPadItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key));
        sink.accept(key, item);
        landingPadItem = item;
        return 1;
    }

    public static boolean registered() {
        return landingPad != null && landingPadItem != null;
    }

    public static Optional<Block> landingPad() {
        return Optional.ofNullable(landingPad);
    }

    public static Optional<Item> landingPadItem() {
        return Optional.ofNullable(landingPadItem);
    }

    public static List<ItemStack> creativeStacks() {
        Item item = landingPadItem;
        return item == null ? List.of() : List.of(new ItemStack(item));
    }
}
