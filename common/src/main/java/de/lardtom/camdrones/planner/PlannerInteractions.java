package de.lardtom.camdrones.planner;

import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class PlannerInteractions {
    private PlannerInteractions() {
    }

    public static boolean pairs(boolean mainHand, boolean sneaking, boolean holdsPlanner, boolean spectator, boolean targetIsDrone) {
        return mainHand && sneaking && holdsPlanner && !spectator && targetIsDrone;
    }

    public static InteractionResult useOnEntity(Player player, InteractionHand hand, Entity target) {
        if (player == null || target == null || !PlannerItems.registered()) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!pairs(hand == InteractionHand.MAIN_HAND, player.isSecondaryUseActive(), PlannerItems.isPlanner(stack), player.isSpectator(),
                PropwashApi.drones().isDrone(target))) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PlannerTablets.pairDrone(serverPlayer, stack, target);
        }
        return InteractionResult.SUCCESS;
    }
}
