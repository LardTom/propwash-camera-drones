package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdWarning;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

public final class CamHud {
    private static boolean initialized;

    private CamHud() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        List<OsdElementSpec> elements = CamOsdElements.specs(CamOsdSource::state);
        for (OsdElementSpec spec : elements) {
            PropwashClientApi.registerOsdElement(spec);
        }
        Map<Identifier, OsdWarning> warnings = CamOsdElements.warnings(CamOsdSource::state);
        for (Map.Entry<Identifier, OsdWarning> entry : warnings.entrySet()) {
            PropwashClientApi.registerOsdWarning(entry.getKey(), entry.getValue());
        }
        PropwashClientApi.registerHudElement(CameraView.ID, new CameraView());
        CamDrones.LOG.info("{}: {} OSD-Elemente, {} OSD-Warnungen und Kamera-Ansicht {} angemeldet", CamDrones.MOD_NAME, elements.size(),
                warnings.size(), CameraView.ID);
    }

    public static boolean initialized() {
        return initialized;
    }

    public static void tick(Minecraft minecraft) {
        if (initialized) {
            ObstacleTone.tick(minecraft);
            LimitTone.tick(minecraft);
            AttiTone.tick(minecraft);
        }
    }
}
