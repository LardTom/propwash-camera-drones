package de.lardtom.camdrones.client.planner;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.crew.client.CrewClient;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.logic.mission.MissionCodec;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.ExitSettingUpload;
import de.lardtom.camdrones.net.MissionUpload;
import de.lardtom.camdrones.net.PlannerSave;
import de.lardtom.camdrones.net.ViewSettingUpload;
import de.lardtom.camdrones.planner.PlannerHooks;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.PlannerLink;
import de.lardtom.camdrones.planner.PlannerMissions;
import de.lardtom.camdrones.planner.PlannerSettings;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.model.ExitSetting;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.FlightRecorder;
import de.lardtom.camdrones.planner.model.TabletState;
import de.lardtom.camdrones.planner.model.ViewSetting;
import de.lardtom.camdrones.planner.model.WaypointPlacement;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PlannerClient {
    public static final long PENDING_MILLIS = 2500L;

    private static final FlightRecorder RECORDER = new FlightRecorder();
    private static boolean initialized;
    private static volatile MissionCommands commands = MissionCommands.NONE;
    private static int nextRequest = 1;
    private static int pendingSlot = -1;
    private static TabletState pendingState;
    private static long pendingAt;
    private static PlannerScreen openScreen;
    private static final Map<Integer, Consumer<ExitSettingUpload.Reply>> EXIT_REPLIES = new ConcurrentHashMap<>();
    private static final Map<Integer, Consumer<ViewSettingUpload.Reply>> VIEW_REPLIES = new ConcurrentHashMap<>();
    private static final Map<UUID, FollowController.Style> FOLLOW_STYLES = new ConcurrentHashMap<>();

    private PlannerClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PlannerHooks.client(new Hooks());
        CamNetwork.onClient(MissionUpload.REPLY, PlannerClient::onReply);
        CamNetwork.onClient(ExitSettingUpload.REPLY, PlannerClient::onExitReply);
        CamNetwork.onClient(ViewSettingUpload.REPLY, PlannerClient::onViewReply);
        PlannerPilotContext.install();
        PropwashClientApi.registerConfigTab(CamSettingsTab::new);
        PropwashClientApi.registerServiceExtension(new MissionService());
    }

    public static void commands(MissionCommands value) {
        commands = Objects.requireNonNull(value, "commands");
    }

    public static MissionCommands commands() {
        return commands;
    }

    public static FlightRecorder recorder() {
        return RECORDER;
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            if (RECORDER.active()) {
                RECORDER.stop();
            }
            return;
        }
        if (!PlannerItems.registered()) {
            return;
        }
        WaypointPreview.tick(player);
        tickRecorder(player);
    }

    private static void tickRecorder(LocalPlayer player) {
        if (!RECORDER.active() || !PropwashClientApi.input().flying()) {
            return;
        }
        Optional<Entity> drone = PropwashClientApi.input().drone();
        if (drone.isEmpty()) {
            return;
        }
        Entity entity = drone.get();
        RECORDER.sample(entity.getX(), entity.getY(), entity.getZ()).ifPresent(point -> {
            if (!addToTablet(player, WaypointPlacement.recorded(point.x(), point.y(), point.z()), "record.added")) {
                RECORDER.stop();
            }
        });
    }

    public static void flightWaypoint(double x, double y, double z, double heading, double commandedTilt) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            LocalPlayer player = minecraft.player;
            if (player != null && PlannerItems.registered()) {
                double tilt = WaypointPlacement.flightTilt(gimbalTilt(), commandedTilt);
                addToTablet(player, WaypointPlacement.inFlight(x, y, z, heading, tilt), "flight.added");
            }
        });
    }

    private static double gimbalTilt() {
        return PropwashClientApi.input().drone().map(GimbalClient::tilt).orElse(Double.NaN);
    }

    static boolean addToTablet(LocalPlayer player, Waypoint waypoint, String messageKey) {
        OptionalInt slot = tabletSlot(player);
        if (slot.isEmpty()) {
            player.sendOverlayMessage(Component.translatable(FieldText.PREFIX + "flight.no_tablet").withStyle(ChatFormatting.RED));
            return false;
        }
        ItemStack stack = player.getInventory().getItem(slot.getAsInt());
        TabletState.PlacementResult result = tabletState(player, stack, slot.getAsInt()).addWaypoint(waypoint);
        if (!result.changed()) {
            player.sendOverlayMessage(Component.translatable(FieldText.PREFIX + (result.outcome() == TabletState.PlacementResult.Outcome.FULL
                    ? "placed.full" : "placed.locked")).withStyle(ChatFormatting.RED));
            return false;
        }
        if (!save(slot.getAsInt(), result.state())) {
            player.sendOverlayMessage(Component.translatable(FieldText.PREFIX + "status.offline").withStyle(ChatFormatting.RED));
            return false;
        }
        player.sendOverlayMessage(Component.translatable(FieldText.PREFIX + messageKey, result.index() + 1, result.state().activeName()));
        return true;
    }

    public static OptionalInt tabletSlot(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        Optional<Entity> drone = PropwashClientApi.input().drone();
        if (drone.isPresent()) {
            UUID serial = PlannerTablets.serialOf(drone.get());
            if (!CamDroneData.NO_SERIAL.equals(serial)) {
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    Optional<PlannerLink> link = PlannerTablets.link(inventory.getItem(i));
                    if (link.isPresent() && link.get().serial().equals(serial)) {
                        return OptionalInt.of(i);
                    }
                }
            }
        }
        if (PlannerItems.isPlanner(inventory.getItem(inventory.getSelectedSlot()))) {
            return OptionalInt.of(inventory.getSelectedSlot());
        }
        if (PlannerItems.isPlanner(inventory.getItem(Inventory.SLOT_OFFHAND))) {
            return OptionalInt.of(Inventory.SLOT_OFFHAND);
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (PlannerItems.isPlanner(inventory.getItem(i))) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    public static int slotFor(LocalPlayer player, InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().getSelectedSlot();
    }

    public static TabletState tabletState(LocalPlayer player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i) == stack) {
                return tabletState(player, stack, i);
            }
        }
        return PlannerTablets.read(stack);
    }

    static synchronized TabletState tabletState(LocalPlayer player, ItemStack stack, int slot) {
        if (pendingState != null && pendingSlot == slot && Util.getMillis() - pendingAt < PENDING_MILLIS) {
            TabletState current = PlannerTablets.read(stack);
            if (!current.equals(pendingState)) {
                return pendingState;
            }
            pendingState = null;
            pendingSlot = -1;
        }
        return PlannerTablets.read(stack);
    }

    public static synchronized boolean save(int slot, TabletState state) {
        boolean sent = CamNetwork.sendToServer(new PlannerSave.Request(slot, PlannerMissions.of(state), PlannerSettings.of(state)));
        if (sent) {
            pendingSlot = slot;
            pendingState = state;
            pendingAt = Util.getMillis();
        }
        return sent;
    }

    public static synchronized OptionalInt upload(PlannerLink link, Mission mission) {
        int id = nextRequest++;
        boolean sent = CamNetwork.sendToServer(new MissionUpload.Request(id, link.serial(), MissionCodec.encode(mission)));
        return sent ? OptionalInt.of(id) : OptionalInt.empty();
    }

    public static synchronized OptionalInt sendExit(UUID serial, ExitSetting exit, Consumer<ExitSettingUpload.Reply> onReply) {
        int id = nextRequest++;
        EXIT_REPLIES.put(id, Objects.requireNonNull(onReply, "onReply"));
        if (!CamNetwork.sendToServer(ExitSettingUpload.Request.of(id, serial, exit))) {
            EXIT_REPLIES.remove(id);
            return OptionalInt.empty();
        }
        return OptionalInt.of(id);
    }

    static void onExitReply(ExitSettingUpload.Reply reply) {
        Consumer<ExitSettingUpload.Reply> handler = EXIT_REPLIES.remove(reply.requestId());
        if (handler != null) {
            handler.accept(reply);
        }
    }

    public static synchronized OptionalInt sendView(UUID serial, ViewSetting view, Consumer<ViewSettingUpload.Reply> onReply) {
        int id = nextRequest++;
        VIEW_REPLIES.put(id, Objects.requireNonNull(onReply, "onReply"));
        if (!CamNetwork.sendToServer(ViewSettingUpload.Request.of(id, serial, view))) {
            VIEW_REPLIES.remove(id);
            return OptionalInt.empty();
        }
        return OptionalInt.of(id);
    }

    static void onViewReply(ViewSettingUpload.Reply reply) {
        Consumer<ViewSettingUpload.Reply> handler = VIEW_REPLIES.remove(reply.requestId());
        if (handler != null) {
            handler.accept(reply);
        }
    }

    public static void followStyle(UUID serial, FollowController.Style style) {
        if (serial != null && style != null && !CamDroneData.NO_SERIAL.equals(serial)) {
            FOLLOW_STYLES.put(serial, style);
        }
    }

    public static Optional<FollowController.Style> followStyle(UUID serial) {
        return serial == null ? Optional.empty() : Optional.ofNullable(FOLLOW_STYLES.get(serial));
    }

    public static Optional<UUID> flownSerial() {
        Optional<Entity> drone = PropwashClientApi.input().drone();
        if (drone.isEmpty() || !CamDroneData.registered()) {
            return Optional.empty();
        }
        UUID serial = PlannerTablets.serialOf(drone.get());
        return CamDroneData.NO_SERIAL.equals(serial) ? Optional.empty() : Optional.of(serial);
    }

    public static boolean flies(UUID serial) {
        return serial != null && PropwashClientApi.input().flying() && flownSerial().filter(serial::equals).isPresent();
    }

    public static boolean liveView(Optional<Entity> drone) {
        return drone.isPresent() && !PropwashClientApi.input().flying() && CrewClient.cameraView(drone.get());
    }

    static void onReply(MissionUpload.Reply reply) {
        PlannerScreen screen = openScreen;
        if (screen != null && screen.onUploadReply(reply)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            Component text = Component.translatable(reply.result().messageKey(), reply.waypoint() + 1);
            player.sendOverlayMessage(reply.result().ok() ? text : text.copy().withStyle(ChatFormatting.RED));
        }
    }

    static void opened(PlannerScreen screen) {
        openScreen = screen;
    }

    static void closed(PlannerScreen screen) {
        if (openScreen == screen) {
            openScreen = null;
        }
    }

    public static Optional<Entity> linkedDrone(Optional<PlannerLink> link) {
        Optional<Entity> flown = PropwashClientApi.input().drone();
        if (flown.isPresent()) {
            return flown;
        }
        return pairedDrone(link);
    }

    public static Optional<Entity> pairedDrone(Optional<PlannerLink> link) {
        Minecraft minecraft = Minecraft.getInstance();
        if (link.isEmpty() || minecraft.level == null || !CamDroneData.registered()) {
            return Optional.empty();
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (PropwashApi.drones().isDrone(entity) && link.get().serial().equals(PlannerTablets.serialOf(entity))) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    static double groundBelow(Entity reference) {
        if (reference.onGround()) {
            return reference.getY();
        }
        int ground = reference.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(reference.getX()),
                (int) Math.floor(reference.getZ()));
        return Math.min(reference.getY(), ground);
    }

    static void openEditor(InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        int slot = slotFor(player, hand);
        ItemStack stack = player.getInventory().getItem(slot);
        if (!PlannerItems.isPlanner(stack)) {
            return;
        }
        Optional<PlannerLink> link = PlannerTablets.link(stack);
        Optional<Entity> drone = linkedDrone(link);
        FlightClass flightClass = link.flatMap(PlannerLink::flightClassValue).orElse(FlightClass.TOUR);
        Entity reference = drone.orElse(player);
        PlannerScreen screen = new PlannerScreen(slot, tabletState(player, stack, slot), link, flightClass, reference.getX(), groundBelow(reference),
                reference.getZ(), player.getYRot());
        minecraft.gui.setScreen(screen);
        CamDrones.LOG.debug("{}: Flugplaner geöffnet (Platz {})", CamDrones.MOD_NAME, slot);
    }

    private static final class Hooks implements PlannerHooks.Client {
        @Override
        public boolean aimsAtDrone() {
            HitResult hit = Minecraft.getInstance().hitResult;
            return hit instanceof EntityHitResult entityHit && PropwashApi.drones().isDrone(entityHit.getEntity());
        }

        @Override
        public boolean aimsAtMarker(ItemStack stack) {
            LocalPlayer player = Minecraft.getInstance().player;
            return player != null && PlannerTablets.aimedMarker(player, stack).isPresent();
        }

        @Override
        public void openEditor(InteractionHand hand) {
            PlannerClient.openEditor(hand);
        }
    }
}
