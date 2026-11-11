package de.lardtom.camdrones.pad;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

public class LandingPadItem extends BlockItem {
    public static final String TOOLTIP_KEY = "block.camdrones.landing_pad.tooltip";

    public LandingPadItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        builder.accept(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY));
    }
}
