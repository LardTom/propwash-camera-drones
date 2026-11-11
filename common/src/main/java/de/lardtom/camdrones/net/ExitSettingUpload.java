package de.lardtom.camdrones.net;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.planner.ExitSettings;
import de.lardtom.camdrones.planner.model.ExitSetting;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;
import java.util.UUID;

public final class ExitSettingUpload {
    public static final CustomPacketPayload.Type<Request> REQUEST = new CustomPacketPayload.Type<>(CamDrones.id("exit_setting"));
    public static final CustomPacketPayload.Type<Reply> REPLY = new CustomPacketPayload.Type<>(CamDrones.id("exit_setting_reply"));

    private ExitSettingUpload() {
    }

    static void define() {
        CamNetwork.serverBound(REQUEST, Request.CODEC, ExitSettings::handle);
        CamNetwork.clientBound(REPLY, Reply.CODEC);
    }

    public record Request(int requestId, UUID serial, int exit) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::requestId,
                UUIDUtil.STREAM_CODEC, Request::serial,
                ByteBufCodecs.VAR_INT, Request::exit,
                Request::new);

        public Request {
            Objects.requireNonNull(serial, "serial");
        }

        public static Request of(int requestId, UUID serial, ExitSetting exit) {
            return new Request(requestId, serial, exit.ordinal());
        }

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return REQUEST;
        }
    }

    public record Reply(int requestId, ExitSetting.Result result, int exit) implements CustomPacketPayload {
        private static final ExitSetting.Result[] RESULTS = ExitSetting.Result.values();
        public static final StreamCodec<ByteBuf, ExitSetting.Result> RESULT_CODEC = ByteBufCodecs.VAR_INT.map(
                id -> id >= 0 && id < RESULTS.length ? RESULTS[id] : ExitSetting.Result.INVALID, ExitSetting.Result::ordinal);
        public static final StreamCodec<ByteBuf, Reply> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Reply::requestId,
                RESULT_CODEC, Reply::result,
                ByteBufCodecs.VAR_INT, Reply::exit,
                Reply::new);

        public Reply {
            Objects.requireNonNull(result, "result");
        }

        @Override
        public CustomPacketPayload.Type<Reply> type() {
            return REPLY;
        }
    }
}
