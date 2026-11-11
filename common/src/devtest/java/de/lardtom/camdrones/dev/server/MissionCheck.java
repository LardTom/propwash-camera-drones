package de.lardtom.camdrones.dev.server;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class MissionCheck extends ServerTest.Scenario {
    static final int SETUP_TICKS = 60;
    static final int LANDING_TICKS = 20 * 40;
    static final int FROZEN_TICKS = 20 * 10;
    static final double FROZEN_METRES = 0.2;
    static final double HEIGHT = 6.0;
    private int stage;
    private int joinedAt = -1;
    private int mark;
    private @Nullable Entity first;
    private @Nullable Entity second;
    private boolean firstStarted;
    private boolean secondFlew;
    private boolean offline;
    private boolean landed;
    private boolean stuck;
    private boolean disconnected;
    private int movedAt;
    private double movedHeight;
    private double lastHeight;

    @Override
    void tick(MinecraftServer server, int tick) {
        ServerPlayer client = ServerSetup.client(server);
        switch (this.stage) {
            case 0 -> {
                if (client == null) {
                    return;
                }
                if (this.joinedAt < 0) {
                    this.joinedAt = tick;
                }
                if (tick - this.joinedAt < SETUP_TICKS) {
                    return;
                }
                this.setup(client);
                this.stage = 1;
                this.mark = tick;
            }
            case 1 -> {
                if (client == null) {
                    ServerTest.fail("mission: Client trennt, bevor die erste Mission läuft");
                    this.stage = 9;
                    return;
                }
                if (this.active(this.first).filter(id -> id.getPath().equals("mission")).isPresent()) {
                    this.firstStarted = true;
                    boolean piloting = PropwashApi.drones().view(this.first).flatMap(v -> v.pilot()).isPresent();
                    ServerTest.line("mission: erste Mission läuft am Server, Besitzer " + client.getPlainTextName() + " verbunden, "
                            + (piloting ? "fliegt selbst (falsch)" : "nicht im FPV"));
                    if (piloting) {
                        ServerTest.fail("mission: Besitzer fliegt die Drohne selbst");
                    }
                    this.stage = 2;
                    this.mark = tick;
                }
            }
            case 2 -> {
                if (this.active(this.second).isPresent()) {
                    this.secondFlew = true;
                }
                if (client == null) {
                    this.disconnected = true;
                    this.movedAt = tick;
                    this.lastHeight = this.first == null ? 0.0 : ServerSetup.height(this.first);
                    this.movedHeight = this.lastHeight;
                    ServerTest.line("mission: Besitzer hat getrennt" + (this.secondFlew ? ", zweite Drohne flog trotz Budget" : ", zweite Drohne blieb am Boden"));
                    if (this.secondFlew) {
                        ServerTest.fail("mission: zweite Mission desselben Spielers startete trotz Budget");
                    }
                    this.stage = 3;
                    this.mark = tick;
                }
            }
            case 3 -> {
                Entity drone = this.first;
                if (drone == null || drone.isRemoved()) {
                    ServerTest.fail("mission: erste Drohne " + ServerSetup.format((tick - this.mark) / 20.0) + " s nach dem Trennen des Besitzers entladen ("
                            + (drone == null ? "fehlt" : String.valueOf(drone.getRemovalReason())) + ", Höhe zuletzt " + ServerSetup.format(this.lastHeight)
                            + " m) statt gelandet");
                    this.stuck = true;
                    this.stage = 9;
                    return;
                }
                double height = ServerSetup.height(drone);
                if (Math.abs(height - this.movedHeight) > FROZEN_METRES) {
                    this.movedAt = tick;
                    this.movedHeight = height;
                }
                this.lastHeight = height;
                if ((tick - this.mark) % 40 == 0) {
                    Optional<DroneView> view = PropwashApi.drones().view(drone);
                    ServerTest.line("mission: nach dem Trennen " + ServerSetup.format((tick - this.mark) / 20.0) + " s: Höhe "
                            + ServerSetup.format(ServerSetup.height(drone)) + " m, scharf " + view.map(DroneView::armed).orElse(false)
                            + ", Autopilot " + view.map(DroneView::autopilot).orElse(false) + ", Tempo "
                            + ServerSetup.format(view.map(v -> v.velocity().length()).orElse(0.0)) + " m/s");
                }
                if (this.offline && !ServerSetup.airborne(drone) && tick - this.mark > 20) {
                    this.landed = true;
                    ServerTest.line("mission: erste Drohne nach dem Trennen gelandet nach " + ServerSetup.format((tick - this.mark) / 20.0) + " s");
                    this.stage = 9;
                    return;
                }
                if (this.offline && ServerSetup.airborne(drone) && tick - this.movedAt > FROZEN_TICKS) {
                    ServerTest.fail("mission: erste Drohne hängt nach OWNER_OFFLINE " + ServerSetup.format((tick - this.movedAt) / 20.0) + " s reglos in "
                            + ServerSetup.format(height) + " m Höhe statt zu landen");
                    this.stuck = true;
                    this.stage = 9;
                    return;
                }
                if (tick - this.mark > LANDING_TICKS) {
                    ServerTest.fail("mission: erste Drohne landet nach dem Trennen nicht (Ende " + (this.offline ? "OWNER_OFFLINE" : "fehlt")
                            + ", Höhe " + ServerSetup.format(ServerSetup.height(drone)) + " m)");
                    this.stage = 9;
                }
            }
            default -> {
            }
        }
    }

    private Optional<Identifier> active(@Nullable Entity drone) {
        return drone == null || drone.isRemoved() ? Optional.empty() : PropwashApi.drones().autonomy().active(drone);
    }

    private void setup(ServerPlayer client) {
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        ServerSetup.teleport(client, arena, 0.0F);
        BlockPos a = ServerSetup.ground(level, arena.getX() - 3, arena.getZ() + 4);
        BlockPos b = ServerSetup.ground(level, arena.getX() + 3, arena.getZ() + 4);
        this.first = ServerSetup.spawn(client, CamPresets.TOUR, a);
        this.second = ServerSetup.spawn(client, CamPresets.TOUR, b);
        ServerSetup.pair(this.first, client.getUUID());
        ServerSetup.pair(this.second, client.getUUID());
        ServerSetup.mission(this.first, mission(a, -1));
        ServerSetup.mission(this.second, mission(b, 1));
        ServerTest.line("mission: zwei Kamera-Drohnen für " + client.getPlainTextName() + " gekoppelt, je eine Mission mit 3 Punkten");
    }

    private static Mission mission(BlockPos ground, int side) {
        double y = ground.getY() + 1.0 + HEIGHT;
        double x = ground.getX() + 0.5;
        double z = ground.getZ() + 0.5;
        return Mission.of(List.of(Waypoint.at(x, y, z + 8.0), Waypoint.at(x + side * 6.0, y, z + 14.0), Waypoint.at(x, y, z + 20.0)))
                .withEnd(Mission.End.HOVER).withBaseSpeed(4.0);
    }

    @Override
    void onEnded(Entity drone, UUID owner, Identifier id, AutonomyEnd reason) {
        if (drone == this.first) {
            ServerTest.line("mission: Flug ohne Pilot " + id + " der ersten Drohne endet: " + reason);
            if (reason == AutonomyEnd.OWNER_OFFLINE) {
                this.offline = true;
            } else if (this.stage < 3) {
                ServerTest.fail("mission: erste Mission endet vor dem Trennen mit " + reason);
            }
        }
    }

    @Override
    boolean done() {
        return this.stage == 9;
    }

    @Override
    @Nullable String missing() {
        if (!this.firstStarted) {
            return "erste Mission lief nicht am Server";
        }
        if (!this.disconnected) {
            return "Besitzer hat nicht getrennt";
        }
        if (!this.offline) {
            return "kein Ende OWNER_OFFLINE nach dem Trennen";
        }
        if (!this.landed && !this.stuck) {
            return "keine Landung nach dem Trennen";
        }
        return null;
    }
}
