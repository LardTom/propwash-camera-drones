package de.lardtom.camdrones.net;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.autonomy.AutonomyCodecs;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;
import java.util.UUID;

public final class MissionCommand {
    public static final CustomPacketPayload.Type<Request> TYPE = new CustomPacketPayload.Type<>(CamDrones.id("mission_command"));
    public static final int AUTO_INDEX = -1;
    public static final double RATE_PER_SECOND = 5.0;

    public enum Command {
        START,
        PAUSE,
        RESUME,
        STOP,
        HOLD
    }

    public static final StreamCodec<ByteBuf, Command> COMMAND_CODEC = AutonomyCodecs.enumCodec(Command.class, Command.PAUSE);

    private MissionCommand() {
    }

    static void define() {
        CamNetwork.serverBound(TYPE, Request.CODEC, AutonomyServer::onMissionCommand);
    }

    public record Request(UUID serial, Command command, int startIndex) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Request::serial,
                COMMAND_CODEC, Request::command,
                ByteBufCodecs.VAR_INT, Request::startIndex,
                Request::new);

        public Request {
            Objects.requireNonNull(serial, "serial");
            Objects.requireNonNull(command, "command");
            startIndex = Math.clamp(startIndex, AUTO_INDEX, MissionLimits.MAX_WAYPOINTS - 1);
        }

        public static Request of(UUID serial, Command command) {
            return new Request(serial, command, AUTO_INDEX);
        }

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return TYPE;
        }
    }
}
