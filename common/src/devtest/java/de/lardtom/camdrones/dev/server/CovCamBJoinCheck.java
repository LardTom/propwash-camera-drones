package de.lardtom.camdrones.dev.server;

import com.mojang.brigadier.tree.CommandNode;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.crew.CrewTexts;
import de.lardtom.camdrones.crew.model.CrewBook;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

final class CovCamBJoinCheck extends ServerTest.Scenario {
    static final String CASE = "covcamb";
    static final String CASE_FAR = "covcamb-weit";
    static final int SETUP_TICKS = 60;
    static final int WAIT_TICKS = 20 * 120;
    static final int FAR_BLOCKS = 300;
    static final int FAR_TICKS = 20 * 150;
    static final double STILL = 0.2;
    static final int STILL_LIMIT = 40;
    static final int CHECK_LIMIT = CrewService.CHECK_TICKS + 2;
    static final List<String> CAMERA_WORDS = List.of("mission", "crew", "follow", "camdrones", "camera", "gimbal");
    private int stage;
    private int joinedAt = -1;
    private int mark;
    private @Nullable ServerPlayer guest;
    private @Nullable Entity drone;
    private @Nullable Vec3 last;
    private int lastMoveTick;
    private int stillWorst;
    private NavLogic.MissionState lastState = NavLogic.MissionState.IDLE;
    private final List<String> states = new ArrayList<>();
    private int ruleOffAt = -1;
    private int leftAt = -1;
    private boolean commands;
    private boolean far;
    private boolean rules;
    private boolean rulesArrived;
    private boolean spectated;
    private boolean ruleEnd;
    private boolean ruleRefused;
    private boolean offlineWait;
    private final boolean farOnly;

    CovCamBJoinCheck(boolean farOnly) {
        this.farOnly = farOnly;
    }

    @Override
    void tick(MinecraftServer server, int tick) {
        ServerPlayer client = ServerSetup.client(server);
        if (client == null && this.stage == 60) {
            this.clientLeft(tick);
        }
        int waited = tick - this.mark;
        if (client == null && this.stage > 0 && this.stage != 70) {
            ServerTest.fail(CASE + ": Client trennt in Stufe " + this.stage);
            this.cleanup(server);
            this.stage = 99;
            return;
        }
        switch (this.stage) {
            case 0 -> {
                if (client == null) {
                    return;
                }
                if (this.joinedAt < 0) {
                    this.joinedAt = tick;
                }
                if (tick - this.joinedAt >= SETUP_TICKS && this.farOnly) {
                    this.setupFar(server, client);
                    this.next(tick, 20);
                } else if (tick - this.joinedAt >= SETUP_TICKS) {
                    this.setupCommands(server, client);
                    this.next(tick, 10);
                }
            }
            case 10 -> this.commandStage(server, client, tick, waited);
            case 15 -> {
                if (waited >= 60) {
                    ServerSetup.command(server, "deop " + client.getPlainTextName());
                    client.getInventory().setSelectedSlot(0);
                    client.setExperienceLevels(2);
                    this.next(tick, 30);
                }
            }
            case 20 -> this.farStage(server, client, tick, waited);
            case 30 -> this.rulesNotOp(server, client, tick, waited);
            case 31 -> this.rulesOp(server, client, tick, waited);
            case 40 -> this.spectateStage(server, client, tick, waited);
            case 50 -> this.crewRuleStage(server, client, tick, waited);
            case 60 -> this.crewBindAgain(server, client, tick, waited);
            case 70 -> this.crewOffline(server, tick, waited);
            default -> {
            }
        }
    }

    private void next(int tick, int stage) {
        this.stage = stage;
        this.mark = tick;
    }

    private static int ack(ServerPlayer client) {
        return client.getInventory().getSelectedSlot();
    }

    private static MissionStatus status(Entity drone) {
        MissionStatus status = PropwashApi.droneData().get(drone, CamDroneData.missionState());
        return status == null ? MissionStatus.IDLE : status;
    }

