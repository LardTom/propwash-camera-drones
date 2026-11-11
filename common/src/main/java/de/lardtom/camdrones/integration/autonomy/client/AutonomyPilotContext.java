package de.lardtom.camdrones.integration.autonomy.client;

import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.NavContext;
import de.lardtom.camdrones.integration.NavContexts;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.NavTarget;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.MissionProgress;
import de.lardtom.camdrones.net.SmartMode;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AutonomyPilotContext implements NavContext {
    private final NavContext inner;
    private final PilotRelay relay = new PilotRelay();
    private volatile NavTarget lastPoint;

    AutonomyPilotContext(NavContext inner) {
        this.inner = Objects.requireNonNull(inner, "inner");
    }

    public static void install() {
        NavContext current = NavContexts.pilot();
        if (current instanceof AutonomyPilotContext) {
            return;
        }
        AutonomyPilotContext wrapper = new AutonomyPilotContext(current);
        NavContexts.pilot(() -> wrapper);
    }

    public PilotRelay relay() {
        return this.relay;
    }

    private static Optional<Entity> drone() {
        return PropwashClientApi.input().drone();
    }

    private static Optional<UUID> serial(Entity drone) {
        if (!CamDroneData.registered()) {
            return Optional.empty();
        }
        UUID serial = PropwashApi.droneData().get(drone, CamDroneData.serial());
        return serial == null || CamDroneData.NO_SERIAL.equals(serial) ? Optional.empty() : Optional.of(serial);
    }

    private static MissionStatus serverStatus(Entity drone) {
        if (!CamDroneData.registered()) {
            return MissionStatus.IDLE;
        }
        return Objects.requireNonNullElse(PropwashApi.droneData().get(drone, CamDroneData.missionState()), MissionStatus.IDLE);
    }

    private static void send(CustomPacketPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> CamNetwork.sendToServer(payload));
    }

    @Override
    public boolean active(CamControl control) {
        return this.relay.pulse(control) || this.inner.active(control);
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
        Optional<Mission> full = this.inner.mission();
        Optional<Entity> drone = drone();
        return drone.isEmpty() ? full : this.relay.mission(full, serverStatus(drone.get()));
    }

    @Override
    public Optional<NavTarget> followTarget() {
        return this.inner.followTarget();
    }

    @Override
    public FollowController.Style followStyle() {
        return this.inner.followStyle();
    }

    @Override
    public Optional<NavTarget> pointOfInterest(double x, double y, double z, double dirX, double dirY, double dirZ, double range) {
        Optional<NavTarget> point = this.inner.pointOfInterest(x, y, z, dirX, dirY, dirZ, range);
        point.ifPresent(p -> this.lastPoint = p);
        return point;
    }

    @Override
    public double probe(double x, double y, double z, double dirX, double dirZ, double range) {
        return this.inner.probe(x, y, z, dirX, dirZ, range);
    }

    @Override
    public void waypointAdded(double x, double y, double z, double heading) {
        this.inner.waypointAdded(x, y, z, heading);
    }

    @Override
    public void missionProgress(NavLogic.MissionState state, int index) {
        this.inner.missionProgress(state, index);
        Optional<Entity> drone = drone();
        Optional<UUID> serial = drone.flatMap(AutonomyPilotContext::serial);
        if (serial.isPresent() && this.relay.seen()) {
            send(new MissionProgress.Report(serial.get(), state, this.relay.absolute(index), this.relay.epoch()));
        }
    }

    @Override
    public void status(NavStatus value) {
        this.inner.status(value);
        Optional<Entity> drone = drone();
        Optional<UUID> serial = drone.flatMap(AutonomyPilotContext::serial);
        if (serial.isEmpty()) {
            this.relay.observe(null, null, null);
            return;
        }
        Entity entity = drone.get();
        this.relay.observe(serial.get(), serverStatus(entity), value.missionState());
        this.relay.changed(value.function()).ifPresent(function -> send(new SmartMode.Request(serial.get(), smart(function, entity, value))));
    }

    private SmartState smart(SmartState.Function function, Entity drone, NavStatus value) {
        return switch (function) {
            case HOLD, AUTO_SHOT -> SmartState.hold(drone.getX(), drone.getY(), drone.getZ());
            case FOLLOW -> {
                LocalPlayer player = Minecraft.getInstance().player;
                yield player == null ? SmartState.NONE : SmartState.follow(player.getUUID(), this.followStyle());
            }
            case POI -> {
                NavTarget point = this.lastPoint;
                yield point == null ? SmartState.NONE : SmartState.pointOfInterest(point.x(), point.y(), point.z(),
                        Math.hypot(drone.getX() - point.x(), drone.getZ() - point.z()), value.flightClass().cineSpeed(), true);
            }
            case COURSE_LOCK -> SmartState.courseLock(0.0);
            case NONE -> SmartState.NONE;
        };
    }
}
