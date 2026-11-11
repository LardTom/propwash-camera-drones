package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.part.CamTab;

public final class PlannerModule {
    private static boolean initialized;

    private PlannerModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PlannerOwners.init();
        CamNetwork.define();
        CamTab.tab().ifPresent(tab -> tab.add(PlannerItems::creativeStacks));
        CamDrones.LOG.info("{}: Flugplaner und Netzwerk (Protokoll {}) angemeldet", CamDrones.MOD_NAME, CamNetwork.PROTOCOL);
    }

    public static synchronized boolean initialized() {
        return initialized;
    }
}
