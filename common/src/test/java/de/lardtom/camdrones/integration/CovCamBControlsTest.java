package de.lardtom.camdrones.integration;

import de.lardtom.propwash.api.input.SwitchSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovCamBControlsTest {
    @Test
    void followMeAndCourseLockLatchOnTheGamepadAndReachTheServer() {
        for (CamControl control : List.of(CamControl.FOLLOW_ME, CamControl.COURSE_LOCK)) {
            SwitchSpec spec = CamSwitches.switchSpec(control);
            assertTrue(spec.toggle(), control + " ist ein Schalter");
            assertTrue(spec.latchesOnGamepad(), control + " rastet am Gamepad je Druck ein und aus");
            assertTrue(spec.serverSync(), control + " meldet jeden Wechsel an den Server");
        }
    }

    @Test
    void recenterIsAPushButtonThatOnlyActsWhileHeld() {
        SwitchSpec spec = CamSwitches.switchSpec(CamControl.GIMBAL_RECENTER);
        assertFalse(spec.toggle(), "Gimbal zentrieren ist ein Taster");
        assertFalse(spec.latchesOnGamepad(), "Gimbal zentrieren rastet am Gamepad nicht ein");
    }

    @Test
    void everySwitchLatchesOnTheGamepadAndNoPushButtonDoes() {
        List<String> wrong = new ArrayList<>();
        for (CamControl control : CamControl.switches()) {
            SwitchSpec spec = CamSwitches.switchSpec(control);
            if (spec.latchesOnGamepad() != (control.kind() == CamControl.Kind.SWITCH)) {
                wrong.add(control.path() + " (" + control.kind() + ", rastet " + spec.latchesOnGamepad() + ")");
            }
        }
        assertEquals(List.of(), wrong);
    }
}
