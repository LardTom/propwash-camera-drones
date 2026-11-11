package de.lardtom.camdrones.part.client;

import de.lardtom.propwash.api.client.PropwashClientApi;

public final class PartsClient {
    private static boolean initialized;

    private PartsClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashClientApi.registerWorkbenchExtension(new CameraCheckExtension());
    }
}
