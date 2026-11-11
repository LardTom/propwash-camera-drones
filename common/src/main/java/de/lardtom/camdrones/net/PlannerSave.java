package de.lardtom.camdrones.net;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.planner.PlannerCodecs;
import de.lardtom.camdrones.planner.PlannerMissions;
import de.lardtom.camdrones.planner.PlannerSettings;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.model.RateLimiter;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;

import java.util.Objects;

public final class PlannerSave {
    public static final CustomPacketPayload.Type<Request> REQUEST = new CustomPacketPayload.Type<>(CamDrones.id("planner_save"));
    public static final double RATE_PER_SECOND = 4.0;
    public static final double BURST = 8.0;
    private static final RateLimiter RATE = new RateLimiter(RATE_PER_SECOND, BURST);

    private PlannerSave() {
    }

    static void define() {
        CamNetwork.serverBound(REQUEST, Request.CODEC, PlannerSave::handle);
    }

    static void handle(Request request, ServerPlayer player) {
        if (!RATE.tryAcquire(player.getUUID(), Util.getMillis())) {
            CamDrones.LOG.debug("{}: Flugplaner von {} zu oft gespeichert", CamDrones.MOD_NAME, player.getGameProfile().name());
            return;
        }
        PlannerTablets.save(player, request.slot(), request.missions(), request.settings());
    }

    public record Request(int slot, PlannerMissions missions, PlannerSettings settings) implements CustomPacketPayload {
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::slot,
                PlannerCodecs.MISSIONS_STREAM_CODEC, Request::missions,
                PlannerCodecs.SETTINGS_STREAM_CODEC, Request::settings,
                Request::new);

        public Request {
            Objects.requireNonNull(missions, "missions");
            Objects.requireNonNull(settings, "settings");
        }

        @Override
        public CustomPacketPayload.Type<Request> type() {
            return REQUEST;
        }
    }
}
