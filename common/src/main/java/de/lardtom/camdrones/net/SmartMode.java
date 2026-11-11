package de.lardtom.camdrones.net;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.autonomy.AutonomyCodecs;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.integration.data.SmartState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;
import java.util.UUID;

public final class SmartMode {
    public static final CustomPacketPayload.Type<Request> TYPE = new CustomPacketPayload.Type<>(CamDrones.id("smart_mode"));
    public static final double RATE_PER_SECOND = 5.0;

    private SmartMode() {
    }

    static void define() {
        CamNetwork.serverBound(TYPE, Request.CODEC, AutonomyServer::onSmartMode);
    }

    public record Request(UUID serial, SmartState smart) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Request::serial,
                AutonomyCodecs.SMART, Request::smart,
                Request::new);

        public Request {
            Objects.requireNonNull(serial, "serial");
            smart = Objects.requireNonNullElse(smart, SmartState.NONE);
        }

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return TYPE;
        }
    }
}
