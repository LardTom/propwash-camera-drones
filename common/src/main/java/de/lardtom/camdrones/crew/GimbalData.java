package de.lardtom.camdrones.crew;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.integration.data.EnumCodec;
import net.minecraft.core.UUIDUtil;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record GimbalData(Optional<UUID> operator, GimbalSetting setting, int seq, boolean solo, boolean steered) {
    public static final GimbalData NONE = new GimbalData(Optional.empty(), GimbalSetting.CENTER, 0);
    public static final Codec<GimbalSetting.Mode> MODE_CODEC = EnumCodec.of(GimbalSetting.Mode.class, GimbalSetting.Mode.FOLLOW);
    public static final Codec<GimbalData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("operator").forGetter(GimbalData::operator),
            MODE_CODEC.optionalFieldOf("mode", GimbalSetting.Mode.FOLLOW).forGetter(data -> data.setting().mode()),
            Codec.FLOAT.optionalFieldOf("tilt", 0.0F).forGetter(data -> (float) data.setting().tilt()),
            Codec.FLOAT.optionalFieldOf("pan", 0.0F).forGetter(data -> (float) data.setting().pan()),
            Codec.FLOAT.optionalFieldOf("heading", 0.0F).forGetter(data -> (float) data.setting().heading()),
            Codec.FLOAT.optionalFieldOf("zoom", 0.0F).forGetter(data -> (float) data.setting().zoom()),
            Codec.INT.optionalFieldOf("seq", 0).forGetter(GimbalData::seq),
            Codec.BOOL.optionalFieldOf("solo", false).forGetter(GimbalData::solo),
            Codec.BOOL.optionalFieldOf("steered", false).forGetter(GimbalData::steered)
    ).apply(instance, GimbalData::of));

    public GimbalData {
        operator = Objects.requireNonNullElse(operator, Optional.empty());
        setting = Objects.requireNonNullElse(setting, GimbalSetting.CENTER);
        seq = Math.max(0, seq);
        solo = solo && operator.isPresent();
        steered = steered && solo;
    }

    public GimbalData(Optional<UUID> operator, GimbalSetting setting, int seq) {
        this(operator, setting, seq, false, false);
    }

    public static GimbalData of(Optional<UUID> operator, GimbalSetting.Mode mode, float tilt, float pan, float heading, float zoom, int seq) {
        return of(operator, mode, tilt, pan, heading, zoom, seq, false, false);
    }

    public static GimbalData of(Optional<UUID> operator, GimbalSetting.Mode mode, float tilt, float pan, float heading, float zoom, int seq,
                                boolean solo, boolean steered) {
        return new GimbalData(operator, new GimbalSetting(mode, tilt, pan, heading, zoom), seq, solo, steered);
    }

    public boolean operated() {
        return this.operator.isPresent();
    }

    public boolean operatedBy(UUID player) {
        return player != null && this.operator.filter(player::equals).isPresent();
    }

    public boolean soloBy(UUID player) {
        return this.solo && this.operatedBy(player);
    }

    public boolean manual() {
        return this.solo && this.steered;
    }

    public GimbalData withOperator(Optional<UUID> value) {
        return new GimbalData(value, this.setting, this.next(), false, false);
    }

    public GimbalData withSolo(UUID player) {
        return new GimbalData(Optional.of(player), this.setting, this.next(), true, false);
    }

    public GimbalData withSteered() {
        return new GimbalData(this.operator, this.setting, this.next(), this.solo, true);
    }

    public GimbalData withSetting(GimbalSetting value) {
        return new GimbalData(this.operator, value, this.next(), this.solo, this.steered);
    }

    private int next() {
        return this.seq == Integer.MAX_VALUE ? 1 : this.seq + 1;
    }
}