    private Entity drone() {
        if (this.drone == null) {
            throw new IllegalStateException("keine Drohne");
        }
        return this.drone;
    }

    private void discard() {
        if (this.drone != null && !this.drone.isRemoved()) {
            if (PropwashApi.drones().autonomy().active(this.drone).isPresent()) {
                PropwashApi.drones().autonomy().stop(this.drone);
            }
            this.drone.discard();
        }
        this.drone = null;
    }

    private void setupCommands(MinecraftServer server, ServerPlayer client) {
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        ServerSetup.teleport(client, arena, 0.0F);
        client.getInventory().setSelectedSlot(0);
        BlockPos ground = ServerSetup.ground(level, arena.getX(), arena.getZ() + 5);
        this.drone = ServerSetup.spawn(client, CamPresets.TOUR, ground);
        ServerSetup.pair(this.drone, client.getUUID());
        double y = ground.getY() + 7.0;
        double x = ground.getX() + 0.5;
        double z = ground.getZ() + 0.5;
        ServerSetup.mission(this.drone, Mission.of(List.of(Waypoint.at(x, y, z + 6.0), Waypoint.at(x + 6.0, y, z + 10.0), Waypoint.at(x, y, z + 14.0),
                Waypoint.at(x - 6.0, y, z + 10.0))).withLoop(true).withBaseSpeed(2.0));
        CommandNode<CommandSourceStack> propwash = server.getCommands().getDispatcher().getRoot().getChild("propwash");
        List<String> children = new ArrayList<>();
        List<String> camera = new ArrayList<>();
        if (propwash != null) {
            for (CommandNode<CommandSourceStack> child : propwash.getChildren()) {
                children.add(child.getName());
                if (CAMERA_WORDS.stream().anyMatch(word -> child.getName().toLowerCase(Locale.ROOT).contains(word))) {
                    camera.add(child.getName());
                }
            }
        }
        if (!camera.isEmpty()) {
            ServerTest.fail(CASE + " befehle: /propwash hat Kamera-Unterbefehle " + camera);
        }
        ServerTest.line(CASE + " befehle: Drohne des Clients mit Mission (4 Punkte, Schleife) am Boden, /propwash mit " + children.size()
                + " Unterbefehlen, davon Kamera: " + camera.size());
        client.setExperienceLevels(1);
    }

    private void commandStage(MinecraftServer server, ServerPlayer client, int tick, int waited) {
        Entity drone = this.drone();
        MissionStatus status = status(drone);
        boolean flying = PropwashApi.drones().autonomy().active(drone).filter(id -> id.getPath().equals("mission")).isPresent();
        String now = status.state() + (flying ? "+Flug" : "");
        if (this.states.isEmpty() || !this.states.getLast().equals(now)) {
            this.states.add(now);
            ServerTest.line(CASE + " befehle: mission_state " + now + " (Tick " + waited + ")");
        }
        if (ack(client) == 1) {
            boolean running = this.states.stream().anyMatch(s -> s.startsWith("RUNNING"));
            boolean paused = this.states.stream().anyMatch(s -> s.startsWith("PAUSED"));
            boolean stopped = status.state() != NavLogic.MissionState.RUNNING && status.state() != NavLogic.MissionState.PAUSED;
            this.commands = running && paused && stopped;
            ServerTest.line(CASE + " befehle: Folge " + this.states + ", gestartet " + running + ", angehalten " + paused + ", gestoppt " + stopped);
            if (!this.commands) {
                ServerTest.fail(CASE + " befehle: mission_state folgt den Befehlen nicht: " + this.states);
            }
            this.discard();
            this.next(tick, 15);
        } else if (waited > WAIT_TICKS) {
            ServerTest.fail(CASE + " befehle: Client meldet sich nicht zurück (" + this.states + ")");
            this.discard();
            this.next(tick, 15);
        }
    }

