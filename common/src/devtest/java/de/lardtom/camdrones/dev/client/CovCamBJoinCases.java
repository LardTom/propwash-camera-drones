package de.lardtom.camdrones.dev.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import de.lardtom.camdrones.client.gimbal.NoticeProbe;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.autonomy.client.StatusProbe;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.CrewPackets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.worldrule.WorldRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class CovCamBJoinCases {
    public static final String CASE = "covcamb";
    public static final String CASE_FAR = "covcamb-weit";
    public static final List<String> CAMERA_WORDS = List.of("mission", "crew", "follow", "camdrones", "camera", "gimbal");
    public static final double ROLL_MIN = 10.0;
    public static final double HORIZON_LIMIT = 2.0;
    private static final String DRONE = "covcamb.join.drone";
    private static final String NOTICE = "covcamb.join.notice";
    private static final String STATUS = "covcamb.join.status";
    private static final String STATS = "covcamb.join.stats";
    private static final String SHOT = "covcamb.join.shot";
    private static final int WAIT = 20 * 180;

    private CovCamBJoinCases() {
    }

    public static Script farScript() {
        return Script.create()
                .then("Besitzer bleibt am Start", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    int level = mc.player == null ? -1 : mc.player.experienceLevel;
                    if (level >= 9) {
                        JoinProbe.line("far done");
                        return StepResult.pass();
                    }
                    return ticks > 20 * 300 ? StepResult.fail("Server meldet das Ende des weiten Flugs nicht") : StepResult.pending();
                });
    }

    public static Script script() {
        return Script.create()
                .then("Hotbar auf 0", (ctx, ticks) -> {
                    ack(0);
                    return StepResult.pass();
                })
                .then("Signal Befehle", level(1))
                .then("eigene Drohne mit Mission", (ctx, ticks) -> {
                    Optional<Entity> drone = ownDrone();
                    if (drone.isEmpty()) {
                        return ticks > WAIT ? StepResult.fail("keine eigene Kamera-Drohne") : StepResult.pending();
                    }
                    ctx.state().put(DRONE, drone.get().getId());
                    return StepResult.pass();
                })
                .then("Vorschläge für /camdrones mission", CovCamBJoinCases::suggestions)
                .then("/propwash ohne Kamera-Befehle", CovCamBJoinCases::propwashTree)
                .then("/camdrones mission start", (ctx, ticks) -> command(ctx, ticks, "camdrones mission start", NavLogic.MissionState.RUNNING,
                        List.of(AutonomyStatus.MISSION_STARTED)))
                .sleep(40)
                .then("/camdrones mission pause", (ctx, ticks) -> command(ctx, ticks, "camdrones mission pause", NavLogic.MissionState.PAUSED,
                        List.of(AutonomyStatus.MISSION_PAUSED)))
                .sleep(20)
                .then("/camdrones mission stop", (ctx, ticks) -> command(ctx, ticks, "camdrones mission stop", null,
                        List.of(AutonomyStatus.MISSION_STOPPED, AutonomyStatus.ENDED_STOPPED)))
                .then("Befehle fertig", (ctx, ticks) -> {
                    ack(1);
                    return StepResult.pass();
                })
                .then("Signal Regeln", level(2))
                .then("Tab Welt-Regeln ohne OP", (ctx, ticks) -> rulesTab(ctx, ticks, false))
                .then("Regel ohne OP ändern", (ctx, ticks) -> {
                    if (ticks == 0) {
                        Minecraft.getInstance().player.connection.sendCommand("gamerule " + CamWorldRules.MAX_ALTITUDE_ID + " 50");
                    }
                    if (ticks < 40) {
                        return StepResult.pending();
                    }
                    int value = CamWorldRules.maxAltitude(Minecraft.getInstance().level);
                    JoinProbe.line("rules not_op command sent value=" + value);
                    ack(2);
                    return StepResult.pass();
                })
                .then("Signal OP", level(3))
                .sleep(20)
                .then("Tab Welt-Regeln mit OP", (ctx, ticks) -> rulesTab(ctx, ticks, true))
                .then("Regel mit OP ändern", (ctx, ticks) -> {
                    if (ticks == 0) {
                        Minecraft.getInstance().player.connection.sendCommand("gamerule " + CamWorldRules.MAX_ALTITUDE_ID + " 50");
                    }
                    int value = CamWorldRules.maxAltitude(Minecraft.getInstance().level);
                    if (value == 50) {
                        JoinProbe.line("rules op change arrived value=" + value);
                        ack(3);
                        return StepResult.pass();
                    }
                    if (ticks > 200) {
                        ack(3);
                        return StepResult.fail("Änderung mit OP kommt am Client nicht an (Wert " + value + ")");
                    }
                    return StepResult.pending();
                })
                .then("Signal Zuschauen", level(4))
                .then("fliegende Drohne per F7 ansehen", CovCamBJoinCases::spectate)
                .then("Horizont im Bild", CovCamBJoinCases::horizon)
                .then("Zuschauen fertig", (ctx, ticks) -> {
                    ack(4);
                    return StepResult.pass();
                })
                .then("Signal Crew", level(5))
                .then("Einladung annehmen", (ctx, ticks) -> acceptInvite(ctx, ticks, "a"))
                .then("Operator", (ctx, ticks) -> bound(ctx, ticks, "a"))
                .then("Regel aus: Bindung endet mit crew_notice", (ctx, ticks) -> {
                    Optional<CrewPackets.Notice> released = NoticeProbe.first((int) ctx.state().get(NOTICE), CrewPackets.Kind.RELEASED);
                    if (released.isPresent() && !PropwashClientApi.input().operating()) {
                        JoinProbe.line("crew rule_off notice released");
                        ack(5);
                        return StepResult.pass();
                    }
                    return ticks > WAIT ? StepResult.fail("keine crew_notice RELEASED nach Regel aus") : StepResult.pending();
                })
                .then("Signal Crew erneut", level(6))
                .then("Einladung erneut annehmen", (ctx, ticks) -> acceptInvite(ctx, ticks, "b"))
                .then("wieder Operator", (ctx, ticks) -> bound(ctx, ticks, "b"))
                .sleep(40)
                .then("Spiel verlassen", (ctx, ticks) -> {
                    JoinProbe.line("crew leave while bound");
                    return StepResult.pass();
                });
    }

    private static void ack(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static Script.Stage level(int wanted) {
        return (ctx, ticks) -> {
            Minecraft mc = Minecraft.getInstance();
            int level = mc.player == null ? -1 : mc.player.experienceLevel;
            if (level >= wanted) {
                return StepResult.pass();
            }
            return ticks > WAIT * 2 ? StepResult.fail("Signal " + wanted + " vom Server kommt nicht (Stufe " + level + ")") : StepResult.pending();
        };
    }

    private static Optional<Entity> ownDrone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return Optional.empty();
        }
        return Arena.clientDrones(mc).stream()
                .filter(d -> !CamDroneData.NO_SERIAL.equals(PropwashApi.droneData().get(d, CamDroneData.serial())))
                .min(Comparator.comparingDouble(d -> d.distanceTo(mc.player)));
    }

    private static Optional<Entity> drone(SelfTest.StepContext ctx) {
        Object id = ctx.state().get(DRONE);
        Minecraft mc = Minecraft.getInstance();
        return id instanceof Integer number && mc.level != null ? Optional.ofNullable(mc.level.getEntity(number)) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static StepResult suggestions(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        CommandDispatcher<ClientSuggestionProvider> dispatcher = mc.getConnection().getCommands();
        if (!ctx.state().containsKey(STATS)) {
            String input = "camdrones mission ";
            ParseResults<ClientSuggestionProvider> parse = dispatcher.parse(input, mc.getConnection().getSuggestionsProvider());
            ctx.state().put(STATS, dispatcher.getCompletionSuggestions(parse));
            return StepResult.pending();
        }
        CompletableFuture<Suggestions> future = (CompletableFuture<Suggestions>) ctx.state().get(STATS);
        if (!future.isDone()) {
            return ticks > 200 ? StepResult.fail("keine Vorschläge") : StepResult.pending();
        }
        ctx.state().remove(STATS);
        List<String> words = future.join().getList().stream().map(Suggestion::getText).toList();
        if (!words.containsAll(List.of("start", "pause", "stop"))) {
            return StepResult.fail("Vorschläge für /camdrones mission: " + words);
        }
        JoinProbe.line("cmd suggestions " + String.join(" ", words));
        return StepResult.pass();
    }

    private static StepResult propwashTree(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        CommandNode<ClientSuggestionProvider> propwash = mc.getConnection().getCommands().getRoot().getChild("propwash");
        List<String> camera = new ArrayList<>();
        List<String> all = new ArrayList<>();
        if (propwash != null) {
            for (CommandNode<ClientSuggestionProvider> child : propwash.getChildren()) {
                all.add(child.getName());
                if (CAMERA_WORDS.stream().anyMatch(w -> child.getName().toLowerCase(Locale.ROOT).contains(w))) {
                    camera.add(child.getName());
                }
            }
        }
        if (!camera.isEmpty()) {
            return StepResult.fail("/propwash mit Kamera-Unterbefehlen " + camera);
        }
        JoinProbe.line("cmd propwash clean " + all.size() + " " + String.join(",", all));
        return StepResult.pass();
    }

    private static StepResult command(SelfTest.StepContext ctx, int ticks, String command, NavLogic.MissionState wanted, List<AutonomyStatus> statuses) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            ctx.state().put(STATUS, StatusProbe.mark());
            mc.player.connection.sendCommand(command);
            return StepResult.pending();
        }
        Optional<Entity> drone = drone(ctx);
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        CamFlights.lookAt(drone.get());
        MissionStatus state = PropwashApi.droneData().get(drone.get(), CamDroneData.missionState());
        NavLogic.MissionState now = state == null ? NavLogic.MissionState.IDLE : state.state();
        Optional<AutonomyStatus> status = statuses.stream().filter(st -> StatusProbe.seen((int) ctx.state().get(STATUS), st)).findFirst();
        boolean seen = status.isPresent();
        boolean reached = wanted == null ? now != NavLogic.MissionState.RUNNING && now != NavLogic.MissionState.PAUSED : now == wanted;
        if (reached && (seen || ticks >= 60)) {
            JoinProbe.line("cmd " + command.substring(command.lastIndexOf(' ') + 1) + " " + now.name().toLowerCase(Locale.ROOT) + " status "
                    + status.map(st -> st.name().toLowerCase(Locale.ROOT)).orElse("chat"));
            return StepResult.pass();
        }
        return ticks > 400 ? StepResult.fail("/" + command + ": mission_state " + now + ", Meldungen " + StatusProbe.describe((int) ctx.state().get(STATUS)))
                : StepResult.pending();
    }

    private static StepResult rulesTab(SelfTest.StepContext ctx, int ticks, boolean op) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            mc.gui.setScreen(null);
            if (!PropwashClientApi.openConfigTab(RulesSteps.TAB, null)) {
                return StepResult.fail("Tab Welt-Regeln öffnet nicht");
            }
            return StepResult.pending();
        }
        Screen screen = mc.gui.screen();
        if (screen == null || ticks < 20) {
            return ticks > 100 ? StepResult.fail("Tab Welt-Regeln öffnet nicht") : StepResult.pending();
        }
        if (!ctx.state().containsKey(STATS)) {
            String readOnly = I18n.get("propwash.server.world_rules.read_only");
            LayoutCheck.Frame top = LayoutCheck.capture(mc, screen);
            ctx.state().put(STATS, top.shows(readOnly.substring(0, Math.min(18, readOnly.length()))));
            RulesSteps.scrollToSection();
            ctx.state().put(SHOT + ".at", ticks);
            return StepResult.pending();
        }
        if (ticks - (int) ctx.state().get(SHOT + ".at") < 10) {
            return StepResult.pending();
        }
        boolean locked = (boolean) ctx.state().get(STATS);
        LayoutCheck.Frame frame = LayoutCheck.capture(mc, screen);
        int shown = 0;
        for (WorldRule<?> rule : CamWorldRules.registered()) {
            if (frame.shows(I18n.get(rule.nameKey()))) {
                shown++;
            }
        }
        if (!ctx.state().containsKey(SHOT)) {
            ctx.state().put(SHOT, Shots.capture(JoinProbe.dir().resolve("covcamb_world_rules_" + (op ? "op" : "not_op") + ".png")));
            return StepResult.pending();
        }
        if (ctx.state().get(SHOT) instanceof CompletableFuture<?> shot && !shot.isDone()) {
            return StepResult.pending();
        }
        ctx.state().remove(SHOT);
        ctx.state().remove(STATS);
        ctx.state().remove(SHOT + ".at");
        mc.gui.setScreen(null);
        JoinProbe.line("rules " + (op ? "op" : "not_op") + " locked=" + locked + " rules=" + shown);
        if (locked == op) {
            return StepResult.fail("Tab Welt-Regeln " + (op ? "mit" : "ohne") + " OP " + (locked ? "gesperrt" : "nicht gesperrt"));
        }
        if (shown < CamWorldRules.registered().size()) {
            return StepResult.fail("nur " + shown + " von " + CamWorldRules.registered().size() + " Kamera-Regeln im Tab sichtbar");
        }
        return StepResult.pass();
    }

    private static Optional<Entity> flyingDrone() {
        Minecraft mc = Minecraft.getInstance();
        return Arena.clientDrones(mc).stream().filter(d -> d.getY() - mc.player.getY() > 3.0).max(Comparator.comparingDouble(Entity::getY));
    }

    private static StepResult spectate(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = flyingDrone();
        if (drone.isEmpty()) {
            return ticks > WAIT ? StepResult.fail("keine fliegende Drohne zum Zuschauen") : StepResult.pending();
        }
        if (ticks < 60) {
            return StepResult.pending();
        }
        if (ticks % 40 == 0) {
            boolean ok = PropwashClientApi.spectate(drone.get());
            JoinProbe.line("spectate request " + ok);
        }
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        if (camera == drone.get() || camera != null && camera != mc.player && camera.distanceTo(drone.get()) < 2.0F) {
            ctx.state().put(DRONE, drone.get().getId());
            JoinProbe.line("spectate on " + camera.getType().getDescription().getString());
            return StepResult.pass();
        }
        double distance = mc.gameRenderer.mainCamera().position().distanceTo(drone.get().position());
        if (distance < 2.0) {
            ctx.state().put(DRONE, drone.get().getId());
            JoinProbe.line("spectate camera at drone");
            return StepResult.pass();
        }
        return ticks > WAIT ? StepResult.fail("Zuschauen per F7 startet nicht (Kamera " + String.format(Locale.ROOT, "%.1f", distance) + " m entfernt)")
                : StepResult.pending();
    }

    private static StepResult horizon(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = drone(ctx);
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne beim Zuschauen verloren");
        }
        double frame = GimbalSteps.frameTilt(drone.get());
        Vector3fc left = Minecraft.getInstance().gameRenderer.mainCamera().leftVector();
        double horizon = Math.toDegrees(Math.asin(Math.clamp(Math.abs(left.y()), 0.0, 1.0)));
        double[] s = (double[]) ctx.state().computeIfAbsent(STATS, key -> new double[]{0.0, 0.0, 0.0});
        s[0] = Math.max(s[0], frame);
        if (frame >= ROLL_MIN) {
            s[1] = Math.max(s[1], horizon);
            s[2] += 1.0;
        }
        if (ticks % 20 == 0) {
            JoinProbe.line(String.format(Locale.ROOT, "spectate t=%d frame=%.1f horizon=%.2f", ticks, frame, horizon));
        }
        if (ticks == 60 && !ctx.state().containsKey(SHOT)) {
            ctx.state().put(SHOT, Shots.capture(JoinProbe.dir().resolve("covcamb_spectate_horizon.png")));
        }
        if (ticks < 20 * 12) {
            return StepResult.pending();
        }
        ctx.state().remove(STATS);
        String line = String.format(Locale.ROOT, "spectate roll max %.1f horizon max %.2f samples %d", s[0], s[1], (int) s[2]);
        JoinProbe.line(line);
        Minecraft.getInstance().setCameraEntity(Minecraft.getInstance().player);
        if (s[2] < 10) {
            return StepResult.fail("Rahmen rollt beim Zuschauen höchstens " + String.format(Locale.ROOT, "%.1f", s[0]) + "° (mindestens " + ROLL_MIN + "°)");
        }
        if (s[1] >= HORIZON_LIMIT) {
            return StepResult.fail("Horizont im Zuschauer-Bild um " + String.format(Locale.ROOT, "%.2f", s[1]) + "° geneigt");
        }
        return StepResult.pass();
    }

    private static StepResult acceptInvite(SelfTest.StepContext ctx, int ticks, String round) {
        Object mark = ctx.state().computeIfAbsent(NOTICE + round, key -> 0);
        Optional<CrewPackets.Notice> invited = NoticeProbe.first((int) mark, CrewPackets.Kind.INVITED);
        if (invited.isEmpty()) {
            return ticks > WAIT ? StepResult.fail("keine Einladung (" + round + ")") : StepResult.pending();
        }
        ctx.state().put(NOTICE, NoticeProbe.mark());
        ctx.state().put(NOTICE + "b", NoticeProbe.mark());
        Minecraft.getInstance().player.connection.sendCommand(CrewService.ACCEPT_COMMAND.substring(1));
        JoinProbe.line("crew " + round + " invited and accepted");
        return StepResult.pass();
    }

    private static StepResult bound(SelfTest.StepContext ctx, int ticks, String round) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof ConfirmScreen confirm) {
            SelfTestRunner.pressButton(confirm, CommonComponents.GUI_YES.getString());
        }
        Optional<CrewPackets.Notice> bound = NoticeProbe.first((int) ctx.state().get(NOTICE), CrewPackets.Kind.BOUND);
        if (bound.isPresent() && PropwashClientApi.input().operating()) {
            JoinProbe.line("crew " + round + " operating");
            ctx.state().put(NOTICE, NoticeProbe.mark());
            return StepResult.pass();
        }
        return ticks > 400 ? StepResult.fail("kein Operator-Bild (" + round + ", gebunden " + bound.isPresent() + ")") : StepResult.pending();
    }
}
