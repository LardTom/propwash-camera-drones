package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.planner.model.TabletState;

public record PlannerSettings(int groundHeight, int active) {
    public static final PlannerSettings DEFAULT = new PlannerSettings(TabletState.DEFAULT_GROUND_HEIGHT, 0);

    public PlannerSettings {
        groundHeight = TabletState.clampGroundHeight(groundHeight);
        active = Math.clamp(active, 0, TabletState.MAX_MISSIONS - 1);
    }

    public static PlannerSettings of(TabletState state) {
        return new PlannerSettings(state.groundHeight(), state.active());
    }
}
