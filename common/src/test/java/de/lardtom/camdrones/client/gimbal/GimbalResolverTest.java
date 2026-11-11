package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.GimbalResolver;
import de.lardtom.camdrones.crew.GimbalData;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.integration.data.MissionData;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.NavLogic;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GimbalResolverTest {
    private static final GimbalLimits TOUR = GimbalLimits.of(-90.0, 30.0, false);
    private static final GimbalLimits CINE = GimbalLimits.of(-90.0, 60.0, true);

    private static Mission mission() {
        Waypoint a = Waypoint.at(0.0, 80.0, 0.0).withTilt(-10);
        Waypoint b = Waypoint.at(0.0, 80.0, -40.0).withTilt(-50);
        Waypoint c = Waypoint.at(40.0, 80.0, -40.0).facing(0);
        Waypoint d = Waypoint.at(40.0, 80.0, 0.0).withHeading(Waypoint.Heading.FREE, 0.0);
        return Mission.of(List.of(a, b, c, d), List.of(PointOfInterest.at(40.0, 64.0, -80.0, 10.0)));
    }

    private static GimbalResolver.Scene running(GimbalLimits limits, Mission mission, int index, double x, double y, double z) {
        return new GimbalResolver.Scene(limits, x, y, z, 180.0, Optional.of(mission), NavLogic.MissionState.RUNNING, index,
                GimbalResolver.Smart.NONE, Optional.empty(), Optional.empty());
    }

    @Test
    void manualSettingIsUsedWithoutAutomation() {
        GimbalSetting manual = new GimbalSetting(GimbalSetting.Mode.FOLLOW, -35.0, 40.0, 0.0, 0.3);
        GimbalResolver.Aim tour = GimbalResolver.resolve(GimbalResolver.Scene.free(TOUR, 0, 70, 0, 90.0), manual);
        assertEquals(GimbalResolver.Source.MANUAL, tour.source());
        assertEquals(-35.0, tour.tilt());
        assertEquals(0.0, tour.pan(), "Tour schwenkt nicht");
        assertEquals(0.3, tour.zoom());
        GimbalResolver.Aim cine = GimbalResolver.resolve(GimbalResolver.Scene.free(CINE, 0, 70, 0, 90.0), manual);
        assertEquals(40.0, cine.pan());
        assertEquals(130.0, cine.heading(), 1.0E-9);
        GimbalResolver.Aim high = GimbalResolver.resolve(GimbalResolver.Scene.free(TOUR, 0, 70, 0, 0.0), manual.withTilt(55.0));
        assertEquals(30.0, high.tilt(), "auf die Grenze des Gimbals gekappt");
    }

    @Test
    void lockedManualKeepsCompassHeading() {
        GimbalSetting locked = new GimbalSetting(GimbalSetting.Mode.LOCK, -20.0, 0.0, 45.0, 0.0);
        GimbalResolver.Aim aim = GimbalResolver.resolve(GimbalResolver.Scene.free(TOUR, 0, 70, 0, 300.0), locked);
        assertTrue(aim.locked());
        assertEquals(45.0, aim.heading());
        assertEquals(105.0, aim.pan(), 1.0E-9);
    }

    @Test
    void missionTiltIsInterpolatedFromThePosition() {
        Mission mission = mission();
        GimbalSetting manual = GimbalSetting.CENTER.withTilt(20.0).withZoom(0.4);
        GimbalResolver.Aim start = GimbalResolver.resolve(running(TOUR, mission, 1, 0.0, 80.0, 0.0), manual);
        GimbalResolver.Aim middle = GimbalResolver.resolve(running(TOUR, mission, 1, 0.0, 80.0, -20.0), manual);
        GimbalResolver.Aim end = GimbalResolver.resolve(running(TOUR, mission, 1, 0.0, 80.0, -40.0), manual);
        assertEquals(GimbalResolver.Source.MISSION, middle.source());
        assertEquals(-10.0, start.tilt(), 1.0E-9);
        assertEquals(-30.0, middle.tilt(), 1.0E-9);
        assertEquals(-50.0, end.tilt(), 1.0E-9);
        assertEquals(0.4, middle.zoom(), "Zoom bleibt beim Operator");
    }

    @Test
    void missionPointOfInterestAimsAtThePoint() {
        GimbalResolver.Aim tour = GimbalResolver.resolve(running(TOUR, mission(), 2, 40.0, 80.0, -40.0), GimbalSetting.CENTER);
        assertEquals(GimbalResolver.Source.MISSION, tour.source());
        assertEquals(Math.toDegrees(Math.atan2(-16.0, 40.0)), tour.tilt(), 1.0E-9);
        assertFalse(tour.locked());
        GimbalResolver.Aim cine = GimbalResolver.resolve(running(CINE, mission(), 2, 40.0, 80.0, -40.0), GimbalSetting.CENTER);
        assertTrue(cine.locked());
        assertEquals(0.0, cine.heading(), 1.0E-9, "Point of Interest liegt im Norden");
        assertEquals(180.0, Math.abs(cine.pan()), 1.0E-9);
    }

    @Test
    void freeWaypointGivesTheOperatorControl() {
        GimbalSetting manual = GimbalSetting.CENTER.withTilt(-70.0);
        GimbalResolver.Scene scene = running(TOUR, mission(), 3, 40.0, 80.0, -20.0);
        assertTrue(GimbalResolver.manualControl(scene));
        GimbalResolver.Aim aim = GimbalResolver.resolve(scene, manual);
        assertEquals(GimbalResolver.Source.MANUAL, aim.source());
        assertEquals(-70.0, aim.tilt());
        GimbalResolver.Scene paused = new GimbalResolver.Scene(TOUR, 0, 80, 0, 0, Optional.of(mission()), NavLogic.MissionState.PAUSED, 1,
                GimbalResolver.Smart.NONE, Optional.empty(), Optional.empty());
        assertEquals(GimbalResolver.Source.MANUAL, GimbalResolver.resolve(paused, manual).source());
    }

    @Test
    void smartPointOfInterestAndFollowAimAtTheirTarget() {
        GimbalResolver.Scene poi = new GimbalResolver.Scene(TOUR, 0, 90, 0, 0, Optional.empty(), NavLogic.MissionState.IDLE, 0,
                GimbalResolver.Smart.POI, Optional.of(new GimbalResolver.Point(0, 70, -20)), Optional.empty());
        GimbalResolver.Aim aim = GimbalResolver.resolve(poi, GimbalSetting.CENTER);
        assertEquals(GimbalResolver.Source.POINT_OF_INTEREST, aim.source());
        assertEquals(-45.0, aim.tilt(), 1.0E-9);
        GimbalResolver.Scene follow = new GimbalResolver.Scene(TOUR, 0, 75, 0, 0, Optional.empty(), NavLogic.MissionState.IDLE, 0,
                GimbalResolver.Smart.FOLLOW, Optional.empty(), Optional.of(new GimbalResolver.Point(0, 70, 5)));
        assertEquals(GimbalResolver.Source.FOLLOW, GimbalResolver.resolve(follow, GimbalSetting.CENTER).source());
        GimbalResolver.Scene lost = new GimbalResolver.Scene(TOUR, 0, 75, 0, 0, Optional.empty(), NavLogic.MissionState.IDLE, 0,
                GimbalResolver.Smart.FOLLOW, Optional.empty(), Optional.empty());
        assertEquals(GimbalResolver.Source.MANUAL, GimbalResolver.resolve(lost, GimbalSetting.CENTER).source(), "Ziel nicht geladen");
        GimbalResolver.Scene hold = new GimbalResolver.Scene(TOUR, 0, 75, 0, 0, Optional.empty(), NavLogic.MissionState.IDLE, 0,
                GimbalResolver.Smart.HOLD, Optional.empty(), Optional.empty());
        assertEquals(GimbalResolver.Source.MANUAL, GimbalResolver.resolve(hold, GimbalSetting.CENTER).source());
    }

    @Test
    void pilotOperatorAndViewerComputeTheSameAimFromSyncedData() {
        UUID operator = UUID.randomUUID();
        GimbalSetting operatorLocal = new GimbalSetting(GimbalSetting.Mode.FOLLOW, -33.3F, 12.5F, 0.0, 0.42F);
        GimbalData stored = GimbalData.NONE.withOperator(Optional.of(operator)).withSetting(operatorLocal);
        Tag synced = GimbalData.CODEC.encodeStart(NbtOps.INSTANCE, stored).getOrThrow();
        GimbalData onPilot = GimbalData.CODEC.parse(NbtOps.INSTANCE, synced).getOrThrow();
        GimbalData onViewer = GimbalData.CODEC.parse(NbtOps.INSTANCE, synced.copy()).getOrThrow();
        Mission mission = mission();
        Tag missionTag = MissionData.CODEC.encodeStart(NbtOps.INSTANCE, MissionData.of(mission)).getOrThrow();
        Mission viewerMission = MissionData.CODEC.parse(NbtOps.INSTANCE, missionTag).getOrThrow().mission().orElseThrow();
        for (int index = 0; index < mission.size(); index++) {
            double z = -5.0 * index - 3.0;
            GimbalResolver.Aim a = GimbalResolver.resolve(running(CINE, mission, index, 10.0, 80.0, z), operatorLocal);
            GimbalResolver.Aim b = GimbalResolver.resolve(running(CINE, viewerMission, index, 10.0, 80.0, z), onPilot.setting());
            GimbalResolver.Aim c = GimbalResolver.resolve(running(CINE, viewerMission, index, 10.0, 80.0, z), onViewer.setting());
            assertEquals(a, b, "Operator gegen Pilot bei Wegpunkt " + index);
            assertEquals(b, c, "Pilot gegen Zuschauer bei Wegpunkt " + index);
        }
        GimbalResolver.Aim free = GimbalResolver.resolve(GimbalResolver.Scene.free(CINE, 0, 0, 0, 10.0), onViewer.setting());
        assertEquals(GimbalResolver.resolve(GimbalResolver.Scene.free(CINE, 0, 0, 0, 10.0), operatorLocal), free);
    }

    @Test
    void fpvManualFollowsTheFrameWithoutPan() {
        GimbalSetting fpv = new GimbalSetting(GimbalSetting.Mode.FPV, -25.0, 40.0, 0.0, 0.2);
        GimbalResolver.Aim aim = GimbalResolver.resolve(GimbalResolver.Scene.free(CINE, 0, 70, 0, 120.0), fpv);
        assertEquals(GimbalResolver.Source.MANUAL, aim.source());
        assertTrue(aim.fpv());
        assertFalse(aim.locked());
        assertEquals(-25.0, aim.tilt());
        assertEquals(0.0, aim.pan(), "FPV schwenkt nicht");
        assertEquals(120.0, aim.heading(), 1.0E-9);
        assertEquals(0.2, aim.zoom());
        assertEquals(30.0, GimbalResolver.resolve(GimbalResolver.Scene.free(TOUR, 0, 70, 0, 0.0), fpv.withTilt(55.0)).tilt());
        GimbalResolver.Aim mission = GimbalResolver.resolve(running(TOUR, mission(), 1, 0.0, 80.0, -20.0), fpv);
        assertEquals(GimbalResolver.Source.MISSION, mission.source(), "Mission zielt weiter selbst");
        assertFalse(mission.fpv());
    }
    @Test
    void soloCameraSteeringReplacesTheFlightPlanForEveryone() {
        UUID owner = UUID.randomUUID();
        GimbalSetting steered = new GimbalSetting(GimbalSetting.Mode.LOCK, -70.0, 0.0, 200.0, 0.25);
        GimbalData stored = GimbalData.NONE.withSolo(owner).withSteered().withSetting(steered);
        GimbalData onViewer = GimbalData.CODEC.parse(NbtOps.INSTANCE, GimbalData.CODEC.encodeStart(NbtOps.INSTANCE, stored).getOrThrow()).getOrThrow();
        assertTrue(onViewer.manual(), "Zuschauer sehen, dass der Besitzer allein steuert");
        GimbalResolver.Scene plan = running(TOUR, mission(), 1, 0.0, 80.0, -20.0);
        assertEquals(GimbalResolver.Source.MISSION, GimbalResolver.resolve(plan, onViewer.setting()).source(), "ohne Steuern zielt der Flugplan");
        GimbalResolver.Aim owned = GimbalResolver.resolve(plan.manualOnly(), steered);
        GimbalResolver.Aim viewer = GimbalResolver.resolve(plan.manualOnly(), onViewer.setting());
        assertEquals(GimbalResolver.Source.MANUAL, owned.source());
        assertEquals(-70.0, owned.tilt(), 1.0E-6);
        assertEquals(owned, viewer, "Besitzer und Zuschauer rechnen dieselbe Lage");
    }

    @Test
    void automaticSettingIsTheVisiblePlanAim() {
        GimbalResolver.Scene plan = running(TOUR, mission(), 1, 0.0, 80.0, -20.0);
        GimbalSetting seen = GimbalResolver.automaticSetting(plan, 0.25).orElseThrow();
        GimbalResolver.Aim aim = GimbalResolver.resolve(plan, GimbalSetting.CENTER);
        assertTrue(seen.locked());
        assertEquals(aim.tilt(), seen.tilt(), 1.0E-9);
        assertEquals(aim.heading(), seen.heading(), 1.0E-9);
        assertEquals(0.25, seen.zoom());
        assertEquals(Optional.empty(), GimbalResolver.automaticSetting(GimbalResolver.Scene.free(TOUR, 0, 70, 0, 0.0), 0.0));
        assertEquals(Optional.empty(), GimbalResolver.automaticSetting(running(TOUR, mission(), 3, 40.0, 80.0, -20.0), 0.0),
                "freier Wegpunkt hat keinen Flugplan-Blick");
    }
}
