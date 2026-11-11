package de.lardtom.camdrones.net;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CrewPackets {
    public static final CustomPacketPayload.Type<Request> REQUEST = new CustomPacketPayload.Type<>(CamDrones.id("crew"));
    public static final CustomPacketPayload.Type<Notice> NOTICE = new CustomPacketPayload.Type<>(CamDrones.id("crew_notice"));
    public static final CustomPacketPayload.Type<Gimbal> GIMBAL = new CustomPacketPayload.Type<>(CamDrones.id("crew_gimbal"));
    public static final int MAX_NAME = 64;

    private CrewPackets() {
    }

    static void define() {
        CamNetwork.serverBound(REQUEST, Request.CODEC, CrewService::handle);
        CamNetwork.clientBound(NOTICE, Notice.CODEC);
        CamNetwork.serverBound(GIMBAL, Gimbal.CODEC, CrewService::gimbal);
    }

    public enum Action {
        INVITE,
        ACCEPT,
        DECLINE,
        LEAVE,
        RELEASE,
        SOLO
    }

    public enum Kind {
        INVITED,
        BOUND,
        RELEASED,
        SOLO_REFUSED
    }

    private static <E extends Enum<E>> StreamCodec<ByteBuf, E> enumCodec(E[] values, E fallback) {
        return ByteBufCodecs.VAR_INT.map(id -> id >= 0 && id < values.length ? values[id] : fallback, Enum::ordinal);
    }

    public static final StreamCodec<ByteBuf, Action> ACTION_CODEC = enumCodec(Action.values(), Action.DECLINE);
    public static final StreamCodec<ByteBuf, Kind> KIND_CODEC = enumCodec(Kind.values(), Kind.RELEASED);
    public static final StreamCodec<ByteBuf, GimbalSetting.Mode> MODE_CODEC = enumCodec(GimbalSetting.Mode.values(), GimbalSetting.Mode.FOLLOW);
    public static final StreamCodec<ByteBuf, GimbalSetting> SETTING_CODEC = StreamCodec.composite(
            MODE_CODEC, GimbalSetting::mode,
            ByteBufCodecs.FLOAT, setting -> (float) setting.tilt(),
            ByteBufCodecs.FLOAT, setting -> (float) setting.pan(),
            ByteBufCodecs.FLOAT, setting -> (float) setting.heading(),
            ByteBufCodecs.FLOAT, setting -> (float) setting.zoom(),
            (mode, tilt, pan, heading, zoom) -> new GimbalSetting(mode, tilt, pan, heading, zoom));

    public record Request(Action action, int drone, Optional<UUID> target) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ACTION_CODEC, Request::action,
                ByteBufCodecs.VAR_INT, Request::drone,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Request::target,
                Request::new);

        public Request {
            Objects.requireNonNull(action, "action");
            target = Objects.requireNonNullElse(target, Optional.empty());
        }

        public static Request of(Action action, int drone) {
            return new Request(action, drone, Optional.empty());
        }

        public static Request invite(int drone, UUID target) {
            return new Request(Action.INVITE, drone, Optional.of(target));
        }

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return REQUEST;
        }
    }

    public record Notice(Kind kind, int drone, String name) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Notice> CODEC = StreamCodec.composite(
                KIND_CODEC, Notice::kind,
                ByteBufCodecs.VAR_INT, Notice::drone,
                ByteBufCodecs.stringUtf8(MAX_NAME), Notice::name,
                Notice::new);

        public Notice {
            Objects.requireNonNull(kind, "kind");
            name = name == null ? "" : name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
        }

        @Override
        public CustomPacketPayload.Type<Notice> type() {
            return NOTICE;
        }
    }

    public record Gimbal(int drone, GimbalSetting setting) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Gimbal> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Gimbal::drone,
                SETTING_CODEC, Gimbal::setting,
                Gimbal::new);

        public Gimbal {
            setting = Objects.requireNonNullElse(setting, GimbalSetting.CENTER);
        }

        @Override
        public CustomPacketPayload.Type<Gimbal> type() {
            return GIMBAL;
        }
    }
}
