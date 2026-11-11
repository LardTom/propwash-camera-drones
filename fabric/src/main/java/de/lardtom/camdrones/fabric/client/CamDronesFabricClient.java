package de.lardtom.camdrones.fabric.client;

import de.lardtom.camdrones.client.CamDronesClient;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.hud.CamHud;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.client.planner.PlannerClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class CamDronesFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CamDronesClient.init();
        CamKeys.create(KeyMapping.Category.register(CamKeys.CATEGORY)).forEach(KeyMappingHelper::registerKeyMapping);
        ClientTickEvents.END_CLIENT_TICK.register(CamHud::tick);
        FabricCamNetworkClient.install();
        ClientTickEvents.END_CLIENT_TICK.register(PlannerClient::tick);
        ClientTickEvents.END_CLIENT_TICK.register(GimbalClient::tick);
        ClientTickEvents.END_CLIENT_TICK.register(CamDronesClient::tick);
    }
}
