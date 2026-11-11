package de.lardtom.camdrones.logic;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionCodec;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionCodecTest {
    static Mission fullMission(Random random) {
        List<PointOfInterest> points = new ArrayList<>();
        for (int i = 0; i < MissionLimits.MAX_POINTS_OF_INTEREST; i++) {
            points.add(new PointOfInterest(random.nextInt(2_000_001) - 1_000_000, random.nextInt(3000) - 600,
                    random.nextInt(2_000_001) - 1_000_000, random.nextInt(MissionLimits.MAX_RADIUS_DM + 1)));
        }
        List<Waypoint> waypoints = new ArrayList<>();
        Waypoint.Heading[] headings = Waypoint.Heading.values();
        Waypoint.Action[] actions = Waypoint.Action.values();
        for (int i = 0; i < MissionLimits.MAX_WAYPOINTS; i++) {
            int speed = random.nextBoolean() ? 0 : MissionLimits.MIN_SPEED_DM_S + random.nextInt(MissionLimits.MAX_SPEED_DM_S);
            waypoints.add(new Waypoint(random.nextInt(2_000_001) - 1_000_000, random.nextInt(3000) - 600, random.nextInt(2_000_001) - 1_000_000,
                    speed, random.nextInt(MissionLimits.MAX_WAIT_DS + 1),
                    MissionLimits.MIN_TILT + random.nextInt(MissionLimits.MAX_TILT - MissionLimits.MIN_TILT + 1),
                    headings[random.nextInt(headings.length)], random.nextInt(points.size()), actions[random.nextInt(actions.length)],
                    random.nextInt(360)));
        }
        return new Mission(random.nextBoolean(), random.nextBoolean(), Mission.End.values()[random.nextInt(3)],
                MissionLimits.MIN_SPEED_DM_S + random.nextInt(MissionLimits.MAX_SPEED_DM_S), ObstacleGuard.Mode.values()[random.nextInt(2)],
                waypoints, points);
    }

    private static Mission sample() {
        return Mission.of(List.of(Waypoint.at(10.0, 70.0, -5.0).withSpeed(6.5).withTilt(-30),
                        Waypoint.at(-20.5, 75.3, 40.1).withWait(4.2).withHeading(Waypoint.Heading.FIXED, 270.0),
                        Waypoint.at(0.0, 80.0, 0.0).facing(1).withAction(Waypoint.Action.PHOTO)),
                List.of(PointOfInterest.at(5.0, 64.0, 5.0, 12.0), PointOfInterest.at(-3.0, 70.0, 9.0, 0.0)))
                .withSmooth(true).withEnd(Mission.End.LAND).withBaseSpeed(7.5).withObstacle(ObstacleGuard.Mode.CLIMB_OVER);
    }

    private static MissionCodec.Status decodeChanged(IntUnaryOperator index, int value) {
        int[] data = MissionCodec.encode(sample());
        data[index.applyAsInt(data.length)] = value;
        return MissionCodec.decode(data).status();
    }

    @Test
    void sampleRoundTripsExactly() {
        Mission mission = sample();
        int[] data = MissionCodec.encode(mission);
        MissionCodec.Result result = MissionCodec.decode(data);
        assertTrue(result.ok());
        assertEquals(mission, result.mission().orElseThrow());
        assertEquals(MissionLimits.packedInts(3, 2), data.length);
        assertEquals(65, mission.waypoint(0).speedDmS());
        assertEquals(42, mission.waypoint(1).waitDs());
    }

    @Test
    void fullMissionRoundTripsAndFitsIntoTwoKibibytes() {
        Random random = new Random(0x5EED);
        for (int round = 0; round < 200; round++) {
            Mission mission = fullMission(random);
            int[] data = MissionCodec.encode(mission);
            assertEquals(322, data.length);
            assertTrue(data.length * 4 + MissionLimits.NBT_OVERHEAD_BYTES <= MissionLimits.DRONE_DATA_BYTES, "zu groß: " + data.length * 4);
            assertEquals(mission, MissionCodec.decode(data).mission().orElseThrow());
        }
        assertEquals(1288, MissionLimits.packedBytes(MissionLimits.MAX_WAYPOINTS, MissionLimits.MAX_POINTS_OF_INTEREST));
        assertTrue(MissionLimits.fits(MissionLimits.MAX_WAYPOINTS, MissionLimits.MAX_POINTS_OF_INTEREST));
    }

    @Test
    void emptyMissionAndEmptyDataAreDistinct() {
        int[] data = MissionCodec.encode(Mission.empty());
        assertEquals(MissionLimits.HEADER_INTS, data.length);
        assertEquals(Mission.empty(), MissionCodec.decode(data).mission().orElseThrow());
        assertEquals(MissionCodec.Status.EMPTY, MissionCodec.decode(new int[0]).status());
        assertEquals(MissionCodec.Status.EMPTY, MissionCodec.decode(null).status());
    }

    @Test
    void worldBorderAndNegativeCoordinatesSurvive() {
        Waypoint far = Waypoint.at(29_999_999.9, -64.0, -29_999_999.9);
        PointOfInterest poi = PointOfInterest.at(-30_000_000.0, 319.9, 30_000_000.0, 200.0);
        Mission mission = Mission.of(List.of(far)).withPointsOfInterest(List.of(poi));
        Mission back = MissionCodec.decode(MissionCodec.encode(mission)).mission().orElseThrow();
        assertEquals(29_999_999.9, back.waypoint(0).x(), 1.0E-6);
        assertEquals(-64.0, back.waypoint(0).y(), 1.0E-9);
        assertEquals(-30_000_000.0, back.pointsOfInterest().get(0).x(), 1.0E-6);
        assertEquals(200.0, back.pointsOfInterest().get(0).radius(), 1.0E-9);
    }

    @Test
    void limitsAreEnforcedWhenBuilding() {
        List<Waypoint> tooMany = new ArrayList<>();
        for (int i = 0; i <= MissionLimits.MAX_WAYPOINTS; i++) {
            tooMany.add(Waypoint.at(i, 70, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> Mission.of(tooMany));
        List<PointOfInterest> points = new ArrayList<>();
        for (int i = 0; i <= MissionLimits.MAX_POINTS_OF_INTEREST; i++) {
            points.add(PointOfInterest.at(i, 70, 0, 5));
        }
        assertThrows(IllegalArgumentException.class, () -> Mission.empty().withPointsOfInterest(points));
        Waypoint base = Waypoint.at(0, 70, 0);
        assertThrows(IllegalArgumentException.class, () -> base.withSpeed(20.1));
        assertThrows(IllegalArgumentException.class, () -> base.withTilt(-91));
        assertThrows(IllegalArgumentException.class, () -> base.withTilt(61));
        assertThrows(IllegalArgumentException.class, () -> base.withWait(300.1));
        assertThrows(IllegalArgumentException.class, () -> base.facing(4));
        assertThrows(IllegalArgumentException.class, () -> Mission.of(List.of(base.facing(0))));
        assertThrows(IllegalArgumentException.class, () -> Waypoint.at(30_000_001, 70, 0));
        assertThrows(IllegalArgumentException.class, () -> Waypoint.at(0, 5000, 0));
        assertThrows(IllegalArgumentException.class, () -> Waypoint.at(Double.NaN, 70, 0));
        assertThrows(IllegalArgumentException.class, () -> Mission.empty().withBaseSpeed(0.0));
        assertThrows(IllegalArgumentException.class, () -> Mission.empty().withBaseSpeed(20.1));
        assertThrows(IllegalArgumentException.class, () -> PointOfInterest.at(0, 70, 0, 200.1));
        assertEquals(359, base.withHeading(Waypoint.Heading.FIXED, -1.0).yawDeg());
        assertEquals(0, base.withSpeed(0.0).speedDmS());
    }

    @Test
    void unknownVersionIsNotLoadedAndLeavesDataUntouched() {
        int[] data = MissionCodec.encode(sample());
        data[0] = MissionLimits.FORMAT_VERSION + 1;
        int[] copy = data.clone();
        MissionCodec.Result result = MissionCodec.decode(data);
        assertEquals(MissionCodec.Status.UNKNOWN_VERSION, result.status());
        assertTrue(result.mission().isEmpty());
        assertArrayEquals(copy, data);
        assertEquals(MissionLimits.FORMAT_VERSION + 1, MissionCodec.version(data));
        data[0] = Integer.MAX_VALUE;
        assertEquals(MissionCodec.Status.UNKNOWN_VERSION, MissionCodec.decode(data).status());
        data[0] = 0;
        assertEquals(MissionCodec.Status.CORRUPT, MissionCodec.decode(data).status());
        data[0] = -1;
        assertEquals(MissionCodec.Status.CORRUPT, MissionCodec.decode(data).status());
        assertEquals(MissionCodec.Status.UNKNOWN_VERSION, MissionCodec.decode(new int[]{7}).status());
    }

    @Test
    void decodingNeverChangesTheInput() {
        int[] data = MissionCodec.encode(sample());
        int[] copy = data.clone();
        MissionCodec.decode(data);
        assertArrayEquals(copy, data);
    }

    @Test
    void brokenDataIsRejected() {
        int[] data = MissionCodec.encode(sample());
        assertEquals(MissionCodec.Status.CORRUPT, MissionCodec.decode(Arrays.copyOf(data, data.length - 1)).status());
        assertEquals(MissionCodec.Status.CORRUPT, MissionCodec.decode(Arrays.copyOf(data, data.length + 1)).status());
        assertEquals(MissionCodec.Status.CORRUPT, MissionCodec.decode(Arrays.copyOf(data, 5)).status());
        assertEquals(MissionCodec.Status.CORRUPT, MissionCodec.decode(Arrays.copyOf(data, data.length + 12)).status());
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 5, 4));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 5, -1));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 5, 51));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 1, 4));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 2, 3));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 2, -1));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 3, 0));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 3, 201));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 4, 2));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 6, 300_000_001));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 7, 50_000));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 9, 201 << 16));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 9, 3001));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 10, 37 << 8));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 10, 189 << 8));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 10, 128 << 8 | 4));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 10, 128 << 8 | 4 << 4));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 10, 1 << 16 | 128 << 8));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 11, 4 << 16));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 11, 360));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> 22, 128 << 8 | 2 << 4 | Waypoint.Heading.POI.ordinal()));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> n - 1, -1));
        assertEquals(MissionCodec.Status.CORRUPT, decodeChanged(n -> n - 1, MissionLimits.MAX_RADIUS_DM + 1));
    }

    @Test
    void randomGarbageNeverThrows() {
        Random random = new Random(42);
        int ok = 0;
        for (int round = 0; round < 20_000; round++) {
            int[] data = new int[random.nextInt(40)];
            for (int i = 0; i < data.length; i++) {
                data[i] = random.nextInt(4) == 0 ? random.nextInt() : random.nextInt(8);
            }
            if (data.length >= MissionLimits.HEADER_INTS && random.nextBoolean()) {
                data[0] = 1;
                data[1] = random.nextInt(4);
                data[2] = random.nextInt(3);
                data[3] = 1 + random.nextInt(200);
                data[4] = random.nextInt(2);
                data[5] = random.nextInt(2);
                for (int i = MissionLimits.HEADER_INTS + 4; i < data.length; i += 6) {
                    data[i] = random.nextInt(4) << 4 | random.nextInt(4) | (38 + random.nextInt(151)) << 8;
                }
            }
            MissionCodec.Result result = MissionCodec.decode(data);
            assertEquals(result.ok(), result.mission().isPresent());
            if (result.ok()) {
                ok++;
                assertArrayEquals(data, MissionCodec.encode(result.mission().orElseThrow()));
            }
        }
        assertTrue(ok > 0, "Fuzz erzeugt nie eine gültige Mission");
    }

    @Test
    void bitFlipsAreEitherRejectedOrStayConsistent() {
        Random random = new Random(7);
        int[] base = MissionCodec.encode(fullMission(random));
        for (int round = 0; round < 5000; round++) {
            int[] data = base.clone();
            int index = random.nextInt(data.length);
            data[index] ^= 1 << random.nextInt(32);
            MissionCodec.Result result = MissionCodec.decode(data);
            if (result.ok()) {
                assertArrayEquals(data, MissionCodec.encode(result.mission().orElseThrow()));
            } else {
                assertFalse(result.mission().isPresent());
            }
        }
    }
}
