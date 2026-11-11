package de.lardtom.camdrones.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.hud.CamHud;
import de.lardtom.camdrones.client.planner.PlannerClient;
import de.lardtom.camdrones.client.school.CamSchool;
import de.lardtom.camdrones.integration.autonomy.client.AutonomyClient;
import de.lardtom.camdrones.integration.client.PilotNavContext;
import de.lardtom.camdrones.part.client.PartsClient;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

public final class CamDronesClient {
    private static final String DEV_CLIENT = "de.lardtom.camdrones.dev.client.DevClientBootstrap";
    private static boolean initialized;
    private static volatile Consumer<Minecraft> devTick = minecraft -> {
    };

    private CamDronesClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CamDrones.LOG.info("{} Client bereit", CamDrones.MOD_NAME);
        PilotNavContext.install();
        CamHud.init();
        PlannerClient.init();
        AutonomyClient.init();
        GimbalClient.init();
        PartsClient.init();
        CamSchool.init();
        devtest();
    }

    public static void devTick(Consumer<Minecraft> listener) {
        devTick = devTick.andThen(listener);
    }

    public static void tick(Minecraft minecraft) {
        devTick.accept(minecraft);
    }

    private static void devtest() {
        if (!Boolean.getBoolean("camdrones.selftest") && !Boolean.getBoolean("camdrones.smoketest")) {
            return;
        }
        Class<?> type;
        try {
            type = Class.forName(DEV_CLIENT, true, CamDronesClient.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            CamDrones.LOG.error("{}: Selbsttest angefordert, aber {} fehlt im Klassenpfad", CamDrones.MOD_NAME, DEV_CLIENT);
            return;
        }
        try {
            type.getMethod("init").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(DEV_CLIENT + " startet nicht", e);
        }
    }
}
