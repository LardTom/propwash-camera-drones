package de.lardtom.camdrones.integration.data;

import com.mojang.serialization.Codec;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionCodec;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;

public final class MissionData {
    public static final MissionData EMPTY = new MissionData(new int[0]);
    public static final Codec<MissionData> CODEC = Codec.INT_STREAM.xmap(stream -> of(stream.toArray()), data -> IntStream.of(data.ints));

    private final int[] ints;

    private MissionData(int[] ints) {
        this.ints = ints;
    }

    public static MissionData of(int[] packed) {
        return packed == null || packed.length == 0 ? EMPTY : new MissionData(packed.clone());
    }

    public static MissionData of(Mission mission) {
        return mission == null || mission.isEmpty() ? EMPTY : new MissionData(MissionCodec.encode(mission));
    }

    public boolean isEmpty() {
        return this.ints.length == 0;
    }

    public int length() {
        return this.ints.length;
    }

    public int[] ints() {
        return this.ints.clone();
    }

    public int version() {
        return this.isEmpty() ? 0 : MissionCodec.version(this.ints);
    }

    public MissionCodec.Result decode() {
        return MissionCodec.decode(this.ints);
    }

    public Optional<Mission> mission() {
        if (this.isEmpty()) {
            return Optional.empty();
        }
        MissionCodec.Result result = this.decode();
        return result.ok() ? result.mission().filter(mission -> !mission.isEmpty()) : Optional.empty();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MissionData data && Arrays.equals(this.ints, data.ints);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.ints);
    }

    @Override
    public String toString() {
        return "MissionData[" + this.ints.length + " ints, version " + this.version() + "]";
    }
}
