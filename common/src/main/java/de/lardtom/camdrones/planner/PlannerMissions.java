package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.planner.model.PlannerEntry;
import de.lardtom.camdrones.planner.model.TabletState;

import java.util.List;

public record PlannerMissions(List<PlannerEntry> entries) {
    public static final PlannerMissions EMPTY = new PlannerMissions(List.of());

    public PlannerMissions {
        List<PlannerEntry> list = entries == null ? List.of() : entries;
        entries = List.copyOf(list.size() > TabletState.MAX_MISSIONS ? list.subList(0, TabletState.MAX_MISSIONS) : list);
    }

    public static PlannerMissions of(TabletState state) {
        return new PlannerMissions(state.entries());
    }
}
