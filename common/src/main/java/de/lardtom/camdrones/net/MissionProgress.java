package de.lardtom.camdrones.net;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.autonomy.AutonomyCodecs;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.nav.NavLogic;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;
import java.util.UUID;

public final class MissionProgress {
    public static final CustomPacketPayload.Type<Report> TYPE = new CustomPacketPayload.Type<>(CamDrones.id("mission_progress"));
    public static final StreamCodec<ByteBuf, NavLogic.MissionState> STATE_CODEC = AutonomyCodecs.enumCodec(NavLogic.MissionState.class,
            NavLogic.MissionState.IDLE);

    private MissionProgress() {
    }

    static void define() {
        CamNetwork.serverBound(TYPE, Report.CODEC, AutonomyServer::onMissionProgress);
    }

    public record Report(UUID serial, NavLogic.MissionState state, int index, int epoch) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Report> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Report::serial,
                STATE_CODEC, Report::state,
                ByteBufCodecs.VAR_INT, Report::index,
                ByteBufCodecs.VAR_INT, Report::epoch,
                Report::new);

        public Report {
            Objects.requireNonNull(serial, "serial");
            Objects.requireNonNull(state, "state");
            index = Math.clamp(index, 0, MissionLimits.MAX_WAYPOINTS - 1);
            epoch = Math.max(0, epoch);
        }

        @Override
        public CustomPacketPayload.Type<Report> type() {
            return TYPE;
        }
    }
}
