package de.lardtom.camdrones.part;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.PropwashApi;

public final class PartsModule {
    private static int parts = -1;
    private static int items;
    private static int presets;
    private static int loot;

    private PartsModule() {
    }

    public static synchronized void init() {
        if (parts >= 0) {
            return;
        }
        parts = PropwashApi.parts().loadBundled(CamDrones.MOD_ID);
        items = PropwashApi.partItems().registerItems(CamDrones.MOD_ID);
        PropwashApi.accessories().register(GimbalTable.CINE, new LensBay());
        for (CamPresets.Entry entry : CamPresets.ALL) {
            PropwashApi.builds().registerPreset(entry.preset());
            presets++;
        }
        loot = CamLoot.register();
        CamTab.init();
        CamDrones.LOG.info("{}: {} Teile aus JSON, {} Teil-Items, {} Presets, {} Truhen-Einspeisungen", CamDrones.MOD_NAME, parts, items,
                presets, loot);
    }

    public static synchronized int parts() {
        return parts;
    }

    public static synchronized int items() {
        return items;
    }

    public static synchronized int presets() {
        return presets;
    }

    public static synchronized int loot() {
        return loot;
    }
}
