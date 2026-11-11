package de.lardtom.camdrones.planner.model;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MissionTemplates {
    public static final double CIRCLE_RADIUS = 20.0;
    public static final int CIRCLE_POINTS = 12;
    public static final double CIRCLE_HEIGHT = 15.0;
    public static final double REVEAL_NEAR = 8.0;
    public static final double REVEAL_FAR = 45.0;
    public static final double REVEAL_START_HEIGHT = 3.0;
    public static final double REVEAL_END_HEIGHT = 30.0;
    public static final int REVEAL_POINTS = 4;
    public static final int REVEAL_SPEED_DM_S = 30;
    public static final int REVEAL_START_TILT = 0;
    public static final int REVEAL_END_TILT = -30;
    public static final double LINE_LENGTH = 60.0;
    public static final double LINE_SPACING = 10.0;
    public static final double LINE_HEIGHT = 10.0;

    public enum Template {
        CIRCLE,
        REVEAL,
        LINE;

        public String labelKey() {
            return FieldText.PREFIX + "template." + this.name().toLowerCase(Locale.ROOT);
        }

        public String tooltipKey() {
            return this.labelKey() + ".tooltip";
        }
    }

    public record Anchor(double x, double groundY, double z, double yawDegrees) {
    }

    private MissionTemplates() {
    }

    public static Optional<Mission> apply(Template template, Mission mission, Anchor anchor) {
        return switch (template) {
            case CIRCLE -> circle(mission, anchor);
            case REVEAL -> reveal(mission, anchor);
            case LINE -> line(mission, anchor);
        };
    }

    static Optional<Mission> circle(Mission mission, Anchor anchor) {
        if (mission.size() + CIRCLE_POINTS > MissionLimits.MAX_WAYPOINTS) {
            return Optional.empty();
        }
        List<PointOfInterest> points = new ArrayList<>(mission.pointsOfInterest());
        int poi = indexOf(points, anchor);
        if (poi < 0) {
            if (points.size() >= MissionLimits.MAX_POINTS_OF_INTEREST) {
                return Optional.empty();
            }
            points.add(PointOfInterest.at(anchor.x(), WaypointPlacement.clampY(anchor.groundY()), anchor.z(), CIRCLE_RADIUS));
            poi = points.size() - 1;
        }
        PointOfInterest center = points.get(poi);
        double radius = center.radius() > 0.0 ? center.radius() : CIRCLE_RADIUS;
        double y = WaypointPlacement.clampY(anchor.groundY() + CIRCLE_HEIGHT);
        int tilt = tiltTowards(CIRCLE_HEIGHT, radius);
        List<Waypoint> list = new ArrayList<>(mission.waypoints());
        double start = Math.toRadians(anchor.yawDegrees() + 180.0);
        for (int i = 0; i < CIRCLE_POINTS; i++) {
            double angle = start + 2.0 * Math.PI * i / CIRCLE_POINTS;
            double x = center.x() - Math.sin(angle) * radius;
            double z = center.z() + Math.cos(angle) * radius;
            list.add(Waypoint.at(x, y, z).facing(poi).withTilt(tilt));
        }
        return Optional.of(mission.withPointsOfInterest(points).withWaypoints(list).withSmooth(true));
    }

    static Optional<Mission> reveal(Mission mission, Anchor anchor) {
        if (mission.size() + REVEAL_POINTS > MissionLimits.MAX_WAYPOINTS) {
            return Optional.empty();
        }
        List<PointOfInterest> points = new ArrayList<>(mission.pointsOfInterest());
        int poi = indexOf(points, anchor);
        if (poi < 0) {
            if (points.size() >= MissionLimits.MAX_POINTS_OF_INTEREST) {
                return Optional.empty();
            }
            points.add(PointOfInterest.at(anchor.x(), WaypointPlacement.clampY(anchor.groundY() + REVEAL_START_HEIGHT), anchor.z(), 0.0));
            poi = points.size() - 1;
        }
        double yaw = Math.toRadians(anchor.yawDegrees());
        double backX = Math.sin(yaw);
        double backZ = -Math.cos(yaw);
        List<Waypoint> list = new ArrayList<>(mission.waypoints());
        for (int i = 0; i < REVEAL_POINTS; i++) {
            double f = i / (double) (REVEAL_POINTS - 1);
            double distance = REVEAL_NEAR + (REVEAL_FAR - REVEAL_NEAR) * f;
            double height = REVEAL_START_HEIGHT + (REVEAL_END_HEIGHT - REVEAL_START_HEIGHT) * f;
            int tilt = (int) Math.round(REVEAL_START_TILT + (REVEAL_END_TILT - REVEAL_START_TILT) * f);
            Waypoint w = Waypoint.at(anchor.x() + backX * distance, WaypointPlacement.clampY(anchor.groundY() + height),
                    anchor.z() + backZ * distance).facing(poi).withTilt(tilt);
            list.add(w.withSpeedDm(REVEAL_SPEED_DM_S));
        }
        return Optional.of(mission.withPointsOfInterest(points).withWaypoints(list).withSmooth(true));
    }

    static Optional<Mission> line(Mission mission, Anchor anchor) {
        List<Waypoint> list = new ArrayList<>(mission.waypoints());
        if (list.size() >= 2) {
            List<Waypoint> inner = between(list.get(list.size() - 2), list.getLast());
            if (inner.isEmpty() || list.size() + inner.size() > MissionLimits.MAX_WAYPOINTS) {
                return Optional.empty();
            }
            list.addAll(list.size() - 1, inner);
            return Optional.of(mission.withWaypoints(list));
        }
        Waypoint start = list.isEmpty() ? Waypoint.at(anchor.x(), WaypointPlacement.clampY(anchor.groundY() + LINE_HEIGHT), anchor.z())
                : list.getFirst();
        double yaw = Math.toRadians(anchor.yawDegrees());
        Waypoint end = Waypoint.at(start.x() - Math.sin(yaw) * LINE_LENGTH, start.y(), start.z() + Math.cos(yaw) * LINE_LENGTH);
        List<Waypoint> added = new ArrayList<>();
        if (list.isEmpty()) {
            added.add(start);
        }
        added.addAll(between(start, end));
        added.add(end);
        if (list.size() + added.size() > MissionLimits.MAX_WAYPOINTS) {
            return Optional.empty();
        }
        list.addAll(added);
        return Optional.of(mission.withWaypoints(list));
    }

    static List<Waypoint> between(Waypoint a, Waypoint b) {
        double length = a.distanceTo(b.x(), b.y(), b.z());
        int segments = (int) Math.ceil(length / LINE_SPACING - 1.0E-9);
        List<Waypoint> inner = new ArrayList<>();
        for (int i = 1; i < segments; i++) {
            double f = i / (double) segments;
            inner.add(Waypoint.at(a.x() + (b.x() - a.x()) * f, a.y() + (b.y() - a.y()) * f, a.z() + (b.z() - a.z()) * f));
        }
        return inner;
    }

    static int indexOf(List<PointOfInterest> points, Anchor anchor) {
        for (int i = 0; i < points.size(); i++) {
            PointOfInterest p = points.get(i);
            if (Math.hypot(p.x() - anchor.x(), p.z() - anchor.z()) < 1.0) {
                return i;
            }
        }
        return -1;
    }

    static int tiltTowards(double height, double distance) {
        int tilt = (int) Math.round(-Math.toDegrees(Math.atan2(height, Math.max(0.1, distance))));
        return Math.clamp(tilt, MissionLimits.MIN_TILT, MissionLimits.MAX_TILT);
    }
}
