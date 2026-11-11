package de.lardtom.camdrones.net;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.award.CamAwards;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class VideoRecorded {
    public static final CustomPacketPayload.Type<Report> TYPE = new CustomPacketPayload.Type<>(CamDrones.id("video_recorded"));

    private VideoRecorded() {
    }

    static void define() {
        CamNetwork.serverBound(TYPE, Report.CODEC, CamAwards::onVideo);
    }

    public record Report(int drone) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Report> CODEC = ByteBufCodecs.VAR_INT.map(Report::new, Report::drone);

        @Override
        public CustomPacketPayload.Type<Report> type() {
            return TYPE;
        }
    }
}
