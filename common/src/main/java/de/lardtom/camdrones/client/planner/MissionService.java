package de.lardtom.camdrones.client.planner;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.crew.CrewDrones;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.propwash.api.client.ServiceExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class MissionService implements ServiceExtension {
    public static final Identifier ID = CamDrones.id("mission");
    public static final String BUTTON_KEY = FieldText.PREFIX + "service.button";

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public Component label() {
        return Component.translatable(BUTTON_KEY);
    }

    @Override
    public boolean visible(Entity drone) {
        return CrewDrones.hasGimbal(drone);
    }

    @Override
    public void open(Entity drone, @Nullable Screen parent) {
        Minecraft.getInstance().gui.setScreen(new MissionServiceScreen(drone, parent));
    }
}
