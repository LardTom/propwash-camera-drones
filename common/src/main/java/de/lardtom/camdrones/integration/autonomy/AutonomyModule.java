package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.CamDrones;

public final class AutonomyModule {
    private static boolean initialized;

    private AutonomyModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        AutonomyServer.init();
        CamDrones.LOG.info("{}: Flüge ohne Pilot angemeldet (Übergabe {}, Ende-Ereignis, {} Flugarten)", CamDrones.MOD_NAME,
                AutonomyServer.EXIT_HANDLER_ID, AutonomyPlan.Kind.values().length);
    }

    public static synchronized boolean initialized() {
        return initialized;
    }
}
