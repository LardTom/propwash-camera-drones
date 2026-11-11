package de.lardtom.camdrones.integration;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.propwash.api.event.DroneDamage;
import de.lardtom.propwash.api.event.PropwashEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public final class CamCrash {
    private static boolean initialized;

    private CamCrash() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashEvents.DRONE_DAMAGED.register(CamCrash::onDamaged);
    }

    public static synchronized boolean initialized() {
        return initialized;
    }

    public static boolean endsMission(boolean cameraDrone, boolean cancelled, float amount, boolean byPlayer) {
        return cameraDrone && !cancelled && amount > 0.0F && !byPlayer;
    }

    static void onDamaged(DroneDamage damage) {
        Entity drone = damage.drone();
        boolean camera = CamDroneData.registered() && !CamDroneData.NO_SERIAL.equals(AutonomyServer.serial(drone));
        if (!endsMission(camera, damage.cancelled(), damage.amount(), damage.source().getEntity() instanceof Player)) {
            return;
        }
        AutonomyServer.Reply reply = AutonomyServer.execute(Optional.empty(), true, drone, MissionCommand.Command.STOP, MissionCommand.AUTO_INDEX);
        if (reply.ok()) {
            CamDrones.LOG.info("{}: Drohne {} beschädigt ({}), Mission bzw. Flug beendet: {}", CamDrones.MOD_NAME, AutonomyServer.label(drone),
                    damage.source().getMsgId(), reply.status());
        }
    }
}
