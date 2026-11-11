package de.lardtom.camdrones.fabric.net;

import de.lardtom.camdrones.net.CamNetwork;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class FabricCamNetwork {
    private static boolean installed;

    private FabricCamNetwork() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        for (CamNetwork.ServerBound<?> entry : CamNetwork.serverBound()) {
            serverBound(entry);
        }
        for (CamNetwork.ClientBound<?> entry : CamNetwork.clientBound()) {
            clientBound(entry);
        }
        CamNetwork.bindServer(new CamNetwork.ServerSender() {
            @Override
            public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
                return ServerPlayNetworking.canSend(player, type);
            }

            @Override
            public void send(ServerPlayer player, CustomPacketPayload payload) {
                ServerPlayNetworking.send(player, payload);
            }
        });
    }

    private static <T extends CustomPacketPayload> void serverBound(CamNetwork.ServerBound<T> entry) {
        PayloadTypeRegistry.serverboundPlay().register(entry.type(), entry.codec());
        ServerPlayNetworking.registerGlobalReceiver(entry.type(), (payload, context) -> entry.handler().handle(payload, context.player()));
    }

    private static <T extends CustomPacketPayload> void clientBound(CamNetwork.ClientBound<T> entry) {
        PayloadTypeRegistry.clientboundPlay().register(entry.type(), entry.codec());
    }
}
