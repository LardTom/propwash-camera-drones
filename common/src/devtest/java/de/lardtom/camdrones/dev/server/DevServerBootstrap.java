package de.lardtom.camdrones.dev.server;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.event.PropwashEvents;

public final class DevServerBootstrap {
    private static boolean installed;

    private DevServerBootstrap() {
    }

    public static synchronized void init() {
        if (installed) {
            return;
        }
        installed = true;
        CamDrones.devServerTick(ServerTest::tick);
        PropwashEvents.AUTONOMY_ENDED.register(ServerTest::onEnded);
        CamDrones.LOG.info("[Kamera-Servertest] aktiv, Fall: {}", ServerTest.caseName());
    }
}
