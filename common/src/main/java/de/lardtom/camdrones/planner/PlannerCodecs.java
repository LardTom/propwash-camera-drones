package de.lardtom.camdrones.planner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.camdrones.planner.model.MissionNames;
import de.lardtom.camdrones.planner.model.PlannerEntry;
import de.lardtom.camdrones.planner.model.TabletState;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.stream.IntStream;

public final class PlannerCodecs {
    public static final int MAX_NAME_BYTES = MissionNames.MAX_LENGTH * 4;
    public static final StreamCodec<ByteBuf, String> NAME = ByteBufCodecs.stringUtf8(MAX_NAME_BYTES);
    public static final StreamCodec<ByteBuf, int[]> PACKED = intArray(PlannerEntry.MAX_INTS);

    public static final Codec<PlannerEntry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("name", "").forGetter(PlannerEntry::name),
                    Codec.INT_STREAM.optionalFieldOf("mission", IntStream.empty()).forGetter(entry -> IntStream.of(entry.packed())))
            .apply(instance, (name, mission) -> PlannerEntry.fromPacked(name, mission.toArray())));

    public static final StreamCodec<ByteBuf, PlannerEntry> ENTRY_STREAM_CODEC = StreamCodec.composite(
            NAME, PlannerEntry::name,
            PACKED, PlannerEntry::packed,
            PlannerEntry::fromPacked);

    public static final Codec<PlannerMissions> MISSIONS_CODEC = ENTRY_CODEC.listOf().xmap(PlannerMissions::new, PlannerMissions::entries);

    public static final StreamCodec<ByteBuf, PlannerMissions> MISSIONS_STREAM_CODEC = ENTRY_STREAM_CODEC
            .apply(ByteBufCodecs.list(TabletState.MAX_MISSIONS))
            .map(PlannerMissions::new, PlannerMissions::entries);

    public static final Codec<PlannerSettings> SETTINGS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("ground_height", TabletState.DEFAULT_GROUND_HEIGHT).forGetter(PlannerSettings::groundHeight),
                    Codec.INT.optionalFieldOf("active", 0).forGetter(PlannerSettings::active))
            .apply(instance, PlannerSettings::new));

    public static final StreamCodec<ByteBuf, PlannerSettings> SETTINGS_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlannerSettings::groundHeight,
            ByteBufCodecs.VAR_INT, PlannerSettings::active,
            PlannerSettings::new);

    private PlannerCodecs() {
    }

    public static StreamCodec<ByteBuf, int[]> intArray(int maxLength) {
        return new StreamCodec<>() {
            @Override
            public int[] decode(ByteBuf buf) {
                int length = ByteBufCodecs.VAR_INT.decode(buf);
                if (length < 0 || length > maxLength) {
                    throw new DecoderException("Zu viele Werte: " + length + " > " + maxLength);
                }
                if (buf.readableBytes() < length * 4L) {
                    throw new DecoderException("Zu wenige Daten für " + length + " Werte");
                }
                int[] out = new int[length];
                for (int i = 0; i < length; i++) {
                    out[i] = buf.readInt();
                }
                return out;
            }

            @Override
            public void encode(ByteBuf buf, int[] value) {
                if (value.length > maxLength) {
                    throw new IllegalArgumentException("Zu viele Werte: " + value.length + " > " + maxLength);
                }
                ByteBufCodecs.VAR_INT.encode(buf, value.length);
                for (int v : value) {
                    buf.writeInt(v);
                }
            }
        };
    }
}
