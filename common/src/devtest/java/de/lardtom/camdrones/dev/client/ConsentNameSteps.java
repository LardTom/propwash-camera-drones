package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.autonomy.DroneSelector;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.net.FollowConsent;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.camdrones.planner.PlannerOwners;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ConsentNameSteps {
    public static final int NUMBER = 559;
    public static final String NAME = "follow: Zustimmungsfrage nennt die Drohne beim Kurznamen des Rahmens (EN und DE)";
    public static final String PHOTO = "follow_consent_question";
    public static final int DX = -60;
    public static final int DZ = -140;
    private static final String MARK = "consent.mark";
    private static final String REPLY = "consent.reply";
    private static final String SERIAL = "consent.serial";
    private static final String LINES = "consent.lines";
    private static final List<Component> CHAT = new CopyOnWriteArrayList<>();
    private static final int STEP_TICKS = 20 * 90;

    private ConsentNameSteps() {
    }

    public static void register() {
        SelfTest.clientStep(NUMBER, NAME, STEP_TICKS, Script.create()
                .run("Chat mitlesen", ConsentNameSteps::listen)
                .then("Anfragen zählen", (ctx, ticks) -> {
                    ctx.state().put(MARK, StatusProbe.consents());
                    return StepResult.pass();
                })
                .server("Regel „Spielern folgen“ an, " + JoinEnv.GUEST_NAME + " mit eigener Tour fragt den Spieler", ConsentNameSteps::ask)
                .then("Antwort an " + JoinEnv.GUEST_NAME, (ctx, ticks) -> {
                    Object reply = ctx.state().get(REPLY);
                    return reply == AutonomyStatus.FOLLOW_REQUESTED ? StepResult.pass()
                            : StepResult.fail("erwartet FOLLOW_REQUESTED, bekommen " + reply);
                })
                .then("Zustimmungsfrage auf Englisch", (ctx, ticks) -> question(ctx, ticks, List.of("wants to follow you")))
                .append(Shots.photos(PHOTO))
                .run("Sprache Deutsch", () -> Shots.language(Minecraft.getInstance(), Shots.GERMAN))
                .then("Zustimmungsfrage auf Deutsch", (ctx, ticks) -> question(ctx, ticks, List.of("möchte dir")))
                .run("Sprache zurück", () -> Shots.restoreLanguage(Minecraft.getInstance()))
                .then("Ablehnen", ConsentNameSteps::decline)
                .then("Meldung abgelehnt", (ctx, ticks) -> CamFlights.awaitStatus(ctx, ticks, AutonomyStatus.CONSENT_REFUSED, 100))
                .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", lines(ctx.state())))),
                Script.create().run("Chat nicht mehr mitlesen", ConsentNameSteps::stopListening)
                        .server(JoinEnv.GUEST_NAME + " abmelden", ConsentNameSteps::leave));
    }

    private static void ask(MinecraftServer server, Map<String, Object> state) {
        Arena.rule(server, CamWorldRules.ALLOW_FOLLOW_PLAYERS_ID, "true");
        ServerPlayer player = Arena.player(server);
        Entity drone = CovCamAKit.spawnPreset(server, state, CamPresets.TOUR, Arena.ground(DX, DZ), 0.0F);
        ServerPlayer guest = CovCamBKit.guest(server, Arena.level(server), drone.position().add(2.0, 0.0, 0.0));
        PlannerOwners.claim(drone, guest.getUUID());
        UUID serial = UUID.randomUUID();
        PropwashApi.droneData().set(drone, CamDroneData.serial(), serial);
        state.put(SERIAL, DroneSelector.shortSerial(serial));
        state.put(REPLY, AutonomyServer.follow(guest, drone, player, FollowController.Style.BEHIND).status());
    }

    private static void leave(MinecraftServer server, Map<String, Object> state) {
        AutonomyServer.answer(Arena.player(server), OptionalInt.empty(), false);
        CovCamBKit.leave(server);
    }

    private static StepResult question(SelfTest.StepContext ctx, int ticks, List<String> words) {
        Minecraft mc = Minecraft.getInstance();
        int mark = (int) ctx.state().getOrDefault(MARK, 0);
        List<FollowConsent.Request> requests = StatusProbe.consentRequests();
        Optional<Component> chat = CHAT.stream().filter(ConsentNameSteps::consentLine).reduce((a, b) -> b);
        if (requests.size() <= mark || !(mc.gui.screen() instanceof ConfirmScreen confirm) || chat.isEmpty()) {
            return ticks > 200 ? StepResult.fail("keine Zustimmungsfrage (Anfragen " + (requests.size() - mark) + ", Fenster "
                    + (mc.gui.screen() == null ? "keins" : mc.gui.screen().getClass().getSimpleName()) + ", Chat " + chat.isPresent() + ")")
                    : StepResult.pending();
        }
        FollowConsent.Request request = requests.get(requests.size() - 1);
        String name = NotReadyMessageSteps.expectedName(CamPresets.TOUR);
        String serial = (String) ctx.state().getOrDefault(SERIAL, "");
        String language = mc.getLanguageManager().getSelected();
        List<String> problems = new ArrayList<>();
        String window = confirm.getNarrationMessage().getString();
        String line = chat.get().getString();
        for (String[] text : List.of(new String[]{"Fenster", window}, new String[]{"Chat", line})) {
            if (!text[1].contains(name)) {
                problems.add(text[0] + " ohne „" + name + "“");
            }
            if (text[1].contains(".frame_short.") || text[1].contains("camdrones.")) {
                problems.add(text[0] + " zeigt den Sprachschlüssel");
            }
            if (!serial.isEmpty() && text[1].contains(serial)) {
                problems.add(text[0] + " nennt die ID " + serial);
            }
            if (words.stream().noneMatch(text[1]::contains)) {
                problems.add(text[0] + " nicht in der Sprache " + language);
            }
        }
        String note = language + ": Paket „" + request.drone() + "“, Fenster „" + window + "“, Chat „" + line + "“";
        lines(ctx.state()).add(note);
        return problems.isEmpty() ? StepResult.pass(note) : StepResult.fail(String.join(", ", problems) + " – " + note);
    }

    private static StepResult decline(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ConfirmScreen confirm)) {
            return StepResult.fail("Fenster der Zustimmungsfrage fehlt");
        }
        ctx.state().put(CamFlights.MARK, StatusProbe.mark());
        String no = Component.translatable(AutonomyServer.CONSENT_KEY + "no").getString();
        return SelfTestRunner.pressButton(confirm, no) ? StepResult.pass() : StepResult.fail("Knopf „" + no + "“ fehlt");
    }

    private static boolean consentLine(Component line) {
        return line.getContents() instanceof TranslatableContents contents && contents.getKey().equals(AutonomyServer.CONSENT_KEY + "chat");
    }

    @SuppressWarnings("unchecked")
    private static List<String> lines(Map<String, Object> state) {
        return (List<String>) state.computeIfAbsent(LINES, key -> new ArrayList<String>());
    }

    private static void listen() {
        CHAT.clear();
        Minecraft.getInstance().gui.hud.getChat().setVisibleMessageFilter(message -> {
            CHAT.add(message.content());
            return true;
        });
    }

    private static void stopListening() {
        Minecraft.getInstance().gui.hud.getChat().setVisibleMessageFilter(message -> true);
        CHAT.clear();
    }
}
