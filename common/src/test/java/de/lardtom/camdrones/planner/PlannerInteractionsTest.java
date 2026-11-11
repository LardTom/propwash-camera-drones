package de.lardtom.camdrones.planner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerInteractionsTest {
    @Test
    void sneakRightClickWithThePlannerOnADronePairs() {
        assertTrue(PlannerInteractions.pairs(true, true, true, false, true));
    }

    @Test
    void everythingElseLeavesTheClickToPropwash() {
        assertFalse(PlannerInteractions.pairs(true, false, true, false, true), "ohne Schleichen: Rechtsklick des Items koppelt weiter");
        assertFalse(PlannerInteractions.pairs(false, true, true, false, true), "Nebenhand");
        assertFalse(PlannerInteractions.pairs(true, true, false, false, true), "leere Hand oder anderes Item: Wartungsmenü");
        assertFalse(PlannerInteractions.pairs(true, true, true, true, true), "Zuschauer");
        assertFalse(PlannerInteractions.pairs(true, true, true, false, false), "keine Drohne");
    }
}
