package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.award.CamAwards;
import de.lardtom.camdrones.award.client.VideoAwards;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.model.CameraActions;
import de.lardtom.camdrones.client.planner.CamClientConfig;
import de.lardtom.camdrones.client.record.model.RecordingMode;
import de.lardtom.camdrones.crew.CrewDrones;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.crew.model.CrewBook;
import de.lardtom.camdrones.dev.AwardProbe;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AwardSteps {
    public static final String GROUP = "fortschritte: ";
    public static final String TAB = GROUP + "Tab Kamera-Drohnen mit Wurzel über einen Gimbal im Inventar";
    public static final String MISSION = GROUP + "Mission aus dem Flugplaner abgeschlossen";
    public static final String VIDEO = GROUP + "Video mit der Kamera-Drohne aufgenommen";
    public static final String CREW = GROUP + "zu zweit geflogen (Pilot und Kameramann)";
    public static final String ALL = GROUP + "alle vier im Tab Kamera-Drohnen";
    public static final String SHOT = "advancements_camdrones";
    private static final String REPORTED = "award.reported";
    private static final String START_Y = "award.startY";
    private static final String CAMERA = "award.camera";
    private static final int WAIT = 20 * 90;
    private static final int RECORD_TICKS = 40;

    private AwardSteps() {
    }

    public static void register() {
        SelfTest.clientStep(901, TAB, 20 * 30, Script.create()
                .server("Fortschritte am Server, zurücksetzen, Gimbal ins Inventar", (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    state.put("award.loaded", AwardProbe.loaded(server));
                    AwardProbe.revokeAll(player);
                    if (AwardProbe.done(player, CamAwards.ROOT)) {
                        throw new IllegalStateException("Wurzel nach dem Zurücksetzen erledigt");
                    }
                    player.getInventory().add(gear("gimbal_tour"));
                })
                .then("Wurzel im Tab erledigt", (ctx, ticks) -> clientDone(CamAwards.ROOT)
                        ? StepResult.pass(ctx.state().get("award.loaded") + "; Wurzel mit Gimbal im Inventar erledigt (" + clientTab() + ")")
                        : ticks > 200 ? StepResult.fail("Wurzel bleibt offen: " + clientTab()) : StepResult.pending())
                .then("drei Fortschritte unter der Wurzel sichtbar", (ctx, ticks) -> {
                    List<String> missing = missingInTab();
                    return missing.isEmpty() ? StepResult.pass() : ticks > 100 ? StepResult.fail("nicht im Tab: " + missing) : StepResult.pending();
                }), ctx -> StepResult.pass());
        SelfTest.clientStep(902, MISSION, 20 * 120, Script.create()
                .server("Fortschritt Mission zurücksetzen", (server, state) -> AwardProbe.revoke(Arena.player(server), CamAwards.MISSION))
                .append(CamFlights.groundDrone(CamPresets.TOUR, -120, -120, 0.0F))
                .server("kurze Mission mit zwei Wegpunkten", AwardSteps::shortMission)
                .then("Mission ohne Pilot starten", (ctx, ticks) -> CamFlights.send(ctx,
                        new MissionCommand.Request(CamFlights.serial(ctx.state()), MissionCommand.Command.START, 0)))
                .then("Mission abgeschlossen", (ctx, ticks) -> {
                    Optional<MissionStatus> status = CamFlights.clientMissionStatus(ctx.state());
                    boolean finished = status.map(s -> s.state() == NavLogic.MissionState.DONE).orElse(false);
                    return finished ? StepResult.pass("Mission " + status.get().state()) : ticks > WAIT ? StepResult.fail("Mission endet nicht: "
                            + status.map(MissionStatus::toString).orElse("ohne Zustand")) : StepResult.pending();
                })
                .then("Fortschritt Mission im Tab", awaitClient(CamAwards.MISSION, 200))
                .server("Fortschritt Mission am Server", (server, state) -> expect(Arena.player(server), CamAwards.MISSION)), ctx -> StepResult.pass());
        SelfTest.clientStep(903, VIDEO, 20 * 150, Script.create()
                .server("Fortschritt Video zurücksetzen", (server, state) -> AwardProbe.revoke(Arena.player(server), CamAwards.VIDEO))
                .append(CamFlights.groundDrone(CamPresets.TOUR, -100, -120, 0.0F))
                .server("Mission zum Schweben", AwardSteps::hoverMission)
                .run("Direkt-Modus wählen", () -> CamClientConfig.get().recordingMode(RecordingMode.DIRECT))
                .then("Mission ohne Pilot starten", (ctx, ticks) -> CamFlights.send(ctx,
                        new MissionCommand.Request(CamFlights.serial(ctx.state()), MissionCommand.Command.START, 0)))
                .then("Flug ohne Pilot am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("Livebild öffnen", (ctx, ticks) -> watch(ctx, ticks, CamFlights.clientDrone(ctx.state())))
                .then("Aufnahme starten", (ctx, ticks) -> record(ctx, ticks, CamFlights.clientDrone(ctx.state())))
                .sleep(RECORD_TICKS)
                .then("Aufnahme beenden", (ctx, ticks) -> stop(ctx, ticks, CamFlights.clientDrone(ctx.state())))
                .then("Fortschritt Video im Tab", awaitClient(CamAwards.VIDEO, 200))
                .server("Fortschritt Video am Server", (server, state) -> expect(Arena.player(server), CamAwards.VIDEO)), AwardSteps::leaveView);
        SelfTest.clientStep(904, CREW, 20 * 150, Script.create()
                .append(Pilot.launch(CamPresets.TOUR, -80, -120, 0.0F))
                .server("Fortschritt zurücksetzen, " + JoinEnv.GUEST_NAME + " als Kameramann einladen und annehmen lassen", AwardSteps::crew)
                .append(Flight.takeoff(3.0))
                .then("Fortschritt zu zweit im Tab", (ctx, ticks) -> {
                    Pilot.steer(Pilot.THROTTLE_CENTER);
                    return awaitClient(CamAwards.CREW_FLIGHT, 200).tick(ctx, ticks);
                })
                .server("Fortschritt zu zweit am Server für Pilot und Kameramann", (server, state) -> {
                    expect(Arena.player(server), CamAwards.CREW_FLIGHT);
                    expect(server.getPlayerList().getPlayer(JoinEnv.GUEST_ID), CamAwards.CREW_FLIGHT);
                }), ctx -> {
                    Pilot.releaseKeys();
                    ctx.onServer(CovCamBKit::leave);
                    return StepResult.pass();
                });
        SelfTest.clientStep(905, ALL, 20 * 60, Script.create()
                .then("alle vier erledigt", (ctx, ticks) -> {
                    List<String> open = openInTab();
                    return open.isEmpty() ? StepResult.pass(clientTab()) : ticks > 100 ? StepResult.fail("offen: " + open + " (" + clientTab() + ")")
                            : StepResult.pending();
                })
                .then("Fortschritte öffnen, Tab Kamera-Drohnen", AwardSteps::openTab)
                .append(Shots.photos(SHOT))
                .run("Fenster schließen", () -> Minecraft.getInstance().gui.setScreen(null)), ctx -> {
                    Minecraft.getInstance().gui.setScreen(null);
                    return StepResult.pass();
                });
    }

    public static Script joinScript() {
        return Script.create()
                .then("Wurzel im Tab", (ctx, ticks) -> {
                    if (!clientDone(CamAwards.ROOT)) {
                        return ticks > WAIT ? StepResult.fail("Wurzel bleibt offen: " + clientTab()) : StepResult.pending();
                    }
                    JoinProbe.line("awards root done");
                    return StepResult.pass();
                })
                .then("selbst fliegen", (ctx, ticks) -> {
                    if (PropwashClientApi.input().flying() && PropwashClientApi.input().drone().isPresent()) {
                        ctx.state().put(START_Y, PropwashClientApi.input().drone().get().getY());
                        JoinProbe.line("awards flying");
                        return StepResult.pass();
                    }
                    return ticks > WAIT ? StepResult.fail("FPV startet nicht") : StepResult.pending();
                })
                .sleep(20)
                .then("GPS-Fix abwarten", (ctx, ticks) -> {
                    boolean fix = Pilot.status().map(status -> !status.atti()).orElse(false);
                    if (fix && ticks >= 10) {
                        return StepResult.pass();
                    }
                    return ticks > 300 ? StepResult.pass("ohne GPS-Halten weiter: " + Flight.describe()) : StepResult.pending();
                })
                .then("scharf schalten", Pilot::arm)
                .then("steigen bis zum Fortschritt zu zweit", (ctx, ticks) -> {
                    if (clientDone(CamAwards.CREW_FLIGHT)) {
                        Pilot.releaseKeys();
                        JoinProbe.line("awards crew_flight done");
                        return StepResult.pass();
                    }
                    Optional<Entity> drone = PropwashClientApi.input().drone();
                    if (drone.isEmpty()) {
                        return ticks > 600 ? StepResult.fail("Drohne verloren, zu zweit offen: " + clientTab()) : StepResult.pending();
                    }
                    boolean high = drone.get().getY() - (double) ctx.state().get(START_Y) >= 3.0;
                    Pilot.steer(high ? Pilot.THROTTLE_CENTER : Flight.CLIMB_THROTTLE);
                    return ticks > 600 ? StepResult.fail("zu zweit bleibt offen: " + clientTab()) : StepResult.pending();
                })
                .then("FPV endet", (ctx, ticks) -> {
                    Pilot.releaseKeys();
                    return !PropwashClientApi.input().flying() ? StepResult.pass() : ticks > 600 ? StepResult.fail("FPV endet nicht") : StepResult.pending();
                })
                .then("Drohne mit Mission", (ctx, ticks) -> {
                    Optional<Entity> drone = missionDrone();
                    if (drone.isPresent()) {
                        ctx.state().put(CAMERA, drone.get().getId());
                        return StepResult.pass();
                    }
                    return ticks > WAIT ? StepResult.fail("keine Kamera-Drohne mit laufender Mission") : StepResult.pending();
                })
                .run("Direkt-Modus wählen", () -> CamClientConfig.get().recordingMode(RecordingMode.DIRECT))
                .then("Livebild öffnen", (ctx, ticks) -> watch(ctx, ticks, joinDrone(ctx)))
                .then("Aufnahme starten", (ctx, ticks) -> record(ctx, ticks, joinDrone(ctx)))
                .sleep(RECORD_TICKS)
                .then("Aufnahme beenden", (ctx, ticks) -> stop(ctx, ticks, joinDrone(ctx)))
                .then("Fortschritt Video", (ctx, ticks) -> {
                    if (clientDone(CamAwards.VIDEO)) {
                        JoinProbe.line("awards video done reported=" + VideoAwards.reported());
                        return StepResult.pass();
                    }
                    return ticks > 400 ? StepResult.fail("Video bleibt offen: " + clientTab()) : StepResult.pending();
                })
                .then("Fortschritt Mission", (ctx, ticks) -> {
                    if (clientDone(CamAwards.MISSION)) {
                        JoinProbe.line("awards mission done");
                        Minecraft.getInstance().setCameraEntity(Minecraft.getInstance().player);
                        return StepResult.pass();
                    }
                    return ticks > WAIT ? StepResult.fail("Mission bleibt offen: " + clientTab()) : StepResult.pending();
                })
                .then("Tab", (ctx, ticks) -> {
                    List<String> open = openInTab();
                    if (!open.isEmpty()) {
                        return ticks > 200 ? StepResult.fail("offen: " + open) : StepResult.pending();
                    }
                    JoinProbe.line("awards tab " + clientTab());
                    return StepResult.pass();
                })
                .then("Fortschritte öffnen, Tab Kamera-Drohnen", AwardSteps::openTab)
                .append(Shots.photos(SHOT + "_server"))
                .run("Fenster schließen", () -> Minecraft.getInstance().gui.setScreen(null));
    }

    private static ItemStack gear(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(CamDrones.id(path)).orElseThrow(() -> new IllegalStateException(CamDrones.id(path) + " fehlt")));
    }

    private static void expect(@Nullable ServerPlayer player, String path) {
        if (player == null) {
            throw new IllegalStateException("Spieler fehlt für " + path);
        }
        if (!AwardProbe.done(player, path)) {
            throw new IllegalStateException(path + " bei " + player.getPlainTextName() + " offen (" + AwardProbe.status(player) + ")");
        }
    }

    private static void shortMission(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        double y = drone.getY() + 4.0;
        CamFlights.mission(server, state, Mission.of(List.of(Waypoint.at(drone.getX(), y, drone.getZ() + 2.0),
                Waypoint.at(drone.getX(), y, drone.getZ() + 5.0))).withEnd(Mission.End.HOVER).withBaseSpeed(3.0));
    }

    private static void hoverMission(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        double y = drone.getY() + 6.0;
        CamFlights.mission(server, state, Mission.of(List.of(Waypoint.at(drone.getX(), y, drone.getZ() + 2.0),
                Waypoint.at(drone.getX(), y, drone.getZ() + 6.0))).withEnd(Mission.End.HOVER).withBaseSpeed(3.0));
    }

    private static void crew(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        AwardProbe.revoke(player, CamAwards.CREW_FLIGHT);
        Entity drone = Pilot.serverDrone(server, state);
        Vec3 stand = player.position().add(3.0, 0.0, 0.0);
        ServerPlayer guest = CovCamBKit.guest(server, Arena.level(server), stand);
        AwardProbe.revoke(guest, CamAwards.CREW_FLIGHT);
        CrewService.book().decline(JoinEnv.GUEST_ID);
        CrewBook.Verdict invited = CrewService.invite(player, drone, guest);
        if (!invited.ok()) {
            throw new IllegalStateException("Einladung abgelehnt: " + invited);
        }
        CrewBook.Verdict accepted = CrewService.accept(guest);
        if (!accepted.ok() || CrewService.book().operator(drone.getUUID()).filter(JoinEnv.GUEST_ID::equals).isEmpty()) {
            throw new IllegalStateException("Kameramann nicht gebunden: " + accepted);
        }
    }

    private static Optional<Entity> missionDrone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return Optional.empty();
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!PropwashApi.drones().isDrone(entity) || !CrewDrones.hasGimbal(entity) || entity.distanceTo(mc.player) > 64.0F) {
                continue;
            }
            MissionStatus status = PropwashApi.droneData().get(entity, CamDroneData.missionState());
            if (status != null && status.state() == NavLogic.MissionState.RUNNING) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    private static Optional<Entity> joinDrone(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Object id = ctx.state().get(CAMERA);
        return id instanceof Integer number && mc.level != null ? Optional.ofNullable(mc.level.getEntity(number)) : Optional.empty();
    }

    private static StepResult watch(SelfTest.StepContext ctx, int ticks, Optional<Entity> drone) {
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() == drone.get() && !PropwashClientApi.input().flying()) {
            return StepResult.pass();
        }
        if (ticks % 20 == 0) {
            PropwashClientApi.spectate(drone.get());
        }
        return ticks > 300 ? StepResult.fail("Livebild zeigt nicht die Drohne") : StepResult.pending();
    }

    private static StepResult record(SelfTest.StepContext ctx, int ticks, Optional<Entity> drone) {
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        if (ticks == 0) {
            ctx.state().put(REPORTED, VideoAwards.reported());
        }
        CameraActions.Camera camera = GimbalClient.camera(drone.get());
        if (camera.recording() || camera.start()) {
            return PropwashClientApi.recording().recording() ? StepResult.pass("Propwash nimmt das Gimbal-Bild direkt auf")
                    : StepResult.fail("Aufnahme gemeldet, aber Propwash nimmt nicht auf");
        }
        return ticks > 100 ? StepResult.fail("Aufnahme startet im Livebild nicht") : StepResult.pending();
    }

    private static StepResult stop(SelfTest.StepContext ctx, int ticks, Optional<Entity> drone) {
        if (ticks == 0) {
            drone.ifPresent(d -> GimbalClient.camera(d).stop());
        }
        if (PropwashClientApi.recording().recording()) {
            return ticks > 200 ? StepResult.fail("Aufnahme endet nicht") : StepResult.pending();
        }
        int before = (int) ctx.state().getOrDefault(REPORTED, 0);
        if (VideoAwards.reported() > before) {
            return StepResult.pass("Ende der Aufnahme an den Server gemeldet (" + (VideoAwards.reported() - before) + "×)");
        }
        return ticks > 100 ? StepResult.fail("Ende der Aufnahme nicht an den Server gemeldet") : StepResult.pending();
    }

    private static StepResult leaveView(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (PropwashClientApi.recording().recording()) {
            PropwashClientApi.recording().stop();
        }
        mc.setCameraEntity(mc.player);
        return StepResult.pass();
    }

    private static StepResult openTab(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        ClientAdvancements advancements = mc.player.connection.getAdvancements();
        AdvancementHolder root = advancements.get(CamDrones.id(CamAwards.ROOT));
        if (root == null) {
            return StepResult.fail("Tab Kamera-Drohnen fehlt");
        }
        if (ticks == 0) {
            advancements.setSelectedTab(root, false);
            mc.gui.setScreen(new AdvancementsScreen(advancements));
            return StepResult.pending();
        }
        if (!(mc.gui.screen() instanceof AdvancementsScreen)) {
            return ticks > 100 ? StepResult.fail("Fortschritte öffnen nicht") : StepResult.pending();
        }
        return ticks >= 20 ? StepResult.pass("Tab " + root.id() + " offen") : StepResult.pending();
    }

    private static Script.Stage awaitClient(String path, int maxTicks) {
        return (ctx, ticks) -> clientDone(path) ? StepResult.pass(path + " erledigt (" + clientTab() + ")")
                : ticks > maxTicks ? StepResult.fail(path + " bleibt offen: " + clientTab()) : StepResult.pending();
    }

    public static boolean clientDone(String path) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        ClientAdvancements advancements = mc.player.connection.getAdvancements();
        AdvancementHolder holder = advancements.get(CamDrones.id(path));
        AdvancementProgress progress = holder == null ? null : advancements.progress().get(holder);
        return progress != null && progress.isDone();
    }

    private static List<String> missingInTab() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return List.copyOf(CamAwards.ALL);
        }
        ClientAdvancements advancements = mc.player.connection.getAdvancements();
        Identifier root = CamDrones.id(CamAwards.ROOT);
        for (String path : CamAwards.ALL) {
            AdvancementHolder holder = advancements.get(CamDrones.id(path));
            if (holder == null) {
                out.add(path);
            } else if (!path.equals(CamAwards.ROOT) && !holder.value().parent().filter(root::equals).isPresent()) {
                out.add(path + " (nicht unter " + root + ")");
            }
        }
        return out;
    }

    private static List<String> openInTab() {
        List<String> out = new ArrayList<>(missingInTab());
        for (String path : CamAwards.ALL) {
            if (!clientDone(path) && !out.contains(path)) {
                out.add(path);
            }
        }
        return out;
    }

    public static String clientTab() {
        List<String> out = new ArrayList<>();
        for (String path : CamAwards.ALL) {
            Minecraft mc = Minecraft.getInstance();
            boolean present = mc.player != null && mc.player.connection.getAdvancements().get(CamDrones.id(path)) != null;
            out.add(path + "=" + (clientDone(path) ? "done" : present ? "open" : "missing"));
        }
        return String.join(" ", out);
    }
}
