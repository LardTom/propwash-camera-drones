package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;

import java.util.Optional;

public interface NavContext {
    NavContext NONE = new NavContext() {
    };

    default boolean active(CamControl control) {
        return false;
    }

    default int maxAltitude() {
        return CamWorldRules.DEFAULT_MAX_ALTITUDE;
    }

    default int maxDistance() {
        return CamWorldRules.DEFAULT_MAX_DISTANCE;
    }

    default Optional<NavTarget> home() {
        return Optional.empty();
    }

    default Optional<Mission> mission() {
        return Optional.empty();
    }

    default Optional<NavTarget> followTarget() {
        return Optional.empty();
    }

    default FollowController.Style followStyle() {
        return FollowController.Style.BEHIND;
    }

    default Optional<NavTarget> pointOfInterest(double x, double y, double z, double dirX, double dirY, double dirZ, double range) {
        return Optional.empty();
    }

    default double probe(double x, double y, double z, double dirX, double dirZ, double range) {
        return Double.NaN;
    }

    default void waypointAdded(double x, double y, double z, double heading) {
    }

    default void missionProgress(NavLogic.MissionState state, int index) {
    }

    default void status(NavStatus status) {
    }
}
