package de.lardtom.camdrones.integration.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.nav.NavLogic;

import java.util.Objects;

public record MissionStatus(NavLogic.MissionState state, int index, int epoch) {
    public static final MissionStatus IDLE = new MissionStatus(NavLogic.MissionState.IDLE, 0, 0);
    public static final Codec<NavLogic.MissionState> STATE_CODEC = EnumCodec.of(NavLogic.MissionState.class, NavLogic.MissionState.IDLE);
    public static final Codec<MissionStatus> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            STATE_CODEC.optionalFieldOf("state", NavLogic.MissionState.IDLE).forGetter(MissionStatus::state),
            Codec.INT.optionalFieldOf("index", 0).forGetter(MissionStatus::index),
            Codec.INT.optionalFieldOf("epoch", 0).forGetter(MissionStatus::epoch)
    ).apply(instance, MissionStatus::new));

    public MissionStatus {
        state = Objects.requireNonNullElse(state, NavLogic.MissionState.IDLE);
        index = Math.clamp(index, 0, MissionLimits.MAX_WAYPOINTS - 1);
        epoch = Math.max(0, epoch);
    }

    public MissionStatus with(NavLogic.MissionState value, int waypoint) {
        return new MissionStatus(value, waypoint, this.epoch);
    }

    public MissionStatus nextEpoch() {
        return new MissionStatus(this.state, this.index, this.epoch == Integer.MAX_VALUE ? 0 : this.epoch + 1);
    }

    public boolean active() {
        return this.state == NavLogic.MissionState.RUNNING || this.state == NavLogic.MissionState.PAUSED;
    }
}
