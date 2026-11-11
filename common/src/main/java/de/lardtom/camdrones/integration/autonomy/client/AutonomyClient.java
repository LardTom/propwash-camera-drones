package de.lardtom.camdrones.integration.autonomy.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.planner.PlannerClient;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.FollowConsent;
import de.lardtom.camdrones.net.StatusMessage;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;

public final class AutonomyClient {
    public static final SoundEvent START_SOUND = SoundEvent.createVariableRangeEvent(CamDrones.id("mission.start"));
    public static final SoundEvent DONE_SOUND = SoundEvent.createVariableRangeEvent(CamDrones.id("mission.done"));
    public static final SoundEvent REQUEST_SOUND = SoundEvent.createVariableRangeEvent(CamDrones.id("crew.request"));
    public static final float VOLUME = 0.6F;

    private static boolean initialized;

    private AutonomyClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CamNetwork.onClient(StatusMessage.TYPE, AutonomyClient::onStatus);
        CamNetwork.onClient(FollowConsent.REQUEST, AutonomyClient::onConsentRequest);
        PlannerClient.commands(new AutonomyCommands());
        AutonomyPilotContext.install();
        CamDrones.LOG.info("{}: Flüge ohne Pilot am Client bereit (Meldungen, Zustimmung, Start, Pause und Stopp im Flugplaner, Fortschritt)",
                CamDrones.MOD_NAME);
    }

    static void onStatus(StatusMessage.Payload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        Component text = AutonomyServer.text(payload.status(), payload.drone(), payload.detail());
        if (AutonomyStatus.overlay(payload.status(), payload.detail())) {
            player.sendOverlayMessage(text);
        }
        if (payload.status().chat()) {
            player.sendSystemMessage(text);
        }
        switch (payload.status().tone()) {
            case START -> play(minecraft, START_SOUND);
            case DONE -> play(minecraft, DONE_SOUND);
            default -> {
            }
        }
    }

    static void play(Minecraft minecraft, SoundEvent sound) {
        if (minecraft.getSoundManager().getSoundEvent(sound.location()) != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, VOLUME));
        }
    }

    static void onConsentRequest(FollowConsent.Request request) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        play(minecraft, REQUEST_SOUND);
        if (minecraft.gui.screen() != null || PropwashClientApi.input().flying()) {
            return;
        }
        Component title = Component.translatable(AutonomyServer.CONSENT_KEY + "title");
        Component question = AutonomyServer.consentQuestion(request);
        minecraft.gui.setScreen(new ConfirmScreen(accept -> {
            CamNetwork.sendToServer(new FollowConsent.Reply(request.requestId(), accept));
            minecraft.gui.setScreen(null);
        }, title, question, Component.translatable(AutonomyServer.CONSENT_KEY + "yes"), Component.translatable(AutonomyServer.CONSENT_KEY + "no")));
    }
}
