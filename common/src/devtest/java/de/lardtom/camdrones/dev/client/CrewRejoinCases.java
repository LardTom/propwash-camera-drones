package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.GimbalControl;
import de.lardtom.camdrones.client.gimbal.NoticeProbe;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.crew.CrewTexts;
import de.lardtom.camdrones.crew.model.CrewBook;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.net.CrewPackets;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CrewRejoinCases {
    public static final int REJOIN_DELAY = 20 * 5;
    public static final String LEAVE_COMMAND = "camdrones crew leave";
    private static final String NOTICE = "crewrejoin.notice";
    private static final int WAIT = 20 * 120;
    private static final int OPERATOR_TICKS = 40;
    private static final int EXPIRY_TICKS = 20 * 150;
    private static final String COUNTDOWN = "crewrejoin.countdown";
    private static final String BLOCKED_COUNTDOWN = "crewrejoin.blocked";
    private static final List<Component> CHAT = new CopyOnWriteArrayList<>();

    private CrewRejoinCases() {
    }

    public static Script script() {
        return Script.create()
                .run("Chat mitlesen", CrewRejoinCases::listen)
                .then("Einladung annehmen", (ctx, ticks) -> {
                    Optional<CrewPackets.Notice> invited = NoticeProbe.first(0, CrewPackets.Kind.INVITED);
                    if (invited.isEmpty()) {
                        return ticks > WAIT ? StepResult.fail("keine Einladung") : StepResult.pending();
                    }
                    ctx.state().put(NOTICE, NoticeProbe.mark());
                    JoinProbe.line("crew-rejoin a invited by " + invited.get().name());
                    Minecraft.getInstance().player.connection.sendUnattendedCommand(CrewService.ACCEPT_COMMAND.substring(1), null);
                    return StepResult.pass();
                })
                .then("Operator", (ctx, ticks) -> operating(ctx.state().get(NOTICE), ticks, "crew-rejoin a operating"))
                .sleep(OPERATOR_TICKS)
                .then("trennen und wieder beitreten", (ctx, ticks) -> {
                    ctx.state().put(NOTICE, NoticeProbe.mark());
                    JoinProbe.line("crew-rejoin a disconnect while operating");
                    JoinProbe.rejoin(REJOIN_DELAY);
                    return StepResult.pass();
                })
                .then("nach dem Wiederbeitritt wieder Operator", (ctx, ticks) -> operating(ctx.state().get(NOTICE), ticks,
                        "crew-rejoin a restored operating"))
                .then("Chat: Crew wiederhergestellt", (ctx, ticks) -> chat(CrewTexts.RESTORED_OPERATOR, ticks, 200, "crew-rejoin a restored notice"))
                .then("Kamera per Befehl abgeben", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (ticks == 0) {
                        ctx.state().put(NOTICE, NoticeProbe.mark());
                        mc.player.connection.sendUnattendedCommand(LEAVE_COMMAND, null);
                    }
                    boolean released = NoticeProbe.first((int) ctx.state().get(NOTICE), CrewPackets.Kind.RELEASED).isPresent();
                    if (released && !PropwashClientApi.input().operating()) {
                        JoinProbe.line("crew-rejoin a left");
                        return StepResult.pass();
                    }
                    return ticks > 400 ? StepResult.fail("Abgeben per /" + LEAVE_COMMAND + " klappt nicht (RELEASED " + released + ")")
                            : StepResult.pending();
                })
                .then("Chat: Operator zurück, Kamera nicht erreichbar", (ctx, ticks) -> {
                    StepResult result = chat(CrewTexts.BLOCKED_PILOT, ticks, WAIT, "crew-rejoin c blocked notice");
                    if (result.done() && !result.failed()) {
                        String text = last(CrewTexts.BLOCKED_PILOT).map(Component::getString).orElse("");
                        if (!text.contains(JoinEnv.GUEST_NAME)) {
                            return StepResult.fail("Hinweis nennt nicht " + JoinEnv.GUEST_NAME + ": „" + text + "“");
                        }
                    }
                    return result;
                })
                .then("Aktionsleiste zählt nach der Rückkehr weiter", (ctx, ticks) -> countdown(ctx, ticks, CrewTexts.BLOCKED_PILOT_OVERLAY,
                        BLOCKED_COUNTDOWN, "crew-rejoin c countdown "))
                .then("Chat: Crew nach 2:00 beendet, Operator war zurück", (ctx, ticks) -> {
                    Optional<Component> wrong = last(CrewTexts.EXPIRED_PILOT);
                    if (wrong.isPresent()) {
                        return StepResult.fail("falscher Text, obwohl " + JoinEnv.GUEST_NAME + " zurück ist: „" + wrong.get().getString() + "“");
                    }
                    return chat(CrewTexts.UNREACHABLE_PILOT, ticks, EXPIRY_TICKS, "crew-rejoin c unreachable notice");
                })
                .run("Chat für b leeren", CHAT::clear)
                .then("Chat: Operator getrennt, wartet 2:00", (ctx, ticks) -> {
                    StepResult result = chat(CrewTexts.WAITING_PILOT, ticks, WAIT, "crew-rejoin b waiting notice");
                    if (result.done() && !result.failed()) {
                        String text = last(CrewTexts.WAITING_PILOT).map(Component::getString).orElse("");
                        String clock = CrewTexts.clock(CrewBook.GRACE_MILLIS);
                        if (!text.contains(JoinEnv.GUEST_NAME) || !text.contains(clock)) {
                            return StepResult.fail("Hinweis nennt nicht " + JoinEnv.GUEST_NAME + " und " + clock + ": „" + text + "“");
                        }
                    }
                    return result;
                })
                .then("Aktionsleiste zählt herunter", (ctx, ticks) -> countdown(ctx, ticks, CrewTexts.WAITING_OVERLAY, COUNTDOWN,
                        "crew-rejoin b countdown "))
                .then("Chat: Crew nach 2:00 beendet", (ctx, ticks) -> chat(CrewTexts.EXPIRED_PILOT, ticks, EXPIRY_TICKS, "crew-rejoin b expired notice"))
                .run("Chat wieder normal", CrewRejoinCases::stopListening);
    }

    private static StepResult countdown(SelfTest.StepContext ctx, int ticks, String overlay, String state, String line) {
        Optional<String> key = CovCamAKit.overlayKey();
        String text = CovCamAKit.overlayText();
        @SuppressWarnings("unchecked")
        List<String> seen = (List<String>) ctx.state().computeIfAbsent(state, k -> new ArrayList<String>());
        if (key.filter(overlay::equals).isPresent() && !seen.contains(text)) {
            seen.add(text);
        }
        if (seen.size() >= 3) {
            JoinProbe.line(line + String.join(" | ", seen));
            return StepResult.pass();
        }
        return ticks > 20 * 20 ? StepResult.fail("Aktionsleiste zählt nicht herunter: " + seen + ", jetzt „" + text + "“") : StepResult.pending();
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
    }

    private static Optional<Component> last(String key) {
        return CHAT.stream().filter(c -> c.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key))
                .reduce((first, second) -> second);
    }

    private static StepResult chat(String key, int ticks, int limit, String line) {
        Optional<Component> found = last(key);
        if (found.isPresent()) {
            JoinProbe.line(line + ": " + found.get().getString());
            return StepResult.pass();
        }
        return ticks > limit ? StepResult.fail("keine Chat-Zeile " + key + " (" + CHAT.size() + " Zeilen)") : StepResult.pending();
    }

    private static StepResult operating(Object mark, int ticks, String line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ConfirmScreen confirm) {
            SelfTestRunner.pressButton(confirm, CommonComponents.GUI_YES.getString());
        }
        boolean bound = NoticeProbe.first((int) mark, CrewPackets.Kind.BOUND).isPresent();
        if (bound && PropwashClientApi.input().operating() && GimbalClient.control().role() == GimbalControl.Role.OPERATOR) {
            JoinProbe.line(line);
            return StepResult.pass();
        }
        return ticks > 600 ? StepResult.fail("kein Operator-Bild (BOUND " + bound + ", operating " + PropwashClientApi.input().operating()
                + ", Rolle " + GimbalClient.control().role() + ")") : StepResult.pending();
    }
}
