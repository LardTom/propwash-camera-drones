package de.lardtom.camdrones.fabric.client;

import de.lardtom.camdrones.net.CamNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class FabricCamNetworkClient {
    private static boolean installed;

    private FabricCamNetworkClient() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        for (CamNetwork.ClientBound<?> entry : CamNetwork.clientBound()) {
            receive(entry);
        }
        CamNetwork.bindClient(new CamNetwork.ClientSender() {
            @Override
            public boolean canSend(CustomPacketPayload.Type<?> type) {
                return ClientPlayNetworking.canSend(type);
            }

            @Override
            public void send(CustomPacketPayload payload) {
                ClientPlayNetworking.send(payload);
            }
        });
    }

    private static <T extends CustomPacketPayload> void receive(CamNetwork.ClientBound<T> entry) {
        ClientPlayNetworking.registerGlobalReceiver(entry.type(), (payload, context) -> CamNetwork.dispatchClient(payload));
    }
}
