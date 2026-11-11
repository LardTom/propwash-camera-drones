package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.part.GimbalTable;
import de.lardtom.propwash.api.drone.AutonomyEnd;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.SensorDirection;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlightOptionsTest {
    private static final Mission HIGH = Mission.of(List.of(Waypoint.at(0.0, 76.0, 0.0), Waypoint.at(10.0, 140.0, 0.0)));

    private static List<AutonomyPlan> plans() {
        return List.of(AutonomyPlan.mission(HIGH, 0), AutonomyPlan.hold(), AutonomyPlan.follow(UUID.randomUUID(), FollowController.Style.BEHIND),
                AutonomyPlan.orbit(0.0, 64.0, 0.0, 10.0, 3.0, true), AutonomyPlan.land());
    }

    @Test
    void flightsLastAsLongAsTheBatteryWithTheHardSixtyMinuteLimit() {
        for (AutonomyPlan plan : plans()) {
            AutonomyOptions options = FlightOptions.of(plan, OptionalDouble.empty());
            assertEquals(AutonomyOptions.MAX_DURATION, options.maxDuration(), plan.kind().name());
            assertEquals(Duration.ofMinutes(60), options.maxDuration());
        }
        assertThrows(IllegalArgumentException.class, () -> AutonomyOptions.of(CamDrones.id("mission")).maxDuration(Duration.ofMinutes(61)));
    }

    @Test
    void worldRuleShortensEveryFlightKindAndStaysWithinPropwashLimits() {
        for (AutonomyPlan plan : plans()) {
            assertEquals(Duration.ofMinutes(15), FlightOptions.of(plan, OptionalDouble.empty(), 15).maxDuration(), plan.kind().name());
            assertEquals(Duration.ofMinutes(1), FlightOptions.of(plan, OptionalDouble.empty(), 1).maxDuration());
            assertEquals(AutonomyOptions.MAX_DURATION, FlightOptions.of(plan, OptionalDouble.empty(), 60).maxDuration());
        }
        assertEquals(Duration.ofMinutes(1), FlightOptions.maxDuration(0));
        assertEquals(Duration.ofMinutes(1), FlightOptions.maxDuration(-5));
        assertEquals(AutonomyOptions.MAX_DURATION, FlightOptions.maxDuration(240));
        assertEquals(60, CamWorldRules.MAX_AUTONOMY_MINUTES);
        assertEquals(CamWorldRules.MAX_AUTONOMY_MINUTES, CamWorldRules.DEFAULT_AUTONOMY_MINUTES);
        AutonomyOptions options = FlightOptions.of(AutonomyPlan.hold(), OptionalDouble.of(5.0), 2);
        assertEquals(Duration.ofMinutes(2), options.maxDuration());
        assertEquals(5.0, options.takeoff().orElseThrow(), 1.0E-9);
    }

    @Test
    void idsAndLabelsStayInTheOwnNamespaceAndFitTheOsd() {
        Set<Identifier> ids = new HashSet<>();
        for (AutonomyPlan plan : plans()) {
            AutonomyOptions options = FlightOptions.of(plan, OptionalDouble.empty());
            assertEquals(CamDrones.MOD_ID, options.id().getNamespace());
            assertTrue(ids.add(options.id()));
            assertTrue(options.label().length() <= AssistOutput.MAX_LABEL, options.label());
            assertEquals(plan.kind(), AutonomyPlan.Kind.byId(options.id()).orElseThrow());
        }
        assertTrue(AutonomyPlan.Kind.byId(Identifier.fromNamespaceAndPath("propwash", "mission")).isEmpty());
    }

    @Test
    void takeoffOnlyFromTheGroundAndToTheFirstWaypointWithinPropwashLimits() {
        assertTrue(FlightOptions.takeoff(true, AutonomyPlan.mission(HIGH, 0), 64.0).isEmpty());
        assertEquals(12.0, FlightOptions.takeoff(false, AutonomyPlan.mission(HIGH, 0), 64.0).orElseThrow(), 1.0E-9);
        assertEquals(AutonomyOptions.MAX_TAKEOFF_METRES, FlightOptions.takeoff(false, AutonomyPlan.mission(HIGH, 1), 64.0).orElseThrow());
        assertEquals(AutonomyOptions.MIN_TAKEOFF_METRES, FlightOptions.takeoff(false, AutonomyPlan.mission(HIGH, 0), 100.0).orElseThrow());
        assertEquals(FollowController.HEIGHT, FlightOptions.takeoff(false, AutonomyPlan.follow(UUID.randomUUID(), FollowController.Style.BEHIND),
                64.0).orElseThrow());
        assertEquals(FlightOptions.HOLD_TAKEOFF, FlightOptions.takeoff(false, AutonomyPlan.hold(), 64.0).orElseThrow());
        assertEquals(FlightOptions.ORBIT_TAKEOFF, FlightOptions.takeoff(false, AutonomyPlan.orbit(0, 64, 0, 10, 3, true), 64.0).orElseThrow());
        assertTrue(FlightOptions.takeoff(false, AutonomyPlan.land(), 64.0).isEmpty());
        AutonomyOptions options = FlightOptions.of(AutonomyPlan.hold(), OptionalDouble.of(99.0));
        assertEquals(AutonomyOptions.MAX_TAKEOFF_METRES, options.takeoff().orElseThrow());
    }

    @Test
    void everyResultAndEndHasAStatus() {
        for (AutonomyResult result : AutonomyResult.values()) {
            AutonomyStatus status = AutonomyStatus.of(result, AutonomyPlan.Kind.MISSION);
            assertEquals(result.started(), !status.error(), result.name());
        }
        assertEquals(AutonomyStatus.NO_BUDGET, AutonomyStatus.of(AutonomyResult.NO_BUDGET, AutonomyPlan.Kind.HOLD));
        Set<AutonomyStatus> ends = new HashSet<>();
        for (AutonomyEnd end : AutonomyEnd.values()) {
            assertTrue(ends.add(AutonomyStatus.ended(end)));
        }
        for (AutonomyPlan.Kind kind : AutonomyPlan.Kind.values()) {
            assertFalse(AutonomyStatus.started(kind).error());
        }
        assertEquals(AutonomyStatus.DENIED, AutonomyStatus.byOrdinal(-1));
        assertEquals(AutonomyStatus.DENIED, AutonomyStatus.byOrdinal(AutonomyStatus.values().length));
    }

    @Test
    void plansValidateTheirInput() {
        assertThrows(IllegalArgumentException.class, () -> new AutonomyPlan(AutonomyPlan.Kind.MISSION, Optional.empty(), 0, false, Optional.empty(),
                FollowController.Style.BEHIND, 0, 0, 0, 0, 0, true));
        assertThrows(IllegalArgumentException.class, () -> new AutonomyPlan(AutonomyPlan.Kind.FOLLOW, Optional.empty(), 0, false, Optional.empty(),
                FollowController.Style.BEHIND, 0, 0, 0, 0, 0, true));
        assertEquals(1, AutonomyPlan.mission(HIGH, 99).startIndex());
        assertEquals(0, AutonomyPlan.mission(HIGH, -5).startIndex());
        assertEquals(0.0, AutonomyPlan.orbit(Double.NaN, 0, 0, -3, Double.POSITIVE_INFINITY, true).x());
    }

    @Test
    void serverProfileFollowsTheGimbalThenTheMass() {
        assertEquals(FlightClass.CINE, ServerProfiles.flightClass(0.3, Optional.of(GimbalTable.CINE)));
        assertEquals(FlightClass.COMPACT, ServerProfiles.flightClass(0.25, Optional.empty()));
        assertEquals(FlightClass.TOUR, ServerProfiles.flightClass(0.72, Optional.empty()));
        assertEquals(FlightClass.CINE, ServerProfiles.flightClass(3.95, Optional.empty()));
        assertEquals(Set.of(SensorDirection.DOWN), ServerProfiles.sensors(FlightClass.COMPACT));
        assertEquals(EnumSet.allOf(SensorDirection.class), ServerProfiles.sensors(FlightClass.TOUR));
        assertTrue(ServerProfiles.cameraDrone(Optional.of(GimbalTable.TOUR), false));
        assertTrue(ServerProfiles.cameraDrone(Optional.empty(), true));
        assertFalse(ServerProfiles.cameraDrone(Optional.of(Identifier.fromNamespaceAndPath("propwash", "action_cam")), false));
    }
}
