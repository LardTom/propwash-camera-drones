package de.lardtom.camdrones.pad;

import de.lardtom.camdrones.part.CamTab;

public final class PadModule {
    private static boolean initialized;

    private PadModule() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CamTab.tab().ifPresent(tab -> tab.add(PadBlocks::creativeStacks));
    }

    public static synchronized boolean initialized() {
        return initialized;
    }
}
