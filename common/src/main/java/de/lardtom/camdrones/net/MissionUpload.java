package de.lardtom.camdrones.net;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.planner.MissionUploads;
import de.lardtom.camdrones.planner.PlannerCodecs;
import de.lardtom.camdrones.planner.model.UploadCheck;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class MissionUpload {
    public static final CustomPacketPayload.Type<Request> REQUEST = new CustomPacketPayload.Type<>(CamDrones.id("mission_upload"));
    public static final CustomPacketPayload.Type<Reply> REPLY = new CustomPacketPayload.Type<>(CamDrones.id("mission_upload_reply"));

    private MissionUpload() {
    }

    static void define() {
        CamNetwork.serverBound(REQUEST, Request.CODEC, MissionUploads::handle);
        CamNetwork.clientBound(REPLY, Reply.CODEC);
    }

    public record Request(int requestId, UUID serial, int[] mission) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::requestId,
                UUIDUtil.STREAM_CODEC, Request::serial,
                PlannerCodecs.PACKED, Request::mission,
                Request::new);

        public Request {
            Objects.requireNonNull(serial, "serial");
            mission = mission == null ? new int[0] : mission.clone();
        }

        @Override
        public int[] mission() {
            return this.mission.clone();
        }

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return REQUEST;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof Request request && this.requestId == request.requestId && this.serial.equals(request.serial)
                    && Arrays.equals(this.mission, request.mission);
        }

        @Override
        public int hashCode() {
            return (31 * this.requestId + this.serial.hashCode()) * 31 + Arrays.hashCode(this.mission);
        }

        @Override
        public String toString() {
            return "MissionUpload.Request[" + this.requestId + ", " + this.serial + ", " + this.mission.length + " ints]";
        }
    }

    public record Reply(int requestId, UploadCheck.Result result, int waypoint) implements CustomPacketPayload {
        private static final UploadCheck.Result[] RESULTS = UploadCheck.Result.values();
        public static final StreamCodec<ByteBuf, UploadCheck.Result> RESULT_CODEC = ByteBufCodecs.VAR_INT.map(
                id -> id >= 0 && id < RESULTS.length ? RESULTS[id] : UploadCheck.Result.INVALID, UploadCheck.Result::ordinal);
        public static final StreamCodec<ByteBuf, Reply> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Reply::requestId,
                RESULT_CODEC, Reply::result,
                ByteBufCodecs.VAR_INT, Reply::waypoint,
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
