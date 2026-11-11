package de.lardtom.camdrones.logic.mission;

import de.lardtom.camdrones.logic.nav.ObstacleGuard;

import java.util.ArrayList;
import java.util.List;

public record Mission(boolean smooth, boolean loop, End end, int baseSpeedDmS, ObstacleGuard.Mode obstacle, List<Waypoint> waypoints,
                      List<PointOfInterest> pointsOfInterest) {
    public static final int DEFAULT_BASE_SPEED_DM_S = 50;

    public enum End {
        HOVER,
        START,
        LAND
    }

    public Mission {
        MissionLimits.require(end != null, "Ende fehlt");
        MissionLimits.require(obstacle != null, "Hindernis-Modus fehlt");
        MissionLimits.require(baseSpeedDmS >= MissionLimits.MIN_SPEED_DM_S && baseSpeedDmS <= MissionLimits.MAX_SPEED_DM_S,
                "Grundtempo ungültig: " + baseSpeedDmS);
        MissionLimits.require(waypoints != null && pointsOfInterest != null, "Listen fehlen");
        waypoints = List.copyOf(waypoints);
        pointsOfInterest = List.copyOf(pointsOfInterest);
        MissionLimits.require(waypoints.size() <= MissionLimits.MAX_WAYPOINTS, "zu viele Wegpunkte: " + waypoints.size());
        MissionLimits.require(pointsOfInterest.size() <= MissionLimits.MAX_POINTS_OF_INTEREST,
                "zu viele Points of Interest: " + pointsOfInterest.size());
        for (Waypoint waypoint : waypoints) {
            MissionLimits.require(waypoint.heading() != Waypoint.Heading.POI || waypoint.poi() < pointsOfInterest.size(),
                    "Wegpunkt zeigt auf fehlenden Point of Interest " + waypoint.poi());
        }
    }

    public static Mission of(List<Waypoint> waypoints) {
        return new Mission(false, false, End.HOVER, DEFAULT_BASE_SPEED_DM_S, ObstacleGuard.Mode.STOP, waypoints, List.of());
    }

    public static Mission of(List<Waypoint> waypoints, List<PointOfInterest> pointsOfInterest) {
        return new Mission(false, false, End.HOVER, DEFAULT_BASE_SPEED_DM_S, ObstacleGuard.Mode.STOP, waypoints, pointsOfInterest);
    }

    public static Mission empty() {
        return of(List.of());
    }

    public int size() {
        return this.waypoints.size();
    }

    public boolean isEmpty() {
        return this.waypoints.isEmpty();
    }

    public Waypoint waypoint(int index) {
        return this.waypoints.get(index);
    }

    public double baseSpeed() {
        return this.baseSpeedDmS / 10.0;
    }

    public double speedTo(int index) {
        Waypoint waypoint = this.waypoints.get(index);
        return waypoint.speedDmS() > 0 ? waypoint.speed() : this.baseSpeed();
    }

    public Mission withSmooth(boolean value) {
        return new Mission(value, this.loop, this.end, this.baseSpeedDmS, this.obstacle, this.waypoints, this.pointsOfInterest);
    }

    public Mission withLoop(boolean value) {
        return new Mission(this.smooth, value, this.end, this.baseSpeedDmS, this.obstacle, this.waypoints, this.pointsOfInterest);
    }

    public Mission withEnd(End value) {
        return new Mission(this.smooth, this.loop, value, this.baseSpeedDmS, this.obstacle, this.waypoints, this.pointsOfInterest);
    }

    public Mission withBaseSpeed(double metresPerSecond) {
        return new Mission(this.smooth, this.loop, this.end, (int) Math.round(metresPerSecond * 10.0), this.obstacle, this.waypoints,
                this.pointsOfInterest);
    }

    public Mission withObstacle(ObstacleGuard.Mode value) {
        return new Mission(this.smooth, this.loop, this.end, this.baseSpeedDmS, value, this.waypoints, this.pointsOfInterest);
    }

    public Mission withWaypoints(List<Waypoint> value) {
        return new Mission(this.smooth, this.loop, this.end, this.baseSpeedDmS, this.obstacle, value, this.pointsOfInterest);
    }

    public Mission withPointsOfInterest(List<PointOfInterest> value) {
        return new Mission(this.smooth, this.loop, this.end, this.baseSpeedDmS, this.obstacle, this.waypoints, value);
    }

    public Mission plus(Waypoint waypoint) {
        List<Waypoint> list = new ArrayList<>(this.waypoints);
        list.add(waypoint);
        return this.withWaypoints(list);
    }

    public Mission replace(int index, Waypoint waypoint) {
        List<Waypoint> list = new ArrayList<>(this.waypoints);
        list.set(index, waypoint);
        return this.withWaypoints(list);
    }

    public Mission remove(int index) {
        List<Waypoint> list = new ArrayList<>(this.waypoints);
        list.remove(index);
        return this.withWaypoints(list);
    }
}
