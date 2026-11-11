package de.lardtom.camdrones.planner;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.planner.model.MissionNames;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PlannerLink(UUID serial, String name, String flightClass) {
    public static final int MAX_NAME_LENGTH = 32;

    public static final Codec<PlannerLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    UUIDUtil.CODEC.fieldOf("serial").forGetter(PlannerLink::serial),
                    Codec.STRING.optionalFieldOf("name", "").forGetter(PlannerLink::name),
                    Codec.STRING.optionalFieldOf("class", "").forGetter(PlannerLink::flightClass))
            .apply(instance, PlannerLink::new));

    public static final StreamCodec<ByteBuf, PlannerLink> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PlannerLink::serial,
            ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH * 4), PlannerLink::name,
            ByteBufCodecs.stringUtf8(16), PlannerLink::flightClass,
            PlannerLink::new);

    public PlannerLink {
        Objects.requireNonNull(serial, "serial");
        name = trim(name);
        flightClass = flightClass == null ? "" : flightClass;
    }

    public static PlannerLink of(UUID serial, String name, FlightClass flightClass) {
        return new PlannerLink(serial, name, flightClass == null ? "" : flightClass.key());
    }

    public Optional<FlightClass> flightClassValue() {
        for (FlightClass value : FlightClass.values()) {
            if (value.key().equals(this.flightClass)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    public String shortSerial() {
        String text = this.serial.toString();
        return text.substring(0, Math.min(8, text.length())).toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        String clean = MissionNames.clean(value);
        return clean.length() > MAX_NAME_LENGTH ? clean.substring(0, MAX_NAME_LENGTH).strip() : clean;
    }
}