    private void setupFar(MinecraftServer server, ServerPlayer client) {
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        ServerSetup.teleport(client, arena, 0.0F);
        for (int x = -16; x <= FAR_BLOCKS + 32; x += 16) {
            level.getChunk((arena.getX() + x) >> 4, (arena.getZ() + 8) >> 4);
        }
        BlockPos ground = ServerSetup.ground(level, arena.getX() + 3, arena.getZ() + 8);
        this.drone = ServerSetup.spawn(client, CamPresets.TOUR, ground);
        ServerSetup.pair(this.drone, client.getUUID());
        double y = ground.getY() + 9.0;
        double z = ground.getZ() + 0.5;
        List<Waypoint> points = new ArrayList<>();
        for (int x = 50; x <= FAR_BLOCKS; x += 50) {
            points.add(Waypoint.at(ground.getX() + x + 0.5, y, z));
        }
        ServerSetup.mission(this.drone, Mission.of(points).withEnd(Mission.End.HOVER).withBaseSpeed(8.0));
        AutonomyServer.Reply reply = AutonomyServer.execute(client, this.drone, MissionCommand.Command.START, MissionCommand.AUTO_INDEX);
        ServerTest.line(CASE + " weit: Mission über " + FAR_BLOCKS + " Blöcke ohne Pilot gestartet (" + reply.status() + "), Besitzer "
                + client.getPlainTextName() + " bleibt am Start, Simulationsdistanz " + server.getPlayerList().getSimulationDistance() + " Chunks");
        if (!reply.ok()) {
            ServerTest.fail(CASE + " weit: Mission startet nicht: " + reply.status());
        }
        this.last = null;
        this.stillWorst = 0;
    }

    private void farStage(MinecraftServer server, ServerPlayer client, int tick, int waited) {
        Entity drone = this.drone();
        Vec3 now = drone.position();
        MissionStatus status = status(drone);
        if (this.last == null || now.distanceTo(this.last) > STILL) {
            this.last = now;
            this.lastMoveTick = waited;
        }
        boolean running = status.state() == NavLogic.MissionState.RUNNING;
        boolean done = status.state() == NavLogic.MissionState.DONE;
        int still = waited - this.lastMoveTick;
        if (running && !done) {
            this.stillWorst = Math.max(this.stillWorst, still);
        }
        if (waited % 20 == 0) {
            double distance = Math.hypot(now.x - client.getX(), now.z - client.getZ());
            ServerTest.line(String.format(Locale.ROOT, "%s weit: t=%ds x=%.1f y=%.1f z=%.1f Abstand=%.0f Zustand=%s Punkt=%d steht=%.1fs", CASE, waited / 20, now.x,
                    now.y, now.z, distance, status.state(), status.index() + 1, still / 20.0));
        }
        if (done || waited > FAR_TICKS || still > 20 * 20) {
            double distance = Math.hypot(now.x - client.getX(), now.z - client.getZ());
            this.far = done && this.stillWorst <= STILL_LIMIT && distance >= FAR_BLOCKS - 10;
            ServerTest.line(String.format(Locale.ROOT, "%s weit: Ende erreicht %s nach %.0f s, %.0f Blöcke vom Besitzer, längster Stillstand %.1f s", CASE, done,
                    waited / 20.0, distance, this.stillWorst / 20.0));
            if (!this.far) {
                ServerTest.fail(CASE + " weit: Mission erreicht das Ende nicht ohne Stillstand (Ende " + done + ", Stillstand " + this.stillWorst / 20.0
                        + " s, Abstand " + Math.round(distance) + ")");
            }
            this.discard();
            client.setExperienceLevels(9);
            this.stage = 99;
        }
    }

    private static int altitude(MinecraftServer server) {
        return CamWorldRules.maxAltitude(server.overworld());
    }

