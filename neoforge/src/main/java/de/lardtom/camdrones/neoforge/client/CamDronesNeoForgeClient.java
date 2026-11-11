package de.lardtom.camdrones.neoforge.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.CamDronesClient;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.hud.CamHud;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.client.planner.PlannerClient;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = CamDrones.MOD_ID, dist = Dist.CLIENT)
public final class CamDronesNeoForgeClient {
    public CamDronesNeoForgeClient(IEventBus modBus, ModContainer container) {
        CamDronesClient.init();
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            KeyMapping.Category category = new KeyMapping.Category(CamKeys.CATEGORY);
            event.registerCategory(category);
            CamKeys.create(category).forEach(event::register);
        });
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> CamHud.tick(Minecraft.getInstance()));
        NeoForgeCamNetworkClient.install(modBus);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> PlannerClient.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> GimbalClient.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> CamDronesClient.tick(Minecraft.getInstance()));
    }
}
