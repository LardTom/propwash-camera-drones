package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.GimbalControl;
import de.lardtom.camdrones.client.gimbal.NoticeProbe;
import de.lardtom.camdrones.client.hud.CamOsd;
import de.lardtom.camdrones.client.hud.CamOsdSource;
import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.crew.CrewTexts;
import de.lardtom.camdrones.crew.client.CrewClient;
import de.lardtom.camdrones.crew.client.CrewScreen;
import de.lardtom.camdrones.crew.GimbalData;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.dev.AwardProbe;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.CrewPackets;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.net.SmartMode;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.KeyMapping;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class JoinCases {
    private static final String DRONES = "join.drones";
    private static final String DRONE = "join.drone";
    private static final String START_Y = "join.startY";
    private static final String NOTICE = "join.notice";
    private static final String SEEN = "join.seen";
    private static final String MIN_TILT = "join.minTilt";
    private static final int WAIT = 20 * 120;
    private static final int KEYCODE_ESCAPE = 27;
    private static final int OPERATOR_TICKS = 20 * 8;

    private JoinCases() {
    }

    public static Script script(String name) {
        return switch (name) {
            case JoinEnv.MISSION -> mission();
            case JoinEnv.CREW -> crew();
            case JoinEnv.FOLLOW -> follow();
            case CovCamAJoinCases.CASE -> CovCamAJoinCases.script();
            case CovCamBJoinCases.CASE -> CovCamBJoinCases.script();
            case JoinEnv.CREW_REJOIN -> CrewRejoinCases.script();
            case AwardProbe.CASE -> AwardSteps.joinScript();
            default -> session();
        };
    }

    private static Script session() {
        return Script.create().then("Sitzung", (ctx, ticks) -> {
            if (ticks < 40) {
                return StepResult.pending();
            }
            long items = BuiltInRegistries.ITEM.keySet().stream().filter(id -> CamDrones.MOD_ID.equals(id.getNamespace())).count();
            long rules = CamWorldRules.IDS.stream().filter(id -> PropwashApi.worldRules().find(id).isPresent()).count();
            boolean channel = CamNetwork.canSendToServer(MissionCommand.TYPE) && CamNetwork.canSendToServer(SmartMode.TYPE);
            JoinProbe.line("session items=" + items + " rules=" + rules + " channel=" + (channel ? "ok" : "missing") + " addon="
                    + (PropwashClientApi.isAddonActive(CamDrones.MOD_ID) ? "active" : "inactive"));
            return StepResult.pass();
        });
    }

    private static List<Entity> pairedDrones() {
        Minecraft mc = Minecraft.getInstance();
        List<Entity> out = new ArrayList<>();
        if (mc.level == null || mc.player == null) {
            return out;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!PropwashApi.drones().isDrone(entity) || entity.distanceTo(mc.player) > 48.0F) {
                continue;
            }
            UUID serial = PropwashApi.droneData().get(entity, CamDroneData.serial());
            if (serial != null && !CamDroneData.NO_SERIAL.equals(serial)) {
                out.add(entity);
            }
        }
        out.sort(Comparator.comparingDouble(Entity::getX));
        return out;
    }

    private static Script.Stage awaitDrones(int count) {
        return (ctx, ticks) -> {
            List<Entity> drones = pairedDrones();
            if (drones.size() >= count) {
                ctx.state().put(DRONES, drones);
                return StepResult.pass();
            }
            return ticks > WAIT ? StepResult.fail("nur " + drones.size() + " gekoppelte Kamera-Drohnen in der Nähe") : StepResult.pending();
        };
    }

    @SuppressWarnings("unchecked")
    private static Entity drone(SelfTest.StepContext ctx, int index) {
        return ((List<Entity>) ctx.state().get(DRONES)).get(index);
    }

    private static UUID serial(Entity drone) {
        return PropwashApi.droneData().get(drone, CamDroneData.serial());
    }

    private static Script.Stage status(AutonomyStatus wanted, String line, int maxTicks) {
        return (ctx, ticks) -> {
            StepResult result = CamFlights.awaitStatus(ctx, ticks, wanted, maxTicks);
            if (result.done() && !result.failed()) {
                JoinProbe.line(line);
            }
            return result;
        };
    }

    private static Script mission() {
        return Script.create()
                .then("zwei Kamera-Drohnen", awaitDrones(2))
                .then("erste Mission starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(serial(drone(ctx, 0)),
                        MissionCommand.Command.START, 0)))
                .then("erste Mission läuft", status(AutonomyStatus.MISSION_STARTED, "mission started", 200))
                .sleep(60)
                .then("zweite Mission starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(serial(drone(ctx, 1)),
                        MissionCommand.Command.START, 0)))
                .then("kein Budget", status(AutonomyStatus.NO_BUDGET, "mission no_budget", 200))
                .then("Blick auf die Drohnen", (ctx, ticks) -> {
                    CamFlights.lookAt(drone(ctx, 0));
                    return ticks >= 40 ? StepResult.pass() : StepResult.pending();
                });
    }

    private static Optional<Entity> clientDrone(SelfTest.StepContext ctx) {
        Object id = ctx.state().get(DRONE);
        Minecraft mc = Minecraft.getInstance();
        return id instanceof Integer number && mc.level != null ? Optional.ofNullable(mc.level.getEntity(number)) : Optional.empty();
    }

    private static Script crew() {
        return Script.create()
                .then("Einladung als Operator", (ctx, ticks) -> {
                    Optional<CrewPackets.Notice> invited = NoticeProbe.first(0, CrewPackets.Kind.INVITED);
                    if (invited.isEmpty()) {
                        return ticks > WAIT ? StepResult.fail("keine Einladung") : StepResult.pending();
                    }
                    ctx.state().put(DRONE, invited.get().drone());
                    ctx.state().put(NOTICE, NoticeProbe.mark());
                    JoinProbe.line("crew a invited by " + invited.get().name());
                    Minecraft.getInstance().player.connection.sendUnattendedCommand(CrewService.ACCEPT_COMMAND.substring(1), null);
                    JoinProbe.line("crew a accept via chat link " + CrewService.ACCEPT_COMMAND);
                    return StepResult.pass();
                })
                .then("Operator", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.gui.screen() instanceof ConfirmScreen confirm) {
                        SelfTestRunner.pressButton(confirm, CommonComponents.GUI_YES.getString());
                    }
                    boolean bound = NoticeProbe.first((int) ctx.state().get(NOTICE), CrewPackets.Kind.BOUND).isPresent();
                    if (bound && PropwashClientApi.input().operating() && GimbalClient.control().role() == GimbalControl.Role.OPERATOR) {
                        JoinProbe.line("crew a operating");
                        return StepResult.pass();
                    }
                    return ticks > 400 ? StepResult.fail("kein Operator-Bild (gebunden " + bound + ", operating " + PropwashClientApi.input().operating()
                            + ", Rolle " + GimbalClient.control().role() + ")") : StepResult.pending();
                })
                .then("Neigung als Operator schicken", (ctx, ticks) -> {
                    Optional<Entity> drone = clientDrone(ctx);
                    if (drone.isEmpty()) {
                        return StepResult.fail("Drohne fehlt am Client");
                    }
                    if (ticks % 2 == 0 && ticks < 60) {
                        CamNetwork.sendToServer(new CrewPackets.Gimbal(drone.get().getId(), new GimbalSetting(GimbalSetting.Mode.FOLLOW, -40.0, 0.0, 0.0,
                                0.0)));
                    }
                    GimbalData data = CrewData.get(drone.get());
                    if (data.setting().tilt() <= -39.0) {
                        ctx.state().put(SEEN, true);
                    }
                    if (ticks < 60) {
                        return StepResult.pending();
                    }
                    if (!(boolean) ctx.state().getOrDefault(SEEN, false)) {
                        return StepResult.fail("Server übernimmt die Neigung des Operators nicht (" + data.setting().tilt() + "°)");
                    }
                    JoinProbe.line("crew a tilt accepted operator=" + data.operator().map(id -> id.equals(Minecraft.getInstance().player.getUUID())
                            ? "self" : "other").orElse("none"));
                    return StepResult.pass();
                })
                .sleep(OPERATOR_TICKS)
                .then("Kamera mit Esc abgeben", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (ticks == 0) {
                        ctx.state().put(NOTICE, NoticeProbe.mark());
                        KeyEvent escape = new KeyEvent(InputConstants.KEY_ESCAPE, KEYCODE_ESCAPE, 0);
                        mc.keyboardHandler.keyPress(mc.getWindow().handle(), InputConstants.PRESS, escape);
                        mc.keyboardHandler.keyPress(mc.getWindow().handle(), InputConstants.RELEASE, escape);
                    }
                    if (mc.gui.screen() != null) {
                        return StepResult.fail("Esc öffnet " + SelfTestRunner.screenName(mc) + " statt die Kamera abzugeben");
                    }
                    boolean released = NoticeProbe.first((int) ctx.state().get(NOTICE), CrewPackets.Kind.RELEASED).isPresent();
                    if (released && !PropwashClientApi.input().operating()) {
                        JoinProbe.line("crew a released by escape");
                        return StepResult.pass();
                    }
                    return ticks > 400 ? StepResult.fail("Abgeben mit Esc klappt nicht (RELEASED " + released + ", operating "
                            + PropwashClientApi.input().operating() + ")") : StepResult.pending();
                })
                .then("eigene Drohne am Boden", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    Optional<Entity> own = pairedDrones().stream().filter(d -> d.distanceTo(mc.player) < 4.0F).findFirst();
                    if (own.isEmpty() || mc.player.getInventory().getItem(1).isEmpty()) {
                        return ticks > WAIT ? StepResult.fail("keine eigene Drohne neben dem Spieler") : StepResult.pending();
                    }
                    ctx.state().put(DRONE, own.get().getId());
                    return ticks >= 20 ? StepResult.pass() : StepResult.pending();
                })
                .then("Wartung öffnen (Schleichen + Rechtsklick)", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    Optional<Entity> drone = clientDrone(ctx);
                    if (drone.isEmpty()) {
                        return StepResult.fail("Drohne fehlt am Client");
                    }
                    if (crewButton(mc).isPresent()) {
                        Pilot.hold(mc.options.keyShift, false);
                        JoinProbe.line("crew b service screen " + mc.gui.screen().getClass().getSimpleName());
                        return StepResult.pass();
                    }
                    if (mc.gui.screen() == null) {
                        mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, drone.get().getBoundingBox().getCenter());
                        Pilot.hold(mc.options.keyShift, true);
                        boolean aimed = mc.hitResult instanceof EntityHitResult hit && hit.getEntity() == drone.get();
                        if (ticks % 40 == 20 && aimed && mc.player.isShiftKeyDown()) {
                            JoinProbe.line("crew b sneak and right click on own drone");
                            KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
                        }
                    }
                    return ticks > 400 ? StepResult.fail("Wartung mit Knopf „" + I18n.get(CrewTexts.BUTTON) + "“ öffnet nicht (Fenster "
                            + SelfTestRunner.screenName(mc) + ")") : StepResult.pending();
                })
                .then("Knopf „Crew“", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.gui.screen() instanceof CrewScreen) {
                        if (ticks < 10) {
                            return StepResult.pending();
                        }
                        JoinProbe.line("crew b crew screen open");
                        return StepResult.pass();
                    }
                    if (ticks % 20 == 0) {
                        crewButton(mc).ifPresent(button -> clickLabel(mc.gui.screen(), button));
                    }
                    return ticks > 200 ? StepResult.fail("Knopf „Crew“ öffnet das Crew-Fenster nicht (" + SelfTestRunner.screenName(mc) + ")")
                            : StepResult.pending();
                })
                .then("Einladen im Crew-Fenster", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    Optional<Entity> drone = clientDrone(ctx);
                    if (drone.isEmpty()) {
                        return StepResult.fail("Drohne fehlt am Client");
                    }
                    if (CrewData.get(drone.get()).operated()) {
                        JoinProbe.line("crew b invited via service button, operator bound");
                        return StepResult.pass();
                    }
                    if (ticks % 40 == 0 && mc.gui.screen() instanceof CrewScreen screen) {
                        clickLabel(screen, I18n.get(CrewTexts.SCREEN_INVITE));
                    }
                    return ticks > WAIT ? StepResult.fail("Einladen im Crew-Fenster bindet keinen Operator (" + SelfTestRunner.screenName(mc) + ")")
                            : StepResult.pending();
                })
                .then("Fenster schließen", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.gui.screen() != null) {
                        if (ticks % 5 == 0) {
                            mc.gui.screen().onClose();
                        }
                        return ticks > 200 ? StepResult.fail("Fenster bleibt offen: " + SelfTestRunner.screenName(mc)) : StepResult.pending();
                    }
                    return StepResult.pass();
                })
                .then("selbst fliegen", (ctx, ticks) -> {
                    if (PropwashClientApi.input().flying() && PropwashClientApi.input().drone().isPresent()) {
                        ctx.state().put(START_Y, PropwashClientApi.input().drone().get().getY());
                        JoinProbe.line("crew b flying");
                        return StepResult.pass();
                    }
                    return ticks > WAIT ? StepResult.fail("FPV startet nicht") : StepResult.pending();
                })
                .sleep(20)
                .then("GPS-Fix abwarten", (ctx, ticks) -> {
                    boolean fix = Pilot.status().map(status -> !status.atti()).orElse(false);
                    if (fix && ticks >= 10) {
                        return StepResult.pass();
                    }
                    return ticks > 300 ? StepResult.pass("ohne GPS-Halten weiter: " + Flight.describe()) : StepResult.pending();
                })
                .then("scharf schalten", Pilot::arm)
                .then("steigen", (ctx, ticks) -> {
                    Optional<Entity> drone = PropwashClientApi.input().drone();
                    if (drone.isEmpty()) {
                        return StepResult.fail("Drohne verloren");
                    }
                    if (drone.get().getY() - (double) ctx.state().get(START_Y) >= 3.5) {
                        return StepResult.pass();
                    }
                    Pilot.steer(Flight.CLIMB_THROTTLE);
                    return ticks > 400 ? StepResult.fail("steigt nicht") : StepResult.pending();
                })
                .then("Gas Mitte", (ctx, ticks) -> Pilot.throttleTo(ctx, ticks, Pilot.THROTTLE_CENTER))
                .then("Gimbal-Lage vom Operator", (ctx, ticks) -> {
                    Optional<Entity> drone = PropwashClientApi.input().drone();
                    if (drone.isEmpty()) {
                        return StepResult.fail("Drohne verloren");
                    }
                    Pilot.steer(Pilot.THROTTLE_CENTER);
                    GimbalData data = CrewData.get(drone.get());
                    double tilt = GimbalClient.tilt(drone.get());
                    Vector3fc forward = Minecraft.getInstance().gameRenderer.mainCamera().forwardVector();
                    double view = Math.toDegrees(Math.asin(Math.clamp(-forward.y(), -1.0, 1.0)));
                    double fpv = PilotViewSteps.fpvOffset(drone.get());
                    double min = Math.min((double) ctx.state().getOrDefault(MIN_TILT, 0.0), tilt);
                    ctx.state().put(MIN_TILT, min);
                    if (ticks % 20 == 0) {
                        String name = data.operator().map(id -> Minecraft.getInstance().level.getPlayerByUUID(id)).map(Player::getPlainTextName)
                                .orElse("keiner");
                        List<String> warnings = CamOsd.warnings(CamOsdSource.last()).stream().map(OsdText::text).toList();
                        JoinProbe.line(String.format(Locale.ROOT, "crew b gimbal operator=%s tilt=%.1f view=%.1f fpv=%.1f osd=%s", name, tilt, view, fpv,
                                warnings));
                    }
                    if (data.operated() && tilt <= -55.0) {
                        if (!(fpv < PilotViewSteps.FPV_LIMIT)) {
                            return ticks > 600 ? StepResult.fail(String.format(Locale.ROOT, "Pilot sieht die Gimbal-Bewegung des Operators: Bild blickt "
                                    + "%.1f° nach unten, %.1f° neben der FPV-Kamera (Neigung des Operators %.1f°)", view, fpv, tilt)) : StepResult.pending();
                        }
                        boolean osd = CamOsd.warnings(CamOsdSource.last()).stream().anyMatch(t -> t.text().equals(CamOsd.Warning.OPERATOR.text()));
                        JoinProbe.line(String.format(Locale.ROOT, "crew b ok: operator tilt %.1f, pilot view fpv %.1f off, osd %s", tilt, fpv,
                                osd ? "OPERATOR" : "ohne OPERATOR"));
                        return osd ? StepResult.pass() : StepResult.fail("OSD zeigt OPERATOR nicht");
                    }
                    return ticks > 600 ? StepResult.fail(String.format(Locale.ROOT, "Lage vom simulierten Operator kommt nicht an (Neigung %.1f, "
                            + "Bild %.1f, Operator %s)", tilt, view, data.operated())) : StepResult.pending();
                })
                .then("Operator gibt ab", (ctx, ticks) -> {
                    Optional<Entity> drone = PropwashClientApi.input().drone();
                    Pilot.steer(Pilot.THROTTLE_CENTER);
                    if (drone.isPresent() && !CrewData.get(drone.get()).operated()) {
                        JoinProbe.line("crew b released");
                        return StepResult.pass();
                    }
                    return ticks > 400 ? StepResult.fail("Operator bleibt gebunden") : StepResult.pending();
                })
                .then("FPV endet", (ctx, ticks) -> {
                    Pilot.releaseKeys();
                    return !PropwashClientApi.input().flying() ? StepResult.pass() : ticks > 600 ? StepResult.fail("FPV endet nicht") : StepResult.pending();
                })
                .then("crew c: eigene Drohne fliegt allein eine Mission", (ctx, ticks) -> {
                    Optional<Entity> drone = clientDrone(ctx);
                    MissionStatus status = drone.map(d -> PropwashApi.droneData().get(d, CamDroneData.missionState())).orElse(null);
                    boolean armed = drone.flatMap(d -> PropwashApi.drones().view(d)).map(DroneView::armed).orElse(false);
                    if (status != null && status.state() == NavLogic.MissionState.RUNNING && armed && CrewClient.soloOffered(drone.get())) {
                        return ticks >= 40 ? StepResult.pass() : StepResult.pending();
                    }
                    return ticks > WAIT ? StepResult.fail("eigene Drohne fliegt keine Mission ohne Pilot") : StepResult.pending();
                })
                .then("crew c: Livebild steuert die Kamera allein", (ctx, ticks) -> {
                    Optional<Entity> drone = clientDrone(ctx);
                    if (drone.isEmpty()) {
                        return StepResult.fail("Drohne fehlt am Client");
                    }
                    if (ticks == 0) {
                        ctx.state().put(NOTICE, NoticeProbe.mark());
                        if (!CrewClient.cameraView(drone.get())) {
                            return StepResult.fail("Livebild lehnt ab");
                        }
                    }
                    GimbalControl control = GimbalClient.control();
                    if (PropwashClientApi.input().operating() && control.solo() && control.droneId() == drone.get().getId()) {
                        JoinProbe.line("crew c solo operating");
                        return StepResult.pass();
                    }
                    return ticks > 400 ? StepResult.fail("keine Kamera allein (operating " + PropwashClientApi.input().operating() + ", Rolle "
                            + control.role() + ", allein " + control.solo() + ")") : StepResult.pending();
                })
                .then("crew c: Kameramann der Crew hat Vorrang", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    Optional<Entity> drone = clientDrone(ctx);
                    GimbalData data = drone.map(CrewData::get).orElse(GimbalData.NONE);
                    boolean released = NoticeProbe.first((int) ctx.state().get(NOTICE), CrewPackets.Kind.RELEASED).isPresent();
                    if (released && !PropwashClientApi.input().operating() && mc.getCameraEntity() == mc.player && data.operatedBy(JoinEnv.GUEST_ID)
                            && !data.solo()) {
                        JoinProbe.line("crew c solo yields to crew");
                        return StepResult.pass();
                    }
                    return ticks > WAIT ? StepResult.fail("Kamera allein bleibt trotz Kameramann (RELEASED " + released + ", operating "
                            + PropwashClientApi.input().operating() + ", Gimbal-Daten " + data + ")") : StepResult.pending();
                });
    }

    private static Script follow() {
        return Script.create()
                .then("Kamera-Drohne", awaitDrones(1))
                .then("Follow me auf " + JoinEnv.GUEST_NAME, (ctx, ticks) -> CamFlights.send(ctx, followRequest(ctx)))
                .then("Anfrage gestellt", status(AutonomyStatus.FOLLOW_REQUESTED, "follow requested", 200))
                .then("abgelehnt", status(AutonomyStatus.FOLLOW_DECLINED, "follow declined", WAIT))
                .then("Follow me noch einmal", (ctx, ticks) -> CamFlights.send(ctx, followRequest(ctx)))
                .then("angenommen", status(AutonomyStatus.FOLLOW_ACCEPTED, "follow accepted", WAIT))
                .then("folgt", (ctx, ticks) -> {
                    Minecraft mc = Minecraft.getInstance();
                    Entity drone = drone(ctx, 0);
                    CamFlights.lookAt(drone);
                    Player guest = mc.level == null ? null : mc.level.getPlayerByUUID(JoinEnv.GUEST_ID);
                    if (ticks == 20 * 8) {
                        JoinProbe.line(String.format(Locale.ROOT, "follow guest visible=%s distance=%.1f", guest != null,
                                guest == null ? Double.NaN : CamFlights.horizontal(drone.position(), guest.position())));
                    }
                    return ticks >= 20 * 10 ? StepResult.pass() : StepResult.pending();
                })
                .then("Regel aus: Ziel verloren", (ctx, ticks) -> {
                    CamFlights.lookAt(drone(ctx, 0));
                    return status(AutonomyStatus.FOLLOW_TARGET_LOST, "follow target_lost", WAIT).tick(ctx, ticks);
                })
                .then("Follow me bei Regel aus", (ctx, ticks) -> CamFlights.send(ctx, followRequest(ctx)))
                .then("verweigert", status(AutonomyStatus.FOLLOW_RULE_OFF, "follow rule_off", 200));
    }

    private static Optional<String> crewButton(Minecraft mc) {
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return Optional.empty();
        }
        String label = I18n.get(CrewTexts.BUTTON);
        for (AbstractWidget widget : KeyboardDeflection.widgets(screen)) {
            if (widget.visible && widget.active && label.equals(widget.getMessage().getString())) {
                return Optional.of(label);
            }
        }
        return Optional.empty();
    }

    private static boolean clickLabel(Screen screen, String label) {
        for (AbstractWidget widget : KeyboardDeflection.widgets(screen)) {
            if (widget.visible && widget.active && label.equals(widget.getMessage().getString())) {
                MouseButtonEvent event = new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0,
                        new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                boolean handled = screen.mouseClicked(event, false);
                screen.mouseReleased(event);
                return handled;
            }
        }
        return false;
    }

    private static SmartMode.Request followRequest(SelfTest.StepContext ctx) {
        return new SmartMode.Request(serial(drone(ctx, 0)), SmartState.follow(JoinEnv.GUEST_ID, FollowController.Style.BEHIND));
    }
}
