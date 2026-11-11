package de.lardtom.camdrones;

import de.lardtom.camdrones.crew.CrewModule;
import de.lardtom.camdrones.integration.CamIntegration;
import de.lardtom.camdrones.integration.autonomy.AutonomyModule;
import de.lardtom.camdrones.pad.PadModule;
import de.lardtom.camdrones.part.PartsModule;
import de.lardtom.camdrones.planner.PlannerModule;
import de.lardtom.camdrones.platform.Services;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public final class CamDrones {
    public static final String MOD_ID = "camdrones";
    public static final String MOD_NAME = "Propwash: Camera Drones";
    public static final String REQUIRED_API = "1.8";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
    private static final String DEV_SERVER = "de.lardtom.camdrones.dev.server.DevServerBootstrap";

    private static boolean initialized;
    private static volatile Consumer<MinecraftServer> serverTick = server -> {
    };

    private CamDrones() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashApi.requireVersion(MOD_ID, REQUIRED_API);
        PartsModule.init();
        CamIntegration.init();
        PlannerModule.init();
        AutonomyModule.init();
        CrewModule.init();
        PadModule.init();
        LOG.info("{} geladen ({}), Propwash-API {}", MOD_NAME, Services.PLATFORM.name(), PropwashApi.API_VERSION);
        devServer();
    }

    public static void devServerTick(Consumer<MinecraftServer> listener) {
        serverTick = serverTick.andThen(listener);
    }

    public static void serverTick(MinecraftServer server) {
        serverTick.accept(server);
    }

    private static void devServer() {
        if (!Boolean.getBoolean("camdrones.serversmoke")) {
            return;
        }
        Class<?> type;
        try {
            type = Class.forName(DEV_SERVER, true, CamDrones.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            LOG.error("{}: Server-Test angefordert, aber {} fehlt im Klassenpfad", MOD_NAME, DEV_SERVER);
            return;
        }
        try {
            type.getMethod("init").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(DEV_SERVER + " startet nicht", e);
        }
    }

    public static boolean initialized() {
        return initialized;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
