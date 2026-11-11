package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.CamDrones;
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

public final class AttiTone {
    public static final Identifier SOUND = CamDrones.id("warning.atti");
    public static final float PITCH = 1.0F;
    public static final float FALLBACK_PITCH = 0.7F;

    private static final LimitChime CHIME = new LimitChime();
    private static final SoundEvent OWN = SoundEvent.createVariableRangeEvent(SOUND);

    private AttiTone() {
    }

    public static boolean warns(NavStatus status) {
        return status.warns(NavOutput.Warning.OBSTACLE_OFF);
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.isPaused()) {
            CHIME.reset();
            return;
        }
        ClientInput input = PropwashClientApi.input();
        Optional<Entity> drone = input.flying() ? input.drone() : Optional.empty();
        if (drone.isEmpty()) {
            CHIME.reset();
            return;
        }
        boolean off = CamOsdSource.pilotStatus(drone.get()).filter(AttiTone::warns).isPresent();
        double time = drone.get().level().getGameTime() / CamOsdSource.TICKS_PER_SECOND;
        if (CHIME.update(time, off)) {
            float volume = ObstacleTone.volume();
            if (volume > 0.0F) {
                boolean own = minecraft.getSoundManager().getSoundEvent(SOUND) != null;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(own ? OWN : SoundEvents.NOTE_BLOCK_BASS.value(), own ? PITCH : FALLBACK_PITCH,
                        volume));
            }
        }
    }
}
