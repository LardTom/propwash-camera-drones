package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.nav.NavLogic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectorAndProgressTest {
    private static final UUID A = UUID.fromString("1a2b3c4d-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("1a2bffff-0000-0000-0000-000000000002");
    private static final UUID C = UUID.fromString("99887766-0000-0000-0000-000000000003");
    private static final List<DroneSelector.Candidate<String>> DRONES = List.of(new DroneSelector.Candidate<>("a", A, "Burg"),
            new DroneSelector.Candidate<>("b", B, ""), new DroneSelector.Candidate<>("c", C, "Turm Nord"));

    @Test
    void nameOrSerialPrefixSelectsOneDrone() {
        assertEquals("a", DroneSelector.select(DRONES, Optional.of("burg"), Optional.empty()).chosen().orElseThrow().handle());
        assertEquals("c", DroneSelector.select(DRONES, Optional.of("9988"), Optional.empty()).chosen().orElseThrow().handle());
        assertEquals("b", DroneSelector.select(DRONES, Optional.of("1A2BFF"), Optional.empty()).chosen().orElseThrow().handle());
        assertEquals(DroneSelector.Outcome.AMBIGUOUS, DroneSelector.select(DRONES, Optional.of("1a2b"), Optional.empty()).outcome());
        assertEquals(DroneSelector.Outcome.NONE, DroneSelector.select(DRONES, Optional.of("1a"), Optional.empty()).outcome(), "zu kurz");
        assertEquals(DroneSelector.Outcome.NONE, DroneSelector.select(DRONES, Optional.of("dorf"), Optional.empty()).outcome());
    }

    @Test
    void withoutQueryTheHeldTabletOrTheOnlyDroneWins() {
        assertEquals("b", DroneSelector.select(DRONES, Optional.empty(), Optional.of(B)).chosen().orElseThrow().handle());
        assertEquals(DroneSelector.Outcome.AMBIGUOUS, DroneSelector.select(DRONES, Optional.empty(), Optional.empty()).outcome());
        assertEquals("c", DroneSelector.select(DRONES.subList(2, 3), Optional.empty(), Optional.of(A)).chosen().orElseThrow().handle());
        assertEquals(DroneSelector.Outcome.NONE, DroneSelector.select(List.<DroneSelector.Candidate<String>>of(), Optional.empty(),
                Optional.empty()).outcome());
    }

    @Test
    void labelsAndSuggestionsUseNamesOrShortSerials() {
        assertEquals("1A2BFFFF", DroneSelector.label(B, " "));
        assertEquals("Burg", DRONES.getFirst().label());
        assertEquals(List.of("Burg", "1A2BFFFF", "99887766"), DroneSelector.suggestions(DRONES));
    }

    @Test
    void progressIsAcceptedOnlyFromThePilotWithTheCurrentEpoch() {
        MissionStatus current = new MissionStatus(NavLogic.MissionState.RUNNING, 1, 6);
        assertEquals(new MissionStatus(NavLogic.MissionState.RUNNING, 2, 6),
                ProgressCheck.apply(true, true, current, NavLogic.MissionState.RUNNING, 2, 6, 5).orElseThrow());
        assertTrue(ProgressCheck.apply(false, true, current, NavLogic.MissionState.RUNNING, 2, 6, 5).isEmpty(), "nur der Pilot");
        assertTrue(ProgressCheck.apply(true, true, current, NavLogic.MissionState.RUNNING, 2, 5, 5).isEmpty(), "alte Epoche");
        assertTrue(ProgressCheck.apply(true, false, current, NavLogic.MissionState.RUNNING, 2, 6, 5).isEmpty(), "Rate");
        assertTrue(ProgressCheck.apply(true, true, current, NavLogic.MissionState.RUNNING, 5, 6, 5).isEmpty(), "Index außerhalb");
        assertTrue(ProgressCheck.apply(true, true, current, NavLogic.MissionState.RUNNING, 1, 6, 5).isEmpty(), "unverändert");
        assertEquals(NavLogic.MissionState.DONE, ProgressCheck.apply(true, true, current, NavLogic.MissionState.DONE, 4, 6, 5).orElseThrow().state());
        assertTrue(ProgressCheck.apply(true, true, current, NavLogic.MissionState.RUNNING, 0, 6, 0).isEmpty(), "ohne Mission kein Lauf");
    }
}
