package de.lardtom.camdrones.client.planner;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.mission.WaypointTasks;
import de.lardtom.camdrones.logic.nav.CurvedTurn;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.model.DroneSupport;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.TabletState;
import de.lardtom.camdrones.planner.model.WaypointField;
import de.lardtom.camdrones.planner.model.WaypointPlacement;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

public final class WaypointPreview {
    public static final double MAX_DISTANCE = 192.0;
    public static final int PATH = 0xFFFFBB00;
    public static final int PATH_LOOP = 0xA0FFBB00;
    public static final int MARKER = 0xFFFFFFFF;
    public static final int MARKER_FIRST = 0xFF5BC85B;
    public static final int MARKER_AIMED = 0xFFF2665E;
    public static final int DROP = 0x80B0B0B0;
    public static final int POI = 0xFF4EA1F3;
    public static final float LINE_WIDTH = 2.5F;
    public static final float MARKER_SIZE = 8.0F;
    public static final float AIMED_SIZE = 12.0F;
    public static final float LABEL_SCALE = 0.5F;
    public static final double LABEL_LIFT = 0.7;
    public static final double TASK_LIFT = 0.45;
    public static final int TASK = 0xFF7FD7FF;
    public static final float TASK_SCALE = 0.5F;

    private static int drawn;

    private WaypointPreview() {
    }

    public static int lastDrawn() {
        return drawn;
    }

    public static ItemStack heldPlanner(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (PlannerItems.isPlanner(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static void tick(LocalPlayer player) {
        drawn = 0;
        if (!PlannerItems.registered() || !CamClientConfig.get().previewMarkers()) {
            return;
        }
        ItemStack stack = heldPlanner(player);
        if (stack.isEmpty()) {
            return;
        }
        TabletState state = PlannerClient.tabletState(player, stack);
        Mission mission = state.activeMission();
        if (mission.isEmpty() && mission.pointsOfInterest().isEmpty()) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        OptionalInt aimed = player.isSecondaryUseActive()
                ? WaypointPlacement.pick(mission.waypoints(), eye.x, eye.y, eye.z, look.x, look.y, look.z, WaypointPlacement.MARKER_REACH,
                WaypointPlacement.MARKER_RADIUS)
                : OptionalInt.empty();
        emit(player.level(), player.position(), mission, aimed);
    }

    static void emit(Level level, Vec3 viewer, Mission mission, OptionalInt aimed) {
        List<Waypoint> waypoints = mission.waypoints();
        path(viewer, CurvedTurn.path(mission, null), PATH);
        path(viewer, CurvedTurn.loopPath(mission), PATH_LOOP);
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint w = waypoints.get(i);
            Vec3 pos = new Vec3(w.x(), w.y(), w.z());
            if (!near(viewer, pos)) {
                continue;
            }
            boolean hit = aimed.isPresent() && aimed.getAsInt() == i;
            int color = hit ? MARKER_AIMED : i == 0 ? MARKER_FIRST : MARKER;
            Gizmos.point(pos, color, hit ? AIMED_SIZE : MARKER_SIZE);
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(w.x()), (int) Math.floor(w.z()));
            if (ground < pos.y - 0.5) {
                Gizmos.line(pos, new Vec3(pos.x, ground, pos.z), DROP, 1.0F);
            }
            Gizmos.billboardText(Integer.toString(i + 1), pos.add(0.0, LABEL_LIFT, 0.0),
                    TextGizmo.Style.forColorAndCentered(color).withScale(LABEL_SCALE));
            WaypointTasks.Due due = WaypointTasks.due(mission, i);
            if (due.any()) {
                Gizmos.billboardText(taskLabel(due), pos.add(0.0, LABEL_LIFT + TASK_LIFT, 0.0),
                        TextGizmo.Style.forColorAndCentered(TASK).withScale(TASK_SCALE));
            }
            drawn++;
        }
        List<PointOfInterest> points = mission.pointsOfInterest();
        for (int i = 0; i < points.size(); i++) {
            PointOfInterest p = points.get(i);
            Vec3 pos = new Vec3(p.x(), p.y(), p.z());
            if (!near(viewer, pos)) {
                continue;
            }
            if (p.radius() > 0.0) {
                Gizmos.circle(pos, (float) p.radius(), GizmoStyle.stroke(POI, 2.0F));
            }
            Gizmos.point(pos, POI, MARKER_SIZE);
            Gizmos.billboardText("P" + (i + 1), pos.add(0.0, LABEL_LIFT, 0.0), TextGizmo.Style.forColorAndCentered(POI).withScale(LABEL_SCALE));
            drawn++;
        }
    }

    private static void path(Vec3 viewer, List<double[]> points, int color) {
        for (int i = 1; i < points.size(); i++) {
            double[] a = points.get(i - 1);
            double[] b = points.get(i);
            Vec3 from = new Vec3(a[0], a[1], a[2]);
            Vec3 to = new Vec3(b[0], b[1], b[2]);
            if (near(viewer, from) || near(viewer, to)) {
                Gizmos.line(from, to, color, LINE_WIDTH);
            }
        }
    }

    public static String taskLabel(WaypointTasks.Due due) {
        List<String> parts = new ArrayList<>();
        if (due.light() != Waypoint.Light.NONE) {
            String light = I18n.get(FieldText.PREFIX + "value.light." + due.light().name().toLowerCase(Locale.ROOT));
            parts.add(I18n.get(FieldText.PREFIX + "support.marker.light", light));
        }
        if (due.accessory() > 0 && due.everyTicks() > 0) {
            String separator = I18n.get(FieldText.PREFIX + "decimal");
            String seconds = WaypointField.seconds(due.everyTicks(), separator.length() == 1 ? separator.charAt(0) : '.');
            parts.add(I18n.get(FieldText.PREFIX + "support.marker.every", due.accessory(), due.everyTicks(), seconds));
        } else if (due.accessory() > 0) {
            parts.add(I18n.get(FieldText.PREFIX + (due.repeated() ? "support.marker.repeated" : "support.marker.accessory"), due.accessory()));
        }
        if (due.release()) {
            parts.add(I18n.get(FieldText.PREFIX + "support.marker.release"));
        }
        return String.join(" · ", parts);
    }

    private static boolean near(Vec3 viewer, Vec3 pos) {
        return viewer.distanceToSqr(pos) <= MAX_DISTANCE * MAX_DISTANCE;
    }
}