    private void rulesNotOp(MinecraftServer server, ServerPlayer client, int tick, int waited) {
        if (ack(client) == 2) {
            int value = altitude(server);
            boolean kept = value == CamWorldRules.DEFAULT_MAX_ALTITUDE;
            ServerTest.line(CASE + " regeln: Client ohne OP versucht camdrones:max_altitude zu ändern, Wert bleibt " + value + ": " + kept);
            if (!kept) {
                ServerTest.fail(CASE + " regeln: Client ohne OP ändert die Höhengrenze auf " + value);
            }
            ServerSetup.command(server, "op " + client.getPlainTextName());
            client.setExperienceLevels(3);
            this.rules = kept;
            this.next(tick, 31);
        } else if (waited > WAIT_TICKS) {
            ServerTest.fail(CASE + " regeln: Client meldet sich ohne OP nicht zurück");
            this.rules = false;
            ServerSetup.command(server, "op " + client.getPlainTextName());
            client.setExperienceLevels(3);
            this.next(tick, 31);
        }
    }

    private void rulesOp(MinecraftServer server, ServerPlayer client, int tick, int waited) {
        int value = altitude(server);
        if (value == 50 && !this.rulesArrived) {
            this.rulesArrived = true;
            ServerTest.line(CASE + " regeln: nach /op kommt die Änderung an, camdrones:max_altitude = " + value);
        }
        if (ack(client) == 3 || waited > WAIT_TICKS) {
            if (!this.rulesArrived) {
                ServerTest.fail(CASE + " regeln: Änderung mit OP kommt nicht an (Wert " + value + ")");
                this.rules = false;
            }
            ServerSetup.command(server, "gamerule " + CamWorldRules.MAX_ALTITUDE_ID + " " + CamWorldRules.DEFAULT_MAX_ALTITUDE);
            ServerSetup.command(server, "deop " + client.getPlainTextName());
            this.setupSpectate(server, client);
            this.next(tick, 40);
        }
    }

