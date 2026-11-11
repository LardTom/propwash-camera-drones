package de.lardtom.camdrones.neoforge.client;

import de.lardtom.camdrones.net.CamNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

public final class NeoForgeCamNetworkClient {
    private static boolean installed;

    private NeoForgeCamNetworkClient() {
    }

    public static synchronized void install(IEventBus modBus) {
        if (installed) {
            return;
        }
        installed = true;
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, NeoForgeCamNetworkClient::register);
        CamNetwork.bindClient(new CamNetwork.ClientSender() {
            @Override
            public boolean canSend(CustomPacketPayload.Type<?> type) {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                return connection != null && connection.hasChannel(type);
            }

            @Override
            public void send(CustomPacketPayload payload) {
                ClientPacketDistributor.sendToServer(payload);
            }
        });
    }

    private static void register(RegisterClientPayloadHandlersEvent event) {
        for (CamNetwork.ClientBound<?> entry : CamNetwork.clientBound()) {
            receive(event, entry);
        }
    }

    private static <T extends CustomPacketPayload> void receive(RegisterClientPayloadHandlersEvent event, CamNetwork.ClientBound<T> entry) {
        event.register(entry.type(), (payload, context) -> CamNetwork.dispatchClient(payload));
    }
}
