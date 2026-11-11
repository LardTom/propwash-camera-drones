package de.lardtom.camdrones.client.planner;

import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.FollowController;

import java.util.UUID;

public interface MissionCommands {
    MissionCommands NONE = new MissionCommands() {
    };

    enum Command {
        START,
        PAUSE,
        STOP
    }

    default boolean available() {
        return false;
    }

    default boolean send(UUID serial, Command command) {
        return false;
    }

    default boolean autoShotAvailable() {
        return false;
    }

    default boolean autoShot(UUID serial, AutoShots.Shot shot) {
        return false;
    }

    default boolean followAvailable() {
        return false;
    }

    default boolean follow(UUID serial, UUID target, FollowController.Style style) {
        return false;
    }
}
