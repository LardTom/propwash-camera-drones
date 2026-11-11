package de.lardtom.camdrones.crew;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.award.CamAwards;
import de.lardtom.camdrones.crew.model.CrewBook;
import de.lardtom.camdrones.crew.model.SoloCamera;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.CrewPackets;
import de.lardtom.camdrones.planner.PlannerOwners;
import de.lardtom.camdrones.planner.PlannerTablets;
import de.lardtom.camdrones.planner.model.RateLimiter;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CrewService {
    public static final double ACTION_RATE = 4.0;
    public static final double ACTION_BURST = 6.0;
    public static final double GIMBAL_RATE = 12.0;
    public static final double GIMBAL_BURST = 15.0;
    public static final int CHECK_TICKS = 20;
    public static final double PILOT_SEARCH = 256.0;
    public static final String ACCEPT_COMMAND = "/camdrones crew accept";
    public static final String DECLINE_COMMAND = "/camdrones crew decline";

    public enum End {
        DRONE_GONE,
        OFFLINE,
        RULE_OFF,
        PILOT,
        NEW_PILOT,
        UNBOUND,
        UNREACHABLE
    }

    public enum Watch {
        CHECK,
        HOLD,
        WAIT,
        RESTORE,
        EXPIRED
    }

    private static final CrewBook BOOK = new CrewBook();
    private static final SoloCamera SOLO = new SoloCamera();
    private static final RateLimiter ACTIONS = new RateLimiter(ACTION_RATE, ACTION_BURST);
    private static final RateLimiter GIMBAL = new RateLimiter(GIMBAL_RATE, GIMBAL_BURST);
    private static final Map<UUID, Integer> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();
    private static int ticks;

    private CrewService() {
    }

    public static CrewBook book() {
        return BOOK;
    }

    public static SoloCamera soloCamera() {
        return SOLO;
    }

    public static void handle(CrewPackets.Request request, ServerPlayer player) {
        if (!ACTIONS.tryAcquire(player.getUUID(), Util.getMillis())) {
            tellVerdict(player, CrewBook.Verdict.RATE, "");
            return;
        }
        ServerLevel level = player.level();
        switch (request.action()) {
            case INVITE -> invite(player, level.getEntity(request.drone()),
                    request.target().map(id -> level.getServer().getPlayerList().getPlayer(id)).orElse(null));
            case ACCEPT -> accept(player);
            case DECLINE -> decline(player);
            case LEAVE -> leave(player);
            case RELEASE -> release(player, level.getEntity(request.drone()));
            case SOLO -> solo(player, level.getEntity(request.drone()));
        }
    }

    public static SoloCamera.Verdict solo(ServerPlayer player, @Nullable Entity drone) {
        SoloCamera.Verdict verdict = drone == null || !PropwashApi.drones().isDrone(drone) ? SoloCamera.Verdict.NO_GIMBAL
                : SoloCamera.check(new SoloCamera.Facts(CrewDrones.hasGimbal(drone), owns(player, drone), CrewDrones.pilot(drone).isPresent(),
                flyingItself(drone), BOOK.operator(drone.getUUID()).isPresent()));
        if (verdict.ok() && SOLO.player(drone.getUUID()).filter(player.getUUID()::equals).isEmpty() && !bind(drone, player)) {
            verdict = SoloCamera.Verdict.REFUSED;
        }
        if (!verdict.ok()) {
            CamDrones.LOG.debug("{}: Kamera allein für {} abgelehnt: {}", CamDrones.MOD_NAME, player.getPlainTextName(), verdict);
            CamNetwork.sendToPlayer(player, new CrewPackets.Notice(CrewPackets.Kind.SOLO_REFUSED, drone == null ? -1 : drone.getId(), verdict.name()));
            return verdict;
        }
        boolean fresh = SOLO.player(drone.getUUID()).isEmpty();
        SOLO.droneOf(player.getUUID()).filter(other -> !other.equals(drone.getUUID())).ifPresent(other -> {
            SOLO.end(other);
            unbind(findDrone(player.level().getServer(), other));
        });
        SOLO.start(drone.getUUID(), player.getUUID());
        NAMES.put(player.getUUID(), player.getPlainTextName());
        GimbalData data = CrewData.get(drone);
        if (!data.soloBy(player.getUUID())) {
            CrewData.set(drone, data.withSolo(player.getUUID()));
        }
        if (fresh) {
            player.sendSystemMessage(Component.translatable(CrewTexts.SOLO_BOUND).withStyle(ChatFormatting.GREEN));
            CamDrones.LOG.info("{}: {} steuert allein die Kamera von Drohne {}, die ohne Pilot fliegt", CamDrones.MOD_NAME, player.getPlainTextName(),
                    drone.getUUID());
        }
        return verdict;
    }

    public static boolean flyingItself(Entity drone) {
        try {
            return PropwashApi.drones().autonomy().active(drone).filter(id -> CamDrones.MOD_ID.equals(id.getNamespace())).isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean owns(ServerPlayer player, Entity drone) {
        UUID owner = PlannerOwners.owner(drone);
        return owner != null && !PlannerOwners.NO_OWNER.equals(owner) && owner.equals(player.getUUID());
    }

    public static void endSolo(@Nullable MinecraftServer server, UUID droneId, @Nullable Entity drone, SoloCamera.End reason) {
        Optional<UUID> player = SOLO.end(droneId);
        if (player.isEmpty()) {
            return;
        }
        if (drone != null && !drone.isRemoved()) {
            Optional<ServerPlayer> bound;
            try {
                bound = PropwashApi.drones().operators().operator(drone);
            } catch (RuntimeException e) {
                bound = Optional.empty();
            }
            if (bound.map(ServerPlayer::getUUID).filter(player.get()::equals).isPresent()) {
                try {
                    PropwashApi.drones().operators().unbind(drone);
                } catch (RuntimeException e) {
                    CamDrones.LOG.warn("{}: Operator-Lösung fehlgeschlagen: {}", CamDrones.MOD_NAME, e.toString());
                }
            }
            GimbalData data = CrewData.get(drone);
            if (data.soloBy(player.get())) {
                CrewData.set(drone, data.withOperator(Optional.empty()));
            }
        }
        CamDrones.LOG.info("{}: Kamera allein von {} an Drohne {} beendet ({})", CamDrones.MOD_NAME, name(player.get()), droneId, reason);
        ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(player.get());
        if (online == null) {
            return;
        }
        String key = soloText(reason);
        if (key != null) {
            online.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
        CamNetwork.sendToPlayer(online, new CrewPackets.Notice(CrewPackets.Kind.RELEASED, drone == null ? -1 : drone.getId(), ""));
    }

    public static @Nullable String soloText(SoloCamera.End reason) {
        return switch (reason) {
            case LEFT -> CrewTexts.LEFT_OPERATOR;
            case NOT_AUTONOMOUS -> CrewTexts.SOLO_ENDED;
            case UNBOUND, DRONE_GONE -> CrewTexts.ENDED_OPERATOR;
            case CREW, PILOTED, OFFLINE -> null;
        };
    }

    public static void flightStarted(Entity drone) {
        if (drone == null || drone.level().isClientSide() || SOLO.player(drone.getUUID()).isPresent()) {
            return;
        }
        GimbalData data = CrewData.get(drone);
        if (data.solo()) {
            CrewData.set(drone, data.withOperator(Optional.empty()));
        }
    }

    private static void yieldSolo(MinecraftServer server, Entity drone, ServerPlayer crew) {
        if (SOLO.player(drone.getUUID()).filter(id -> !id.equals(crew.getUUID())).isPresent()) {
            endSolo(server, drone.getUUID(), drone, SoloCamera.End.CREW);
        }
    }

    public static CrewBook.Verdict invite(ServerPlayer inviter, @Nullable Entity drone, @Nullable ServerPlayer invitee) {
        ServerLevel level = inviter.level();
        boolean ruleOn = CamWorldRules.allowOperator(level);
        boolean gimbal = drone != null && PropwashApi.drones().isDrone(drone) && CrewDrones.hasGimbal(drone);
        boolean controls = drone != null && mayInvite(inviter, drone);
        double distance = invitee == null || invitee.level() != level ? Double.POSITIVE_INFINITY : inviter.distanceTo(invitee);
        boolean busy = invitee != null && drone != null && CrewDrones.pilotedBy(drone, invitee.getUUID());
        CrewBook.Verdict verdict = BOOK.invite(drone == null ? null : drone.getUUID(), inviter.getUUID(),
                invitee == null ? null : invitee.getUUID(), new CrewBook.Facts(ruleOn, gimbal, controls, distance, busy), Util.getMillis());
        String name = invitee == null ? "" : invitee.getPlainTextName();
        if (!verdict.ok() || invitee == null || drone == null) {
            tellVerdict(inviter, verdict, name);
            return verdict;
        }
        inviter.sendSystemMessage(Component.translatable(CrewTexts.INVITE_SENT, name).withStyle(ChatFormatting.GREEN));
        invitee.sendSystemMessage(invitation(inviter.getPlainTextName()));
        CamNetwork.sendToPlayer(invitee, new CrewPackets.Notice(CrewPackets.Kind.INVITED, drone.getId(), inviter.getPlainTextName()));
        CamDrones.LOG.info("{}: {} lädt {} als Kamera-Operator von Drohne {} ein", CamDrones.MOD_NAME, inviter.getPlainTextName(), name,
                drone.getUUID());
        return verdict;
    }

    public static CrewBook.Verdict inviteByCommand(ServerPlayer inviter, ServerPlayer invitee) {
        return invite(inviter, resolveDrone(inviter).orElse(null), invitee);
    }

    public static CrewBook.Verdict accept(ServerPlayer player) {
        long now = Util.getMillis();
        Optional<CrewBook.Invite> pending = BOOK.pending(player.getUUID(), now);
        if (pending.isEmpty()) {
            tellVerdict(player, CrewBook.Verdict.NO_INVITE, "");
            return CrewBook.Verdict.NO_INVITE;
        }
        CrewBook.Invite invite = pending.get();
        MinecraftServer server = player.level().getServer();
        ServerPlayer inviter = server.getPlayerList().getPlayer(invite.inviter());
        Entity drone = findDrone(server, invite.drone());
        if (inviter == null || drone == null) {
            BOOK.decline(player.getUUID());
            CrewBook.Verdict verdict = inviter == null ? CrewBook.Verdict.OFFLINE : CrewBook.Verdict.NO_DRONE;
            tellVerdict(player, verdict, "");
            return verdict;
        }
        boolean ruleOn = CamWorldRules.allowOperator(drone.level());
        boolean gimbal = CrewDrones.hasGimbal(drone);
        double distance = inviter.level() != player.level() ? Double.POSITIVE_INFINITY : player.distanceTo(inviter);
        boolean busy = CrewDrones.pilotedBy(drone, player.getUUID());
        boolean controls = mayInvite(inviter, drone);
        CrewBook.Verdict verdict = BOOK.accept(player.getUUID(), new CrewBook.Facts(ruleOn, gimbal, controls, distance, busy), now,
                (droneId, operatorId) -> {
                    yieldSolo(server, drone, player);
                    return bind(drone, player);
                });
        if (!verdict.ok()) {
            tellVerdict(player, verdict, inviter.getPlainTextName());
            if (verdict == CrewBook.Verdict.REFUSED) {
                tellVerdict(inviter, verdict, player.getPlainTextName());
            }
            return verdict;
        }
        CrewData.set(drone, CrewData.get(drone).withOperator(Optional.of(player.getUUID())));
        SESSIONS.put(drone.getUUID(), player.getId());
        NAMES.put(player.getUUID(), player.getPlainTextName());
        player.sendSystemMessage(Component.translatable(CrewTexts.BOUND_OPERATOR).withStyle(ChatFormatting.GREEN));
        for (ServerPlayer lead : leads(drone, server, Optional.of(inviter.getUUID()))) {
            lead.sendSystemMessage(Component.translatable(CrewTexts.BOUND_PILOT, player.getPlainTextName()).withStyle(ChatFormatting.GREEN));
        }
        CamNetwork.sendToPlayer(player, new CrewPackets.Notice(CrewPackets.Kind.BOUND, drone.getId(), inviter.getPlainTextName()));
        CamDrones.LOG.info("{}: {} ist Kamera-Operator von Drohne {}", CamDrones.MOD_NAME, player.getPlainTextName(), drone.getUUID());
        return verdict;
    }

    public static CrewBook.Verdict decline(ServerPlayer player) {
        Optional<CrewBook.Invite> invite = BOOK.decline(player.getUUID());
        if (invite.isEmpty()) {
            tellVerdict(player, CrewBook.Verdict.NO_INVITE, "");
            return CrewBook.Verdict.NO_INVITE;
        }
        player.sendSystemMessage(Component.translatable(CrewTexts.INVITE_DECLINED_SELF).withStyle(ChatFormatting.GRAY));
        ServerPlayer inviter = player.level().getServer().getPlayerList().getPlayer(invite.get().inviter());
        if (inviter != null) {
            inviter.sendSystemMessage(Component.translatable(CrewTexts.INVITE_DECLINED, player.getPlainTextName()).withStyle(ChatFormatting.GRAY));
        }
        return CrewBook.Verdict.OK;
    }

    public static CrewBook.Verdict leave(ServerPlayer player) {
        Optional<UUID> solo = SOLO.droneOf(player.getUUID());
        if (solo.isPresent()) {
            MinecraftServer server = player.level().getServer();
            endSolo(server, solo.get(), findDrone(server, solo.get()), SoloCamera.End.LEFT);
            return CrewBook.Verdict.OK;
        }
        Optional<UUID> inviter = BOOK.droneOf(player.getUUID()).flatMap(BOOK::inviter);
        Optional<UUID> droneId = BOOK.leave(player.getUUID());
        if (droneId.isEmpty()) {
            tellVerdict(player, CrewBook.Verdict.NOT_OPERATOR, "");
            return CrewBook.Verdict.NOT_OPERATOR;
        }
        MinecraftServer server = player.level().getServer();
        Entity drone = findDrone(server, droneId.get());
        SESSIONS.remove(droneId.get());
        unbind(drone);
        player.sendSystemMessage(Component.translatable(CrewTexts.LEFT_OPERATOR).withStyle(ChatFormatting.GRAY));
        if (drone != null) {
            for (ServerPlayer lead : leads(drone, server, inviter)) {
                lead.sendSystemMessage(Component.translatable(CrewTexts.LEFT_PILOT, player.getPlainTextName()).withStyle(ChatFormatting.GRAY));
            }
        }
        CamNetwork.sendToPlayer(player, new CrewPackets.Notice(CrewPackets.Kind.RELEASED, drone == null ? -1 : drone.getId(), ""));
        return CrewBook.Verdict.OK;
    }

    public static CrewBook.Verdict release(ServerPlayer player, @Nullable Entity drone) {
        if (drone == null || !PropwashApi.drones().isDrone(drone)) {
            tellVerdict(player, CrewBook.Verdict.NO_DRONE, "");
            return CrewBook.Verdict.NO_DRONE;
        }
        boolean inviter = BOOK.inviter(drone.getUUID()).filter(player.getUUID()::equals).isPresent();
        if (!controls(player, drone) && !inviter) {
            tellVerdict(player, CrewBook.Verdict.NOT_ALLOWED, "");
            return CrewBook.Verdict.NOT_ALLOWED;
        }
        Optional<UUID> operatorId = BOOK.release(drone.getUUID());
        if (operatorId.isEmpty()) {
            tellVerdict(player, CrewBook.Verdict.NO_OPERATOR, "");
            return CrewBook.Verdict.NO_OPERATOR;
        }
        SESSIONS.remove(drone.getUUID());
        unbind(drone);
        MinecraftServer server = player.level().getServer();
        ServerPlayer operator = server.getPlayerList().getPlayer(operatorId.get());
        String name = operator == null ? name(operatorId.get()) : operator.getPlainTextName();
        player.sendSystemMessage(Component.translatable(CrewTexts.RELEASED_PILOT, name).withStyle(ChatFormatting.GRAY));
        if (operator != null) {
            operator.sendSystemMessage(Component.translatable(CrewTexts.RELEASED_OPERATOR).withStyle(ChatFormatting.GRAY));
            CamNetwork.sendToPlayer(operator, new CrewPackets.Notice(CrewPackets.Kind.RELEASED, drone.getId(), player.getPlainTextName()));
        }
        return CrewBook.Verdict.OK;
    }

    public static CrewBook.Verdict releaseByCommand(ServerPlayer player) {
        return release(player, resolveDrone(player).orElse(null));
    }

    public static void gimbal(CrewPackets.Gimbal update, ServerPlayer player) {
        if (!GIMBAL.tryAcquire(player.getUUID(), Util.getMillis())) {
            return;
        }
        Entity drone = player.level().getEntity(update.drone());
        if (drone == null || !PropwashApi.drones().isDrone(drone) || !CrewDrones.hasGimbal(drone)) {
            return;
        }
        Optional<UUID> bound = BOOK.operator(drone.getUUID());
        Optional<UUID> solo = SOLO.player(drone.getUUID());
        if (!controller(bound.or(() -> solo), CrewDrones.pilot(drone), player.getUUID())) {
            return;
        }
        GimbalData data = CrewData.get(drone);
        if (bound.isEmpty() && solo.isPresent()) {
            if (!data.soloBy(player.getUUID())) {
                data = data.withSolo(player.getUUID());
            }
            if (!data.steered()) {
                data = data.withSteered();
            } else if (!update.setting().differs(data.setting())) {
                return;
            }
        } else if (bound.isEmpty() && data.operated()) {
            data = data.withOperator(Optional.empty());
        } else if (!update.setting().differs(data.setting())) {
            return;
        }
        CrewData.set(drone, data.withSetting(update.setting()));
    }

    public static boolean controller(Optional<UUID> bound, Optional<UUID> pilot, UUID player) {
        return bound.isPresent() ? bound.get().equals(player) : pilot.filter(player::equals).isPresent();
    }

    public static void onPilotChanged(Entity drone, Optional<UUID> previous, Optional<UUID> current) {
        if (drone == null || drone.level().isClientSide()) {
            return;
        }
        if (current.isPresent() && SOLO.player(drone.getUUID()).isPresent()) {
            endSolo(drone.level().getServer(), drone.getUUID(), drone, SoloCamera.End.PILOTED);
        }
        Optional<UUID> bound = BOOK.operator(drone.getUUID());
        GimbalData data = CrewData.get(drone);
        if (bound.isEmpty() && data.operated()) {
            CrewData.set(drone, data.withOperator(Optional.empty()));
            return;
        }
        if (bound.isEmpty() || current.isEmpty()) {
            return;
        }
        End reason = pilotChange(bound.get(), current.get(), BOOK.inviter(drone.getUUID()), PlannerOwners.owner(drone));
        if (reason != null) {
            MinecraftServer server = drone.level().getServer();
            end(server, drone.getUUID(), drone, bound.get(), server == null ? null : server.getPlayerList().getPlayer(bound.get()), reason);
        }
    }

    public static @Nullable End pilotChange(UUID operator, UUID pilot, Optional<UUID> inviter, UUID owner) {
        if (pilot.equals(operator)) {
            return End.PILOT;
        }
        boolean invitedByPilot = inviter.filter(pilot::equals).isPresent();
        boolean ownerFlies = owner != null && !PlannerOwners.NO_OWNER.equals(owner) && owner.equals(pilot);
        return invitedByPilot || ownerFlies ? null : End.NEW_PILOT;
    }

    public static void tick(MinecraftServer server) {
        if (++ticks % CHECK_TICKS != 0) {
            return;
        }
        long now = Util.getMillis();
        for (CrewBook.Invite invite : BOOK.expire(now)) {
            ServerPlayer inviter = server.getPlayerList().getPlayer(invite.inviter());
            ServerPlayer invitee = server.getPlayerList().getPlayer(invite.invitee());
            if (inviter != null) {
                inviter.sendSystemMessage(Component.translatable(CrewTexts.INVITE_EXPIRED,
                        invitee == null ? invite.invitee().toString() : invitee.getPlainTextName()).withStyle(ChatFormatting.GRAY));
            }
        }
        for (Map.Entry<UUID, UUID> binding : BOOK.bindings().entrySet()) {
            UUID droneId = binding.getKey();
            UUID operatorId = binding.getValue();
            Entity drone = findDrone(server, droneId);
            ServerPlayer operator = server.getPlayerList().getPlayer(operatorId);
            if (drone == null || drone.isRemoved()) {
                end(server, droneId, drone, operatorId, operator, End.DRONE_GONE);
                continue;
            }
            Optional<CrewBook.Wait> wait = BOOK.waiting(droneId);
            Integer session = SESSIONS.get(droneId);
            boolean sameSession = operator == null || session == null || session == operator.getId();
            switch (watch(operator != null, sameSession, wait, now)) {
                case CHECK -> {
                    End reason = check(drone, operatorId, operator);
                    if (reason != null) {
                        end(server, droneId, drone, operatorId, operator, reason);
                    } else {
                        CamAwards.crewFlight(drone, operator);
                    }
                }
                case HOLD -> hold(server, droneId, drone, operatorId, now);
                case WAIT -> waitFor(server, droneId, drone, wait.orElseThrow(), now);
                case RESTORE -> restore(server, droneId, drone, operator, now);
                case EXPIRED -> end(server, droneId, drone, operatorId, operator, expired(operator != null));
            }
        }
        for (Map.Entry<UUID, UUID> session : SOLO.sessions().entrySet()) {
            watchSolo(server, session.getKey(), session.getValue());
        }
        ACTIONS.tracked(now);
        GIMBAL.tracked(now);
    }

    private static void watchSolo(MinecraftServer server, UUID droneId, UUID playerId) {
        Entity drone = findDrone(server, droneId);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        boolean bound = false;
        if (drone != null) {
            try {
                bound = PropwashApi.drones().operators().operator(drone).map(ServerPlayer::getUUID).filter(playerId::equals).isPresent();
            } catch (RuntimeException e) {
                bound = false;
            }
        }
        Optional<SoloCamera.End> end = SoloCamera.keep(new SoloCamera.Watch(drone != null && !drone.isRemoved(), player != null, bound,
                drone != null && CrewDrones.pilot(drone).isPresent(), drone != null && flyingItself(drone),
                BOOK.operator(droneId).isPresent()));
        end.ifPresent(reason -> endSolo(server, droneId, drone, reason));
    }

    public static Watch watch(boolean online, boolean sameSession, Optional<CrewBook.Wait> wait, long now) {
        if (wait.isPresent()) {
            if (wait.get().expired(now)) {
                return Watch.EXPIRED;
            }
            return online ? Watch.RESTORE : Watch.WAIT;
        }
        if (!online) {
            return Watch.HOLD;
        }
        return sameSession ? Watch.CHECK : Watch.RESTORE;
    }

    public static End expired(boolean online) {
        return online ? End.UNREACHABLE : End.OFFLINE;
    }

    public static String pilotText(End reason) {
        return switch (reason) {
            case OFFLINE -> CrewTexts.EXPIRED_PILOT;
            case UNREACHABLE -> CrewTexts.UNREACHABLE_PILOT;
            default -> CrewTexts.ENDED_PILOT;
        };
    }

    public static String operatorText(End reason) {
        return reason == End.UNREACHABLE ? CrewTexts.UNREACHABLE_OPERATOR : CrewTexts.ENDED_OPERATOR;
    }

    private static void hold(MinecraftServer server, UUID droneId, Entity drone, UUID operatorId, long now) {
        if (!CamWorldRules.allowOperator(drone.level())) {
            end(server, droneId, drone, operatorId, null, End.RULE_OFF);
            return;
        }
        String name = name(operatorId);
        if (!BOOK.hold(droneId, name, now)) {
            return;
        }
        String clock = CrewTexts.clock(CrewBook.GRACE_MILLIS);
        CamDrones.LOG.info("{}: Kamera-Operator {} von Drohne {} getrennt, Crew wartet {}", CamDrones.MOD_NAME, name, droneId, clock);
        for (ServerPlayer lead : leads(drone, server, BOOK.inviter(droneId))) {
            lead.sendSystemMessage(Component.translatable(CrewTexts.WAITING_PILOT, name, clock).withStyle(ChatFormatting.YELLOW));
            lead.sendOverlayMessage(Component.translatable(CrewTexts.WAITING_OVERLAY, clock).withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void waitFor(MinecraftServer server, UUID droneId, Entity drone, CrewBook.Wait wait, long now) {
        if (!CamWorldRules.allowOperator(drone.level())) {
            end(server, droneId, drone, wait.operator(), null, End.RULE_OFF);
            return;
        }
        BOOK.away(droneId);
        Component text = Component.translatable(CrewTexts.WAITING_OVERLAY, CrewTexts.clock(wait.remaining(now))).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer lead : leads(drone, server, BOOK.inviter(droneId))) {
            lead.sendOverlayMessage(text);
        }
    }

    private static void restore(MinecraftServer server, UUID droneId, Entity drone, ServerPlayer operator, long now) {
        End blocked = !CamWorldRules.allowOperator(drone.level()) ? End.RULE_OFF : CrewDrones.pilotedBy(drone, operator.getUUID()) ? End.PILOT : null;
        if (blocked != null) {
            end(server, droneId, drone, operator.getUUID(), operator, blocked);
            return;
        }
        yieldSolo(server, drone, operator);
        if (!bind(drone, operator)) {
            refused(server, droneId, drone, operator, now);
            return;
        }
        BOOK.resume(droneId);
        SESSIONS.put(droneId, operator.getId());
        NAMES.put(operator.getUUID(), operator.getPlainTextName());
        GimbalData data = CrewData.get(drone);
        if (!data.operatedBy(operator.getUUID())) {
            CrewData.set(drone, data.withOperator(Optional.of(operator.getUUID())));
        }
        Optional<UUID> inviter = BOOK.inviter(droneId);
        String inviterName = inviter.map(server.getPlayerList()::getPlayer).map(ServerPlayer::getPlainTextName).orElse("");
        operator.sendSystemMessage(Component.translatable(CrewTexts.RESTORED_OPERATOR).withStyle(ChatFormatting.GREEN));
        CamNetwork.sendToPlayer(operator, new CrewPackets.Notice(CrewPackets.Kind.BOUND, drone.getId(), inviterName));
        Component back = Component.translatable(CrewTexts.RESTORED_PILOT, operator.getPlainTextName()).withStyle(ChatFormatting.GREEN);
        for (ServerPlayer lead : leads(drone, server, inviter)) {
            if (!lead.getUUID().equals(operator.getUUID())) {
                lead.sendSystemMessage(back);
                lead.sendOverlayMessage(back);
            }
        }
        CamDrones.LOG.info("{}: Kamera-Operator {} von Drohne {} ist zurück, Crew wiederhergestellt", CamDrones.MOD_NAME, operator.getPlainTextName(),
                droneId);
    }

    private static void refused(MinecraftServer server, UUID droneId, Entity drone, ServerPlayer operator, long now) {
        String name = operator.getPlainTextName();
        boolean first = BOOK.refuse(droneId, name, now);
        Optional<CrewBook.Wait> wait = BOOK.waiting(droneId);
        if (wait.isEmpty()) {
            return;
        }
        String clock = CrewTexts.clock(wait.get().remaining(now));
        List<ServerPlayer> leads = new ArrayList<>(leads(drone, server, BOOK.inviter(droneId)));
        leads.removeIf(lead -> lead.getUUID().equals(operator.getUUID()));
        if (first) {
            CamDrones.LOG.info("{}: Kamera-Operator {} von Drohne {} ist zurück, Propwash lehnt die Bindung ab, Crew wartet noch {}", CamDrones.MOD_NAME,
                    name, droneId, clock);
            operator.sendSystemMessage(Component.translatable(CrewTexts.BLOCKED_OPERATOR, clock).withStyle(ChatFormatting.YELLOW));
            for (ServerPlayer lead : leads) {
                lead.sendSystemMessage(Component.translatable(CrewTexts.BLOCKED_PILOT, name, clock).withStyle(ChatFormatting.YELLOW));
            }
        }
        operator.sendOverlayMessage(Component.translatable(CrewTexts.BLOCKED_OPERATOR_OVERLAY, clock).withStyle(ChatFormatting.YELLOW));
        Component text = Component.translatable(CrewTexts.BLOCKED_PILOT_OVERLAY, clock).withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer lead : leads) {
            lead.sendOverlayMessage(text);
        }
    }

    static String name(UUID player) {
        return NAMES.getOrDefault(player, player.toString());
    }

    private static @Nullable End check(Entity drone, UUID operatorId, ServerPlayer operator) {
        if (!CamWorldRules.allowOperator(drone.level())) {
            return End.RULE_OFF;
        }
        if (CrewDrones.pilotedBy(drone, operatorId)) {
            return End.PILOT;
        }
        Optional<ServerPlayer> bound;
        try {
            bound = PropwashApi.drones().operators().operator(drone);
        } catch (RuntimeException e) {
            bound = Optional.empty();
        }
        return bound.map(ServerPlayer::getUUID).filter(operatorId::equals).isPresent() ? null : End.UNBOUND;
    }

    private static void end(@Nullable MinecraftServer server, UUID droneId, @Nullable Entity drone, UUID operatorId,
                            @Nullable ServerPlayer operator, End reason) {
        Optional<UUID> inviter = BOOK.inviter(droneId);
        BOOK.release(droneId);
        SESSIONS.remove(droneId);
        if (drone != null && !drone.isRemoved()) {
            unbind(drone);
        }
        CamDrones.LOG.info("{}: Kamera-Operator {} von Drohne {} beendet ({})", CamDrones.MOD_NAME, operatorId, droneId, reason);
        if (operator != null) {
            operator.sendSystemMessage(Component.translatable(operatorText(reason)).withStyle(ChatFormatting.GRAY));
            CamNetwork.sendToPlayer(operator, new CrewPackets.Notice(CrewPackets.Kind.RELEASED, drone == null ? -1 : drone.getId(), ""));
        }
        if (drone != null && server != null) {
            String name = operator == null ? name(operatorId) : operator.getPlainTextName();
            String key = pilotText(reason);
            for (ServerPlayer lead : leads(drone, server, inviter)) {
                if (!lead.getUUID().equals(operatorId)) {
                    lead.sendSystemMessage(Component.translatable(key, name).withStyle(ChatFormatting.GRAY));
                }
            }
        }
    }

    static boolean bind(Entity drone, ServerPlayer operator) {
        try {
            return PropwashApi.drones().operators().bind(drone, operator);
        } catch (RuntimeException e) {
            CamDrones.LOG.warn("{}: Operator-Bindung fehlgeschlagen: {}", CamDrones.MOD_NAME, e.toString());
            return false;
        }
    }

    static void unbind(@Nullable Entity drone) {
        if (drone == null) {
            return;
        }
        try {
            PropwashApi.drones().operators().unbind(drone);
        } catch (RuntimeException e) {
            CamDrones.LOG.warn("{}: Operator-Lösung fehlgeschlagen: {}", CamDrones.MOD_NAME, e.toString());
        }
        GimbalData data = CrewData.get(drone);
        if (data.operated()) {
            CrewData.set(drone, data.withOperator(Optional.empty()));
        }
    }

    public static boolean controls(ServerPlayer player, Entity drone) {
        return controls(player.getUUID(), CrewDrones.pilot(drone), PlannerOwners.owner(drone));
    }

    public static boolean mayInvite(ServerPlayer player, Entity drone) {
        boolean near = player.level() == drone.level() && player.distanceTo(drone) <= CrewBook.MAX_DISTANCE;
        return mayInvite(player.getUUID(), CrewDrones.pilot(drone), PlannerOwners.owner(drone), near);
    }

    public static boolean controls(UUID player, Optional<UUID> pilot, UUID owner) {
        return pilot.filter(player::equals).isPresent() || owner != null && !PlannerOwners.NO_OWNER.equals(owner) && owner.equals(player);
    }

    public static boolean mayInvite(UUID player, Optional<UUID> pilot, UUID owner, boolean near) {
        if (controls(player, pilot, owner)) {
            return true;
        }
        return near && pilot.isEmpty() && (owner == null || PlannerOwners.NO_OWNER.equals(owner));
    }

    static List<ServerPlayer> leads(Entity drone, MinecraftServer server, Optional<UUID> inviter) {
        List<UUID> ids = new ArrayList<>();
        CrewDrones.pilot(drone).ifPresent(ids::add);
        UUID owner = PlannerOwners.owner(drone);
        if (!PlannerOwners.NO_OWNER.equals(owner)) {
            ids.add(owner);
        }
        inviter.ifPresent(ids::add);
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : ids.stream().distinct().toList()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                out.add(player);
            }
        }
        return out;
    }

    public static @Nullable Entity findDrone(MinecraftServer server, UUID droneId) {
        if (server == null || droneId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(droneId);
            if (entity != null && !entity.isRemoved()) {
                return entity;
            }
        }
        return null;
    }

    public static Optional<Entity> resolveDrone(ServerPlayer player) {
        ServerLevel level = player.level();
        UUID me = player.getUUID();
        for (DroneView view : PropwashApi.drones().dronesNear(level, player.position(), PILOT_SEARCH, false)) {
            if (view.pilot().filter(me::equals).isPresent() && CrewDrones.hasGimbal(view.entity())) {
                return Optional.of(view.entity());
            }
        }
        for (InteractionHand hand : InteractionHand.values()) {
            Optional<Entity> linked = PlannerTablets.link(player.getItemInHand(hand)).flatMap(link -> PlannerTablets.findDrone(level, link.serial()));
            if (linked.isPresent() && CrewDrones.hasGimbal(linked.get()) && controls(player, linked.get())) {
                return linked;
            }
        }
        Entity best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (DroneView view : PropwashApi.drones().dronesNear(level, player.position(), CrewBook.MAX_DISTANCE, false)) {
            Entity entity = view.entity();
            if (!CrewDrones.hasGimbal(entity) || !mayInvite(player, entity)) {
                continue;
            }
            double distance = entity.distanceTo(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return Optional.ofNullable(best);
    }

    static MutableComponent invitation(String inviter) {
        MutableComponent accept = Component.translatable(CrewTexts.INVITE_ACCEPT).withStyle(style -> style.withColor(ChatFormatting.GREEN)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(ACCEPT_COMMAND))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(CrewTexts.INVITE_ACCEPT_HOVER))));
        MutableComponent decline = Component.translatable(CrewTexts.INVITE_DECLINE).withStyle(style -> style.withColor(ChatFormatting.RED)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(DECLINE_COMMAND))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(CrewTexts.INVITE_DECLINE_HOVER))));
        return Component.translatable(CrewTexts.INVITE_RECEIVED, inviter).withStyle(ChatFormatting.AQUA).append(" ").append(accept)
                .append(" ").append(decline);
    }

    static void tellVerdict(ServerPlayer player, CrewBook.Verdict verdict, String name) {
        if (verdict.ok()) {
            return;
        }
        player.sendSystemMessage(Component.translatable(CrewTexts.verdict(verdict), name, (int) CrewBook.MAX_DISTANCE)
                .withStyle(ChatFormatting.RED));
    }

    public static void reset() {
        BOOK.clear();
        SOLO.clear();
        SESSIONS.clear();
        NAMES.clear();
        ticks = 0;
    }
}
