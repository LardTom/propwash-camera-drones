package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.TabletState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;
import java.util.function.Consumer;

public final class FlightPlannerItem extends Item {
    public FlightPlannerItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean sneaking = player.isSecondaryUseActive();
        if (level.isClientSide()) {
            PlannerHooks.Client client = PlannerHooks.client();
            if (sneaking && client.aimsAtMarker(stack) || !sneaking && client.aimsAtDrone()) {
                return InteractionResult.SUCCESS;
            }
            client.openEditor(hand);
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (sneaking) {
                PlannerTablets.deleteAimedMarker(serverPlayer, stack);
            } else {
                PlannerTablets.pairAimedDrone(serverPlayer, stack);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        if (player.isSecondaryUseActive()) {
            if (level.isClientSide()) {
                return PlannerHooks.client().aimsAtMarker(stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return player instanceof ServerPlayer serverPlayer && PlannerTablets.deleteAimedMarker(serverPlayer, stack)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            BlockPos pos = context.getClickedPos();
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            double top = pos.getY() + (shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y));
            PlannerTablets.addBlockWaypoint(serverPlayer, stack, pos.getX(), top, pos.getZ());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        if (!PlannerItems.registered()) {
            return;
        }
        Optional<PlannerLink> link = PlannerTablets.link(stack);
        if (link.isPresent()) {
            String name = link.get().name().isEmpty() ? link.get().shortSerial() : link.get().name();
            builder.accept(Component.translatable(FieldText.PREFIX + "tooltip.linked", name).withStyle(ChatFormatting.GRAY));
        } else {
            builder.accept(Component.translatable(FieldText.PREFIX + "tooltip.unlinked").withStyle(ChatFormatting.GRAY));
        }
        TabletState state = PlannerTablets.read(stack);
        builder.accept(Component.translatable(FieldText.PREFIX + "tooltip.missions", state.size(), TabletState.MAX_MISSIONS)
                .withStyle(ChatFormatting.GRAY));
        if (!state.isEmpty()) {
            builder.accept(Component.translatable(FieldText.PREFIX + "tooltip.active", state.activeName(), state.activeMission().size(),
                    state.groundHeight()).withStyle(ChatFormatting.DARK_GRAY));
        }
        builder.accept(Component.translatable(FieldText.PREFIX + "tooltip.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
