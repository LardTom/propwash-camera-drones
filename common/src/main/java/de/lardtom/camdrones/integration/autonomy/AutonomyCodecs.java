package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.FollowController;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

public final class AutonomyCodecs {
    public static final int MAX_NAME_BYTES = 48 * 4;
    public static final StreamCodec<ByteBuf, String> NAME = ByteBufCodecs.stringUtf8(MAX_NAME_BYTES);
    public static final StreamCodec<ByteBuf, Optional<UUID>> OPTIONAL_UUID = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC);
    public static final StreamCodec<ByteBuf, SmartState.Function> FUNCTION = enumCodec(SmartState.Function.class, SmartState.Function.NONE);
    public static final StreamCodec<ByteBuf, FollowController.Style> STYLE = enumCodec(FollowController.Style.class, FollowController.Style.BEHIND);
    public static final StreamCodec<ByteBuf, AutoShots.Shot> SHOT = enumCodec(AutoShots.Shot.class, AutoShots.Shot.RETREAT);
    public static final StreamCodec<ByteBuf, SmartState> SMART = new StreamCodec<>() {
        @Override
        public SmartState decode(ByteBuf buf) {
            SmartState.Function function = FUNCTION.decode(buf);
            Optional<UUID> target = OPTIONAL_UUID.decode(buf);
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            double radius = buf.readDouble();
            double speed = buf.readDouble();
            boolean clockwise = buf.readBoolean();
            double course = buf.readDouble();
            FollowController.Style style = STYLE.decode(buf);
            AutoShots.Shot shot = SHOT.decode(buf);
            return new SmartState(function, target, x, y, z, radius, speed, clockwise, course, style, shot);
        }

        @Override
        public void encode(ByteBuf buf, SmartState value) {
            FUNCTION.encode(buf, value.function());
            OPTIONAL_UUID.encode(buf, value.target());
            buf.writeDouble(value.x());
            buf.writeDouble(value.y());
            buf.writeDouble(value.z());
            buf.writeDouble(value.radius());
            buf.writeDouble(value.speed());
            buf.writeBoolean(value.clockwise());
            buf.writeDouble(value.course());
            STYLE.encode(buf, value.followStyle());
            SHOT.encode(buf, value.shot());
        }
    };

    private AutonomyCodecs() {
    }

    public static <E extends Enum<E>> StreamCodec<ByteBuf, E> enumCodec(Class<E> type, E fallback) {
        E[] values = type.getEnumConstants();
        return ByteBufCodecs.VAR_INT.map(id -> id >= 0 && id < values.length ? values[id] : fallback, Enum::ordinal);
    }
}
