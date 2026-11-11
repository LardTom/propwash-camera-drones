package de.lardtom.camdrones.dev.server;

import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.crew.model.CrewBook;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.CrewPackets;
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
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

final class CrewCheck extends ServerTest.Scenario {
    static final int SETUP_TICKS = 60;
    static final int WAIT_TICKS = 20 * 90;
    static final double OPERATOR_TILT = -40.0;
    static final double GUEST_TILT = -60.0;
    static final int SWEEP_TICKS = 60;
    static final int HOLD_TICKS = 100;
    static final double MISSION_HEIGHT = 6.0;
    static final int UNBIND_TICKS = 40;
    static final double MISSION_PATH = 6.0;
    static final int SOLO_TICKS = 100;
    private int stage;
    private int joinedAt = -1;
    private int mark;
    private @Nullable ServerPlayer guest;
    private @Nullable Entity held;
    private @Nullable Entity flown;
    private boolean aBound;
    private boolean aTilt;
    private boolean aLeft;
    private boolean aMission;
    private final Set<Integer> aIndices = new TreeSet<>();
    private @Nullable Vec3 aLast;
    private double aPath;
    private int unboundAt = -1;
    private boolean bInvited;
    private boolean bBound;
    private boolean bTilt;
    private boolean bLeft;
    private boolean cFlying;
    private boolean cSolo;
    private boolean cYield;
    private int cSoloAt = -1;
    private int cAcceptedAt = -1;
    private int sent;

