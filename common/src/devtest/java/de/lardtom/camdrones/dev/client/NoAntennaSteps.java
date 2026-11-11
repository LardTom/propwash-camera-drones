package de.lardtom.camdrones.dev.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.part.GimbalTable;
import de.lardtom.camdrones.platform.Services;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.AnchorSpec;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.part.VideoAntenna;
import de.lardtom.propwash.api.part.VideoDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class NoAntennaSteps {
    public static final int NUMBER = 571;
    public static final String NAME = "teile: Kamera-Drohnen ohne Antennen, Werfer vorne bündig am Rahmen";
    public static final String DIR = "rc3c2";
    public static final Identifier MOB_DEFENSE_DISPENSER = Identifier.fromNamespaceAndPath("mobdefense", "dispenser_mount");
    public static final Identifier FREESTYLE_FRAME = Identifier.fromNamespaceAndPath("propwash", "freestyle_5");
    public static final Identifier PAINT = Identifier.fromNamespaceAndPath(CamDrones.MOD_ID, "drone_paint.json");
    private static final String ANTENNA = "antenna";
    private static final int DX = -90;
    private static final int DZ = 120;
    private static final int SPACING = 2;
    private static final int PILLAR = 2;
    private static final double EYE_ABOVE = 0.03;
    private static final double[] DISTANCE = {0.3, 0.42, 0.75, 0.3};
    private static final double NOSE_DISTANCE = 0.22;
    private static final double NOSE_AHEAD = 0.06;
    private static final int STEP_TICKS = 20 * 150;
    private static final int SETTLE_TICKS = 25;
    private static final String DRONES = "noantenna.drones";
    private static final String LABELS = "noantenna.labels";
    private static final String EYE = "noantenna.eye";
    private static final String TARGET = "noantenna.target";
    private static final String SHOTS = "noantenna.shots";
    private static final String PROBLEMS = "noantenna.problems";
    private static final String LOWEST = "noantenna.lowest";
    private static boolean hidHud;

    private NoAntennaSteps() {
    }

    private record Drone(String label, DroneData data) {
    }

    public static void register() {
        ExampleDropper.install();
        Script script = Script.create()
                .server("Kamera-Drohnen Kompakt, Tour und Kino sowie freestyle_5 mit Kamera-Funk, alle mit Werfer vorne, auf Säulen setzen",
                        NoAntennaSteps::place)
                .then("vier Drohnen am Client, Werfer vorne", NoAntennaSteps::arrived)
                .append(Shots.photoWindow())
                .run("Anzeige aus", NoAntennaSteps::hideHud);
        for (int i = 0; i < DISTANCE.length; i++) {
            int index = i;
            script.server("Blick von der Seite auf Drohne " + (i + 1), (server, state) -> side(server, state, index))
                    .then("zielen", NoAntennaSteps::aim)
                    .sleep(SETTLE_TICKS)
                    .then("Bild Drohne " + (i + 1), (ctx, ticks) -> shot(ctx, ticks, "no_antenna_" + label(ctx, index)));
            if (i < CamPresets.ALL.size()) {
                script.server("Blick auf die Nase von Drohne " + (i + 1), (server, state) -> nose(server, state, index))
                        .then("zielen", NoAntennaSteps::aim)
                        .sleep(SETTLE_TICKS)
                        .then("Bild Nase mit Werfer, Drohne " + (i + 1), (ctx, ticks) -> shot(ctx, ticks, "front_gap_" + label(ctx, index)));
            }
        }
        script.server("Blick auf alle vier", NoAntennaSteps::overview)
                .then("zielen", NoAntennaSteps::aim)
                .sleep(SETTLE_TICKS)
                .then("Bild aller vier Drohnen", (ctx, ticks) -> shot(ctx, ticks, "no_antenna_all"))
                .then("Funkstrecken ohne Antennen", (ctx, ticks) -> antennas(ctx))
                .then("kein Lack-Kanal antenna", (ctx, ticks) -> paint(ctx))
                .then("Werfer und Gimbals vorne bündig an der Nase", (ctx, ticks) -> fit(ctx))
                .then("Bewertung", (ctx, ticks) -> verdict(ctx));
        SelfTest.clientStep(NUMBER, NAME, STEP_TICKS, script, NoAntennaSteps::cleanup);
    }

    public static Identifier dispenser() {
        Optional<PartDefinition> mobDefense = PropwashApi.parts().get(MOB_DEFENSE_DISPENSER);
        if (mobDefense.isPresent() && mobDefense.get() instanceof AccessoryDefinition accessory && accessory.slot() == AccessorySlot.FRONT) {
            return MOB_DEFENSE_DISPENSER;
        }
        return ExampleDropper.ID;
    }

    private static List<Drone> drones() {
        Identifier dispenser = dispenser();
        List<Drone> out = new ArrayList<>();
        for (CamPresets.Entry entry : CamPresets.ALL) {
            DroneData data = Pilot.preset(entry).data();
            out.add(new Drone(entry.path(), withBuild(data, data.build().withAccessory(AccessorySlot.FRONT, dispenser))));
        }
        DroneData freestyle = null;
        for (DronePreset preset : PropwashApi.builds().presets()) {
            if (preset.data().build().frame().equals(FREESTYLE_FRAME)) {
                freestyle = preset.data();
                break;
            }
        }
        if (freestyle == null) {
            throw new IllegalStateException("kein Propwash-Preset mit " + FREESTYLE_FRAME);
        }
        BuildSpec free = freestyle.build().withPart(PartKind.VIDEO, CamPresets.part(CamPresets.COMPACT.video()))
                .withAccessory(AccessorySlot.FRONT, dispenser);
        out.add(new Drone("freestyle_5_cam_link", withBuild(freestyle, free)));
        return out;
    }

    private static DroneData withBuild(DroneData data, BuildSpec build) {
        return new DroneData(build, data.paint(), data.tune(), data.name());
    }

    private static void place(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        List<Drone> drones = drones();
        List<UUID> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < drones.size(); i++) {
            Drone drone = drones.get(i);
            BuildCheck check = PropwashApi.builds().check(drone.data().build());
            if (!check.buildable()) {
                throw new IllegalStateException(drone.label() + " mit Werfer vorne nicht baubar: " + check.problems() + " " + check.unknownParts());
            }
            BlockPos ground = Arena.ground(DX + i * SPACING, DZ);
            for (int h = 1; h <= PILLAR; h++) {
                Arena.place(level, ground.above(h), Blocks.STONE.defaultBlockState());
            }
            BlockPos top = ground.above(PILLAR);
            Vec3 stand = Vec3.atBottomCenterOf(ground.above()).add(0.0, 0.0, 2.5);
            player.teleportTo(level, stand.x, stand.y, stand.z, Set.<Relative>of(), -90.0F, 0.0F, true);
            ItemStack item = PropwashApi.builds().createDrone(drone.data());
            player.setItemInHand(InteractionHand.MAIN_HAND, item);
            Set<UUID> before = new HashSet<>();
            for (Entity existing : Arena.drones(level)) {
                before.add(existing.getUUID());
            }
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(top).add(0.0, 0.5, 0.0), Direction.UP, top, false);
            InteractionResult placed = item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            Entity spawned = null;
            for (Entity candidate : Arena.drones(level)) {
                if (!before.contains(candidate.getUUID())) {
                    spawned = candidate;
                }
            }
            if (spawned == null) {
                throw new IllegalStateException("Drohne " + drone.label() + " nicht gesetzt: " + placed);
            }
            ids.add(spawned.getUUID());
            labels.add(drone.label());
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        state.put(DRONES, List.copyOf(ids));
        state.put(LABELS, List.copyOf(labels));
    }

    @SuppressWarnings("unchecked")
    private static String label(SelfTest.StepContext ctx, int index) {
        List<String> labels = (List<String>) ctx.state().getOrDefault(LABELS, List.of());
        return index < labels.size() ? labels.get(index) : "drohne_" + (index + 1);
    }

    @SuppressWarnings("unchecked")
    private static List<UUID> ids(Map<String, Object> state) {
        return (List<UUID>) state.getOrDefault(DRONES, List.of());
    }

    private static Optional<Entity> clientDrone(Map<String, Object> state, int index) {
        List<UUID> ids = ids(state);
        if (index >= ids.size()) {
            return Optional.empty();
        }
        for (Entity entity : Arena.clientDrones(Minecraft.getInstance())) {
            if (entity.getUUID().equals(ids.get(index))) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    private static StepResult arrived(SelfTest.StepContext ctx, int ticks) {
        List<UUID> ids = ids(ctx.state());
        Identifier dispenser = dispenser();
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Optional<Entity> drone = clientDrone(ctx.state(), i);
            if (drone.isEmpty()) {
                missing.add(label(ctx, i) + " fehlt am Client");
                continue;
            }
            Optional<Identifier> front = PropwashApi.drones().view(drone.get()).flatMap(view -> view.accessory(AccessorySlot.FRONT));
            if (front.isEmpty() || !front.get().equals(dispenser)) {
                missing.add(label(ctx, i) + ": vorne " + front.map(Identifier::toString).orElse("nichts") + " statt " + dispenser);
            }
        }
        if (ids.size() == DISTANCE.length && missing.isEmpty()) {
            ctx.state().put(LOWEST, lowest(dispenser));
            return StepResult.pass(ids.size() + " Drohnen mit " + dispenser + " vorne");
        }
        return ticks < 200 ? StepResult.pending() : StepResult.fail(String.join("; ", missing));
    }

    private static Vec3 forward(Entity drone) {
        Vec3 forward = PropwashApi.drones().view(drone).map(DroneView::forward).orElseGet(drone::getLookAngle);
        Vec3 flat = new Vec3(forward.x, 0.0, forward.z);
        return flat.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, -1.0) : flat.normalize();
    }

    private static Vec3 right(Vec3 forward) {
        return new Vec3(-forward.z, 0.0, forward.x);
    }

    private static List<Double> lowest(Identifier dispenser) {
        double height = PropwashApi.parts().get(dispenser).orElse(null) instanceof AccessoryDefinition accessory ? accessory.heightMm() : 0.0;
        List<Double> out = new ArrayList<>();
        for (Drone drone : drones()) {
            Identifier frameId = drone.data().build().frame();
            double low = 0.0;
            for (Vector3d[] corners : FrontFitProbe.frameSolids(Minecraft.getInstance().getResourceManager(), frameId)) {
                for (Vector3d corner : corners) {
                    low = Math.min(low, corner.y);
                }
            }
            if (PropwashApi.parts().get(frameId).orElse(null) instanceof FrameDefinition frame
                    && frame.geometry().accessoryAnchors().get(AccessorySlot.FRONT) instanceof AnchorSpec anchor) {
                low = Math.min(low, anchor.y() - height);
            }
            out.add(low);
        }
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    private static Vec3 frameOrigin(Map<String, Object> state, Entity drone, int index) {
        List<Double> lowest = (List<Double>) state.getOrDefault(LOWEST, List.of());
        double top = Arena.ground(DX + index * SPACING, DZ).getY() + PILLAR + 1.0;
        double lift = index < lowest.size() ? -lowest.get(index) / 1000.0 : drone.getBbHeight() / 2.0;
        return new Vec3(drone.getX(), top + lift, drone.getZ());
    }

    private static void side(MinecraftServer server, Map<String, Object> state, int index) {
        Entity drone = serverDrone(server, state, index);
        Vec3 target = frameOrigin(state, drone, index);
        Vec3 eye = target.add(right(forward(drone)).scale(DISTANCE[index])).add(0.0, EYE_ABOVE, 0.0);
        view(server, state, eye, target);
    }

    private static void nose(MinecraftServer server, Map<String, Object> state, int index) {
        Entity drone = serverDrone(server, state, index);
        CamPresets.Entry entry = CamPresets.ALL.get(index);
        if (!(PropwashApi.parts().get(CamPresets.part(entry.frame())).orElse(null) instanceof FrameDefinition frame)) {
            throw new IllegalStateException(entry.frame() + " ist kein angemeldeter Rahmen");
        }
        AnchorSpec anchor = frame.geometry().accessoryAnchors().get(AccessorySlot.FRONT);
        if (anchor == null) {
            throw new IllegalStateException(entry.frame() + " ohne Anker vorne");
        }
        Vec3 forward = forward(drone);
        Vec3 origin = frameOrigin(state, drone, index);
        Vec3 target = origin.add(forward.scale(-anchor.z() / 1000.0)).add(0.0, anchor.y() / 1000.0, 0.0);
        Vec3 eye = target.add(right(forward).scale(NOSE_DISTANCE)).add(forward.scale(NOSE_AHEAD));
        view(server, state, eye, target);
    }

    private static void overview(MinecraftServer server, Map<String, Object> state) {
        Entity first = serverDrone(server, state, 0);
        Entity last = serverDrone(server, state, DISTANCE.length - 1);
        Vec3 target = frameOrigin(state, first, 0).add(frameOrigin(state, last, DISTANCE.length - 1)).scale(0.5);
        Vec3 eye = target.add(0.0, 0.8, 2.3);
        view(server, state, eye, target);
    }

    private static Entity serverDrone(MinecraftServer server, Map<String, Object> state, int index) {
        List<UUID> ids = ids(state);
        Entity drone = index < ids.size() ? Arena.level(server).getEntity(ids.get(index)) : null;
        if (drone == null) {
            throw new IllegalStateException("Drohne " + (index + 1) + " fehlt am Server");
        }
        return drone;
    }

    private static void view(MinecraftServer server, Map<String, Object> state, Vec3 eye, Vec3 target) {
        ServerPlayer player = Arena.player(server);
        player.setGameMode(GameType.SPECTATOR);
        Vec3 look = target.subtract(eye);
        float yaw = (float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        player.teleportTo(player.level(), eye.x, eye.y - player.getEyeHeight(), eye.z, Set.<Relative>of(), yaw, pitch, true);
        player.setDeltaMovement(Vec3.ZERO);
        state.put(EYE, eye);
        state.put(TARGET, target);
    }

    private static StepResult aim(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(ctx.state().get(EYE) instanceof Vec3 eye) || !(ctx.state().get(TARGET) instanceof Vec3 target)) {
            return StepResult.fail("kein Spieler oder kein Blickpunkt");
        }
        if (mc.player.getEyePosition().distanceTo(eye) > 0.05) {
            return ticks < 100 ? StepResult.pending() : StepResult.fail("Spieler erreicht den Blickpunkt nicht: "
                    + CamFlights.format(mc.player.getEyePosition()) + " statt " + CamFlights.format(eye));
        }
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
        return StepResult.pass();
    }

    private static void hideHud() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
            hidHud = true;
        }
    }

    private static void showHud(Minecraft mc) {
        if (hidHud) {
            hidHud = false;
            if (mc.gui.hud.isHidden()) {
                mc.gui.hud.toggle();
            }
        }
    }

    public static Path out() {
        return Services.PLATFORM.gameDir().resolve(DIR);
    }

    @SuppressWarnings("unchecked")
    private static StepResult shot(SelfTest.StepContext ctx, int ticks, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            Shots.clearOverlays(mc);
            Shots.parkMouse(mc);
        }
        List<CompletableFuture<String>> pending = (List<CompletableFuture<String>>) ctx.state().get(SHOTS);
        if (pending == null) {
            pending = new ArrayList<>();
            pending.add(Shots.capture(out().resolve(name + ".png")));
            if (Shots.enabled()) {
                pending.add(Shots.capture("cam_" + name));
            }
            ctx.state().put(SHOTS, pending);
            return StepResult.pending();
        }
        for (CompletableFuture<String> future : pending) {
            if (!future.isDone()) {
                return ticks > 400 ? StepResult.fail("Prüfbild " + name + " hängt") : StepResult.pending();
            }
            if (future.isCompletedExceptionally()) {
                ctx.state().remove(SHOTS);
                return StepResult.fail("Prüfbild " + name + ": " + future.exceptionNow());
            }
        }
        ctx.state().remove(SHOTS);
        return StepResult.pass("Prüfbild " + out().resolve(name + ".png") + " (" + pending.getFirst().join() + ")");
    }

    @SuppressWarnings("unchecked")
    private static void problem(SelfTest.StepContext ctx, String text) {
        ((List<String>) ctx.state().computeIfAbsent(PROBLEMS, key -> new ArrayList<String>())).add(text);
    }

    private static String antenna(VideoDefinition video) {
        return video.antenna().map(a -> a.isNone() ? "keine" : a.count() + " × " + a.lengthMm() + " mm " + a.cap()).orElse("abgeleitet");
    }

    private static StepResult antennas(SelfTest.StepContext ctx) {
        Set<Identifier> links = new TreeSet<>();
        for (Drone drone : drones()) {
            links.add(drone.data().build().video());
        }
        List<String> seen = new ArrayList<>();
        for (Identifier link : links) {
            Optional<PartDefinition> part = PropwashApi.parts().get(link);
            if (part.isEmpty() || !(part.get() instanceof VideoDefinition video)) {
                problem(ctx, link + " ist kein angemeldetes Videoteil");
                continue;
            }
            seen.add(link.getPath() + ": " + antenna(video));
            if (!video.antenna().map(VideoAntenna::isNone).orElse(false)) {
                problem(ctx, link + ": Propwash zeichnet Antennen (" + antenna(video) + ")");
            }
        }
        return StepResult.pass("Antennen " + String.join(", ", seen));
    }

    private static StepResult paint(SelfTest.StepContext ctx) {
        JsonObject paint = FrontFitProbe.json(Minecraft.getInstance().getResourceManager(), PAINT);
        int layers = 0;
        for (Map.Entry<String, JsonElement> model : paint.getAsJsonObject("models").entrySet()) {
            for (JsonElement group : model.getValue().getAsJsonArray()) {
                layers++;
                if (ANTENNA.equals(group.getAsJsonObject().get("channel").getAsString())) {
                    problem(ctx, PAINT + ": " + model.getKey() + " mit Lack-Ebene " + group);
                }
            }
        }
        return StepResult.pass(layers + " Lack-Ebenen in " + PAINT + ", keine im Kanal " + ANTENNA);
    }

    private static StepResult fit(SelfTest.StepContext ctx) {
        List<AccessoryDefinition> fronts = new ArrayList<>();
        for (Identifier id : List.of(dispenser(), GimbalTable.COMPACT, GimbalTable.TOUR, GimbalTable.CINE)) {
            if (PropwashApi.parts().get(id).orElse(null) instanceof AccessoryDefinition accessory) {
                fronts.add(accessory);
            } else {
                problem(ctx, id + " ist kein angemeldetes Zubehör");
            }
        }
        List<String> worst = new ArrayList<>();
        for (CamPresets.Entry entry : CamPresets.ALL) {
            Identifier frameId = CamPresets.part(entry.frame());
            if (!(PropwashApi.parts().get(frameId).orElse(null) instanceof FrameDefinition frame)) {
                problem(ctx, frameId + " ist kein angemeldeter Rahmen");
                continue;
            }
            AnchorSpec anchor = frame.geometry().accessoryAnchors().get(AccessorySlot.FRONT);
            if (anchor == null) {
                problem(ctx, frameId + " ohne Anker vorne");
                continue;
            }
            List<Vector3d[]> solids = FrontFitProbe.frameSolids(Minecraft.getInstance().getResourceManager(), frameId);
            double gap = 0.0;
            double overlap = 0.0;
            for (AccessoryDefinition accessory : fronts) {
                FrontFitProbe.Fit measured = FrontFitProbe.measure(solids, anchor, accessory.widthMm(), accessory.heightMm(), accessory.depthMm());
                gap = Math.max(gap, measured.gapMm());
                overlap = Math.max(overlap, measured.overlapMm());
                if (!measured.ok()) {
                    problem(ctx, String.format(Locale.ROOT, "%s + %s: %s, Anker (%.1f; %.1f; %.1f)", frameId.getPath(), accessory.id(),
                            measured.describe(), anchor.x(), anchor.y(), anchor.z()));
                }
            }
            worst.add(String.format(Locale.ROOT, "%s höchstens %.1f mm Abstand, %.1f mm Überschneidung, Anker (%.1f; %.1f; %.1f)", frameId.getPath(),
                    gap, overlap, anchor.x(), anchor.y(), anchor.z()));
        }
        return StepResult.pass(String.join("; ", worst));
    }

    @SuppressWarnings("unchecked")
    private static StepResult verdict(SelfTest.StepContext ctx) {
        List<String> problems = (List<String>) ctx.state().getOrDefault(PROBLEMS, List.of());
        return problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        showHud(mc);
        if (ctx.tick() == 0) {
            ctx.onServer(server -> Arena.player(server).setGameMode(GameType.CREATIVE));
        }
        return ctx.tick() < 5 ? StepResult.pending() : StepResult.pass();
    }
}
