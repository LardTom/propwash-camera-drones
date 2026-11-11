package de.lardtom.camdrones.fabric;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.command.CamCommands;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.fabric.net.FabricCamNetwork;
import de.lardtom.camdrones.integration.autonomy.WaypointTaskServer;
import de.lardtom.camdrones.pad.PadBlocks;
import de.lardtom.camdrones.part.LensItems;
import de.lardtom.camdrones.planner.PlannerInteractions;
import de.lardtom.camdrones.planner.PlannerItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class CamDronesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        CamDrones.init();
        LensItems.register((key, item) -> Registry.register(BuiltInRegistries.ITEM, key, item));
        PlannerItems.registerComponents((key, type) -> Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, key, type));
        PlannerItems.registerItems((key, item) -> Registry.register(BuiltInRegistries.ITEM, key, item));
        PadBlocks.registerBlocks((key, block) -> Registry.register(BuiltInRegistries.BLOCK, key, block));
        PadBlocks.registerItems((key, item) -> Registry.register(BuiltInRegistries.ITEM, key, item));
        FabricCamNetwork.install();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> CamCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(CrewService::tick);
        ServerTickEvents.END_SERVER_TICK.register(CamDrones::serverTick);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> PlannerInteractions.useOnEntity(player, hand, entity));
        ServerTickEvents.END_SERVER_TICK.register(WaypointTaskServer::tick);
    }
}
