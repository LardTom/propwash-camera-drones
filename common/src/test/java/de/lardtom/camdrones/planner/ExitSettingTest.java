package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.planner.model.ExitSetting;
import de.lardtom.camdrones.planner.model.UploadCheck;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExitSettingTest {
    private static final UploadCheck.Drone OWN = new UploadCheck.Drone(true, true, false, false, 0.0, 64.0, 0.0);
    private static final UploadCheck.Drone FOREIGN = new UploadCheck.Drone(true, false, false, false, 0.0, 64.0, 0.0);
    private static final UploadCheck.Drone FLOWN_BY_OTHER = new UploadCheck.Drone(true, true, true, false, 0.0, 64.0, 0.0);
    private static final UploadCheck.Drone FLOWN_BY_SELF = new UploadCheck.Drone(true, false, false, true, 0.0, 64.0, 0.0);

    @Test
    void stepsCycleThroughHoverHomeAndLand() {
        assertEquals(ExitSetting.HOME, ExitSetting.HOVER.step(1));
        assertEquals(ExitSetting.LAND, ExitSetting.HOME.step(1));
        assertEquals(ExitSetting.HOVER, ExitSetting.LAND.step(1));
        assertEquals(ExitSetting.LAND, ExitSetting.HOVER.step(-1));
        assertEquals(ExitSetting.HOVER, ExitSetting.HOVER.step(0));
        assertEquals(ExitSetting.HOME, ExitSetting.HOVER.step(7));
    }

    @Test
    void everyChoiceMapsToTheDroneSettingAndBack() {
        assertEquals(ExitSetting.values().length, DroneSettings.ExitBehaviour.values().length);
        for (ExitSetting setting : ExitSetting.values()) {
            DroneSettings.ExitBehaviour behaviour = ExitSettings.behaviour(setting);
            assertEquals(setting.name(), behaviour.name());
            assertEquals(setting, ExitSettings.of(behaviour));
            assertEquals(behaviour, DroneSettings.DEFAULT.withExit(behaviour).exit());
            assertEquals(Optional.of(setting), ExitSetting.byId(setting.ordinal()));
        }
        assertEquals(Optional.empty(), ExitSetting.byId(-1));
        assertEquals(Optional.empty(), ExitSetting.byId(ExitSetting.values().length));
        assertEquals(ExitSetting.HOVER, ExitSettings.of(DroneSettings.DEFAULT.exit()));
    }

    @Test
    void serverChecksRateIdPairingOwnerAndPilot() {
        int home = ExitSetting.HOME.ordinal();
        assertEquals(ExitSetting.Result.RATE_LIMITED, ExitSetting.check(false, home, true, OWN, false));
        assertEquals(ExitSetting.Result.INVALID, ExitSetting.check(true, 9, true, OWN, false));
        assertEquals(ExitSetting.Result.NOT_LINKED, ExitSetting.check(true, home, false, OWN, false));
        assertEquals(ExitSetting.Result.NOT_FOUND, ExitSetting.check(true, home, true, UploadCheck.Drone.MISSING, false));
        assertEquals(ExitSetting.Result.NOT_OWNER, ExitSetting.check(true, home, true, FOREIGN, false));
        assertEquals(ExitSetting.Result.NOT_OWNER, ExitSetting.check(true, home, true, FLOWN_BY_OTHER, false));
        assertEquals(ExitSetting.Result.OK, ExitSetting.check(true, home, true, OWN, false));
        assertEquals(ExitSetting.Result.OK, ExitSetting.check(true, home, true, FLOWN_BY_SELF, false));
        assertEquals(ExitSetting.Result.OK, ExitSetting.check(true, home, true, FOREIGN, true));
    }

    @Test
    void textsUsePlannerKeys() {
        for (ExitSetting setting : ExitSetting.values()) {
            assertEquals("camdrones.planner.value.exit." + setting.key(), setting.value().key());
        }
        assertEquals("camdrones.planner.value.none", ExitSetting.value(Optional.empty()).key());
        for (ExitSetting.Result result : ExitSetting.Result.values()) {
            assertTrue(result.messageKey().startsWith("camdrones.planner.exit.result."), result.messageKey());
            assertEquals(result == ExitSetting.Result.OK, result.ok());
        }
    }
}
