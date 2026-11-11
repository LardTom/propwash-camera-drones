package de.lardtom.camdrones.planner.model;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionCodec;
import de.lardtom.camdrones.logic.mission.MissionLimits;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public final class PlannerEntry {
    public static final int MAX_INTS = MissionLimits.packedInts(MissionLimits.MAX_WAYPOINTS, MissionLimits.MAX_POINTS_OF_INTEREST);

    private final String name;
    private final int[] packed;
    private final Mission mission;
    private final MissionCodec.Status status;

    private PlannerEntry(String name, int[] packed, Mission mission, MissionCodec.Status status) {
        this.name = MissionNames.clean(name);
        this.packed = packed;
        this.mission = mission;
        this.status = status;
    }

    public static PlannerEntry of(String name, Mission mission) {
        Objects.requireNonNull(mission, "mission");
        return new PlannerEntry(name, MissionCodec.encode(mission), mission, MissionCodec.Status.OK);
    }

    public static PlannerEntry empty(String name) {
        return of(name, Mission.empty());
    }

    public static PlannerEntry fromPacked(String name, int[] packed) {
        int[] copy = packed == null ? new int[0] : packed.clone();
        if (copy.length == 0) {
            return empty(name);
        }
        if (copy.length > MAX_INTS) {
            return new PlannerEntry(name, Arrays.copyOf(copy, MAX_INTS), null, MissionCodec.Status.CORRUPT);
        }
        MissionCodec.Result result = MissionCodec.decode(copy);
        if (result.ok() && result.mission().isPresent()) {
            return new PlannerEntry(name, copy, result.mission().get(), MissionCodec.Status.OK);
        }
        return new PlannerEntry(name, copy, null, result.status());
    }

    public String name() {
        return this.name;
    }

    public String displayName(int number) {
        return MissionNames.orDefault(this.name, number);
    }

    public int[] packed() {
        return this.packed.clone();
    }

    public int packedLength() {
        return this.packed.length;
    }

    public boolean readable() {
        return this.mission != null;
    }

    public MissionCodec.Status status() {
        return this.status;
    }

    public Optional<Mission> mission() {
        return Optional.ofNullable(this.mission);
    }

    public Mission missionOrEmpty() {
        return this.mission == null ? Mission.empty() : this.mission;
    }

    public int waypointCount() {
        return this.mission == null ? 0 : this.mission.size();
    }

    public PlannerEntry withName(String value) {
        return new PlannerEntry(value, this.packed, this.mission, this.status);
    }

    public PlannerEntry withMission(Mission value) {
        return of(this.name, value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PlannerEntry entry && this.name.equals(entry.name) && Arrays.equals(this.packed, entry.packed);
    }

    @Override
    public int hashCode() {
        return 31 * this.name.hashCode() + Arrays.hashCode(this.packed);
    }

    @Override
    public String toString() {
        return "PlannerEntry[" + this.name + ", " + this.packed.length + " ints, " + this.status + "]";
    }
}
