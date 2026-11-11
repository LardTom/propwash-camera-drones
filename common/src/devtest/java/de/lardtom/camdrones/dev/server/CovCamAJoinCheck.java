package de.lardtom.camdrones.dev.server;

import com.mojang.authlib.GameProfile;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.crew.model.CrewBook;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.net.CrewPackets;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerOwners;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.model.PlannerEntry;
import de.lardtom.camdrones.planner.model.TabletState;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class CovCamAJoinCheck extends ServerTest.Scenario {
    static final String CASE = "covcama";
    static final String OPERATOR_NAME = "camdrones_covop";
    static final UUID OPERATOR_ID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + OPERATOR_NAME).getBytes(StandardCharsets.UTF_8));
    static final int SETUP_TICKS = 60;
    static final int PAIR_TICKS = 20 * 15;
    static final int MARKER_TICKS = 20 * 8;
    static final int WAIT_TICKS = 20 * 90;
    static final int OWN_POINTS = 3;
    static final int GUEST_POINTS = 5;
    static final int RAMP = 30;
    static final int[][] SWEEP = {{40, 0, 0}, {110, -30, 0}, {180, -60, 0}, {260, -60, 90}, {340, -60, 180}};
    private int stage;
    private int joinedAt = -1;
    private int mark;
    private @Nullable ServerPlayer guest;
    private @Nullable ServerPlayer operator;
    private @Nullable Entity foreign;
    private @Nullable Entity cine;
    private boolean pairChecked;
    private boolean markersGiven;
    private boolean bound;
    private boolean swept;
    private int sent;
    private double lastHeight;
    private int steadyTicks;

    @Override
    void tick(MinecraftServer server, int tick) {
        ServerPlayer client = ServerSetup.client(server);
        if (client == null && this.stage > 0 && this.stage < 9) {
            ServerTest.fail(CASE + ": Client trennt in Stufe " + this.stage);
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
                    this.setupPair(server, client);
                    this.next(tick, 1);
                }
            }
            case 1 -> {
                if (waited >= PAIR_TICKS) {
                    ItemStack held = client.getMainHandItem();
                    boolean linked = PlannerItems.isPlanner(held) && PlannerTablets.link(held).isPresent();
                    UUID owner = PlannerOwners.owner(this.foreign);
                    this.pairChecked = !linked && owner.equals(this.guest.getUUID());
                    ServerTest.line(CASE + " Nr. 68: Flugplaner des Clients nach dem Koppelversuch " + (linked ? "GEKOPPELT" : "ohne Seriennummer")
                            + ", Besitzer der Drohne " + (owner.equals(this.guest.getUUID()) ? JoinEnv.GUEST_NAME : owner));
                    if (!this.pairChecked) {
                        ServerTest.fail(CASE + " Nr. 68: fremde Drohne gekoppelt oder Besitzer geändert");
                    }
                    this.foreign.discard();
                    this.setupMarkers(client);
                    this.next(tick, 2);
                }
            }
            case 2 -> {
                if (waited >= MARKER_TICKS) {
                    ItemStack own = planner(missionAround(client.position(), OWN_POINTS, 4.0), "Eigene");
                    client.setItemInHand(InteractionHand.MAIN_HAND, own);
                    ServerTest.line(CASE + " Nr. 69: Client hält jetzt einen eigenen Flugplaner mit " + OWN_POINTS + " Punkten");
                    this.next(tick, 3);
                }
            }
            case 3 -> {
                if (waited >= MARKER_TICKS) {
                    this.markersGiven = true;
                    client.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    this.guest.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    this.setupGimbal(server, client);
                    this.next(tick, 4);
                }
            }
            case 4 -> {
                Entity drone = this.cine;
                double height = drone == null ? 0.0 : ServerSetup.height(drone);
                boolean steady = Math.abs(height - this.lastHeight) < 0.02;
                this.lastHeight = height;
                this.steadyTicks = steady && height > 2.5 ? this.steadyTicks + 1 : 0;
                if (drone != null && this.steadyTicks >= 20) {
                    watch(client, drone);
                    this.operator = fake(server, client.level(), drone.position().add(3.0, -ServerSetup.height(drone), -3.0));
                    CrewBook.Verdict invited = CrewService.invite(this.guest, drone, this.operator);
                    CrewBook.Verdict accepted = invited.ok() ? CrewService.accept(this.operator) : invited;
                    this.bound = accepted.ok();
                    ServerTest.line(CASE + " Nr. 50: " + JoinEnv.GUEST_NAME + " lädt " + OPERATOR_NAME + " als Operator der Kino ein: " + invited
                            + ", angenommen: " + accepted);
                    if (!this.bound) {
                        ServerTest.fail(CASE + " Nr. 50: simulierter Operator nicht gebunden: " + accepted);
                        this.cleanup(server);
                        this.stage = 9;
                        return;
                    }
                    this.next(tick, 5);
                } else if (waited > WAIT_TICKS) {
                    ServerTest.fail(CASE + " Nr. 50: Kino steigt nicht (Höhe " + (drone == null ? "-" : ServerSetup.format(ServerSetup.height(drone))) + " m)");
                    this.cleanup(server);
                    this.stage = 9;
                }
            }
            case 5 -> {
                Entity drone = this.cine;
                double[] target = target(waited);
                if (waited % 2 == 0) {
                    GimbalSetting setting = new GimbalSetting(GimbalSetting.Mode.FOLLOW, target[0], target[1], 0.0, 0.0);
                    CrewService.gimbal(new CrewPackets.Gimbal(drone.getId(), setting), this.operator);
                    this.sent++;
                }
                if (waited % 40 == 0) {
                    GimbalSetting stored = CrewData.get(drone).setting();
                    ServerTest.line(CASE + " Nr. 50: Operator schickt Neigung " + ServerSetup.format(target[0]) + "°, Schwenk " + ServerSetup.format(target[1])
                            + "°, gespeichert " + ServerSetup.format(stored.tilt()) + "° / " + ServerSetup.format(stored.pan()) + "°");
                }
                if (waited >= SWEEP[SWEEP.length - 1][0] + 80) {
                    this.swept = true;
                    CrewBook.Verdict left = CrewService.leave(this.operator);
                    AutonomyServer.Reply stop = AutonomyServer.execute(this.guest, drone, MissionCommand.Command.STOP, MissionCommand.AUTO_INDEX);
                    ServerTest.line(CASE + " Nr. 50: " + this.sent + " Eingaben geschickt, Operator gibt ab: " + left + ", Halten beendet: " + stop.status());
                    this.next(tick, 6);
                }
            }
            case 6 -> {
                if (waited >= 100) {
                    ServerTest.line(CASE + ": fertig");
                    this.cleanup(server);
                    this.stage = 9;
                }
            }
            default -> {
            }
        }
    }

    static double[] target(int waited) {
        double tilt = 0.0;
        double pan = 0.0;
        for (int[] point : SWEEP) {
            int start = point[0] - RAMP;
            if (waited < start) {
                return new double[]{tilt, pan};
            }
            if (waited < point[0]) {
                double t = (waited - start) / (double) RAMP;
                return new double[]{tilt + (point[1] - tilt) * t, pan + (point[2] - pan) * t};
            }
            tilt = point[1];
            pan = point[2];
        }
        return new double[]{tilt, pan};
    }

    private static void watch(ServerPlayer client, Entity drone) {
        ServerLevel level = client.level();
        BlockPos stand = BlockPos.containing(drone.getX() + 1.5, drone.getY() - 2.4, drone.getZ() + 3.5);
        for (BlockPos pos : BlockPos.betweenClosed(stand.offset(-1, 0, -1), stand.offset(1, 0, 1))) {
            level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
        }
        Vec3 feet = Vec3.atBottomCenterOf(stand.above());
        client.teleportTo(level, feet.x, feet.y, feet.z, Set.<Relative>of(), 160.0F, -10.0F, true);
        ServerTest.line(CASE + " Nr. 50: Client steht auf einer Glasplatte " + ServerSetup.format(feet.distanceTo(drone.position()))
                + " m vor der Kino, etwa auf Höhe des Gimbals");
    }

    private void next(int tick, int stage) {
        this.stage = stage;
        this.mark = tick;
    }

    private void setupPair(MinecraftServer server, ServerPlayer client) {
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        ServerSetup.teleport(client, arena, 0.0F);
        this.guest = ServerSetup.guest(server, level, Vec3.atBottomCenterOf(ServerSetup.ground(level, arena.getX() + 3, arena.getZ() + 1).above()));
        this.foreign = ServerSetup.spawn(this.guest, CamPresets.TOUR, ServerSetup.ground(level, arena.getX(), arena.getZ() + 2));
        ServerSetup.pair(this.foreign, this.guest.getUUID());
        ServerSetup.teleport(client, arena, 0.0F);
        client.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PlannerItems.flightPlanner().orElseThrow()));
        ServerTest.line(CASE + " Nr. 68: Tour von " + JoinEnv.GUEST_NAME + " steht vor dem Client, Client hält einen leeren Flugplaner");
    }

    private void setupMarkers(ServerPlayer client) {
        client.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        this.guest.snapTo(client.getX() + 2.0, client.getY(), client.getZ(), 0.0F, 0.0F);
        this.guest.setItemInHand(InteractionHand.MAIN_HAND, planner(missionAround(client.position(), GUEST_POINTS, 6.0), "Gast"));
        ServerTest.line(CASE + " Nr. 69: " + JoinEnv.GUEST_NAME + " hält neben dem Client einen Flugplaner mit " + GUEST_POINTS
                + " Punkten, Client ohne Flugplaner");
    }

    private void setupGimbal(MinecraftServer server, ServerPlayer client) {
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        BlockPos ground = ServerSetup.ground(level, arena.getX() + 10, arena.getZ() + 6);
        this.cine = ServerSetup.spawn(this.guest, CamPresets.CINE, ground);
        ServerSetup.pair(this.cine, this.guest.getUUID());
        AutonomyServer.Reply reply = AutonomyServer.execute(this.guest, this.cine, MissionCommand.Command.HOLD, MissionCommand.AUTO_INDEX);
        ServerTest.line(CASE + " Nr. 50: Kino von " + JoinEnv.GUEST_NAME + " startet Halten am Server: " + reply.status());
        if (!reply.ok()) {
            ServerTest.fail(CASE + " Nr. 50: Halten startet nicht: " + reply.status());
        }
        Vec3 stand = Vec3.atBottomCenterOf(ServerSetup.ground(level, arena.getX() + 10, arena.getZ() + 1).above());
        client.teleportTo(level, stand.x, stand.y, stand.z, Set.<Relative>of(), 0.0F, -20.0F, true);
    }

    private static Mission missionAround(Vec3 center, int count, double radius) {
        List<Waypoint> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = 2.0 * Math.PI * i / count;
            points.add(Waypoint.at(center.x + radius * Math.sin(angle), center.y + 4.0, center.z + 4.0 + radius * Math.cos(angle)));
        }
        return Mission.of(points).withEnd(Mission.End.HOVER).withBaseSpeed(4.0);
    }

    private static ItemStack planner(Mission mission, String name) {
        ItemStack tablet = new ItemStack(PlannerItems.flightPlanner().orElseThrow());
        PlannerTablets.write(tablet, new TabletState(List.of(PlannerEntry.of(name, mission)), 0, TabletState.DEFAULT_GROUND_HEIGHT));
        return tablet;
    }

    private static ServerPlayer fake(MinecraftServer server, ServerLevel level, Vec3 position) {
        GameProfile profile = new GameProfile(OPERATOR_ID, OPERATOR_NAME);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault()) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.teleportTo(level, position.x, position.y, position.z, Set.<Relative>of(), 0.0F, 0.0F, true);
        player.snapTo(position.x, position.y, position.z, 0.0F, 0.0F);
        ServerTest.line("simulierter Operator " + OPERATOR_NAME + " am Server angemeldet bei " + ServerSetup.format(position));
        return player;
    }

    private void cleanup(MinecraftServer server) {
        if (this.operator != null && server.getPlayerList().getPlayer(this.operator.getUUID()) == this.operator) {
            server.getPlayerList().remove(this.operator);
        }
        ServerSetup.leave(server, this.guest);
    }

    @Override
    void abort() {
        super.abort();
        if (this.guest != null) {
            this.cleanup(this.guest.level().getServer());
        }
    }

    @Override
    boolean done() {
        return this.stage == 9;
    }

    @Override
    @Nullable String missing() {
        if (!this.pairChecked) {
            return "Nr. 68 nicht geprüft";
        }
        if (!this.markersGiven) {
            return "Nr. 69 nicht abgeschlossen";
        }
        if (!this.bound || !this.swept) {
            return "Nr. 50: Operator gebunden " + this.bound + ", geschwenkt " + this.swept;
        }
        return null;
    }
}
