package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.OperatorGimbalInput;
import de.lardtom.camdrones.client.gimbal.model.SoloMission;
import de.lardtom.camdrones.client.gimbal.model.SoloPickup;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.logic.nav.NavLogic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoloPickupTest {
    private static final GimbalLimits TOUR = GimbalLimits.of(-90.0, 30.0, false);
    private static final boolean[] NONE = new boolean[4];

    private static boolean[] press(int number) {
        boolean[] actions = new boolean[4];
        actions[number - 1] = true;
        return actions;
    }

    private static double[] sticks(double pan, double tilt, double throttle) {
        return new double[]{pan, tilt, 0.0, throttle};
    }

    @Test
    void untilTheFirstStickMoveTheFlightPlanKeepsTheGimbal() {
        SoloPickup pickup = new SoloPickup();
        pickup.start(0.0, false);
        assertFalse(pickup.update(0.0, 0.0, 0.0, false));
        assertFalse(pickup.update(0.05, -0.05, 0.02, false), "Totzone und Zittern am Gas zählen nicht");
        assertTrue(pickup.update(0.0, -0.3, 0.0, false), "Neigen übernimmt");
        assertTrue(pickup.update(0.0, 0.0, 0.0, false), "bleibt übernommen");
        SoloPickup pan = new SoloPickup();
        pan.start(0.0, false);
        assertTrue(pan.update(0.4, 0.0, 0.0, false));
        SoloPickup zoom = new SoloPickup();
        zoom.start(0.0, false);
        assertTrue(zoom.update(0.0, 0.0, 0.5, false), "Zoomen übernimmt");
        SoloPickup recenter = new SoloPickup();
        recenter.start(0.0, false);
        assertTrue(recenter.update(0.0, 0.0, 0.0, true), "Aktion 2 übernimmt");
        SoloPickup back = new SoloPickup();
        back.start(0.0, true);
        assertTrue(back.steering(), "schon gesteuert, etwa nach dem Wiederöffnen");
    }

    @Test
    void steeringStartsAtTheVisiblePlanAimWithoutJump() {
        OperatorGimbalInput input = new OperatorGimbalInput();
        input.missionControl(true);
        input.takeOver(GimbalSetting.CENTER, NONE);
        GimbalSetting plan = new GimbalSetting(GimbalSetting.Mode.LOCK, -30.0, 0.0, 90.0, 0.0);
        input.adopt(plan);
        input.update(TOUR, sticks(0.0, -1.0, 0.0), NONE, 0.25, 0L, 90.0);
        assertEquals(-45.0, input.setting().tilt(), 1.0E-9, "Neigung läuft vom Flugplan (−30°) weiter, nicht von 0°");
        assertEquals(90.0, input.setting().heading(), 1.0E-9, "Blickrichtung bleibt die des Flugplans");
        assertTrue(input.setting().locked());
    }

    @Test
    void actionOnePausesResumesAndHeldAbortsOnlyForTheSoloCamera() {
        OperatorGimbalInput crew = new OperatorGimbalInput();
        crew.takeOver(GimbalSetting.CENTER, NONE);
        List<OperatorGimbalInput.Event> crewEvents = new ArrayList<>();
        for (long t = 0L; t <= 2_000L; t += 50L) {
            crewEvents.addAll(crew.update(TOUR, sticks(0, 0, 0), press(1), 0.05, t, 0.0));
        }
        crewEvents.addAll(crew.update(TOUR, sticks(0, 0, 0), NONE, 0.05, 2_050L, 0.0));
        assertTrue(crewEvents.isEmpty(), "Aktion 1 bleibt für den Kameramann der Crew ohne Funktion: " + crewEvents);

        OperatorGimbalInput solo = new OperatorGimbalInput();
        solo.missionControl(true);
        solo.takeOver(GimbalSetting.CENTER, NONE);
        assertTrue(solo.update(TOUR, sticks(0, 0, 0), press(1), 0.05, 0L, 0.0).isEmpty());
        assertTrue(solo.update(TOUR, sticks(0, 0, 0), press(1), 0.05, 200L, 0.0).isEmpty());
        assertEquals(List.of(OperatorGimbalInput.Event.MISSION), solo.update(TOUR, sticks(0, 0, 0), NONE, 0.05, 250L, 0.0), "kurz drücken");
        List<OperatorGimbalInput.Event> held = new ArrayList<>();
        for (long t = 1_000L; t <= 2_500L; t += 50L) {
            held.addAll(solo.update(TOUR, sticks(0, 0, 0), press(1), 0.05, t, 0.0));
        }
        held.addAll(solo.update(TOUR, sticks(0, 0, 0), NONE, 0.05, 2_550L, 0.0));
        assertEquals(List.of(OperatorGimbalInput.Event.ABORT), held, "lang drücken bricht einmal ab, Loslassen pausiert nicht");

        OperatorGimbalInput heldAtStart = new OperatorGimbalInput();
        heldAtStart.missionControl(true);
        heldAtStart.takeOver(GimbalSetting.CENTER, press(1));
        List<OperatorGimbalInput.Event> late = new ArrayList<>();
        for (long t = 0L; t <= 1_500L; t += 50L) {
            late.addAll(heldAtStart.update(TOUR, sticks(0, 0, 0), press(1), 0.05, t, 0.0));
        }
        late.addAll(heldAtStart.update(TOUR, sticks(0, 0, 0), NONE, 0.05, 1_550L, 0.0));
        assertTrue(late.isEmpty(), "beim Öffnen schon gedrückt löst nichts aus: " + late);
    }

    @Test
    void missionCommandsFollowTheMissionState() {
        assertEquals(Optional.of(SoloMission.Command.PAUSE), SoloMission.command(OperatorGimbalInput.Event.MISSION, NavLogic.MissionState.RUNNING));
        assertEquals(Optional.of(SoloMission.Command.RESUME), SoloMission.command(OperatorGimbalInput.Event.MISSION, NavLogic.MissionState.PAUSED));
        assertEquals(Optional.empty(), SoloMission.command(OperatorGimbalInput.Event.MISSION, NavLogic.MissionState.DONE));
        assertEquals(Optional.empty(), SoloMission.command(OperatorGimbalInput.Event.MISSION, null));
        assertEquals(Optional.of(SoloMission.Command.STOP), SoloMission.command(OperatorGimbalInput.Event.ABORT, NavLogic.MissionState.RUNNING));
        assertEquals(Optional.of(SoloMission.Command.STOP), SoloMission.command(OperatorGimbalInput.Event.ABORT, NavLogic.MissionState.PAUSED));
        assertEquals(Optional.empty(), SoloMission.command(OperatorGimbalInput.Event.RECENTER, NavLogic.MissionState.RUNNING));
    }
}
