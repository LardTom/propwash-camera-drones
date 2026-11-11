package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.logic.mission.Waypoint;
import net.minecraft.world.entity.Entity;

public final class LightControl {
    private LightControl() {
    }

    public static boolean available() {
        return false;
    }

    public static boolean apply(Entity drone, Waypoint.Light light) {
        return false;
    }
}
