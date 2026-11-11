package de.lardtom.camdrones.planner.model;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.Waypoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TabletState {
    public static final int MAX_MISSIONS = MissionLimits.MAX_SAVED_MISSIONS;
    public static final int MIN_GROUND_HEIGHT = 1;
    public static final int MAX_GROUND_HEIGHT = 120;
    public static final int DEFAULT_GROUND_HEIGHT = 10;
    public static final TabletState EMPTY = new TabletState(List.of(), 0, DEFAULT_GROUND_HEIGHT);

    private final List<PlannerEntry> entries;
    private final int active;
    private final int groundHeight;

    public TabletState(List<PlannerEntry> entries, int active, int groundHeight) {
        List<PlannerEntry> list = entries == null ? List.of() : entries;
        this.entries = List.copyOf(list.size() > MAX_MISSIONS ? list.subList(0, MAX_MISSIONS) : list);
        this.active = this.entries.isEmpty() ? 0 : Math.clamp(active, 0, this.entries.size() - 1);
        this.groundHeight = clampGroundHeight(groundHeight);
    }

    public static int clampGroundHeight(int value) {
        return Math.clamp(value, MIN_GROUND_HEIGHT, MAX_GROUND_HEIGHT);
    }

    public List<PlannerEntry> entries() {
        return this.entries;
    }

    public int size() {
        return this.entries.size();
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public boolean full() {
        return this.entries.size() >= MAX_MISSIONS;
    }

    public int active() {
        return this.active;
    }

    public int groundHeight() {
        return this.groundHeight;
    }

    public Optional<PlannerEntry> activeEntry() {
        return this.entries.isEmpty() ? Optional.empty() : Optional.of(this.entries.get(this.active));
    }

    public Mission activeMission() {
        return this.activeEntry().map(PlannerEntry::missionOrEmpty).orElse(Mission.empty());
    }

    public String activeName() {
        return this.activeEntry().map(entry -> entry.displayName(this.active + 1)).orElse(MissionNames.defaultName(1));
    }

    public TabletState withActive(int index) {
        return new TabletState(this.entries, index, this.groundHeight);
    }

    public TabletState withGroundHeight(int value) {
        return new TabletState(this.entries, this.active, value);
    }

    public TabletState withEntries(List<PlannerEntry> value, int activeIndex) {
        return new TabletState(value, activeIndex, this.groundHeight);
    }

    public TabletState withEntry(int index, PlannerEntry entry) {
        List<PlannerEntry> list = new ArrayList<>(this.entries);
        list.set(index, entry);
        return new TabletState(list, this.active, this.groundHeight);
    }

    public TabletState ensureActive() {
        if (!this.entries.isEmpty()) {
            return this;
        }
        return new TabletState(List.of(PlannerEntry.empty(MissionNames.defaultName(1))), 0, this.groundHeight);
    }

    public Optional<TabletState> addMission(String name) {
        if (this.full()) {
            return Optional.empty();
        }
        List<PlannerEntry> list = new ArrayList<>(this.entries);
        list.add(PlannerEntry.empty(MissionNames.orDefault(name, list.size() + 1)));
        return Optional.of(new TabletState(list, list.size() - 1, this.groundHeight));
    }

    public Optional<TabletState> duplicateActive() {
        if (this.full() || this.entries.isEmpty()) {
            return Optional.empty();
        }
        List<PlannerEntry> list = new ArrayList<>(this.entries);
        PlannerEntry source = list.get(this.active);
        PlannerEntry copy = PlannerEntry.fromPacked(copyName(source.displayName(this.active + 1)), source.packed());
        list.add(this.active + 1, copy);
        return Optional.of(new TabletState(list, this.active + 1, this.groundHeight));
    }

    public TabletState removeActive() {
        if (this.entries.isEmpty()) {
            return this;
        }
        List<PlannerEntry> list = new ArrayList<>(this.entries);
        list.remove(this.active);
        return new TabletState(list, Math.min(this.active, list.size() - 1), this.groundHeight);
    }

    public PlacementResult addWaypoint(Waypoint waypoint) {
        TabletState base = this.ensureActive();
        PlannerEntry entry = base.entries.get(base.active);
        if (!entry.readable()) {
            return PlacementResult.failed(PlacementResult.Outcome.LOCKED, this);
        }
        Mission mission = entry.missionOrEmpty();
        if (mission.size() >= MissionLimits.MAX_WAYPOINTS) {
            return PlacementResult.failed(PlacementResult.Outcome.FULL, this);
        }
        TabletState next = base.withEntry(base.active, entry.withMission(mission.plus(waypoint)));
        return new PlacementResult(PlacementResult.Outcome.ADDED, next, mission.size());
    }

    public PlacementResult removeWaypoint(int index) {
        Optional<PlannerEntry> entry = this.activeEntry();
        if (entry.isEmpty() || !entry.get().readable()) {
            return PlacementResult.failed(PlacementResult.Outcome.LOCKED, this);
        }
        Mission mission = entry.get().missionOrEmpty();
        if (index < 0 || index >= mission.size()) {
            return PlacementResult.failed(PlacementResult.Outcome.MISSING, this);
        }
        TabletState next = this.withEntry(this.active, entry.get().withMission(mission.remove(index)));
        return new PlacementResult(PlacementResult.Outcome.REMOVED, next, index);
    }

    static String copyName(String name) {
        String base = MissionNames.clean(name);
        String suffix = " 2";
        int room = MissionNames.MAX_LENGTH - suffix.length();
        if (base.length() > room) {
            base = base.substring(0, room).strip();
        }
        return base + suffix;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof TabletState state && this.active == state.active && this.groundHeight == state.groundHeight
                && this.entries.equals(state.entries);
    }

    @Override
    public int hashCode() {
        return (this.entries.hashCode() * 31 + this.active) * 31 + this.groundHeight;
    }

    public record PlacementResult(Outcome outcome, TabletState state, int index) {
        public enum Outcome {
            ADDED,
            REMOVED,
            FULL,
            LOCKED,
            MISSING
        }

        static PlacementResult failed(Outcome outcome, TabletState state) {
            return new PlacementResult(outcome, state, -1);
        }

        public boolean changed() {
            return this.outcome == Outcome.ADDED || this.outcome == Outcome.REMOVED;
        }
    }
}
