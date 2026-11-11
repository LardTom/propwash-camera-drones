package de.lardtom.camdrones.client.planner;

import de.lardtom.camdrones.planner.PlannerTablets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

final class PlannerSounds {
    static final float CLICK_VOLUME = 0.5F;

    private PlannerSounds() {
    }

    static void click(float pitch) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager().getSoundEvent(PlannerTablets.CLICK_SOUND.location()) != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(PlannerTablets.CLICK_SOUND, pitch, CLICK_VOLUME));
        }
    }
}
