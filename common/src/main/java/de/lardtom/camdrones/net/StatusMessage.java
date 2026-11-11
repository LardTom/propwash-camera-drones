package de.lardtom.camdrones.net;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.autonomy.AutonomyCodecs;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;

public final class StatusMessage {
    public static final CustomPacketPayload.Type<Payload> TYPE = new CustomPacketPayload.Type<>(CamDrones.id("status"));
    public static final StreamCodec<ByteBuf, AutonomyStatus> STATUS_CODEC = ByteBufCodecs.VAR_INT.map(AutonomyStatus::byOrdinal, Enum::ordinal);

    private StatusMessage() {
    }

    static void define() {
        CamNetwork.clientBound(TYPE, Payload.CODEC);
    }

    public record Payload(AutonomyStatus status, String drone, String detail) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Payload> CODEC = StreamCodec.composite(
                STATUS_CODEC, Payload::status,
                AutonomyCodecs.NAME, Payload::drone,
                AutonomyCodecs.NAME, Payload::detail,
                Payload::new);

        public Payload {
            Objects.requireNonNull(status, "status");
            drone = drone == null ? "" : drone;
            detail = detail == null ? "" : detail;
        }

        @Override
        public CustomPacketPayload.Type<Payload> type() {
            return TYPE;
        }
    }
}
