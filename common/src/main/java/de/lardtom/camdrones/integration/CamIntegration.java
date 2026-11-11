package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.propwash.api.PropwashApi;

public final class CamIntegration {
    private static boolean initialized;

    private CamIntegration() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CamSwitches.init();
        CamWorldRules.init();
        CamDroneData.init();
        PropwashApi.flight().registerAssist(NavAssist.ID, NavAssist::pilotFor);
        CamCrash.init();
        CamDrones.LOG.info("{}: Flughilfe {}, {} Schalter, {} Achsen, {} Welt-Regeln, {} Drohnen-Daten", CamDrones.MOD_NAME, NavAssist.ID,
                CamSwitches.switches().size(), CamSwitches.axes().size(), CamWorldRules.registered().size(), CamDroneData.keys().all().size());
    }

    public static synchronized boolean initialized() {
        return initialized;
    }
}
