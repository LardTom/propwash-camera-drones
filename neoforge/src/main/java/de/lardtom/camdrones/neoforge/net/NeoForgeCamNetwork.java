package de.lardtom.camdrones.neoforge.net;

import de.lardtom.camdrones.net.CamNetwork;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NeoForgeCamNetwork {
    private static boolean installed;

    private NeoForgeCamNetwork() {
    }

    public static synchronized void install(IEventBus modBus) {
        if (installed) {
            return;
        }
        installed = true;
        modBus.addListener(RegisterPayloadHandlersEvent.class, NeoForgeCamNetwork::register);
        CamNetwork.bindServer(new CamNetwork.ServerSender() {
            @Override
            public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
                return player.connection != null && player.connection.hasChannel(type);
            }

            @Override
            public void send(ServerPlayer player, CustomPacketPayload payload) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        });
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(CamNetwork.VERSION).optional();
        for (CamNetwork.ServerBound<?> entry : CamNetwork.serverBound()) {
            serverBound(registrar, entry);
        }
        for (CamNetwork.ClientBound<?> entry : CamNetwork.clientBound()) {
            clientBound(registrar, entry);
        }
    }

    private static <T extends CustomPacketPayload> void serverBound(PayloadRegistrar registrar, CamNetwork.ServerBound<T> entry) {
        registrar.playToServer(entry.type(), entry.codec(), (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                entry.handler().handle(payload, player);
            }
        });
    }

    private static <T extends CustomPacketPayload> void clientBound(PayloadRegistrar registrar, CamNetwork.ClientBound<T> entry) {
        registrar.playToClient(entry.type(), entry.codec());
    }
}
