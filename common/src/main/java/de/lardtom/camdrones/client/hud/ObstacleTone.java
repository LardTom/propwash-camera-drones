package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.planner.CamClientConfig;
import de.lardtom.camdrones.integration.NavStatus;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.propwash.api.client.ClientInput;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public final class ObstacleTone {
    public static final Identifier SOUND = CamDrones.id("warning.obstacle");
    public static final float VOLUME = (float) CamClientConfig.WARNING_VOLUME_DEFAULT;

    private static final ObstacleBeeper BEEPER = new ObstacleBeeper();
    private static final SoundEvent OWN = SoundEvent.createVariableRangeEvent(SOUND);

    private ObstacleTone() {
    }

    public static float volume() {
        return (float) CamClientConfig.get().warningVolume();
    }

    public static boolean warns(NavStatus status) {
        return status.warns(NavOutput.Warning.OBSTACLE) || status.warns(NavOutput.Warning.OBSTACLE_STOP);
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.isPaused()) {
            BEEPER.reset();
            return;
        }
        ClientInput input = PropwashClientApi.input();
        Optional<Entity> drone = input.flying() ? input.drone() : Optional.empty();
        if (drone.isEmpty()) {
            BEEPER.reset();
            return;
        }
        Optional<NavStatus> status = CamOsdSource.pilotStatus(drone.get());
        double distance = status.map(NavStatus::nearestDistance).orElse(Double.NaN);
        boolean warn = status.filter(ObstacleTone::warns).isPresent();
        double time = drone.get().level().getGameTime() / CamOsdSource.TICKS_PER_SECOND;
        if (BEEPER.update(time, distance, warn)) {
            float volume = volume();
            if (volume > 0.0F) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound(minecraft), ObstacleBeeper.pitch(distance), volume));
            }
        }
    }

    private static SoundEvent sound(Minecraft minecraft) {
        return minecraft.getSoundManager().getSoundEvent(SOUND) != null ? OWN : SoundEvents.NOTE_BLOCK_BIT.value();
    }
}
