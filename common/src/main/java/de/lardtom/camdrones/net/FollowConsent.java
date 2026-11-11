package de.lardtom.camdrones.net;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.autonomy.AutonomyCodecs;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class FollowConsent {
    public static final CustomPacketPayload.Type<Request> REQUEST = new CustomPacketPayload.Type<>(CamDrones.id("follow_consent"));
    public static final CustomPacketPayload.Type<Reply> REPLY = new CustomPacketPayload.Type<>(CamDrones.id("follow_consent_reply"));

    private FollowConsent() {
    }

    static void define() {
        CamNetwork.clientBound(REQUEST, Request.CODEC);
        CamNetwork.serverBound(REPLY, Reply.CODEC, AutonomyServer::onConsentReply);
    }

    public record Request(int requestId, String requester, String drone, boolean droneKey, int seconds) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::requestId,
                AutonomyCodecs.NAME, Request::requester,
                AutonomyCodecs.NAME, Request::drone,
                ByteBufCodecs.BOOL, Request::droneKey,
                ByteBufCodecs.VAR_INT, Request::seconds,
                Request::new);

        public Request {
            requester = requester == null ? "" : requester;
            drone = drone == null ? "" : drone;
            droneKey = droneKey && !drone.isEmpty();
            seconds = Math.max(0, seconds);
        }

        public static Request of(int requestId, String requester, Component drone, int seconds) {
            if (drone.getContents() instanceof TranslatableContents key) {
                return new Request(requestId, requester, key.getKey(), true, seconds);
            }
            return new Request(requestId, requester, drone.getString(), false, seconds);
        }

        public Component droneName() {
            return this.droneKey ? Component.translatable(this.drone) : Component.literal(this.drone);
        }

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return REQUEST;
        }
    }

    public record Reply(int requestId, boolean accept) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Reply> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Reply::requestId,
                ByteBufCodecs.BOOL, Reply::accept,
                Reply::new);

        @Override
        public CustomPacketPayload.Type<Reply> type() {
            return REPLY;
        }
    }
}
