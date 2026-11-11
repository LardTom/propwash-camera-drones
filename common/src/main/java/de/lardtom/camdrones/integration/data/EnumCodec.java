package de.lardtom.camdrones.integration.data;

import com.mojang.serialization.Codec;

import java.util.Locale;

public final class EnumCodec {
    private EnumCodec() {
    }

    public static <E extends Enum<E>> Codec<E> of(Class<E> type, E fallback) {
        return Codec.STRING.xmap(name -> parse(type, name, fallback), value -> value.name().toLowerCase(Locale.ROOT));
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        if (name == null) {
            return fallback;
        }
        for (E value : type.getEnumConstants()) {
            if (value.name().equalsIgnoreCase(name.strip())) {
                return value;
            }
        }
        return fallback;
    }
}
