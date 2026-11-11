package de.lardtom.camdrones.net;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.planner.ViewSettings;
import de.lardtom.camdrones.planner.model.ExitSetting;
import de.lardtom.camdrones.planner.model.ViewSetting;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;
import java.util.UUID;

public final class ViewSettingUpload {
    public static final CustomPacketPayload.Type<Request> REQUEST = new CustomPacketPayload.Type<>(CamDrones.id("view_setting"));
    public static final CustomPacketPayload.Type<Reply> REPLY = new CustomPacketPayload.Type<>(CamDrones.id("view_setting_reply"));

    private ViewSettingUpload() {
    }

    static void define() {
        CamNetwork.serverBound(REQUEST, Request.CODEC, ViewSettings::handle);
        CamNetwork.clientBound(REPLY, Reply.CODEC);
    }

    public record Request(int requestId, UUID serial, int view) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::requestId,
                UUIDUtil.STREAM_CODEC, Request::serial,
                ByteBufCodecs.VAR_INT, Request::view,
                Request::new);

        public Request {
            Objects.requireNonNull(serial, "serial");
        }

        public static Request of(int requestId, UUID serial, ViewSetting view) {
            return new Request(requestId, serial, view.ordinal());
        }

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return REQUEST;
        }
    }

    public record Reply(int requestId, ExitSetting.Result result, int view) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Reply> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Reply::requestId,
                ExitSettingUpload.Reply.RESULT_CODEC, Reply::result,
                ByteBufCodecs.VAR_INT, Reply::view,
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
