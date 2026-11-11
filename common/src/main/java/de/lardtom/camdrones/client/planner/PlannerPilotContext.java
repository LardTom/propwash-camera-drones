package de.lardtom.camdrones.client.planner;

import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.NavContext;
import de.lardtom.camdrones.integration.NavContexts;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.NavTarget;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class PlannerPilotContext implements NavContext {
    private final NavContext inner;
    private final Supplier<Optional<FollowController.Style>> chosenStyle;
    private volatile NavStatus status;

    PlannerPilotContext(NavContext inner) {
        this(inner, () -> PlannerClient.flownSerial().flatMap(PlannerClient::followStyle));
    }

    PlannerPilotContext(NavContext inner, Supplier<Optional<FollowController.Style>> chosenStyle) {
        this.inner = Objects.requireNonNull(inner, "inner");
        this.chosenStyle = Objects.requireNonNull(chosenStyle, "chosenStyle");
    }

    public static void install() {
        NavContext current = NavContexts.pilot();
        if (current instanceof PlannerPilotContext) {
            return;
        }
        PlannerPilotContext wrapper = new PlannerPilotContext(current);
        NavContexts.pilot(() -> wrapper);
    }

    public Optional<NavStatus> lastStatus() {
        return Optional.ofNullable(this.status);
    }

    @Override
    public boolean active(CamControl control) {
        return this.inner.active(control);
    }

    @Override
    public int maxAltitude() {
        return this.inner.maxAltitude();
    }

    @Override
    public int maxDistance() {
        return this.inner.maxDistance();
    }

    @Override
    public Optional<NavTarget> home() {
        return this.inner.home();
    }

    @Override
    public Optional<Mission> mission() {
        return this.inner.mission();
    }

    @Override
    public Optional<NavTarget> followTarget() {
        return this.inner.followTarget();
    }

    @Override
    public FollowController.Style followStyle() {
        return this.chosenStyle.get().orElseGet(this.inner::followStyle);
    }

    @Override
    public Optional<NavTarget> pointOfInterest(double x, double y, double z, double dirX, double dirY, double dirZ, double range) {
        return this.inner.pointOfInterest(x, y, z, dirX, dirY, dirZ, range);
    }

    @Override
    public double probe(double x, double y, double z, double dirX, double dirZ, double range) {
        return this.inner.probe(x, y, z, dirX, dirZ, range);
    }

    @Override
    public void waypointAdded(double x, double y, double z, double heading) {
        this.inner.waypointAdded(x, y, z, heading);
        NavStatus last = this.status;
        PlannerClient.flightWaypoint(x, y, z, heading, last == null ? Double.NaN : last.gimbalTilt());
    }

    @Override
    public void missionProgress(NavLogic.MissionState state, int index) {
        this.inner.missionProgress(state, index);
    }

    @Override
    public void status(NavStatus value) {
        this.status = value;
        this.inner.status(value);
    }
}
