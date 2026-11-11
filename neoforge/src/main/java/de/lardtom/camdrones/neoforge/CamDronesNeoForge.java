package de.lardtom.camdrones.neoforge;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.command.CamCommands;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.integration.autonomy.WaypointTaskServer;
import de.lardtom.camdrones.neoforge.net.NeoForgeCamNetwork;
import de.lardtom.camdrones.pad.PadBlocks;
import de.lardtom.camdrones.part.LensItems;
import de.lardtom.camdrones.planner.PlannerInteractions;
import de.lardtom.camdrones.planner.PlannerItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(CamDrones.MOD_ID)
public final class CamDronesNeoForge {
    public CamDronesNeoForge(IEventBus modBus, ModContainer container) {
        CamDrones.init();
        modBus.addListener(RegisterEvent.class, event -> event.register(Registries.ITEM, helper -> LensItems.register(helper::register)));
        modBus.addListener(RegisterEvent.class, event -> {
            event.register(Registries.DATA_COMPONENT_TYPE, helper -> PlannerItems.registerComponents(helper::register));
            event.register(Registries.ITEM, helper -> PlannerItems.registerItems(helper::register));
        });
        modBus.addListener(RegisterEvent.class, event -> {
            event.register(Registries.BLOCK, helper -> PadBlocks.registerBlocks(helper::register));
            event.register(Registries.ITEM, helper -> PadBlocks.registerItems(helper::register));
        });
        NeoForgeCamNetwork.install(modBus);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> CamCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> CrewService.tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> CamDrones.serverTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, event -> {
            InteractionResult result = PlannerInteractions.useOnEntity(event.getEntity(), event.getHand(), event.getTarget());
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> WaypointTaskServer.tick(event.getServer()));
    }
}
