package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.award.CamAwards;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.integration.NavTarget;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.logic.nav.OrbitController;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.FollowConsent;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.net.MissionProgress;
import de.lardtom.camdrones.net.SmartMode;
import de.lardtom.camdrones.net.StatusMessage;
import de.lardtom.camdrones.planner.PlannerOwners;
import de.lardtom.camdrones.planner.PlannerSupport;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.model.RateLimiter;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.drone.AutonomyAccess;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.AutonomyNotReady;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyRequest;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.flight.SensorDirection;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AutonomyServer {
    public static final Identifier EXIT_HANDLER_ID = CamDrones.id("autonomy");
    public static final long STALE_MILLIS = 5_000L;
    public static final long UNCONFIRMED_MILLIS = 3_000L;
    public static final int NOTICE_TICKS = 40;
    public static final double TICKS_PER_SECOND = 20.0;
    public static final double MAX_POI_DISTANCE = OrbitController.MAX_RADIUS + 16.0;
    public static final String CONSENT_KEY = CamDrones.MOD_ID + ".autonomy.consent.";
    public static final String FOLLOWED_KEY = CamDrones.MOD_ID + ".autonomy.followed";
    public static final double AIRBORNE_CLEARANCE = 0.2;

    private static final RateLimiter COMMAND_RATE = new RateLimiter(MissionCommand.RATE_PER_SECOND);
    private static final RateLimiter PROGRESS_RATE = new RateLimiter(20.0, 40.0);
    private static final RateLimiter SMART_RATE = new RateLimiter(SmartMode.RATE_PER_SECOND, 10.0);
    private static final RateLimiter CONSENT_RATE = new RateLimiter(2.0, 4.0);
    private static final FollowConsents CONSENTS = new FollowConsents();
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    private static UUID replacing;
    private static boolean initialized;

    private AutonomyServer() {
    }

    public record Reply(AutonomyStatus status, String drone, String detail) {
        public Reply {
            Objects.requireNonNull(status, "status");
            drone = drone == null ? "" : drone;
            detail = detail == null ? "" : detail;
        }

        public static Reply of(AutonomyStatus status) {
            return new Reply(status, "", "");
        }

        public Reply withDrone(String label) {
            return this.drone.isEmpty() ? new Reply(this.status, label, this.detail) : this;
        }

        public boolean ok() {
            return !this.status.error();
        }
    }

    static final class Flight {
        final Entity drone;
        final UUID owner;
        final UUID serial;
        final AutonomyAssist assist;
        final long createdAt;
        final boolean fromExit;
        boolean confirmed;

        Flight(Entity drone, UUID owner, UUID serial, AutonomyAssist assist, long createdAt, boolean fromExit) {
            this.drone = drone;
            this.owner = owner;
            this.serial = serial;
            this.assist = assist;
            this.createdAt = createdAt;
            this.fromExit = fromExit;
        }
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashApi.drones().autonomy().registerExitHandler(EXIT_HANDLER_ID, AutonomyServer::onPilotExit);
        PropwashEvents.AUTONOMY_ENDED.register(EndGuard.of(AutonomyServer::onEnded));
    }

    public static synchronized boolean initialized() {
        return initialized;
    }

    private static AutonomyAccess autonomy() {
        return PropwashApi.drones().autonomy();
    }

    public static Component text(AutonomyStatus status, String drone, String detail) {
        return text(status, drone, detail, Language.getInstance()::has);
    }

    public static Component text(AutonomyStatus status, String drone, String detail, Predicate<String> known) {
        Component name = DroneNames.text(drone, known);
        MutableComponent text = AutonomyStatus.Reason.of(status, detail).map(reason -> Component.translatable(reason.key(), name))
                .orElseGet(() -> Component.translatable(status.key(), name, detail));
        return status.error() ? text.withStyle(ChatFormatting.RED) : text;
    }

    static Reply refusal(AutonomyResult result, AutonomyPlan.Kind kind, Supplier<Optional<AutonomyNotReady>> why) {
        AutonomyStatus status = AutonomyStatus.of(result, kind);
        if (result != AutonomyResult.NOT_READY) {
            return Reply.of(status);
        }
        Optional<AutonomyNotReady> reason;
        try {
            reason = Objects.requireNonNullElse(why.get(), Optional.empty());
        } catch (RuntimeException e) {
            reason = Optional.empty();
        }
        return new Reply(status, "", reason.flatMap(AutonomyStatus.Reason::notReady).map(AutonomyStatus.Reason::code).orElse(""));
    }

    public static void send(ServerPlayer player, Reply reply) {
        if (player == null) {
            return;
        }
        if (!CamNetwork.sendToPlayer(player, new StatusMessage.Payload(reply.status(), reply.drone(), reply.detail()))) {
            player.sendSystemMessage(text(reply.status(), reply.drone(), reply.detail()));
        }
    }

    public static void send(ServerPlayer player, AutonomyStatus status, String drone, String detail) {
        send(player, new Reply(status, drone, detail));
    }

    static ServerPlayer player(MinecraftServer server, UUID id) {
        return server == null || id == null ? null : server.getPlayerList().getPlayer(id);
    }

    static ServerPlayer player(Entity drone, UUID id) {
        return drone.level() instanceof ServerLevel level ? player(level.getServer(), id) : null;
    }

    public static boolean operator(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static UUID serial(Entity drone) {
        return PlannerTablets.serialOf(drone);
    }

    public static UUID owner(Entity drone) {
        return PlannerOwners.owner(drone);
    }

    public static String label(Entity drone) {
        return DroneSelector.label(serial(drone), customName(drone));
    }

    public static String name(Entity drone) {
        return DroneNames.messageName(customName(drone), frame(drone), serial(drone), Language.getInstance()::has);
    }

    public static Component nameText(Entity drone) {
        return DroneNames.component(customName(drone), frame(drone), serial(drone), Language.getInstance()::has);
    }

    private static Optional<Identifier> frame(Entity drone) {
        return PlannerSupport.build(drone).map(BuildSpec::frame);
    }

    private static String customName(Entity drone) {
        return drone.hasCustomName() && drone.getCustomName() != null ? drone.getCustomName().getString() : "";
    }

    public static MissionStatus missionStatus(Entity drone) {
        if (!CamDroneData.registered()) {
            return MissionStatus.IDLE;
        }
        try {
            return Objects.requireNonNullElse(PropwashApi.droneData().get(drone, CamDroneData.missionState()), MissionStatus.IDLE);
        } catch (RuntimeException e) {
            return MissionStatus.IDLE;
        }
    }

    static void missionStatus(Entity drone, MissionStatus value) {
        WaypointTaskServer.observe(drone, value);
        MissionStatus previous = missionStatus(drone);
        if (!CamDroneData.registered() || value == null || value.equals(previous)) {
            return;
        }
        try {
            PropwashApi.droneData().set(drone, CamDroneData.missionState(), value);
        } catch (RuntimeException e) {
            CamDrones.LOG.warn("{}: Missionszustand nicht gespeichert: {}", CamDrones.MOD_NAME, e.toString());
            return;
        }
        CamAwards.missionStatus(drone, previous, value);
    }

    public static Optional<Mission> mission(Entity drone) {
        if (!CamDroneData.registered()) {
            return Optional.empty();
        }
        try {
            MissionData data = PropwashApi.droneData().get(drone, CamDroneData.mission());
            return data == null ? Optional.empty() : data.mission();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static SmartState smart(Entity drone) {
        if (!CamDroneData.registered()) {
            return SmartState.NONE;
        }
        try {
            return Objects.requireNonNullElse(PropwashApi.droneData().get(drone, CamDroneData.smart()), SmartState.NONE);
        } catch (RuntimeException e) {
            return SmartState.NONE;
        }
    }

    static void smart(Entity drone, SmartState value) {
        if (!CamDroneData.registered() || value == null || value.equals(smart(drone))) {
            return;
        }
        try {
            PropwashApi.droneData().set(drone, CamDroneData.smart(), value);
        } catch (RuntimeException e) {
            CamDrones.LOG.warn("{}: Funktion nicht gespeichert: {}", CamDrones.MOD_NAME, e.toString());
        }
    }

    public static DroneSettings settings(Entity drone) {
        if (!CamDroneData.registered()) {
            return DroneSettings.DEFAULT;
        }
        try {
            return Objects.requireNonNullElse(PropwashApi.droneData().get(drone, CamDroneData.settings()), DroneSettings.DEFAULT);
        } catch (RuntimeException e) {
            return DroneSettings.DEFAULT;
        }
    }

    static SmartState smartFor(AutonomyPlan plan, Entity drone) {
        return switch (plan.kind()) {
            case HOLD -> SmartState.hold(drone.getX(), drone.getY(), drone.getZ());
            case FOLLOW -> SmartState.follow(plan.target().orElseThrow(), plan.style());
            case ORBIT -> SmartState.pointOfInterest(plan.x(), plan.y(), plan.z(), plan.radius(), plan.speed(), plan.clockwise());
            case AUTO_SHOT -> SmartState.autoShot(plan.shot(), plan.x(), plan.y(), plan.z());
            case MISSION, LAND, HOME -> SmartState.NONE;
        };
    }

    static AutonomyPlan autoShot(Entity drone, DroneView view, AutoShots.Shot shot) {
        Vec3 forward = view.forward();
        AutonomyPlan ahead = AutonomyPlan.autoShotAhead(shot, drone.getX(), drone.getZ(), Compass.headingTo(forward.x, forward.z), drone.getY());
        int blockX = (int) Math.floor(ahead.x());
        int blockZ = (int) Math.floor(ahead.z());
        Level level = drone.level();
        double groundY = level.hasChunk(blockX >> 4, blockZ >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ) : drone.getY();
        return AutonomyPlan.autoShot(shot, ahead.x(), groundY, ahead.z());
    }

    static String detail(AutonomyPlan plan, Entity drone) {
        return switch (plan.kind()) {
            case MISSION -> Integer.toString(plan.startIndex() + 1);
            case FOLLOW -> plan.target().flatMap(id -> entity(drone.level(), id)).map(e -> e.getName().getString()).orElse("");
            default -> "";
        };
    }

    static synchronized Optional<Flight> flight(Entity drone) {
        Flight flight = FLIGHTS.get(drone.getUUID());
        if (flight == null) {
            return Optional.empty();
        }
        long now = Util.getMillis();
        boolean stale = flight.drone.isRemoved()
                || (flight.assist.started() ? now - flight.assist.lastUpdate() > STALE_MILLIS : now - flight.createdAt > UNCONFIRMED_MILLIS);
        if (stale) {
            FLIGHTS.remove(drone.getUUID());
            return Optional.empty();
        }
        return Optional.of(flight);
    }

    static synchronized List<Flight> flights() {
        return List.copyOf(FLIGHTS.values());
    }

    public static Optional<AutonomyPlan> activePlan(Entity drone) {
        return flight(drone).map(f -> f.assist.plan());
    }

    public static Optional<NavStatus> navStatus(Entity drone) {
        Flight flight;
        synchronized (AutonomyServer.class) {
            flight = FLIGHTS.get(drone.getUUID());
        }
        return flight == null ? Optional.empty() : flight.assist.status();
    }

    static boolean cameraDrone(Entity drone, DroneView view) {
        return ServerProfiles.cameraDrone(view.accessory(AccessorySlot.FRONT), !CamDroneData.NO_SERIAL.equals(serial(drone)));
    }

    static AutonomyAssist assist(Entity drone, DroneView view, AutonomyPlan plan, boolean groundStart, UUID owner) {
        FlightClass flightClass = ServerProfiles.flightClass(view.massKg(), view.accessory(AccessorySlot.FRONT));
        NavTarget home = NavTarget.at(drone.getX(), drone.getY(), drone.getZ());
        Set<SensorDirection> sensors = PlannerSupport.fpv(drone) ? Set.of() : ServerProfiles.sensors(flightClass);
        return new AutonomyAssist(flightClass, sensors, plan, groundStart, settings(drone).obstacle(), home,
                new ServerEnvironment(drone, owner), new FlightListener(drone, owner), Util::getMillis);
    }

    static Reply startFlight(ServerPlayer owner, Entity drone, AutonomyPlan plan) {
        Optional<DroneView> view = PropwashApi.drones().view(drone);
        if (view.isEmpty()) {
            return Reply.of(AutonomyStatus.NOT_FOUND);
        }
        boolean airborne = view.get().flying();
        if (!airborne && plan.kind() == AutonomyPlan.Kind.LAND) {
            return Reply.of(AutonomyStatus.NOT_RUNNING);
        }
        if (StartCheck.needsChecks(plan.kind())) {
            Optional<StartCheck.Refusal> refusal = StartCheck.check(airborne, plan.kind(), airborne ? StartCheck.Battery.UNKNOWN
                    : StartProbes.battery(drone), StartProbes.skyView(drone));
            if (refusal.isPresent()) {
                CamDrones.LOG.info("{}: Flug ohne Pilot {} für Drohne {} von {} abgelehnt: {}", CamDrones.MOD_NAME, plan.id(), label(drone),
                        owner.getGameProfile().name(), refusal.get().reason().code());
                return new Reply(refusal.get().status(), "", refusal.get().reason().code());
            }
        }
        OptionalDouble takeoff = FlightOptions.takeoff(airborne, plan, drone.getY());
        AutonomyAssist assist = assist(drone, view.get(), plan, !airborne, owner.getUUID());
        AutonomyOptions options = FlightOptions.of(plan, takeoff, CamWorldRules.maxAutonomyMinutes(drone.level()));
        Flight flight = new Flight(drone, owner.getUUID(), serial(drone), assist, Util.getMillis(), false);
        AutonomyResult result;
        synchronized (AutonomyServer.class) {
            replacing = drone.getUUID();
        }
        try {
            result = autonomy().start(drone, owner, assist, options);
        } catch (RuntimeException e) {
            CamDrones.LOG.warn("{}: Flug ohne Pilot nicht gestartet: {}", CamDrones.MOD_NAME, e.toString());
            result = AutonomyResult.DENIED;
        } finally {
            synchronized (AutonomyServer.class) {
                replacing = null;
            }
        }
        if (result.started()) {
            flight.confirmed = true;
            synchronized (AutonomyServer.class) {
                FLIGHTS.put(drone.getUUID(), flight);
            }
            smart(drone, smartFor(plan, drone));
            CrewService.flightStarted(drone);
        }
        CamDrones.LOG.info("{}: Flug ohne Pilot {} für Drohne {} von {}: {}{}", CamDrones.MOD_NAME, plan.id(), label(drone),
                owner.getGameProfile().name(), result, takeoff.isPresent() ? " (Start auf " + takeoff.getAsDouble() + " m)" : "");
        return result.started() ? Reply.of(plan.startedStatus()) : refusal(result, plan.kind(), () -> autonomy().notReadyReason(drone, options));
    }

    static Reply startOrReplan(ServerPlayer owner, Entity drone, AutonomyPlan plan) {
        Optional<DroneView> view = PropwashApi.drones().view(drone);
        if (view.isEmpty()) {
            return Reply.of(AutonomyStatus.NOT_FOUND);
        }
        if (view.get().pilot().isPresent()) {
            return Reply.of(AutonomyStatus.PILOTED);
        }
        Optional<Flight> flight = flight(drone);
        if (flight.isPresent()) {
            flight.get().assist.replan(plan);
            smart(drone, smartFor(plan, drone));
            return Reply.of(plan.startedStatus());
        }
        if (autonomy().active(drone).isPresent()) {
            return Reply.of(AutonomyStatus.BUSY);
        }
        if (!CamWorldRules.allowAutonomy(drone.level())) {
            return Reply.of(AutonomyStatus.DISABLED);
        }
        if (!cameraDrone(drone, view.get())) {
            return Reply.of(AutonomyStatus.NOT_CAMERA);
        }
        return startFlight(owner, drone, plan);
    }

    public static Reply execute(ServerPlayer player, Entity drone, MissionCommand.Command command, int startIndex) {
        return execute(Optional.of(player), operator(player), drone, command, startIndex);
    }

    public static Reply execute(Optional<ServerPlayer> actor, boolean operator, Entity drone, MissionCommand.Command command, int startIndex) {
        String label = name(drone);
        Optional<DroneView> view = PropwashApi.drones().view(drone);
        boolean owner = actor.filter(p -> owner(drone).equals(p.getUUID())).isPresent();
        boolean piloted = view.map(v -> v.pilot().isPresent()).orElse(false);
        Optional<Flight> flight = flight(drone);
        boolean other = flight.isEmpty() && view.isPresent() && autonomy().active(drone).isPresent();
        Optional<Mission> mission = mission(drone);
        MissionStatus status = missionStatus(drone);
        boolean camera = view.map(v -> cameraDrone(drone, v)).orElse(false);
        if (piloted && owner && FpvMissions.refusedWithPilot(command, PlannerSupport.fpv(drone))) {
            return new Reply(AutonomyStatus.PILOTED, label, AutonomyStatus.Reason.FPV_PILOTED.code());
        }
        CommandDecision.Decision decision = CommandDecision.decide(new CommandDecision.Situation(command, startIndex, owner, operator, true,
                view.isPresent(), camera, piloted, flight.isPresent(), other, CamWorldRules.allowAutonomy(drone.level()),
                mission.map(Mission::size).orElse(0), status));
        String waypoint = Integer.toString(decision.index() + 1);
        switch (decision.action()) {
            case REJECT -> {
                return new Reply(decision.reply(), label, waypoint);
            }
            case RELAY, RESET -> {
                missionStatus(drone, status.with(decision.state(), decision.index()).nextEpoch());
                return new Reply(decision.reply(), label, waypoint);
            }
            case START_FLIGHT -> {
                if (!owner || actor.isEmpty()) {
                    return new Reply(AutonomyStatus.NOT_OWNER, label, "");
                }
                AutonomyPlan plan = decision.plan() == AutonomyPlan.Kind.MISSION ? AutonomyPlan.mission(mission.orElseThrow(), decision.index())
                        : AutonomyPlan.hold();
                Reply started = startFlight(actor.get(), drone, plan);
                if (!started.ok()) {
                    return started.withDrone(label);
                }
                missionStatus(drone, status.with(decision.state(), decision.index()).nextEpoch());
                return new Reply(decision.reply(), label, waypoint);
            }
            case CONTROL -> {
                AutonomyAssist assist = flight.orElseThrow().assist;
                boolean missionPlan = assist.plan().kind() == AutonomyPlan.Kind.MISSION;
                switch (command) {
                    case START -> assist.replan(AutonomyPlan.mission(mission.orElseThrow(), decision.index()));
                    case RESUME -> {
                        if (missionPlan) {
                            assist.control(AutonomyAssist.Control.RESUME);
                        } else {
                            assist.replan(AutonomyPlan.mission(mission.orElseThrow(), decision.index()));
                        }
                    }
                    case PAUSE -> assist.control(AutonomyAssist.Control.PAUSE);
                    case HOLD -> assist.control(AutonomyAssist.Control.HOLD);
                    case STOP -> assist.control(AutonomyAssist.Control.STOP);
                }
                if (command == MissionCommand.Command.START || command == MissionCommand.Command.RESUME) {
                    smart(drone, SmartState.NONE);
                }
                if (command == MissionCommand.Command.HOLD) {
                    smart(drone, SmartState.hold(drone.getX(), drone.getY(), drone.getZ()));
                }
                missionStatus(drone, missionStatus(drone).nextEpoch());
                return new Reply(decision.reply(), label, waypoint);
            }
        }
        return new Reply(AutonomyStatus.DENIED, label, "");
    }

    public static void onMissionCommand(MissionCommand.Request request, ServerPlayer player) {
        if (!COMMAND_RATE.tryAcquire(player.getUUID(), Util.getMillis())) {
            send(player, AutonomyStatus.TOO_FAST, DroneSelector.shortSerial(request.serial()), "");
            return;
        }
        Optional<Entity> drone = PlannerTablets.findDrone((ServerLevel) player.level(), request.serial());
        if (drone.isEmpty()) {
            send(player, AutonomyStatus.NOT_FOUND, DroneSelector.shortSerial(request.serial()), "");
            return;
        }
        send(player, execute(player, drone.get(), request.command(), request.startIndex()));
    }

    public static void onMissionProgress(MissionProgress.Report report, ServerPlayer player) {
        boolean withinRate = PROGRESS_RATE.tryAcquire(player.getUUID(), Util.getMillis());
        if (!withinRate) {
            return;
        }
        Optional<Entity> drone = PlannerTablets.findDrone((ServerLevel) player.level(), report.serial());
        if (drone.isEmpty()) {
            return;
        }
        boolean pilot = PropwashApi.drones().view(drone.get()).flatMap(DroneView::pilot).filter(player.getUUID()::equals).isPresent();
        int size = mission(drone.get()).map(Mission::size).orElse(0);
        ProgressCheck.apply(pilot, true, missionStatus(drone.get()), report.state(), report.index(), report.epoch(), size)
                .ifPresent(next -> missionStatus(drone.get(), next));
    }

    public static void onSmartMode(SmartMode.Request request, ServerPlayer player) {
        if (!SMART_RATE.tryAcquire(player.getUUID(), Util.getMillis())) {
            return;
        }
        Optional<Entity> found = PlannerTablets.findDrone((ServerLevel) player.level(), request.serial());
        if (found.isEmpty()) {
            return;
        }
        Entity drone = found.get();
        Optional<DroneView> view = PropwashApi.drones().view(drone);
        if (view.isEmpty()) {
            return;
        }
        boolean pilot = view.get().pilot().filter(player.getUUID()::equals).isPresent();
        boolean owner = owner(drone).equals(player.getUUID());
        if (!pilot && !owner) {
            send(player, AutonomyStatus.NOT_OWNER, name(drone), "");
            return;
        }
        SmartState smart = request.smart();
        if (smart.function() == SmartState.Function.POI && new Vec3(smart.x(), smart.y(), smart.z()).distanceTo(drone.position()) > MAX_POI_DISTANCE) {
            send(player, AutonomyStatus.DENIED, name(drone), "");
            return;
        }
        if (pilot) {
            if (smart.function() == SmartState.Function.AUTO_SHOT) {
                send(player, AutonomyStatus.PILOTED, name(drone), "");
                return;
            }
            if (smart.function() == SmartState.Function.FOLLOW && !smart.target().filter(player.getUUID()::equals).isPresent()) {
                send(player, AutonomyStatus.FOLLOW_NOT_ALLOWED, name(drone), "");
                return;
            }
            smart(drone, smart);
            return;
        }
        Reply reply = switch (smart.function()) {
            case NONE -> {
                if (flight(drone).isEmpty()) {
                    smart(drone, SmartState.NONE);
                    yield new Reply(AutonomyStatus.NOT_RUNNING, name(drone), "");
                }
                yield execute(player, drone, MissionCommand.Command.HOLD, MissionCommand.AUTO_INDEX);
            }
            case HOLD -> execute(player, drone, MissionCommand.Command.HOLD, MissionCommand.AUTO_INDEX);
            case COURSE_LOCK -> new Reply(AutonomyStatus.DENIED, name(drone), "");
            case POI -> startOrReplan(player, drone, AutonomyPlan.orbit(smart.x(), smart.y(), smart.z(), smart.radius(), smart.speed(),
                    smart.clockwise())).withDrone(name(drone));
            case FOLLOW -> follow(player, drone, smart.target().orElse(player.getUUID()), smart.followStyle());
            case AUTO_SHOT -> startOrReplan(player, drone, autoShot(drone, view.get(), smart.shot())).withDrone(name(drone));
        };
        send(player, reply);
    }

    static FollowPolicy.Target describe(ServerPlayer owner, Entity target) {
        boolean self = target.getUUID().equals(owner.getUUID());
        boolean player = target instanceof ServerPlayer;
        boolean animal = target instanceof OwnableEntity ownable && ownable.getOwner() instanceof LivingEntity living
                && living.getUUID().equals(owner.getUUID());
        boolean mount = owner.getVehicle() == target || target.hasPassenger(owner);
        return new FollowPolicy.Target(self, player, animal, mount, !player && !ridersAgreed(owner, target));
    }

    static List<ServerPlayer> riders(Entity target, UUID owner) {
        List<ServerPlayer> out = new ArrayList<>();
        target.getRootVehicle().getSelfAndPassengers().forEach(entity -> {
            if (entity instanceof ServerPlayer player && entity != target && !player.getUUID().equals(owner)) {
                out.add(player);
            }
        });
        return out;
    }

    static boolean ridersAgreed(ServerPlayer owner, Entity target) {
        boolean rule = CamWorldRules.allowFollowPlayers(target.level());
        for (ServerPlayer rider : riders(target, owner.getUUID())) {
            if (!rule || !CONSENTS.granted(owner.getUUID(), rider.getUUID(), owner, rider)) {
                return false;
            }
        }
        return true;
    }

    static Optional<Entity> entity(Level level, UUID id) {
        if (level == null || id == null) {
            return Optional.empty();
        }
        Entity entity = level.getEntity(id);
        if (entity == null && level instanceof ServerLevel server) {
            entity = player(server.getServer(), id);
        }
        return Optional.ofNullable(entity);
    }

    public static Reply follow(ServerPlayer owner, Entity drone, UUID targetId, FollowController.Style style) {
        Optional<Entity> target = entity(drone.level(), targetId);
        if (target.isEmpty()) {
            return new Reply(AutonomyStatus.FOLLOW_TARGET_LOST, name(drone), "");
        }
        return follow(owner, drone, target.get(), style);
    }

    public static Reply follow(ServerPlayer owner, Entity drone, Entity target, FollowController.Style style) {
        String label = name(drone);
        String targetName = target.getName().getString();
        if (!owner(drone).equals(owner.getUUID())) {
            return new Reply(AutonomyStatus.NOT_OWNER, label, targetName);
        }
        if (target.level() != drone.level()) {
            return new Reply(AutonomyStatus.FOLLOW_TARGET_LOST, label, targetName);
        }
        boolean consent = target instanceof ServerPlayer player && CONSENTS.granted(owner.getUUID(), player.getUUID(), owner, player);
        FollowPolicy.Verdict verdict = FollowPolicy.check(describe(owner, target), CamWorldRules.allowFollowPlayers(drone.level()), consent);
        switch (verdict) {
            case RULE_OFF, NOT_ALLOWED -> {
                return new Reply(verdict.status(), label, targetName);
            }
            case NEEDS_CONSENT -> {
                ServerPlayer player = (ServerPlayer) target;
                long now = Util.getMillis();
                expireRequests(owner.level().getServer(), now);
                FollowConsents.Request request = CONSENTS.request(owner.getUUID(), player.getUUID(), drone.getUUID(), style, owner, player, now);
                askConsent(player, request, owner.getGameProfile().name(), nameText(drone), now);
                return new Reply(AutonomyStatus.FOLLOW_REQUESTED, label, targetName);
            }
            case ALLOWED -> {
                Reply reply = startOrReplan(owner, drone, AutonomyPlan.follow(target.getUUID(), style));
                return reply.ok() || AutonomyStatus.Reason.of(reply.status(), reply.detail()).isEmpty() ? new Reply(reply.status(), label, targetName)
                        : reply.withDrone(label);
            }
        }
        return new Reply(AutonomyStatus.DENIED, label, targetName);
    }

    static void askConsent(ServerPlayer target, FollowConsents.Request request, String requester, Component drone, long now) {
        int seconds = request.secondsLeft(now);
        CamNetwork.sendToPlayer(target, FollowConsent.Request.of(request.id(), requester, drone, seconds));
        target.sendSystemMessage(consentMessage(requester, drone, seconds));
    }

    public static Component consentQuestion(FollowConsent.Request request) {
        return Component.translatable(CONSENT_KEY + "question", request.requester(), request.droneName(), request.seconds());
    }

    public static Component consentMessage(String requester, Component drone, int seconds) {
        MutableComponent accept = Component.translatable(CONSENT_KEY + "accept").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand("/" + CamDrones.MOD_ID + " follow accept"))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(CONSENT_KEY + "accept.hint"))));
        MutableComponent deny = Component.translatable(CONSENT_KEY + "deny").withStyle(style -> style.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent.RunCommand("/" + CamDrones.MOD_ID + " follow deny"))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(CONSENT_KEY + "deny.hint"))));
        return Component.translatable(CONSENT_KEY + "chat", requester, drone, seconds, accept, deny);
    }

    public static void onConsentReply(FollowConsent.Reply reply, ServerPlayer player) {
        if (!CONSENT_RATE.tryAcquire(player.getUUID(), Util.getMillis())) {
            return;
        }
        send(player, answer(player, OptionalInt.of(reply.requestId()), reply.accept()));
    }

    public static Reply answer(ServerPlayer target, OptionalInt id, boolean accept) {
        long now = Util.getMillis();
        expireRequests(target.level().getServer(), now);
        Optional<FollowConsents.Request> found = CONSENTS.answer(target.getUUID(), id, target, now);
        if (found.isEmpty()) {
            return Reply.of(AutonomyStatus.FOLLOW_NO_REQUEST);
        }
        FollowConsents.Request request = found.get();
        ServerPlayer requester = player(target.level().getServer(), request.requester());
        String targetName = target.getGameProfile().name();
        if (requester == null || requester != request.requesterSession()) {
            return Reply.of(AutonomyStatus.FOLLOW_EXPIRED);
        }
        String requesterName = requester.getGameProfile().name();
        Optional<Entity> drone = entity(requester.level(), request.drone());
        String droneLabel = drone.map(AutonomyServer::label).orElse("");
        if (!accept) {
            send(requester, AutonomyStatus.FOLLOW_DECLINED, droneLabel, targetName);
            return new Reply(AutonomyStatus.CONSENT_REFUSED, droneLabel, requesterName);
        }
        CONSENTS.grant(request);
        if (drone.isEmpty()) {
            send(requester, AutonomyStatus.NOT_FOUND, droneLabel, targetName);
            return new Reply(AutonomyStatus.CONSENT_GIVEN, droneLabel, requesterName);
        }
        Reply started = follow(requester, drone.get(), target, request.style());
        send(requester, started.ok() ? new Reply(AutonomyStatus.FOLLOW_ACCEPTED, droneLabel, targetName) : started);
        return new Reply(AutonomyStatus.CONSENT_GIVEN, droneLabel, requesterName);
    }

    static void expireRequests(MinecraftServer server, long now) {
        for (FollowConsents.Request request : CONSENTS.expire(now)) {
            ServerPlayer requester = player(server, request.requester());
            if (requester != null) {
                ServerPlayer target = player(server, request.target());
                send(requester, AutonomyStatus.FOLLOW_EXPIRED, "", target == null ? "" : target.getGameProfile().name());
            }
        }
    }

    public static int stopFollowing(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        CONSENTS.revoke(player.getUUID());
        int stopped = 0;
        for (Flight flight : flights()) {
            AutonomyPlan plan = flight.assist.plan();
            if (plan.kind() != AutonomyPlan.Kind.FOLLOW || flight.drone.isRemoved()) {
                continue;
            }
            boolean target = plan.target().filter(player.getUUID()::equals).isPresent() || plan.target()
                    .flatMap(id -> entity(flight.drone.level(), id)).filter(e -> riders(e, flight.owner).contains(player)).isPresent();
            boolean own = flight.owner.equals(player.getUUID());
            if (!target && !own) {
                continue;
            }
            flight.assist.control(AutonomyAssist.Control.HOLD);
            smart(flight.drone, SmartState.hold(flight.drone.getX(), flight.drone.getY(), flight.drone.getZ()));
            stopped++;
            if (target && !own) {
                send(player(server, flight.owner), AutonomyStatus.FOLLOW_STOPPED_BY_TARGET, name(flight.drone), player.getGameProfile().name());
            }
        }
        return stopped;
    }

    static boolean followAllowed(ServerPlayer owner, Entity drone, UUID targetId) {
        if (targetId.equals(owner.getUUID())) {
            return true;
        }
        Optional<Entity> target = entity(drone.level(), targetId);
        if (target.isEmpty() || target.get().level() != drone.level()) {
            return false;
        }
        boolean consent = target.get() instanceof ServerPlayer player && CONSENTS.granted(owner.getUUID(), player.getUUID(), owner, player);
        return FollowPolicy.check(describe(owner, target.get()), CamWorldRules.allowFollowPlayers(drone.level()), consent)
                == FollowPolicy.Verdict.ALLOWED;
    }

    static boolean airborne(Entity drone) {
        return drone.level().noCollision(drone, drone.getBoundingBox().move(0.0, -AIRBORNE_CLEARANCE, 0.0));
    }

    static Optional<AutonomyRequest> onPilotExit(Entity drone, ServerPlayer pilot) {
        try {
            if (!CamDroneData.registered() || !(drone.level() instanceof ServerLevel)) {
                return Optional.empty();
            }
            Optional<DroneView> view = PropwashApi.drones().view(drone);
            if (view.isEmpty()) {
                return Optional.empty();
            }
            FlightClass flightClass = ServerProfiles.flightClass(view.get().massKg(), view.get().accessory(AccessorySlot.FRONT));
            SmartState smart = smart(drone);
            if (FpvMissions.keepsPropwashExit(PlannerSupport.fpv(drone), mission(drone).isPresent() && missionStatus(drone).active(),
                    smart.function())) {
                return Optional.empty();
            }
            boolean targetAllowed = smart.function() == SmartState.Function.FOLLOW && smart.target().isPresent()
                    && followAllowed(pilot, drone, smart.target().get());
            ExitDecision.Situation situation = new ExitDecision.Situation(CamWorldRules.allowAutonomy(drone.level()), cameraDrone(drone, view.get()),
                    airborne(drone), mission(drone), missionStatus(drone), smart, settings(drone).exit(), pilot.getUUID(), targetAllowed,
                    flightClass, drone.getX(), drone.getY(), drone.getZ());
            Optional<AutonomyPlan> plan = ExitDecision.decide(situation);
            if (plan.isEmpty()) {
                return Optional.empty();
            }
            AutonomyAssist assist = assist(drone, view.get(), plan.get(), false, pilot.getUUID());
            Flight flight = new Flight(drone, pilot.getUUID(), serial(drone), assist, Util.getMillis(), true);
            synchronized (AutonomyServer.class) {
                FLIGHTS.put(drone.getUUID(), flight);
            }
            CamDrones.LOG.info("{}: Pilot {} verlässt die Drohne {}, Übergabe an {}", CamDrones.MOD_NAME, pilot.getGameProfile().name(), label(drone),
                    plan.get().id());
            return Optional.of(AutonomyRequest.of(assist, FlightOptions.of(plan.get(), OptionalDouble.empty(),
                    CamWorldRules.maxAutonomyMinutes(drone.level()))));
        } catch (RuntimeException e) {
            CamDrones.LOG.warn("{}: Übergabe beim Verlassen der FPV-Ansicht fehlgeschlagen: {}", CamDrones.MOD_NAME, e.toString());
            return Optional.empty();
        }
    }

    static void onEnded(Entity drone, UUID owner, Identifier id, AutonomyEnd reason) {
        if (drone == null || id == null || reason == null || !CamDrones.MOD_ID.equals(id.getNamespace())) {
            return;
        }
        Flight flight;
        boolean replacedByUs;
        synchronized (AutonomyServer.class) {
            replacedByUs = reason == AutonomyEnd.REPLACED && drone.getUUID().equals(replacing);
            flight = replacedByUs ? FLIGHTS.get(drone.getUUID()) : FLIGHTS.remove(drone.getUUID());
        }
        AutonomyPlan.Kind kind = flight != null ? flight.assist.plan().kind() : AutonomyPlan.Kind.byId(id).orElse(AutonomyPlan.Kind.HOLD);
        boolean missionLoaded = flight != null && flight.assist.missionLoaded();
        NavLogic.MissionState state = flight != null ? flight.assist.missionState() : NavLogic.MissionState.IDLE;
        int index = flight != null ? flight.assist.missionIndex() : 0;
        EndDecision.Outcome outcome = EndDecision.decide(new EndDecision.Situation(reason, kind, missionLoaded, state, index, replacedByUs,
                missionStatus(drone)));
        try {
            missionStatus(drone, outcome.status());
            if (outcome.clearSmart()) {
                smart(drone, SmartState.NONE);
            }
        } catch (RuntimeException e) {
            CamDrones.LOG.warn("{}: Zustand nach dem Ende nicht gespeichert: {}", CamDrones.MOD_NAME, e.toString());
        }
        if (reason != AutonomyEnd.OWNER_OFFLINE) {
            outcome.message().ifPresent(message -> send(player(drone, owner), message, name(drone), Integer.toString(index + 1)));
        }
        CamDrones.LOG.info("{}: Flug ohne Pilot {} an Drohne {} beendet: {}", CamDrones.MOD_NAME, id, label(drone), reason);
    }

    public static List<DroneSelector.Candidate<Entity>> drones(MinecraftServer server, Optional<UUID> owner) {
        List<DroneSelector.Candidate<Entity>> out = new ArrayList<>();
        if (server == null || !CamDroneData.registered()) {
            return out;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!PropwashApi.drones().isDrone(entity)) {
                    continue;
                }
                UUID serial = serial(entity);
                if (CamDroneData.NO_SERIAL.equals(serial) || owner.filter(id -> !id.equals(owner(entity))).isPresent()) {
                    continue;
                }
                String name = entity.hasCustomName() && entity.getCustomName() != null ? entity.getCustomName().getString() : "";
                out.add(new DroneSelector.Candidate<>(entity, serial, name));
            }
        }
        return out;
    }

    static final class ServerEnvironment implements AutonomyAssist.Environment {
        private final Entity drone;
        private final UUID owner;
        private long noticeAt = Long.MIN_VALUE;

        ServerEnvironment(Entity drone, UUID owner) {
            this.drone = drone;
            this.owner = owner;
        }

        @Override
        public int maxAltitude() {
            return CamWorldRules.maxAltitude(this.drone.level());
        }

        @Override
        public int maxDistance() {
            return CamWorldRules.maxDistance(this.drone.level());
        }

        @Override
        public Optional<NavTarget> landingPad(double x, double y, double z) {
            return LandingPads.below(this.drone.level(), x, y, z);
        }

        @Override
        public Optional<NavTarget> followTarget(UUID target) {
            Optional<Entity> found = entity(this.drone.level(), target);
            if (found.isEmpty() || found.get().isRemoved() || !found.get().isAlive() || found.get().level() != this.drone.level()) {
                return Optional.empty();
            }
            Entity entity = found.get();
            if (!entity.getUUID().equals(this.owner)) {
                ServerPlayer ownerPlayer = player(this.drone, this.owner);
                if (ownerPlayer == null) {
                    return Optional.empty();
                }
                List<ServerPlayer> watched = new ArrayList<>();
                if (entity instanceof ServerPlayer player) {
                    if (!CamWorldRules.allowFollowPlayers(this.drone.level()) || !CONSENTS.granted(this.owner, player.getUUID(), ownerPlayer, player)) {
                        return Optional.empty();
                    }
                    watched.add(player);
                } else {
                    if (FollowPolicy.check(describe(ownerPlayer, entity), CamWorldRules.allowFollowPlayers(this.drone.level()), false)
                            != FollowPolicy.Verdict.ALLOWED) {
                        return Optional.empty();
                    }
                    watched.addAll(riders(entity, this.owner));
                }
                this.notice(watched, ownerPlayer);
            }
            Entity moving = entity.getRootVehicle();
            Vec3 motion = moving.getKnownMovement();
            return Optional.of(new NavTarget(entity.getX(), entity.getY(), entity.getZ(), motion.x * TICKS_PER_SECOND, motion.y * TICKS_PER_SECOND,
                    motion.z * TICKS_PER_SECOND));
        }

        private void notice(List<ServerPlayer> followed, ServerPlayer owner) {
            long now = this.drone.level().getGameTime();
            if (followed.isEmpty() || this.noticeAt != Long.MIN_VALUE && now - this.noticeAt < NOTICE_TICKS && now >= this.noticeAt) {
                return;
            }
            this.noticeAt = now;
            Component text = Component.translatable(FOLLOWED_KEY, owner.getGameProfile().name(), nameText(this.drone));
            for (ServerPlayer player : followed) {
                player.sendOverlayMessage(text);
            }
        }
    }

    static final class FlightListener implements AutonomyAssist.Listener {
        private final Entity drone;
        private final UUID owner;

        FlightListener(Entity drone, UUID owner) {
            this.drone = drone;
            this.owner = owner;
        }

        private Optional<Flight> own(AutonomyAssist assist) {
            synchronized (AutonomyServer.class) {
                Flight flight = FLIGHTS.get(this.drone.getUUID());
                return flight != null && flight.assist == assist ? Optional.of(flight) : Optional.empty();
            }
        }

        @Override
        public void started(AutonomyAssist assist) {
            Optional<Flight> flight = this.own(assist);
            if (flight.isEmpty()) {
                return;
            }
            boolean announce = !flight.get().confirmed && flight.get().fromExit;
            flight.get().confirmed = true;
            if (announce) {
                AutonomyPlan plan = assist.plan();
                smart(this.drone, smartFor(plan, this.drone));
                send(player(this.drone, this.owner), plan.startedStatus(), name(this.drone), detail(plan, this.drone));
            }
        }

        @Override
        public void progress(AutonomyAssist assist, NavLogic.MissionState state, int index) {
            if (this.own(assist).isEmpty()) {
                return;
            }
            ProgressCheck.fromFlight(assist.missionLoaded(), missionStatus(this.drone), state, index).ifPresent(next -> missionStatus(this.drone, next));
        }

        @Override
        public void missionDone(AutonomyAssist assist) {
            if (this.own(assist).isPresent()) {
                send(player(this.drone, this.owner), AutonomyStatus.MISSION_DONE, name(this.drone), "");
            }
        }

        @Override
        public void autoShotDone(AutonomyAssist assist, AutoShots.Shot shot) {
            if (this.own(assist).isPresent()) {
                smart(this.drone, SmartState.hold(this.drone.getX(), this.drone.getY(), this.drone.getZ()));
                send(player(this.drone, this.owner), AutonomyStatus.AUTO_SHOT_DONE, name(this.drone), "");
            }
        }

        @Override
        public void targetLost(AutonomyAssist assist) {
            if (this.own(assist).isPresent()) {
                smart(this.drone, SmartState.hold(this.drone.getX(), this.drone.getY(), this.drone.getZ()));
                send(player(this.drone, this.owner), AutonomyStatus.FOLLOW_TARGET_LOST, name(this.drone), "");
            }
        }
    }
}
