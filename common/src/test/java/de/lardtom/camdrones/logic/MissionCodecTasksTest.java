package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionCodec;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionCodecTasksTest {
    private static final int[] FORMAT_ONE = {1, 1, 2, 75, 1, 3,
            100, 700, -50, 65 << 16, (128 - 30) << 8, 0,
            -205, 753, 401, 42, 128 << 8 | Waypoint.Heading.FIXED.ordinal(), 270,
            0, 800, 0, 0, 128 << 8 | 1 << 4 | Waypoint.Heading.POI.ordinal(), Waypoint.Action.PHOTO.ordinal() << 16,
            50, 640, 50, 120,
            -30, 700, 90, 0};

    private static Mission withTasks() {
        Mission base = Mission.of(List.of(Waypoint.at(0.0, 70.0, 0.0), Waypoint.at(10.0, 72.0, 0.0), Waypoint.at(20.0, 74.0, 0.0),
                Waypoint.at(30.0, 76.0, 0.0)));
        base = base.replace(1, base.waypoint(1).withLight(Waypoint.Light.ON).withAction(Waypoint.Action.RECORD_START));
        base = base.replace(2, base.waypoint(2).withAccessory(Waypoint.Accessory.ACTION_4, true).withLight(Waypoint.Light.RAINBOW));
        base = base.replace(3, base.waypoint(3).withAccessory(Waypoint.Accessory.END_REPEAT, false).withLight(Waypoint.Light.OFF)
                .withHeading(Waypoint.Heading.FIXED, 359.0));
        return base.withLoop(true);
    }

    private static MissionCodec.Status changed(int[] data, int index, int value) {
        int[] copy = data.clone();
        copy[index] = value;
        return MissionCodec.decode(copy).status();
    }

    @Test
    void missionsFromTheFirstFormatStillLoadUnchanged() {
        MissionCodec.Result result = MissionCodec.decode(FORMAT_ONE.clone());
        assertTrue(result.ok(), result.status().name());
        Mission mission = result.mission().orElseThrow();
        assertEquals(3, mission.size());
        assertEquals(2, mission.pointsOfInterest().size());
        assertEquals(Waypoint.Action.PHOTO, mission.waypoint(2).action());
        for (Waypoint waypoint : mission.waypoints()) {
            assertEquals(Waypoint.Light.NONE, waypoint.light());
            assertEquals(Waypoint.Accessory.NONE, waypoint.accessory());
        }
        assertArrayEquals(FORMAT_ONE, MissionCodec.encode(mission), "ohne Aktionen bleibt das Format 1 Byte für Byte");
        assertEquals(MissionLimits.FIRST_FORMAT_VERSION, MissionCodec.formatFor(mission));
    }

    @Test
    void tasksUseTheSecondFormatAndRoundTrip() {
        Mission mission = withTasks();
        int[] data = MissionCodec.encode(mission);
        assertEquals(MissionLimits.TASK_FORMAT_VERSION, data[0]);
        assertEquals(2, data[0]);
        assertEquals(MissionLimits.packedInts(4, 0), data.length, "gleiche Größe wie Format 1");
        assertEquals(mission, MissionCodec.decode(data).mission().orElseThrow());
        int[] withoutTasks = MissionCodec.encode(mission.withWaypoints(mission.waypoints().stream().map(Waypoint::withoutTasks).toList()));
        assertEquals(MissionLimits.FIRST_FORMAT_VERSION, withoutTasks[0]);
    }

    @Test
    void secondFormatWithoutTasksIsAccepted() {
        Mission plain = Mission.of(List.of(Waypoint.at(1.0, 70.0, 1.0)));
        int[] data = MissionCodec.encode(plain);
        data[0] = MissionLimits.TASK_FORMAT_VERSION;
        assertEquals(plain, MissionCodec.decode(data).mission().orElseThrow());
        data[0] = MissionLimits.FORMAT_VERSION;
        assertEquals(plain, MissionCodec.decode(data).mission().orElseThrow());
    }

    @Test
    void everyTaskCombinationRoundTripsInFullMissions() {
        Random random = new Random(0xACC);
        Waypoint.Light[] lights = Waypoint.Light.values();
        Waypoint.Accessory[] accessories = Waypoint.Accessory.values();
        for (int round = 0; round < 100; round++) {
            Mission full = MissionCodecTest.fullMission(random);
            List<Waypoint> list = new ArrayList<>();
            for (Waypoint w : full.waypoints()) {
                Waypoint.Accessory accessory = accessories[random.nextInt(accessories.length)];
                list.add(w.withLight(lights[random.nextInt(lights.length)]).withAccessory(accessory, random.nextBoolean()));
            }
            Mission mission = full.withWaypoints(list);
            int[] data = MissionCodec.encode(mission);
            assertTrue(data.length * 4 + MissionLimits.NBT_OVERHEAD_BYTES <= MissionLimits.DRONE_DATA_BYTES);
            assertEquals(mission, MissionCodec.decode(data).mission().orElseThrow());
            assertArrayEquals(data, MissionCodec.encode(MissionCodec.decode(data).mission().orElseThrow()));
        }
    }

    @Test
    void brokenTaskBitsAreRejected() {
        int[] data = MissionCodec.encode(withTasks());
        int slot = MissionLimits.HEADER_INTS + MissionLimits.INTS_PER_WAYPOINT + 5;
        int good = data[slot];
        assertEquals(MissionCodec.Status.CORRUPT, changed(data, slot, good | 1 << 29));
        assertEquals(MissionCodec.Status.CORRUPT, changed(data, slot, good | 1 << 31));
        assertEquals(MissionCodec.Status.CORRUPT, changed(data, slot, (good & ~(0xF << 20)) | Waypoint.Light.values().length << 20));
        assertEquals(MissionCodec.Status.CORRUPT, changed(data, slot, (good & ~(0xF << 24)) | Waypoint.Accessory.values().length << 24));
        assertEquals(MissionCodec.Status.CORRUPT, changed(data, slot, (good & ~(0xF << 16)) | Waypoint.Action.values().length << 16));
        assertEquals(MissionCodec.Status.CORRUPT, changed(data, slot, good | 1 << 28), "Wiederholen ohne Zubehör-Aktion");
        assertEquals(MissionCodec.Status.CORRUPT, changed(data, slot, good & ~0xFFFF | 360));
        int[] old = FORMAT_ONE.clone();
        int oldSlot = MissionLimits.HEADER_INTS + 5;
        assertEquals(MissionCodec.Status.CORRUPT, changed(old, oldSlot, 1 << 20), "Format 1 kennt keine Aktionen");
        assertEquals(MissionCodec.Status.UNKNOWN_VERSION, changed(data, 0, MissionLimits.FORMAT_VERSION + 1));
    }

    @Test
    void fullMissionWithTasksStillFitsTheDroneData() {
        List<Waypoint> list = new ArrayList<>();
        for (int i = 0; i < MissionLimits.MAX_WAYPOINTS; i++) {
            list.add(Waypoint.at(i, 80.0, -i).withLight(Waypoint.Light.LOW_BATTERY).withAccessory(Waypoint.Accessory.ACTION_4, true));
        }
        List<PointOfInterest> points = new ArrayList<>();
        for (int i = 0; i < MissionLimits.MAX_POINTS_OF_INTEREST; i++) {
            points.add(PointOfInterest.at(i, 64.0, i, 200.0));
        }
        Mission mission = new Mission(true, true, Mission.End.LAND, MissionLimits.MAX_SPEED_DM_S, ObstacleGuard.Mode.CLIMB_OVER, list, points);
        int[] data = MissionCodec.encode(mission);
        assertEquals(322, data.length);
        assertTrue(MissionLimits.fits(MissionLimits.MAX_WAYPOINTS, MissionLimits.MAX_POINTS_OF_INTEREST));
        assertEquals(mission, MissionCodec.decode(data).mission().orElseThrow());
    }
}
