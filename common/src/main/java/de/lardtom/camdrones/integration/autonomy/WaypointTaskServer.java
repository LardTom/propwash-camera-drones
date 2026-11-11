package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.TaskTracker;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.mission.WaypointTasks;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.planner.LightControl;
import de.lardtom.camdrones.planner.PlannerSupport;
import de.lardtom.camdrones.planner.model.DroneSupport;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WaypointTaskServer {
    public static final String PREFIX = CamDrones.MOD_ID + ".tasks.";
    public static final String RELEASE_TASK = "gripper.release";

    public enum Outcome {
        DONE,
        SKIPPED,
        DENIED
    }

    public record Report(UUID drone, int index, String task, Outcome outcome, DroneSupport.Skip skip, long tick, boolean timed) {
    }

    private static final Map<UUID, Watch> WATCHED = new HashMap<>();
    private static final List<Report> REPORTS = new CopyOnWriteArrayList<>();
    private static final int MAX_REPORTS = 256;

    private static final class Watch {
        final Entity drone;
        final TaskTracker tracker = new TaskTracker();
        final Set<String> warned = new HashSet<>();
        final Set<String> timedSeen = new HashSet<>();
        final MissionGripper gripper = new MissionGripper();
        boolean gripFailed;

        Watch(Entity drone) {
            this.drone = drone;
        }
    }

    private WaypointTaskServer() {
    }

    public static synchronized void observe(Entity drone, MissionStatus status) {
        if (drone == null || status == null || !(drone.level() instanceof ServerLevel) || !status.active()) {
            return;
        }
        WATCHED.computeIfAbsent(drone.getUUID(), id -> new Watch(drone));
    }

    public static synchronized int watched() {
        return WATCHED.size();
    }

    public static List<Report> reports() {
        return List.copyOf(REPORTS);
    }

    public static void clearReports() {
        REPORTS.clear();
    }

    public static void tick(MinecraftServer server) {
        List<Watch> watches;
        synchronized (WaypointTaskServer.class) {
            if (WATCHED.isEmpty()) {
                return;
            }
            watches = new ArrayList<>(WATCHED.values());
        }
        for (Watch watch : watches) {
            boolean keep;
            try {
                keep = update(watch);
            } catch (RuntimeException e) {
                CamDrones.LOG.warn("{}: Wegpunkt-Aktionen für Drohne {} fehlgeschlagen: {}", CamDrones.MOD_NAME, watch.drone.getUUID(), e.toString());
                keep = false;
            }
            if (!keep) {
                synchronized (WaypointTaskServer.class) {
                    WATCHED.remove(watch.drone.getUUID(), watch);
                }
            }
        }
    }

    private static boolean update(Watch watch) {
        Entity drone = watch.drone;
        if (drone.isRemoved() || !(drone.level() instanceof ServerLevel)) {
            return false;
        }
        MissionStatus status = AutonomyServer.missionStatus(drone);
        Optional<Mission> mission = AutonomyServer.mission(drone);
        if (mission.isPresent()) {
            List<WaypointTasks.Due> due = watch.tracker.update(mission.get(), status.state(), status.index(), drone.getX(), drone.getY(),
                    drone.getZ(), drone.level().getGameTime());
            for (WaypointTasks.Due task : due) {
                run(watch, task);
            }
        }
        grip(watch, mission.isPresent() ? status.state() : NavLogic.MissionState.IDLE);
        return mission.isPresent() && status.active();
    }

    static void run(Watch watch, WaypointTasks.Due due) {
        ServerPlayer owner = owner(watch.drone);
        DroneSupport support = PlannerSupport.of(watch.drone, false);
        if (due.light() != Waypoint.Light.NONE) {
            light(watch, owner, support, due);
        }
        if (due.accessory() > 0) {
            accessory(watch, owner, support, due);
        }
        if (due.release()) {
            release(watch, owner, support, due);
        }
    }

    private static ServerPlayer owner(Entity drone) {
        UUID ownerId = AutonomyServer.owner(drone);
        return ownerId != null && drone.level() instanceof ServerLevel level ? level.getServer().getPlayerList().getPlayer(ownerId) : null;
    }

    private static boolean present(ServerPlayer owner, Entity drone) {
        return owner != null && !owner.isRemoved() && owner.level() == drone.level();
    }

    private static void grip(Watch watch, NavLogic.MissionState state) {
        Entity drone = watch.drone;
        long now = drone.level().getGameTime();
        try {
            boolean loaded = !PropwashApi.accessories().cargo(drone).isEmpty();
            if (watch.gripper.renew(state, loaded, now)) {
                ServerPlayer owner = owner(drone);
                if (present(owner, drone)) {
                    PropwashApi.accessories().grabCargo(drone, owner);
                }
            } else if (watch.gripper.close(state, loaded, now)) {
                ServerPlayer owner = owner(drone);
                if (present(owner, drone)) {
                    PropwashApi.accessories().releaseCargo(drone, owner);
                }
            }
        } catch (RuntimeException e) {
            if (!watch.gripFailed) {
                watch.gripFailed = true;
                CamDrones.LOG.warn("{}: Greifer der Drohne {} in der Mission nicht bereit: {}", CamDrones.MOD_NAME, AutonomyServer.label(drone),
                        e.toString());
            }
        }
    }

    private static void release(Watch watch, ServerPlayer owner, DroneSupport support, WaypointTasks.Due due) {
        DroneSupport.Skip skip = support.release();
        if (skip.skipped()) {
            report(watch, owner, due, RELEASE_TASK, Outcome.SKIPPED, skip, Optional.empty());
            return;
        }
        if (!present(owner, watch.drone)) {
            report(watch, null, due, RELEASE_TASK, Outcome.DENIED, DroneSupport.Skip.NONE, Optional.empty());
            return;
        }
        int count;
        try {
            count = PropwashApi.accessories().releaseCargo(watch.drone, owner);
        } catch (RuntimeException e) {
            CamDrones.LOG.warn("{}: Greifer an Wegpunkt {} nicht ausgelöst: {}", CamDrones.MOD_NAME, due.index() + 1, e.toString());
            report(watch, owner, due, RELEASE_TASK, Outcome.SKIPPED, DroneSupport.Skip.NO_GRIPPER, Optional.empty());
            return;
        }
        CamDrones.LOG.info("{}: Greifer der Drohne {} an Wegpunkt {} ausgelöst, {} Items fallen gelassen", CamDrones.MOD_NAME,
                AutonomyServer.label(watch.drone), due.index() + 1, count);
        report(watch, owner, due, RELEASE_TASK, Outcome.DONE, DroneSupport.Skip.NONE, Optional.empty());
    }

    private static void light(Watch watch, ServerPlayer owner, DroneSupport support, WaypointTasks.Due due) {
        DroneSupport.Skip skip = support.light(due.light());
        String task = "light." + due.light().name().toLowerCase(Locale.ROOT);
        if (skip == DroneSupport.Skip.NONE && LightControl.apply(watch.drone, due.light())) {
            report(watch, owner, due, task, Outcome.DONE, DroneSupport.Skip.NONE, Optional.empty());
            return;
        }
        report(watch, owner, due, task, Outcome.SKIPPED, skip == DroneSupport.Skip.NONE ? DroneSupport.Skip.NO_LIGHT_API : skip, Optional.empty());
    }

    private static void accessory(Watch watch, ServerPlayer owner, DroneSupport support, WaypointTasks.Due due) {
        String task = "accessory." + due.accessory();
        DroneSupport.Skip skip = support.accessory(due.accessory());
        if (skip.skipped()) {
            report(watch, owner, due, task, Outcome.SKIPPED, skip, Optional.empty());
            return;
        }
        if (!present(owner, watch.drone)) {
            report(watch, null, due, task, Outcome.DENIED, DroneSupport.Skip.NONE, Optional.empty());
            return;
        }
        boolean reached;
        try {
            reached = PropwashApi.accessories().triggerAction(watch.drone, due.accessory(), true, owner);
            PropwashApi.accessories().triggerAction(watch.drone, due.accessory(), false, owner);
        } catch (RuntimeException e) {
            CamDrones.LOG.warn("{}: Zubehör-Aktion {} an Wegpunkt {} fehlgeschlagen: {}", CamDrones.MOD_NAME, due.accessory(), due.index() + 1,
                    e.toString());
            reached = false;
        }
        if (!reached) {
            report(watch, owner, due, task, Outcome.SKIPPED, DroneSupport.Skip.NO_ACCESSORY, Optional.empty());
            return;
        }
        report(watch, owner, due, task, Outcome.DONE, DroneSupport.Skip.NONE, support.actionName(due.accessory()));
    }

    private static void report(Watch watch, ServerPlayer owner, WaypointTasks.Due due, String task, Outcome outcome, DroneSupport.Skip skip,
                               Optional<String> actionName) {
        Report entry = new Report(watch.drone.getUUID(), due.index(), task, outcome, skip, watch.drone.level().getGameTime(), due.timed());
        if (REPORTS.size() >= MAX_REPORTS) {
            REPORTS.removeFirst();
        }
        REPORTS.add(entry);
        boolean quiet = due.timed() && !watch.timedSeen.add(due.index() + ":" + task + ":" + outcome);
        if (!quiet) {
            CamDrones.LOG.info("{}: Wegpunkt {} an Drohne {}: {} {}{}{}", CamDrones.MOD_NAME, due.index() + 1, AutonomyServer.label(watch.drone), task,
                    outcome, skip == DroneSupport.Skip.NONE ? "" : " (" + skip + ")", due.timed() ? " im Takt alle " + due.everyTicks() + " Ticks" : "");
        }
        if (owner == null || quiet) {
            return;
        }
        String waypoint = Integer.toString(due.index() + 1);
        Component drone = AutonomyServer.nameText(watch.drone);
        Component what = Component.translatable(PREFIX + task);
        switch (outcome) {
            case DONE -> {
                if (due.timed()) {
                    return;
                }
                MutableComponent text = Component.translatable(PREFIX + "done", drone, waypoint, what);
                actionName.ifPresent(name -> text.append(Component.literal(" – ")).append(Component.translatable(name)));
                owner.sendOverlayMessage(text);
            }
            case SKIPPED -> {
                if (watch.warned.add(due.index() + ":" + task)) {
                    owner.sendSystemMessage(Component.translatable(PREFIX + "skipped", drone, waypoint, what, Component.translatable(skip.key()))
                            .withStyle(ChatFormatting.GOLD));
                }
            }
            case DENIED -> {
            }
        }
    }
}
