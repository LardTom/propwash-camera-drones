package de.lardtom.camdrones.crew.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.CrewDrones;
import de.lardtom.camdrones.crew.CrewTexts;
import de.lardtom.camdrones.crew.GimbalData;
import de.lardtom.camdrones.crew.model.SoloCamera;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.CrewPackets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.ServiceExtension;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class CrewClient {
    public static final Identifier SERVICE_ID = CamDrones.id("crew");
    private static boolean initialized;
    private static volatile int soloRequested = -1;

    private CrewClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        PropwashClientApi.registerServiceExtension(new Button());
    }

    public static boolean cameraView(Entity drone) {
        if (drone == null) {
            return false;
        }
        if (soloOffered(drone) && CamNetwork.sendToServer(CrewPackets.Request.of(CrewPackets.Action.SOLO, drone.getId()))) {
            soloRequested = drone.getId();
            return true;
        }
        return liveView(drone);
    }

    public static boolean soloOffered(Entity drone) {
        Minecraft minecraft = Minecraft.getInstance();
        if (drone == null || minecraft.player == null) {
            return false;
        }
        GimbalData data = CrewData.get(drone);
        Optional<DroneView> view = PropwashApi.drones().view(drone);
        return SoloCamera.offered(CrewDrones.hasGimbal(drone), PropwashClientApi.input().flying(), view.map(DroneView::armed).orElse(false),
                view.flatMap(DroneView::pilot).isPresent(), data.operated(), data.soloBy(minecraft.player.getUUID()));
    }

    public static void soloRefused(int droneId) {
        if (droneId < 0 || droneId != soloRequested) {
            return;
        }
        soloRequested = -1;
        Minecraft minecraft = Minecraft.getInstance();
        Entity drone = minecraft.level == null ? null : minecraft.level.getEntity(droneId);
        if (drone != null && !PropwashClientApi.input().operating()) {
            liveView(drone);
        }
    }

    public static boolean liveView(Entity drone) {
        Minecraft minecraft = Minecraft.getInstance();
        if (drone != null && PropwashClientApi.spectate(drone)) {
            return true;
        }
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.translatable(CrewTexts.SCREEN_VIEW_FAILED).withStyle(ChatFormatting.RED));
        }
        return false;
    }

    public static final class Button implements ServiceExtension {
        @Override
        public Identifier id() {
            return SERVICE_ID;
        }

        @Override
        public Component label() {
            return Component.translatable(CrewTexts.BUTTON);
        }

        @Override
        public boolean visible(Entity drone) {
            return CrewDrones.hasGimbal(drone);
        }

        @Override
        public void open(Entity drone, @Nullable Screen parent) {
            Minecraft.getInstance().gui.setScreen(new CrewScreen(drone, parent));
        }
    }
}
