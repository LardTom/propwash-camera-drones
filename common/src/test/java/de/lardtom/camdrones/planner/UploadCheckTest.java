package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionCodec;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.PointOfInterest;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.planner.model.PlannerEntry;
import de.lardtom.camdrones.planner.model.UploadCheck;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadCheckTest {
    private static final UploadCheck.Drone PARKED = new UploadCheck.Drone(true, true, false, false, 0.0, 64.0, 0.0);
    private static final UploadCheck.Drone FOREIGN = new UploadCheck.Drone(true, false, false, false, 0.0, 64.0, 0.0);
    private static final UploadCheck.Rules DEFAULT_RULES = new UploadCheck.Rules(120, 0);

    private static int[] mission(Waypoint... points) {
        return MissionCodec.encode(Mission.of(List.of(points)));
    }

    private static UploadCheck.Result check(int[] packed, UploadCheck.Drone drone, boolean operator, UploadCheck.Rules rules) {
        return UploadCheck.check(true, packed, true, drone, operator, rules).result();
    }

    @Test
    void validMissionFromTheOwnerIsAccepted() {
        UploadCheck.Verdict verdict = UploadCheck.check(true, mission(Waypoint.at(10, 74, 10), Waypoint.at(40, 80, 0)), true, PARKED, false,
                DEFAULT_RULES);
        assertEquals(UploadCheck.Result.OK, verdict.result());
        assertEquals(2, verdict.mission().orElseThrow().size());
    }

    @Test
    void fullMissionFits() {
        List<Waypoint> list = new ArrayList<>();
        for (int i = 0; i < MissionLimits.MAX_WAYPOINTS; i++) {
            list.add(Waypoint.at(i, 70, i));
        }
        List<PointOfInterest> pois = new ArrayList<>();
        for (int i = 0; i < MissionLimits.MAX_POINTS_OF_INTEREST; i++) {
            pois.add(PointOfInterest.at(i, 64, 0, 10));
        }
        int[] packed = MissionCodec.encode(Mission.of(list, pois));
        assertEquals(UploadCheck.Result.OK, check(packed, PARKED, false, DEFAULT_RULES));
    }

    @Test
    void rateLimitComesFirst() {
        assertEquals(UploadCheck.Result.RATE_LIMITED, UploadCheck.check(false, mission(Waypoint.at(1, 70, 1)), true, PARKED, true,
                DEFAULT_RULES).result());
    }

    @Test
    void brokenOrForeignDataIsRejected() {
        assertEquals(UploadCheck.Result.EMPTY, check(new int[0], PARKED, false, DEFAULT_RULES));
        assertEquals(UploadCheck.Result.EMPTY, check(MissionCodec.encode(Mission.empty()), PARKED, false, DEFAULT_RULES));
        int[] future = mission(Waypoint.at(1, 70, 1));
        future[0] = MissionLimits.FORMAT_VERSION + 1;
        assertEquals(UploadCheck.Result.UNKNOWN_VERSION, check(future, PARKED, false, DEFAULT_RULES));
        int[] corrupt = mission(Waypoint.at(1, 70, 1));
        corrupt[5] = 7;
        assertEquals(UploadCheck.Result.INVALID, check(corrupt, PARKED, false, DEFAULT_RULES));
        int[] huge = new int[PlannerEntry.MAX_INTS + 1];
        Arrays.fill(huge, 1);
        assertEquals(UploadCheck.Result.TOO_LARGE, check(huge, PARKED, false, DEFAULT_RULES));
        int[] tooMany = new int[MissionLimits.packedInts(MissionLimits.MAX_WAYPOINTS, 0)];
        tooMany[0] = MissionLimits.FORMAT_VERSION;
        tooMany[5] = MissionLimits.MAX_WAYPOINTS + 1;
        assertEquals(UploadCheck.Result.INVALID, check(tooMany, PARKED, false, DEFAULT_RULES));
    }

    @Test
    void droneMustBeLinkedFoundAndOwned() {
        int[] packed = mission(Waypoint.at(1, 70, 1));
        assertEquals(UploadCheck.Result.NOT_LINKED, UploadCheck.check(true, packed, false, PARKED, false, DEFAULT_RULES).result());
        assertEquals(UploadCheck.Result.NOT_FOUND, check(packed, UploadCheck.Drone.MISSING, true, DEFAULT_RULES));
        assertEquals(UploadCheck.Result.NOT_OWNER, check(packed, FOREIGN, false, DEFAULT_RULES), "gekoppelter Flugplaner allein reicht nicht");
        UploadCheck.Drone ownedButFlownByOther = new UploadCheck.Drone(true, true, true, false, 0, 64, 0);
        assertEquals(UploadCheck.Result.NOT_OWNER, check(packed, ownedButFlownByOther, false, DEFAULT_RULES));
        assertEquals(UploadCheck.Result.OK, check(packed, ownedButFlownByOther, true, DEFAULT_RULES), "Rechtestufe 2");
        UploadCheck.Drone ownPilot = new UploadCheck.Drone(true, false, false, true, 0, 64, 0);
        assertEquals(UploadCheck.Result.OK, check(packed, ownPilot, false, DEFAULT_RULES), "eigener Pilot");
        assertTrue(UploadCheck.owner(false, PARKED));
        assertFalse(UploadCheck.owner(false, FOREIGN));
        assertTrue(UploadCheck.owner(true, FOREIGN));
    }

    @Test
    void worldRulesLimitAltitudeAndDistance() {
        int[] packed = mission(Waypoint.at(1, 100, 1), Waypoint.at(10, 190, 10), Waypoint.at(500, 80, 0));
        UploadCheck.Verdict altitude = UploadCheck.check(true, packed, true, PARKED, false, new UploadCheck.Rules(120, 0));
        assertEquals(UploadCheck.Result.ALTITUDE, altitude.result());
        assertEquals(1, altitude.waypoint());
        UploadCheck.Verdict distance = UploadCheck.check(true, packed, true, PARKED, false, new UploadCheck.Rules(0, 300));
        assertEquals(UploadCheck.Result.DISTANCE, distance.result());
        assertEquals(2, distance.waypoint());
        assertEquals(UploadCheck.Result.OK, check(packed, PARKED, false, new UploadCheck.Rules(0, 0)), "0 = aus");
        assertEquals(UploadCheck.Result.ALTITUDE, check(packed, PARKED, true, new UploadCheck.Rules(120, 0)), "auch für Operatoren");
    }

    @Test
    void everyResultHasAMessageKey() {
        for (UploadCheck.Result result : UploadCheck.Result.values()) {
            assertTrue(result.messageKey().startsWith("camdrones.planner.upload."));
        }
    }
}