    @Override
    void tick(MinecraftServer server, int tick) {
        ServerPlayer client = ServerSetup.client(server);
        if (client == null && this.stage > 0 && this.stage < 9 && !(this.stage == 8 && this.cYield)) {
            ServerTest.fail("crew: Client trennt in Stufe " + this.stage);
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
                    this.setupA(server, client);
                    this.next(tick, 1);
                }
            }
            case 1 -> {
                Entity drone = Objects.requireNonNull(this.held);
                boolean mission = PropwashApi.drones().autonomy().active(drone).filter(id -> id.getPath().equals("mission")).isPresent();
                if (mission && ServerSetup.height(drone) > 3.0) {
                    CrewBook.Verdict verdict = CrewService.invite(this.guest, drone, client);
                    ServerTest.line("crew a: " + JoinEnv.GUEST_NAME + " (Besitzer, Drohne fliegt eine Mission am Server, Punkt " + (missionIndex(drone) + 1)
                            + ") lädt " + client.getPlainTextName() + " als Operator ein: " + verdict);
                    if (!verdict.ok()) {
                        ServerTest.fail("crew a: Einladung abgelehnt: " + verdict);
                    }
                    this.next(tick, 2);
                } else if (waited > WAIT_TICKS) {
                    this.timeout("crew a: Mission startet nicht (Flug " + PropwashApi.drones().autonomy().active(drone).map(Identifier::toString)
                            .orElse("keiner") + ", Höhe " + ServerSetup.format(ServerSetup.height(drone)) + " m)", server);
                }
            }
            case 2 -> this.operating(server, client, tick, waited);
            case 3 -> {
                Entity drone = Objects.requireNonNull(this.flown);
                Optional<CrewBook.Invite> pending = CrewService.book().pending(JoinEnv.GUEST_ID, Util.getMillis());
                if (!this.bInvited && pending.isPresent() && pending.get().drone().equals(drone.getUUID())) {
                    this.bInvited = true;
                    ServerTest.line("crew b: " + client.getPlainTextName() + " lädt " + JoinEnv.GUEST_NAME + " über den Knopf „Crew“ in der Wartung ein");
                    ServerSetup.command(this.guest, CrewService.ACCEPT_COMMAND);
                }
                Optional<UUID> operator = CrewService.book().operator(drone.getUUID());
                if (this.bInvited && !this.bBound && operator.filter(JoinEnv.GUEST_ID::equals).isPresent()) {
                    this.bBound = true;
                    ServerTest.line("crew b: " + JoinEnv.GUEST_NAME + " nimmt per Befehl an (" + CrewService.ACCEPT_COMMAND + "), Propwash-Operator "
                            + PropwashApi.drones().operators().operator(drone).map(ServerPlayer::getPlainTextName).orElse("keiner"));
                }
                if (this.bBound && client.containerMenu == client.inventoryMenu) {
                    this.startFlight(client);
                    this.next(tick, 4);
                } else if (waited > WAIT_TICKS) {
                    this.timeout("crew b: keine Einladung über die Wartung (eingeladen " + this.bInvited + ")", server);
                }
            }
            case 4 -> {
                Entity drone = Objects.requireNonNull(this.flown);
                boolean flying = PropwashApi.drones().view(drone).flatMap(DroneView::pilot).filter(client.getUUID()::equals).isPresent();
                if (flying && ServerSetup.height(drone) > 2.5) {
                    this.guest.snapTo(drone.getX() + 3.0, client.getY(), drone.getZ() - 3.0, 0.0F, 0.0F);
                    ServerTest.line("crew b: " + client.getPlainTextName() + " fliegt, Operator " + JoinEnv.GUEST_NAME + " gebunden: "
                            + CrewService.book().operator(drone.getUUID()).filter(JoinEnv.GUEST_ID::equals).isPresent());
                    this.sent = 0;
                    this.next(tick, 5);
                } else if (waited > WAIT_TICKS) {
                    this.timeout("crew b: Client fliegt nicht (FPV " + flying + ", Höhe " + ServerSetup.format(ServerSetup.height(drone)) + " m)", server);
                }
            }
            case 5 -> {
                Entity drone = Objects.requireNonNull(this.flown);
                if (waited % 2 == 0) {
                    double tilt = GUEST_TILT * Math.min(1.0, waited / (double) SWEEP_TICKS);
                    GimbalSetting setting = new GimbalSetting(GimbalSetting.Mode.FOLLOW, tilt, 0.0, 0.0, 0.0);
                    CrewService.gimbal(new CrewPackets.Gimbal(drone.getId(), setting), this.guest);
                    this.sent++;
                }
                double stored = CrewData.get(drone).setting().tilt();
                if (waited % 20 == 0) {
                    ServerTest.line("crew b: simulierter Operator schickt Neigung, gespeichert " + ServerSetup.format(stored) + "°");
                }
                if (!this.bTilt && stored <= GUEST_TILT + 0.5) {
                    this.bTilt = true;
                }
                if (waited >= SWEEP_TICKS + HOLD_TICKS) {
                    if (!this.bTilt) {
                        ServerTest.fail("crew b: Neigung des simulierten Operators kommt nicht an (" + ServerSetup.format(stored) + "°)");
                    }
                    ServerSetup.command(this.guest, "/camdrones crew leave");
                    this.bLeft = CrewService.book().operator(drone.getUUID()).isEmpty() && !CrewData.get(drone).operated();
                    ServerTest.line("crew b: " + this.sent + " Eingaben geschickt, Operator gibt per Befehl /camdrones crew leave ab: " + this.bLeft);
                    this.next(tick, 6);
                }
            }
            case 6 -> {
                if (waited >= 80) {
                    release(client, this.flown);
                    this.setupC(client);
                    this.next(tick, 7);
                }
            }
            case 7 -> {
                Entity drone = Objects.requireNonNull(this.flown);
                boolean mission = CrewService.flyingItself(drone) && missionStatus(drone).state() == NavLogic.MissionState.RUNNING;
                if (mission && ServerSetup.height(drone) > 3.0) {
                    this.cFlying = true;
                    ServerTest.line("crew c: Drohne von " + client.getPlainTextName() + " fliegt eine Mission am Server, " + JoinEnv.GUEST_NAME
                            + " steht daneben");
                    this.next(tick, 8);
                } else if (waited > WAIT_TICKS) {
                    this.timeout("crew c: Mission startet nicht (Flug " + PropwashApi.drones().autonomy().active(drone).map(Identifier::toString)
                            .orElse("keiner") + ", Mission " + missionStatus(drone).state() + ", Höhe " + ServerSetup.format(ServerSetup.height(drone))
                            + " m)", server);
                }
            }
            case 8 -> this.soloYields(server, client, tick, waited);
            default -> {
            }
        }
    }

    private void operating(MinecraftServer server, ServerPlayer client, int tick, int waited) {
        Entity drone = Objects.requireNonNull(this.held);
        Optional<UUID> operator = CrewService.book().operator(drone.getUUID());
        boolean bound = operator.filter(client.getUUID()::equals).isPresent();
        if (!this.aBound && bound) {
            this.aBound = true;
            Optional<ServerPlayer> propwash = PropwashApi.drones().operators().operator(drone);
            ServerTest.line("crew a: " + client.getPlainTextName() + " nimmt mit dem Befehl aus der Einladung an, Propwash-Operator "
                    + propwash.map(ServerPlayer::getPlainTextName).orElse("keiner") + ", Mission bei Punkt " + (missionIndex(drone) + 1));
        }
        if (bound) {
            MissionStatus status = missionStatus(drone);
            if (status.state() == NavLogic.MissionState.RUNNING) {
                this.aIndices.add(status.index());
            }
            Vec3 now = drone.position();
            if (this.aLast != null) {
                this.aPath += Math.hypot(now.x - this.aLast.x, now.z - this.aLast.z);
            }
            this.aLast = now;
        }
        double tilt = CrewData.get(drone).setting().tilt();
        if (this.aBound && !this.aTilt && tilt <= OPERATOR_TILT + 1.0) {
            this.aTilt = true;
            ServerTest.line("crew a: Neigung " + ServerSetup.format(tilt) + "° vom Operator-Client angenommen und an alle verteilt, Mission läuft weiter");
        }
        if (this.aBound && operator.isEmpty() && this.unboundAt < 0) {
            this.unboundAt = tick;
            this.aLeft = !CrewData.get(drone).operated() && PropwashApi.drones().operators().operator(drone).isEmpty();
            ServerTest.line("crew a: Operator gibt die Kamera mit Esc ab, Bindung gelöst: " + this.aLeft);
        }
        if (this.unboundAt >= 0 && tick - this.unboundAt >= UNBIND_TICKS) {
            MissionStatus status = missionStatus(drone);
            boolean flying = PropwashApi.drones().autonomy().active(drone).filter(id -> id.getPath().equals("mission")).isPresent();
            this.aMission = flying && status.state() == NavLogic.MissionState.RUNNING && this.aPath >= MISSION_PATH;
            ServerTest.line("crew a: Mission während der Bindung " + ServerSetup.format(this.aPath) + " m weiter geflogen (Punkte "
                    + this.aIndices.stream().map(i -> i + 1).toList() + "), danach " + status.state() + " bei Punkt " + (status.index() + 1)
                    + ", Flug am Server " + flying);
            if (!this.aMission) {
                ServerTest.fail("crew a: Mission läuft während der Bindung nicht weiter (" + ServerSetup.format(this.aPath) + " m, " + status.state() + ")");
            }
            AutonomyServer.Reply reply = AutonomyServer.execute(this.guest, drone, MissionCommand.Command.STOP, MissionCommand.AUTO_INDEX);
            ServerTest.line("crew a: Mission beendet: " + reply.status());
            drone.discard();
            this.setupB(client);
            this.next(tick, 3);
        } else if (waited > WAIT_TICKS) {
            this.timeout("crew a: Operator-Ablauf hängt (gebunden " + this.aBound + ", Neigung " + ServerSetup.format(tilt) + ")", server);
        }
    }

    private void setupC(ServerPlayer client) {
        Entity drone = Objects.requireNonNull(this.flown);
        Objects.requireNonNull(this.guest).snapTo(client.getX() + 2.0, client.getY(), client.getZ() + 2.0, 0.0F, 0.0F);
        double y = client.getY() + MISSION_HEIGHT;
        double x = drone.getX();
        double z = drone.getZ();
        ServerSetup.mission(drone, Mission.of(List.of(Waypoint.at(x, y, z + 4.0), Waypoint.at(x + 6.0, y, z + 8.0), Waypoint.at(x, y, z + 12.0),
                Waypoint.at(x - 6.0, y, z + 8.0))).withLoop(true).withBaseSpeed(2.0));
        AutonomyServer.Reply reply = AutonomyServer.execute(client, drone, MissionCommand.Command.START, MissionCommand.AUTO_INDEX);
        ServerTest.line("crew c: " + client.getPlainTextName() + " startet allein eine Mission als Schleife am Server: " + reply.status());
        if (!reply.ok()) {
            ServerTest.fail("crew c: Mission startet nicht: " + reply.status());
        }
    }

    private void soloYields(MinecraftServer server, @Nullable ServerPlayer client, int tick, int waited) {
        Entity drone = Objects.requireNonNull(this.flown);
        ServerPlayer guest = Objects.requireNonNull(this.guest);
        if (!this.cSolo) {
            Objects.requireNonNull(client);
            boolean solo = CrewService.soloCamera().player(drone.getUUID()).filter(client.getUUID()::equals).isPresent();
            boolean propwash = PropwashApi.drones().operators().operator(drone).map(ServerPlayer::getUUID).filter(client.getUUID()::equals).isPresent();
            if (solo && propwash && CrewData.get(drone).soloBy(client.getUUID()) && this.cSoloAt < 0) {
                this.cSoloAt = tick;
                ServerTest.line("crew c: " + client.getPlainTextName() + " öffnet das Livebild und steuert allein die Kamera (Propwash-Operator "
                        + client.getPlainTextName() + ")");
            }
            if (this.cSoloAt >= 0 && tick - this.cSoloAt >= SOLO_TICKS) {
                if (!solo || !propwash) {
                    ServerTest.fail("crew c: Kamera allein endet von selbst (allein " + solo + ", Propwash-Operator " + propwash + ")");
                }
                this.cSolo = true;
                CrewBook.Verdict invited = CrewService.invite(client, drone, guest);
                ServerTest.line("crew c: " + client.getPlainTextName() + " lädt " + JoinEnv.GUEST_NAME + " als Kameramann ein: " + invited);
                ServerSetup.command(guest, CrewService.ACCEPT_COMMAND);
                this.cAcceptedAt = tick;
                boolean soloGone = CrewService.soloCamera().player(drone.getUUID()).isEmpty();
                Optional<ServerPlayer> bound = PropwashApi.drones().operators().operator(drone);
                boolean crew = CrewService.book().operator(drone.getUUID()).filter(JoinEnv.GUEST_ID::equals).isPresent();
                this.cYield = invited.ok() && soloGone && crew && bound.map(ServerPlayer::getUUID).filter(JoinEnv.GUEST_ID::equals).isPresent()
                        && CrewData.get(drone).operatedBy(JoinEnv.GUEST_ID) && !CrewData.get(drone).solo();
                ServerTest.line("crew c: " + JoinEnv.GUEST_NAME + " nimmt an und hat Vorrang: Kamera allein beendet " + soloGone + ", Crew-Operator "
                        + crew + ", Propwash-Operator " + bound.map(ServerPlayer::getPlainTextName).orElse("keiner") + ", Gimbal-Daten "
                        + CrewData.get(drone));
                if (!this.cYield) {
                    ServerTest.fail("crew c: Kameramann der Crew hat keinen Vorrang vor der Kamera allein");
                }
            } else if (waited > WAIT_TICKS) {
                this.timeout("crew c: Client steuert die Kamera nicht allein (Kamera allein " + solo + ", Propwash-Operator " + propwash + ")", server);
            }
            return;
        }
        if (client == null || tick - this.cAcceptedAt >= 100) {
            ServerSetup.command(guest, "/camdrones crew leave");
            boolean stopped = PropwashApi.drones().autonomy().stop(drone);
            ServerTest.line("crew c: " + JoinEnv.GUEST_NAME + " gibt ab, Flug ohne Pilot beendet: " + stopped);
            ServerTest.line("crew: fertig");
            this.cleanup(server);
            this.stage = 9;
        }
    }

    private static MissionStatus missionStatus(Entity drone) {
        MissionStatus status = PropwashApi.droneData().get(drone, CamDroneData.missionState());
        return status == null ? MissionStatus.IDLE : status;
    }

    private static int missionIndex(Entity drone) {
        return missionStatus(drone).index();
    }

    private void next(int tick, int stage) {
        this.stage = stage;
        this.mark = tick;
    }

    private void timeout(String why, MinecraftServer server) {
        ServerTest.fail(why);
        this.cleanup(server);
        this.stage = 9;
    }

    private void setupA(MinecraftServer server, ServerPlayer client) {
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        ServerSetup.teleport(client, arena, 0.0F);
        BlockPos ground = ServerSetup.ground(level, arena.getX(), arena.getZ() + 6);
        this.guest = ServerSetup.guest(server, level, Vec3.atBottomCenterOf(ServerSetup.ground(level, arena.getX() + 3, arena.getZ() + 3).above()));
        this.held = ServerSetup.spawn(this.guest, CamPresets.TOUR, ground);
        ServerSetup.pair(this.held, this.guest.getUUID());
        double y = ground.getY() + 1.0 + MISSION_HEIGHT;
        double x = ground.getX() + 0.5;
        double z = ground.getZ() + 0.5;
        ServerSetup.mission(this.held, Mission.of(List.of(Waypoint.at(x, y, z + 4.0), Waypoint.at(x + 6.0, y, z + 8.0), Waypoint.at(x, y, z + 12.0),
                Waypoint.at(x - 6.0, y, z + 8.0))).withLoop(true).withBaseSpeed(3.0));
        AutonomyServer.Reply reply = AutonomyServer.execute(this.guest, this.held, MissionCommand.Command.START, MissionCommand.AUTO_INDEX);
        ServerTest.line("crew a: Drohne von " + JoinEnv.GUEST_NAME + " startet eine Mission als Schleife am Server: " + reply.status());
        if (!reply.ok()) {
            ServerTest.fail("crew a: Mission startet nicht: " + reply.status());
        }
    }

    private void setupB(ServerPlayer client) {
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        BlockPos ground = ServerSetup.ground(level, arena.getX() + 8, arena.getZ() + 4);
        this.flown = ServerSetup.spawn(client, CamPresets.TOUR, ground);
        ServerSetup.pair(this.flown, client.getUUID());
        Vec3 stand = Vec3.atBottomCenterOf(ground.above()).add(0.0, 0.0, -2.0);
        client.teleportTo(level, stand.x, stand.y, stand.z, Set.<Relative>of(), 0.0F, 30.0F, true);
        this.guest.snapTo(stand.x + 4.0, stand.y, stand.z - 2.0, 0.0F, 0.0F);
        client.setItemSlot(EquipmentSlot.HEAD, item("propwash:goggles_creative"));
        client.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        client.getInventory().setItem(1, item("propwash:remote"));
        ServerTest.line("crew b: zweite Drohne für " + client.getPlainTextName() + " am Boden, " + JoinEnv.GUEST_NAME + " daneben");
    }

    private void startFlight(ServerPlayer client) {
        ServerLevel level = client.level();
        client.setItemInHand(InteractionHand.MAIN_HAND, item("propwash:remote"));
        client.interactOn(this.flown, InteractionHand.MAIN_HAND, this.flown.position());
        client.getItemInHand(InteractionHand.MAIN_HAND).use(level, client, InteractionHand.MAIN_HAND);
        boolean flying = PropwashApi.drones().view(this.flown).flatMap(DroneView::pilot).filter(client.getUUID()::equals).isPresent();
        ServerTest.line("crew b: FPV gestartet: " + flying);
        if (!flying) {
            ServerTest.fail("crew b: FPV startet nicht");
        }
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow(() -> new IllegalStateException(id + " fehlt")));
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

    private void cleanup(MinecraftServer server) {
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
        if (!this.aBound) {
            return "Client wurde nicht Operator (a)";
        }
        if (!this.aTilt) {
            return "Neigung des Operator-Clients kam nicht an (a)";
        }
        if (!this.aLeft) {
            return "Operator-Client gab nicht ab (a)";
        }
        if (!this.aMission) {
            return "Mission lief während der Bindung nicht weiter (a)";
        }
        if (!this.bInvited || !this.bBound || !this.bTilt || !this.bLeft) {
            return "simulierter Operator (b): über die Wartung eingeladen " + this.bInvited + ", gebunden " + this.bBound + ", Neigung " + this.bTilt
                    + ", abgegeben " + this.bLeft;
        }
        if (!this.cFlying || !this.cSolo || !this.cYield) {
            return "Kamera allein (c): Mission " + this.cFlying + ", allein " + this.cSolo + ", Vorrang der Crew " + this.cYield;
        }
        return null;
    }
}
