package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.CameraActions;
import de.lardtom.camdrones.client.gimbal.model.ShotSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShotSourceTest {
    @Test
    void ownerWatchingInLiveViewTakesTheMissionShots() {
        ShotSource source = ShotSource.of(false, true, false);
        assertEquals(ShotSource.WATCHING, source);
        assertTrue(source.takesShots());
    }

    @Test
    void pilotAndOperatorKeepTakingTheShots() {
        assertEquals(ShotSource.CONTROL, ShotSource.of(true, false, true));
        assertEquals(ShotSource.CONTROL, ShotSource.of(true, true, false));
        assertTrue(ShotSource.of(true, false, false).takesShots());
    }

    @Test
    void nobodyWatchingOrFlyingAnotherDroneTakesNoShots() {
        assertFalse(ShotSource.of(false, false, false).takesShots());
        assertFalse(ShotSource.of(false, true, true).takesShots());
        assertFalse(ShotSource.of(false, false, true).takesShots());
    }

    @Test
    void watcherSeesNoRefusalButEveryShot() {
        assertFalse(ShotSource.WATCHING.shows(CameraActions.Done.REFUSED));
        assertTrue(ShotSource.WATCHING.shows(CameraActions.Done.STARTED));
        assertTrue(ShotSource.WATCHING.shows(CameraActions.Done.STOPPED));
        assertTrue(ShotSource.WATCHING.shows(CameraActions.Done.PHOTO));
        assertTrue(ShotSource.CONTROL.shows(CameraActions.Done.REFUSED));
    }
}