    private void setupSpectate(MinecraftServer server, ServerPlayer client) {
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        ServerSetup.teleport(client, arena, 0.0F);
        BlockPos ground = ServerSetup.ground(level, arena.getX(), arena.getZ() + 20);
        this.drone = ServerSetup.spawn(client, CamPresets.TOUR, ground);
        ServerSetup.pair(this.drone, client.getUUID());
        client.setItemSlot(EquipmentSlot.HEAD, item("propwash:goggles_creative"));
        List<Waypoint> circle = new ArrayList<>();
        double cx = ground.getX() + 0.5;
        double cz = ground.getZ() + 0.5;
        double y = ground.getY() + 9.0;
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45.0);
            circle.add(Waypoint.at(cx + 14.0 * Math.cos(a), y, cz + 14.0 * Math.sin(a)));
        }
        ServerSetup.mission(this.drone, Mission.of(circle).withSmooth(true).withLoop(true).withBaseSpeed(12.0));
        AutonomyServer.Reply reply = AutonomyServer.execute(client, this.drone, MissionCommand.Command.START, MissionCommand.AUTO_INDEX);
        ServerTest.line(CASE + " zuschauen: zweite Drohne von " + client.getPlainTextName() + " fliegt ohne Pilot einen engen Kreis (Radius 14 m, weich, "
                + "12 m/s), der Client schaut mit Brille und F7 zu: " + reply.status());
        client.getInventory().setSelectedSlot(0);
        client.setExperienceLevels(4);
    }

    private void spectateStage(MinecraftServer server, ServerPlayer client, int tick, int waited) {
        Entity drone = this.drone();
        if (waited % 40 == 0) {
            Optional<DroneView> view = PropwashApi.drones().view(drone);
            double roll = view.map(v -> Math.toDegrees(Math.acos(Math.clamp(v.up().y, -1.0, 1.0)))).orElse(Double.NaN);
            ServerTest.line(String.format(Locale.ROOT, "%s zuschauen: Flug %s, Neigung des Rahmens %.1f°, Tempo %.1f m/s", CASE,
                    PropwashApi.drones().autonomy().active(drone).map(Identifier::toString).orElse("keiner"), roll,
                    view.map(DroneView::speed).orElse(0.0)));
        }
        if (ack(client) == 4 || waited > WAIT_TICKS) {
            this.spectated = ack(client) == 4;
            if (!this.spectated) {
                ServerTest.fail(CASE + " zuschauen: Client meldet sich nicht zurück");
            }
            this.discard();
            this.setupCrew(server, client);
            this.next(tick, 50);
        }
    }

    private void setupCrew(MinecraftServer server, ServerPlayer client) {
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        client.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        ServerSetup.teleport(client, arena, 0.0F);
        if (this.guest == null) {
            this.guest = ServerSetup.guest(server, level, Vec3.atBottomCenterOf(ServerSetup.ground(level, arena.getX() + 3, arena.getZ() + 3).above()));
        }
        this.guest.snapTo(arena.getX() + 3.5, client.getY(), arena.getZ() + 3.5, 0.0F, 0.0F);
        BlockPos ground = ServerSetup.ground(level, arena.getX(), arena.getZ() + 6);
        this.drone = ServerSetup.spawn(this.guest, CamPresets.CINE, ground);
        ServerSetup.pair(this.drone, this.guest.getUUID());
        CrewBook.Verdict verdict = CrewService.invite(this.guest, this.drone, client);
        ServerTest.line(CASE + " crew: " + JoinEnv.GUEST_NAME + " lädt " + client.getPlainTextName() + " als Operator ein: " + verdict);
        if (!verdict.ok()) {
            ServerTest.fail(CASE + " crew: Einladung abgelehnt: " + verdict);
        }
        client.getInventory().setSelectedSlot(0);
        client.setExperienceLevels(5);
        this.ruleOffAt = -1;
    }

    private void crewRuleStage(MinecraftServer server, ServerPlayer client, int tick, int waited) {
        Entity drone = this.drone();
        Optional<UUID> operator = CrewService.book().operator(drone.getUUID());
        boolean bound = operator.filter(client.getUUID()::equals).isPresent();
        if (this.ruleOffAt < 0 && bound) {
            ServerTest.line(CASE + " crew: " + client.getPlainTextName() + " ist Operator, Welt-Regel camdrones:allow_operator wird ausgeschaltet");
            ServerSetup.command(server, "gamerule " + CamWorldRules.ALLOW_OPERATOR_ID + " false");
            this.ruleOffAt = tick;
            return;
        }
        if (this.ruleOffAt >= 0 && !this.ruleEnd && operator.isEmpty()) {
            int took = tick - this.ruleOffAt;
            this.ruleEnd = took <= CHECK_LIMIT && !CrewData.get(drone).operated() && PropwashApi.drones().operators().operator(drone).isEmpty();
            ServerTest.line(CASE + " crew: Regel aus beendet die Bindung nach " + took + " Ticks (Grenze " + CHECK_LIMIT + "), Gimbal frei: "
                    + !CrewData.get(drone).operated());
            if (!this.ruleEnd) {
                ServerTest.fail(CASE + " crew: Bindung endet nicht binnen 1 s nach Regel aus (" + took + " Ticks)");
            }
            CrewBook.Verdict again = CrewService.invite(this.guest, drone, client);
            this.ruleRefused = again == CrewBook.Verdict.RULE_OFF;
            ServerTest.line(CASE + " crew: neue Einladung bei Regel aus: " + again);
            if (!this.ruleRefused) {
                ServerTest.fail(CASE + " crew: neue Einladung bei Regel aus ergibt " + again + " statt RULE_OFF");
            }
        }
        if (this.ruleOffAt >= 0 && tick - this.ruleOffAt > 3 * 20 && !this.ruleEnd) {
            ServerTest.fail(CASE + " crew: Bindung bleibt trotz Regel aus");
            this.ruleEnd = false;
        }
        if (this.ruleOffAt >= 0 && (ack(client) == 5 || tick - this.ruleOffAt > WAIT_TICKS)) {
            ServerSetup.command(server, "gamerule " + CamWorldRules.ALLOW_OPERATOR_ID + " true");
            CrewBook.Verdict verdict = CrewService.invite(this.guest, drone, client);
            ServerTest.line(CASE + " crew: Regel wieder an, neue Einladung: " + verdict);
            client.setExperienceLevels(6);
            this.next(tick, 60);
        } else if (this.ruleOffAt < 0 && waited > WAIT_TICKS) {
            ServerTest.fail(CASE + " crew: Client wird nicht Operator");
            this.cleanup(server);
            this.stage = 99;
        }
    }

    private void crewBindAgain(MinecraftServer server, ServerPlayer client, int tick, int waited) {
        Entity drone = this.drone();
        boolean bound = CrewService.book().operator(drone.getUUID()).filter(client.getUUID()::equals).isPresent();
        if (bound && waited % 20 == 0) {
            ServerTest.line(CASE + " crew: wieder gebunden, warte auf das Trennen des Clients");
        }
        if (waited > WAIT_TICKS) {
            ServerTest.fail(CASE + " crew: Client trennt nicht");
            this.cleanup(server);
            this.stage = 99;
        }
    }

    @Override
    void abort() {
        super.abort();
        if (this.guest != null) {
            ServerSetup.leave(this.guest.level().getServer(), this.guest);
        }
    }

    void clientLeft(int tick) {
        if (this.stage == 60) {
            this.leftAt = tick;
            this.next(tick, 70);
        }
    }

    private void crewOffline(MinecraftServer server, int tick, int waited) {
        Entity drone = this.drone();
        Optional<UUID> operator = CrewService.book().operator(drone.getUUID());
        Optional<CrewBook.Wait> wait = CrewService.book().waiting(drone.getUUID());
        if (wait.isPresent()) {
            int took = tick - this.leftAt;
            boolean kept = operator.isPresent() && CrewData.get(drone).operated() && PropwashApi.drones().operators().operator(drone).isEmpty();
            this.offlineWait = took <= CHECK_LIMIT && kept;
            ServerTest.line(CASE + " crew: Operator verlässt das Spiel, nach " + took + " Ticks (Grenze " + CHECK_LIMIT + ") wartet die Crew "
                    + CrewTexts.clock(wait.get().remaining(Util.getMillis())) + " auf die Rückkehr, Gimbal beim Operator und Propwash-Bindung gelöst: "
                    + kept);
            if (!this.offlineWait) {
                ServerTest.fail(CASE + " crew: Crew wartet nicht binnen 1 s nach dem Trennen (" + took + " Ticks, " + kept + ")");
            }
            this.cleanup(server);
            this.stage = 99;
        } else if (operator.isEmpty()) {
            ServerTest.fail(CASE + " crew: Crew endet beim Trennen sofort, statt 2:00 auf den Operator zu warten");
            this.cleanup(server);
            this.stage = 99;
        } else if (waited > 5 * 20) {
            ServerTest.fail(CASE + " crew: Crew wartet nach dem Trennen nicht auf den Operator");
            this.cleanup(server);
            this.stage = 99;
        }
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow(() -> new IllegalStateException(id + " fehlt")));
    }

    private void cleanup(MinecraftServer server) {
        this.discard();
        ServerSetup.command(server, "gamerule " + CamWorldRules.ALLOW_OPERATOR_ID + " true");
        ServerSetup.leave(server, this.guest);
        this.guest = null;
    }

    @Override
    boolean done() {
        return this.stage == 99;
    }

    @Override
    @Nullable String missing() {
        List<String> missing = new ArrayList<>();
        if (this.farOnly) {
            return this.far ? null : "weiter Flug";
        }
        if (!this.commands) {
            missing.add("Befehle");
        }
        if (!this.rules) {
            missing.add("Regeln nur für OP");
        }
        if (!this.spectated) {
            missing.add("Zuschauen");
        }
        if (!this.ruleEnd || !this.ruleRefused) {
            missing.add("Regel aus beendet Bindung");
        }
        if (!this.offlineWait) {
            missing.add("Operator verlässt das Spiel");
        }
        return missing.isEmpty() ? null : String.join(", ", missing);
    }
}
