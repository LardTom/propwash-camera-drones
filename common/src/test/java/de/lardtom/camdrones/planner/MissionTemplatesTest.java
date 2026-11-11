package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.planner.model.MissionTemplates;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionTemplatesTest {
    private static final MissionTemplates.Anchor ANCHOR = new MissionTemplates.Anchor(100.0, 64.0, -50.0, 0.0);

    @Test
    void circleAroundNewPointOfInterest() {
        Mission mission = MissionTemplates.apply(MissionTemplates.Template.CIRCLE, Mission.empty(), ANCHOR).orElseThrow();
        assertEquals(1, mission.pointsOfInterest().size());
        PointOfInterest center = mission.pointsOfInterest().getFirst();
        assertEquals(MissionTemplates.CIRCLE_POINTS, mission.size());
        assertTrue(mission.smooth());
        for (Waypoint w : mission.waypoints()) {
            assertEquals(MissionTemplates.CIRCLE_RADIUS, Math.hypot(w.x() - center.x(), w.z() - center.z()), 0.15);
            assertEquals(64.0 + MissionTemplates.CIRCLE_HEIGHT, w.y(), 0.05);
            assertEquals(Waypoint.Heading.POI, w.heading());
            assertEquals(0, w.poi());
            assertTrue(w.tiltDeg() < 0, "Kamera nach unten zur Mitte");
        }
        Waypoint first = mission.waypoints().getFirst();
        assertTrue(first.z() < center.z(), "Start auf der Seite des Spielers (Blick nach Süden)");
        double step = 2.0 * Math.PI * MissionTemplates.CIRCLE_RADIUS / MissionTemplates.CIRCLE_POINTS;
        for (int i = 0; i < mission.size(); i++) {
            Waypoint a = mission.waypoint(i);
            Waypoint b = mission.waypoint((i + 1) % mission.size());
            double gap = Math.hypot(b.x() - a.x(), b.z() - a.z());
            assertEquals(step, gap, 0.6, "Abstand " + (i + 1) + " zu " + ((i + 1) % mission.size() + 1) + ": kein Punkt liegt auf einem anderen");
        }
    }

    @Test
    void circleReusesExistingPointOfInterest() {
        Mission base = Mission.of(List.of(), List.of(PointOfInterest.at(100.2, 70, -49.8, 30)));
        Mission mission = MissionTemplates.apply(MissionTemplates.Template.CIRCLE, base, ANCHOR).orElseThrow();
        assertEquals(1, mission.pointsOfInterest().size());
        Waypoint w = mission.waypoints().get(3);
        assertEquals(30.0, Math.hypot(w.x() - 100.2, w.z() + 49.8), 0.15);
    }

    @Test
    void revealMovesAwayAndUp() {
        Mission mission = MissionTemplates.apply(MissionTemplates.Template.REVEAL, Mission.empty(), ANCHOR).orElseThrow();
        assertEquals(MissionTemplates.REVEAL_POINTS, mission.size());
        double lastDistance = -1.0;
        double lastHeight = -1.0;
        for (Waypoint w : mission.waypoints()) {
            double distance = Math.hypot(w.x() - ANCHOR.x(), w.z() - ANCHOR.z());
            assertTrue(distance > lastDistance);
            assertTrue(w.y() > lastHeight);
            assertEquals(Waypoint.Heading.POI, w.heading());
            lastDistance = distance;
            lastHeight = w.y();
        }
        assertEquals(MissionTemplates.REVEAL_FAR, lastDistance, 0.15);
        assertTrue(mission.waypoints().getFirst().z() < ANCHOR.z(), "rückwärts zum Spieler hin");
    }

    @Test
    void lineFillsBetweenTheLastTwoPoints() {
        Mission base = Mission.of(List.of(Waypoint.at(0, 70, 0), Waypoint.at(0, 70, 35)));
        Mission mission = MissionTemplates.apply(MissionTemplates.Template.LINE, base, ANCHOR).orElseThrow();
        assertEquals(5, mission.size());
        for (int i = 1; i < mission.size(); i++) {
            Waypoint a = mission.waypoint(i - 1);
            Waypoint b = mission.waypoint(i);
            assertTrue(a.distanceTo(b.x(), b.y(), b.z()) <= MissionTemplates.LINE_SPACING + 0.1);
            assertTrue(b.z() > a.z());
        }
        Mission fresh = MissionTemplates.apply(MissionTemplates.Template.LINE, Mission.empty(), ANCHOR).orElseThrow();
        assertEquals(7, fresh.size());
        assertEquals(MissionTemplates.LINE_LENGTH, fresh.waypoints().getLast().z() - fresh.waypoints().getFirst().z(), 0.15);
    }

    @Test
    void templatesRespectTheLimits() {
        List<Waypoint> many = new ArrayList<>();
        for (int i = 0; i < MissionLimits.MAX_WAYPOINTS - 3; i++) {
            many.add(Waypoint.at(i, 70, 0));
        }
        Mission crowded = Mission.of(many);
        for (MissionTemplates.Template template : MissionTemplates.Template.values()) {
            MissionTemplates.apply(template, crowded, ANCHOR).ifPresent(m -> assertTrue(m.size() <= MissionLimits.MAX_WAYPOINTS));
        }
        assertTrue(MissionTemplates.apply(MissionTemplates.Template.CIRCLE, crowded, ANCHOR).isEmpty());
        List<PointOfInterest> pois = new ArrayList<>();
        for (int i = 0; i < MissionLimits.MAX_POINTS_OF_INTEREST; i++) {
            pois.add(PointOfInterest.at(i * 100, 64, 0, 5));
        }
        assertTrue(MissionTemplates.apply(MissionTemplates.Template.REVEAL, Mission.of(List.of(), pois), ANCHOR).isEmpty());
    }
}
