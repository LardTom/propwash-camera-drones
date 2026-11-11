package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.autonomy.DroneSelector;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.net.StatusMessage;
import de.lardtom.camdrones.part.CamPresets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class NotReadyMessageSteps {
    public static final int NUMBER = 557;
    public static final String NAME = "mission: Ablehnung nennt Drohnennamen und Grund (leerer Akku, nicht am Boden)";
    public static final String PHOTO_BATTERY = "mission_refused_battery_empty";
    public static final String PHOTO_GROUND = "mission_refused_not_on_ground";
    public static final int BATTERY_DX = -100;
    public static final int BATTERY_DZ = -100;
    public static final int DROP_DX = -60;
    public static final int DROP_DZ = -100;
    public static final int DROP_HEIGHT = 40;
    public static final int FALL_TICKS = 4;
    public static final int VIEW_DISTANCE = 12;
    private static final String PAYLOAD = "refused.payload";
    private static final String PILLAR = "refused.pillar";
    private static final int STEP_TICKS = 20 * 120;

    private NotReadyMessageSteps() {
    }

    public static void register() {
        SelfTest.clientStep(NUMBER, NAME, STEP_TICKS, Script.create()
                .server("Werkbank aufstellen", (server, state) -> CovCamAKit.placeBench(server))
                .server("Tour mit leerem Akku bauen, abstellen und koppeln", NotReadyMessageSteps::emptyTour)
                .then("Drohne am Client", NotReadyMessageSteps::clientDrone)
                .sleep(20)
                .then("Mission starten", NotReadyMessageSteps::start)
                .then("Ablehnung mit leerem Akku nennt die Tour beim Namen", (ctx, ticks) -> refused(ctx, ticks, CamPresets.TOUR,
                        AutonomyStatus.NOT_ARMABLE, "battery_empty", List.of("battery", "akku")))
                .append(photos(PHOTO_BATTERY))
                .server("Tour abräumen", NotReadyMessageSteps::discard)
                .server("Kino auf einen Pfeiler 40 m über dem Boden setzen und koppeln", NotReadyMessageSteps::cineOnPillar)
                .then("Drohne am Client", NotReadyMessageSteps::clientDrone)
                .sleep(20)
                .server("Pfeiler entfernen, die Kino fällt", NotReadyMessageSteps::drop)
                .sleep(FALL_TICKS)
                .then("Mission im Fall starten", NotReadyMessageSteps::start)
                .then("Ablehnung im Fall nennt die Kino beim Namen und den Grund", (ctx, ticks) -> refused(ctx, ticks, CamPresets.CINE,
                        AutonomyStatus.NOT_READY, "not_on_ground", List.of("ground", "boden")))
                .append(photos(PHOTO_GROUND))
                .server("Kino abräumen", NotReadyMessageSteps::discard));
    }

    private static void emptyTour(MinecraftServer server, Map<String, Object> state) {
        ItemStack empty = CovCamAKit.buildInBench(server, CamPresets.TOUR.build(), 0.0F)
                .orElseThrow(() -> new IllegalStateException("Tour mit leerem Akku nicht gebaut"));
        CovCamAKit.closeBench(server);
        ServerPlayer player = Arena.player(server);
        Entity drone = CovCamAKit.place(player, empty, Arena.ground(BATTERY_DX, BATTERY_DZ), 0.0F);
        state.put(Pilot.DRONE, drone.getUUID());
        CamFlights.pair(server, state);
        mission(server, state, drone);
    }

    private static void cineOnPillar(MinecraftServer server, Map<String, Object> state) {
        BlockPos pillar = Arena.ground(DROP_DX, DROP_DZ).above(DROP_HEIGHT);
        Arena.place(Arena.level(server), pillar, Blocks.STONE.defaultBlockState());
        state.put(PILLAR, pillar);
        Entity drone = CovCamAKit.spawnPreset(server, state, CamPresets.CINE, pillar, 0.0F);
        CamFlights.pair(server, state);
        mission(server, state, drone);
        ServerPlayer player = Arena.player(server);
        Vec3 stand = Vec3.atBottomCenterOf(Arena.ground(DROP_DX, DROP_DZ - VIEW_DISTANCE).above());
        player.teleportTo(Arena.level(server), stand.x, stand.y, stand.z, Set.<Relative>of(), 0.0F, -60.0F, true);
    }

    private static void drop(MinecraftServer server, Map<String, Object> state) {
        Arena.place(Arena.level(server), (BlockPos) state.get(PILLAR), Blocks.AIR.defaultBlockState());
    }

    private static void mission(MinecraftServer server, Map<String, Object> state, Entity drone) {
        double y = Arena.ground().getY() + 1.0 + 6.0;
        CamFlights.mission(server, state, Mission.of(List.of(Waypoint.at(drone.getX(), y, drone.getZ() + 6.0),
                Waypoint.at(drone.getX() + 6.0, y, drone.getZ() + 6.0))).withEnd(Mission.End.HOVER).withBaseSpeed(4.0));
    }

    private static void discard(MinecraftServer server, Map<String, Object> state) {
        CamFlights.stopFlights(server);
        Pilot.serverDrone(server, state).discard();
        state.remove(PAYLOAD);
    }

    private static StepResult clientDrone(SelfTest.StepContext ctx, int ticks) {
        return CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending();
    }

    private static StepResult start(SelfTest.StepContext ctx, int ticks) {
        return CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()), MissionCommand.Command.START, 0));
    }

    static String expectedName(CamPresets.Entry entry) {
        return Component.translatable(CamDrones.MOD_ID + ".frame_short." + entry.frame()).getString();
    }

    private static StepResult refused(SelfTest.StepContext ctx, int ticks, CamPresets.Entry entry, AutonomyStatus status, String detail,
                                      List<String> words) {
        CamFlights.clientDrone(ctx.state()).ifPresent(CamFlights::lookAt);
        int mark = CamFlights.mark(ctx);
        Optional<StatusMessage.Payload> error = StatusProbe.since(mark).stream().filter(p -> p.status().error()).findFirst();
        if (error.isEmpty()) {
            return ticks > 200 ? StepResult.fail("keine Ablehnung (" + StatusProbe.describe(mark) + ")") : StepResult.pending();
        }
        StatusMessage.Payload payload = error.get();
        ctx.state().put(PAYLOAD, payload);
        String text = AutonomyServer.text(payload.status(), payload.drone(), payload.detail()).getString();
        String lower = text.toLowerCase(Locale.ROOT);
        String name = expectedName(entry);
        String serial = DroneSelector.shortSerial(CamFlights.serial(ctx.state()));
        String line = "Status " + payload.status().name() + ", Grund \"" + payload.detail() + "\": \"" + text + "\"";
        List<String> problems = new ArrayList<>();
        if (payload.status() != status || !payload.detail().equals(detail)) {
            problems.add("erwartet " + status.name() + " mit Grund \"" + detail + "\"");
        }
        if (!text.contains(name)) {
            problems.add("Drohnenname „" + name + "“ fehlt");
        }
        if (text.contains(serial)) {
            problems.add("Meldung nennt die ID " + serial);
        }
        if (words.stream().noneMatch(lower::contains)) {
            problems.add("Grund fehlt im Text");
        }
        return problems.isEmpty() ? StepResult.pass(line) : StepResult.fail(String.join(", ", problems) + " – " + line);
    }

    private static Script photos(String name) {
        if (!Shots.enabled()) {
            return Script.create();
        }
        return Script.create()
                .then("Bildschirmfoto " + name, (ctx, ticks) -> photo(ctx, ticks, name))
                .run("Sprache Deutsch", () -> Shots.language(Minecraft.getInstance(), Shots.GERMAN))
                .then("Bildschirmfoto " + name + "_de", (ctx, ticks) -> photo(ctx, ticks, name + "_de"))
                .run("Sprache zurück", () -> Shots.restoreLanguage(Minecraft.getInstance()));
    }

    private static StepResult photo(SelfTest.StepContext ctx, int ticks, String name) {
        Minecraft mc = Minecraft.getInstance();
        CamFlights.clientDrone(ctx.state()).ifPresent(CamFlights::lookAt);
        if (ticks == 0) {
            Shots.clearOverlays(mc);
            return show(mc, ctx.state()) ? StepResult.pending() : StepResult.fail("keine Meldung für das Bildschirmfoto");
        }
        return Shots.shot(ctx, ticks, name);
    }

    private static boolean show(Minecraft mc, Map<String, Object> state) {
        LocalPlayer player = mc.player;
        if (player == null || !(state.get(PAYLOAD) instanceof StatusMessage.Payload payload)) {
            return false;
        }
        Component text = AutonomyServer.text(payload.status(), payload.drone(), payload.detail());
        if (AutonomyStatus.overlay(payload.status(), payload.detail())) {
            player.sendOverlayMessage(text);
        }
        if (payload.status().chat()) {
            player.sendSystemMessage(text);
        }
        return true;
    }
}
