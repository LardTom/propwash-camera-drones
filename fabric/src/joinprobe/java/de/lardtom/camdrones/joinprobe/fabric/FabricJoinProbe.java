package de.lardtom.camdrones.joinprobe.fabric;

import de.lardtom.camdrones.joinprobe.BareJoinProbe;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class FabricJoinProbe implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (BareJoinProbe.enabled()) {
            ClientTickEvents.END_CLIENT_TICK.register(BareJoinProbe::tick);
        }
    }
}
