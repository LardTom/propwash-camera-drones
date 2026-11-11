package de.lardtom.camdrones.dev.server;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.award.CamAwards;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.crew.model.CrewBook;
import de.lardtom.camdrones.dev.AwardProbe;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class AwardJoinCheck extends ServerTest.Scenario {
    static final String CASE = AwardProbe.CASE;
    static final int SETUP_TICKS = 60;
    static final int WAIT_TICKS = 20 * 90;
    static final int RELEASE_TICKS = 40;
    static final int LINGER_TICKS = 20 * 8;
    private final Set<String> seen = new LinkedHashSet<>();
    private int stage;
    private int joinedAt = -1;
    private int mark;
    private @Nullable ServerPlayer guest;
    private @Nullable Entity flown;
    private @Nullable Entity filmed;
    private boolean guestCrew;

    @Override
    void tick(MinecraftServer server, int tick) {
        ServerPlayer client = ServerSetup.client(server);
        if (client == null && this.stage > 0 && this.stage < 6) {
            ServerTest.fail(CASE + ": Client trennt in Stufe " + this.stage + " (" + this.seen + ")");
            this.cleanup(server);
            this.stage = 9;
            return;
        }
        int waited = tick - this.mark;
        switch (this.stage) {
            case 0 -> {
                if (client == null) {
                    return;
                }
                if (this.joinedAt < 0) {
                    this.joinedAt = tick;
                }
                if (tick - this.joinedAt >= SETUP_TICKS) {
                    ServerTest.line(CASE + ": " + AwardProbe.loaded(server));
                    AwardProbe.revokeAll(client);
                    ServerTest.line(CASE + ": Fortschritte von " + client.getPlainTextName() + " zurückgesetzt (" + AwardProbe.status(client)
                            + "), Gimbal ins Inventar");
                    client.getInventory().add(item(CamDrones.id("gimbal_tour").toString()));
                    this.next(tick, 1);
                }
            }
            case 1 -> {
                if (AwardProbe.done(client, CamAwards.ROOT)) {
                    this.seen.add(CamAwards.ROOT);
                    ServerTest.line(CASE + " root: Gimbal im Inventar öffnet den Tab (minecraft:inventory_changed)");
                    this.setupCrew(server, client);
                    this.next(tick, 2);
                } else if (waited > 200) {
                    this.timeout("root bleibt offen", server);
                }
            }
            case 2 -> {
                Entity drone = this.flown;
                boolean bound = CrewService.book().operator(drone.getUUID()).filter(JoinEnv.GUEST_ID::equals).isPresent();
                if (bound && client.containerMenu == client.inventoryMenu) {
                    this.startFlight(client);
                    this.next(tick, 3);
                } else if (waited > WAIT_TICKS) {
                    this.timeout("Kameramann nicht gebunden", server);
                }
            }
            case 3 -> {
                Entity drone = this.flown;
                if (waited % 20 == 0) {
                    this.guest.snapTo(drone.getX() + 3.0, client.getY(), drone.getZ() - 3.0, 0.0F, 0.0F);
                }
                boolean pilot = AwardProbe.done(client, CamAwards.CREW_FLIGHT);
                this.guestCrew |= AwardProbe.done(this.guest, CamAwards.CREW_FLIGHT);
                if (pilot && this.guestCrew) {
                    this.seen.add(CamAwards.CREW_FLIGHT);
                    ServerTest.line(CASE + " crew_flight: " + client.getPlainTextName() + " fliegt, " + JoinEnv.GUEST_NAME + " führt die Kamera, Höhe "
                            + ServerSetup.format(ServerSetup.height(drone)) + " m: Fortschritt für Pilot und Kameramann");
                    ServerSetup.command(this.guest, "/camdrones crew leave");
                    release(client, drone);
                    this.next(tick, 4);
                } else if (waited > WAIT_TICKS) {
                    boolean armed = PropwashApi.drones().view(drone).map(DroneView::armed).orElse(false);
                    this.timeout("zu zweit bleibt offen (Pilot " + pilot + ", Kameramann " + this.guestCrew + ", scharf " + armed + ", Höhe "
                            + ServerSetup.format(ServerSetup.height(drone)) + " m, in der Luft " + CamAwards.airborne(drone) + ")", server);
                }
            }
            case 4 -> {
                boolean flying = PropwashApi.drones().view(this.flown).flatMap(DroneView::pilot).filter(client.getUUID()::equals).isPresent();
                if (!flying && waited >= RELEASE_TICKS) {
                    this.setupMission(client);
                    this.next(tick, 5);
                } else if (waited > WAIT_TICKS) {
                    this.timeout("FPV endet nicht", server);
                }
            }
            case 5 -> {
                for (String path : List.of(CamAwards.VIDEO, CamAwards.MISSION)) {
                    if (!this.seen.contains(path) && AwardProbe.done(client, path)) {
                        this.seen.add(path);
                        ServerTest.line(CASE + " " + path + ": " + (path.equals(CamAwards.VIDEO)
                                ? "Client nimmt im Livebild der Kamera-Drohne ein Video auf, Paket camdrones:video_recorded"
                                : "Mission aus dem Flugplaner am Server abgeschlossen (Besitzer " + client.getPlainTextName() + ")"));
                    }
                }
                if (this.seen.containsAll(CamAwards.ALL)) {
                    ServerTest.line(CASE + ": " + client.getPlainTextName() + " " + AwardProbe.status(client));
                    PropwashApi.drones().autonomy().stop(this.filmed);
                    this.next(tick, 6);
                } else if (waited > WAIT_TICKS) {
                    this.timeout("offen: " + AwardProbe.status(client), server);
                }
            }
            case 6 -> {
                if (client == null || waited >= LINGER_TICKS) {
                    ServerTest.line(CASE + ": " + (client == null ? "Client fertig, alle vier ausgelöst " + this.seen
                            : client.getPlainTextName() + " " + AwardProbe.status(client)) + ", " + JoinEnv.GUEST_NAME + " " + CamAwards.CREW_FLIGHT + "="
                            + (this.guestCrew ? "done" : "open"));
                    this.cleanup(server);
                    this.stage = 9;
                }
            }
            default -> {
            }
        }
    }

    private void setupCrew(MinecraftServer server, ServerPlayer client) {
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        BlockPos ground = ServerSetup.ground(level, arena.getX() + 8, arena.getZ() + 4);
        this.flown = ServerSetup.spawn(client, CamPresets.TOUR, ground);
        ServerSetup.pair(this.flown, client.getUUID());
        Vec3 stand = Vec3.atBottomCenterOf(ground.above()).add(0.0, 0.0, -2.0);
        client.teleportTo(level, stand.x, stand.y, stand.z, Set.<Relative>of(), 0.0F, 30.0F, true);
        this.guest = ServerSetup.guest(server, level, stand.add(3.0, 0.0, 0.0));
        AwardProbe.revoke(this.guest, CamAwards.CREW_FLIGHT);
        client.setItemSlot(EquipmentSlot.HEAD, item("propwash:goggles_creative"));
        client.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        CrewService.book().decline(JoinEnv.GUEST_ID);
        CrewBook.Verdict verdict = CrewService.invite(client, this.flown, this.guest);
        ServerTest.line(CASE + " crew_flight: " + client.getPlainTextName() + " lädt " + JoinEnv.GUEST_NAME + " als Kameramann ein: " + verdict);
        if (!verdict.ok()) {
            ServerTest.fail(CASE + ": Einladung abgelehnt: " + verdict);
        }
        ServerSetup.command(this.guest, CrewService.ACCEPT_COMMAND);
    }

    private void startFlight(ServerPlayer client) {
        ServerLevel level = client.level();
        client.setItemInHand(InteractionHand.MAIN_HAND, item("propwash:remote"));
        client.interactOn(this.flown, InteractionHand.MAIN_HAND, this.flown.position());
        client.getItemInHand(InteractionHand.MAIN_HAND).use(level, client, InteractionHand.MAIN_HAND);
        boolean flying = PropwashApi.drones().view(this.flown).flatMap(DroneView::pilot).filter(client.getUUID()::equals).isPresent();
        ServerTest.line(CASE + " crew_flight: Kameramann gebunden, FPV gestartet: " + flying);
        if (!flying) {
            ServerTest.fail(CASE + ": FPV startet nicht");
        }
    }

    private void setupMission(ServerPlayer client) {
        client.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        this.filmed = this.flown;
        Entity drone = this.filmed;
        String before = PropwashApi.drones().autonomy().active(drone).map(Identifier::toString).orElse("keiner");
        double y = drone.getY();
        double x = drone.getX();
        double z = drone.getZ();
        ServerSetup.mission(drone, Mission.of(List.of(Waypoint.at(x, y, z + 3.0), Waypoint.at(x + 4.0, y, z + 8.0), Waypoint.at(x, y, z + 13.0),
                Waypoint.at(x - 4.0, y, z + 18.0))).withEnd(Mission.End.HOVER).withBaseSpeed(1.5));
        AutonomyServer.Reply reply = AutonomyServer.execute(client, drone, MissionCommand.Command.START, MissionCommand.AUTO_INDEX);
        ServerTest.line(CASE + " mission: Drohne von " + client.getPlainTextName() + " (nach dem FPV-Flug " + before + ", Höhe "
                + ServerSetup.format(ServerSetup.height(drone)) + " m) startet eine Mission mit vier Wegpunkten am Server: " + reply.status());
        if (!reply.ok()) {
            ServerTest.fail(CASE + ": Mission startet nicht: " + reply.status());
        }
    }

    private static void release(ServerPlayer client, @Nullable Entity drone) {
        if (drone == null || drone.isRemoved()) {
            return;
        }
        boolean flying = PropwashApi.drones().view(drone).flatMap(DroneView::pilot).filter(client.getUUID()::equals).isPresent();
        if (flying) {
            client.setItemInHand(InteractionHand.MAIN_HAND, item("propwash:remote"));
            client.getItemInHand(InteractionHand.MAIN_HAND).use(client.level(), client, InteractionHand.MAIN_HAND);
        }
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow(() -> new IllegalStateException(id + " fehlt")));
    }

    private void next(int tick, int stage) {
        this.stage = stage;
        this.mark = tick;
    }

    private void timeout(String why, MinecraftServer server) {
        ServerTest.fail(CASE + ": " + why);
        this.cleanup(server);
        this.stage = 9;
    }

    private void cleanup(MinecraftServer server) {
        if (this.filmed != null && !this.filmed.isRemoved()) {
            PropwashApi.drones().autonomy().stop(this.filmed);
            this.filmed.discard();
        }
        ServerSetup.leave(server, this.guest);
    }

    @Override
    void abort() {
        super.abort();
        if (this.guest != null) {
            ServerSetup.leave(this.guest.level().getServer(), this.guest);
        }
    }

    @Override
    boolean done() {
        return this.stage == 9;
    }

    @Override
    @Nullable String missing() {
        return this.seen.containsAll(CamAwards.ALL) && this.guestCrew ? null : "Fortschritte ausgelöst: " + this.seen + ", Kameramann " + this.guestCrew;
    }
}
