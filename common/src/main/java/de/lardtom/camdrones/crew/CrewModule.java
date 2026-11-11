package de.lardtom.camdrones.crew;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.event.PropwashEvents;

public final class CrewModule {
    private static boolean initialized;

    private CrewModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CrewData.init();
        PropwashEvents.PILOT_CHANGED.register(CrewService::onPilotChanged);
        CrewCommands.extend();
        CamDrones.LOG.info("{}: Gimbal-Daten {} und Zwei-Piloten bereit", CamDrones.MOD_NAME, CrewData.GIMBAL_ID);
    }

    public static synchronized boolean initialized() {
        return initialized;
    }
}
