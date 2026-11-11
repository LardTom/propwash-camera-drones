package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.MissionCodec;
import de.lardtom.camdrones.logic.mission.MissionLimits;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.planner.model.MissionNames;
import de.lardtom.camdrones.planner.model.PlannerEntry;
import de.lardtom.camdrones.planner.model.TabletState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabletStateTest {
    @Test
    void firstBlockClickCreatesMissionOne() {
        TabletState.PlacementResult result = TabletState.EMPTY.addWaypoint(Waypoint.at(10.5, 74.0, -3.5));
        assertEquals(TabletState.PlacementResult.Outcome.ADDED, result.outcome());
        assertEquals(0, result.index());
        TabletState state = result.state();
        assertEquals(1, state.size());
        assertEquals("Mission 1", state.activeName());
        assertEquals(1, state.activeMission().size());
        assertEquals(TabletState.DEFAULT_GROUND_HEIGHT, state.groundHeight());
    }

    @Test
    void fiftyWaypointsAreTheLimit() {
        TabletState state = TabletState.EMPTY;
        for (int i = 0; i < MissionLimits.MAX_WAYPOINTS; i++) {
            TabletState.PlacementResult result = state.addWaypoint(Waypoint.at(i, 70, 0));
            assertTrue(result.changed());
            state = result.state();
        }
        TabletState.PlacementResult full = state.addWaypoint(Waypoint.at(99, 70, 0));
        assertEquals(TabletState.PlacementResult.Outcome.FULL, full.outcome());
        assertEquals(state, full.state());
        assertEquals(MissionLimits.MAX_WAYPOINTS, state.activeMission().size());
    }

    @Test
    void eightNamedMissionsAreTheLimit() {
        TabletState state = TabletState.EMPTY;
        for (int i = 0; i < TabletState.MAX_MISSIONS; i++) {
            Optional<TabletState> next = state.addMission("Flug " + i);
            assertTrue(next.isPresent());
            state = next.get();
            assertEquals(i, state.active());
        }
        assertTrue(state.full());
        assertTrue(state.addMission("zu viel").isEmpty());
        assertTrue(state.duplicateActive().isEmpty());
        assertEquals("Flug 7", state.activeName());
    }

    @Test
    void duplicateAndRemoveKeepSelectionInRange() {
        TabletState state = TabletState.EMPTY.addWaypoint(Waypoint.at(1, 70, 1)).state();
        state = state.duplicateActive().orElseThrow();
        assertEquals(2, state.size());
        assertEquals(1, state.active());
        assertEquals("Mission 1 2", state.activeName());
        assertEquals(state.entries().get(0).missionOrEmpty(), state.entries().get(1).missionOrEmpty());
        state = state.removeActive();
        assertEquals(1, state.size());
        assertEquals(0, state.active());
        state = state.removeActive();
        assertTrue(state.isEmpty());
        assertEquals(1, state.ensureActive().size());
    }

    @Test
    void removeWaypointByIndex() {
        TabletState state = TabletState.EMPTY;
        for (int i = 0; i < 3; i++) {
            state = state.addWaypoint(Waypoint.at(i, 70, 0)).state();
        }
        TabletState.PlacementResult removed = state.removeWaypoint(1);
        assertEquals(TabletState.PlacementResult.Outcome.REMOVED, removed.outcome());
        assertEquals(List.of(0.0, 2.0), removed.state().activeMission().waypoints().stream().map(Waypoint::x).toList());
        assertEquals(TabletState.PlacementResult.Outcome.MISSING, state.removeWaypoint(7).outcome());
    }

    @Test
    void groundHeightIsClamped() {
        assertEquals(TabletState.MIN_GROUND_HEIGHT, TabletState.EMPTY.withGroundHeight(-5).groundHeight());
        assertEquals(TabletState.MAX_GROUND_HEIGHT, TabletState.EMPTY.withGroundHeight(900).groundHeight());
        assertEquals(25, TabletState.EMPTY.withGroundHeight(25).groundHeight());
    }

    @Test
    void unknownVersionStaysUntouchedAndLocked() {
        int[] future = MissionCodec.encode(Mission.of(List.of(Waypoint.at(1, 70, 1))));
        future[0] = MissionLimits.FORMAT_VERSION + 1;
        PlannerEntry entry = PlannerEntry.fromPacked("Zukunft", future);
        assertFalse(entry.readable());
        assertEquals(MissionCodec.Status.UNKNOWN_VERSION, entry.status());
        assertArrayEquals(future, entry.packed());
        TabletState state = new TabletState(List.of(entry), 0, 10);
        assertEquals(TabletState.PlacementResult.Outcome.LOCKED, state.addWaypoint(Waypoint.at(0, 70, 0)).outcome());
        assertArrayEquals(future, state.activeEntry().orElseThrow().packed());
    }

    @Test
    void entriesCompareByNameAndContent() {
        Mission mission = Mission.of(List.of(Waypoint.at(1, 70, 1)));
        assertEquals(PlannerEntry.of("A", mission), PlannerEntry.of("A", mission));
        assertNotEquals(PlannerEntry.of("A", mission), PlannerEntry.of("B", mission));
        assertNotEquals(PlannerEntry.of("A", mission), PlannerEntry.of("A", Mission.empty()));
        assertEquals(PlannerEntry.of("A", mission).hashCode(), PlannerEntry.fromPacked("A", MissionCodec.encode(mission)).hashCode());
    }

    @Test
    void namesAreCleaned() {
        assertEquals("Burg Nord", MissionNames.clean("  Burg\tNord \u0007"));
        assertEquals("Rot", MissionNames.clean("§cRot".replace("§c", "§")));
        assertEquals(MissionNames.MAX_LENGTH, MissionNames.clean("x".repeat(80)).length());
        assertEquals("Mission 3", MissionNames.orDefault("   ", 3));
        assertEquals("Ü-Bahn ß", MissionNames.clean("Ü-Bahn ß"));
    }

    @Test
    void tooManyEntriesAreCut() {
        List<PlannerEntry> list = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            list.add(PlannerEntry.empty("M" + i));
        }
        TabletState state = new TabletState(list, 11, 10);
        assertEquals(TabletState.MAX_MISSIONS, state.size());
        assertEquals(TabletState.MAX_MISSIONS - 1, state.active());
    }
}
