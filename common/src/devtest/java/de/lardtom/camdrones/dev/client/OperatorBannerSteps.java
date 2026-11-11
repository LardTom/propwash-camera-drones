package de.lardtom.camdrones.dev.client;

import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.GimbalControl;
import de.lardtom.camdrones.client.hud.CamOsd;
import de.lardtom.camdrones.client.hud.CamOsdSource;
import de.lardtom.camdrones.client.hud.HudHooks;
import de.lardtom.camdrones.client.planner.PlannerClient;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.crew.model.CrewBook;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.data.MissionStatus;
import de.lardtom.camdrones.logic.mission.Mission;
import de.lardtom.camdrones.logic.mission.Waypoint;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.net.MissionCommand;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class OperatorBannerSteps {
    public static final int NUMBER = 564;
    public static final String NAME = "gimbal: Kameramann-Bild – das Banner des Livebilds verdeckt keine OSD-Anzeige (GIMBAL LIMIT, OPERATOR), "
            + "Kamera allein und Kameramann der Crew";
    public static final String OPERATOR_INPUT = "propwash.test.operatorInput";
    public static final String NEUTRAL = "0,0,0,0";
    public static final int DX = -60;
    public static final int DZ = -150;
    public static final double HEIGHT = 8.0;
    public static final double LEG = 25.0;
    public static final int LEGS = 4;
    public static final double SPEED = 1.5;
    public static final int PAN_TICKS = 30;
    public static final int PAN_MAX_TICKS = 200;
    public static final int LONG_TICKS = 24;
    public static final int SETTLE_TICKS = 30;
    public static final int QUIET_TICKS = 60;
    public static final int WINDOW_TICKS = 100;
    public static final int OVERLAP_WIDTH = 3;
    public static final int OVERLAP_HEIGHT = 2;
    public static final int SLACK = 1;
    private static final UUID PILOT = UUID.nameUUIDFromBytes("camdrones-selftest-banner-pilot".getBytes(StandardCharsets.UTF_8));
    private static final String NOTES = "banner.notes";
    private static final String SURVEY = "banner.survey";
    private static final String COVERED = "banner.covered";
    private static final String REACHED = "banner.reached";
    private static final int STEP_TICKS = 20 * 240;
    private static @Nullable String savedInput;
    private static boolean inputSaved;

    private OperatorBannerSteps() {
    }

    public static void register() {
        SelfTest.clientStep(NUMBER, NAME, STEP_TICKS, Script.create()
                .run("Kameramann-Eingaben neutral", () -> input(NEUTRAL))
                .server("Tour setzen und koppeln, gerade Mission mit freier Blickrichtung", OperatorBannerSteps::setup)
                .then("Drohne am Client", (ctx, ticks) -> CamFlights.clientDrone(ctx.state()).isPresent() ? StepResult.pass()
                        : ticks > 100 ? StepResult.fail("Drohne kommt nicht am Client an") : StepResult.pending())
                .sleep(10)
                .then("Mission ohne Pilot starten", (ctx, ticks) -> CamFlights.send(ctx, new MissionCommand.Request(CamFlights.serial(ctx.state()),
                        MissionCommand.Command.START, 0)))
                .then("Flug ohne Pilot am Server", (ctx, ticks) -> CamFlights.serverFlight(ctx, ticks, "mission"))
                .then("Mission läuft in der Luft", OperatorBannerSteps::airborne)
                .then("Livebild im Flugplaner", OperatorBannerSteps::openLive)
                .then("Kamera allein: Kameramann-Bild", (ctx, ticks) -> operating(ctx, ticks, true))
                .then("Kamera allein: Knüppel schwenkt bis zum Anschlag, GIMBAL LIMIT", (ctx, ticks) -> panToStop(ctx, ticks, "1,0,0,0"))
                .then("Kamera allein: OSD unter dem Banner messen", (ctx, ticks) -> survey(ctx, ticks, "Kamera allein"))
                .then("Aktion 4 lang: zurück in die normale Ansicht", OperatorBannerSteps::leave)
                .server("Spieler als Kameramann der Crew binden", OperatorBannerSteps::bind)
                .then("Kameramann der Crew: Kameramann-Bild", (ctx, ticks) -> operating(ctx, ticks, false))
                .then("Kameramann der Crew: Knüppel schwenkt bis zum Anschlag, GIMBAL LIMIT", (ctx, ticks) -> panToStop(ctx, ticks, "-1,0,0,0"))
                .then("Kameramann der Crew: OSD unter dem Banner messen", (ctx, ticks) -> survey(ctx, ticks, "Kameramann der Crew"))
                .then("Banner verdeckt in beiden Ansichten keine OSD-Anzeige", OperatorBannerSteps::result), OperatorBannerSteps::cleanup);
    }

    public record Area(int left, int top, int right, int bottom) {
        static Area of(ScreenRectangle rect) {
            return new Area(rect.left(), rect.top(), rect.left() + rect.width(), rect.top() + rect.height());
        }

        public int width() {
            return this.right - this.left;
        }

        public int height() {
            return this.bottom - this.top;
        }

        public long size() {
            return (long) Math.max(0, this.width()) * Math.max(0, this.height());
        }

        public boolean contains(Area other, int slack) {
            return other.left >= this.left - slack && other.top >= this.top - slack && other.right <= this.right + slack
                    && other.bottom <= this.bottom + slack;
        }

        public boolean covers(Area other) {
            int width = Math.min(this.right, other.right) - Math.max(this.left, other.left);
            int height = Math.min(this.bottom, other.bottom) - Math.max(this.top, other.top);
            return width >= OVERLAP_WIDTH && height >= OVERLAP_HEIGHT;
        }

        public String describe() {
            return this.left + "," + this.top + " " + this.width() + "x" + this.height();
        }
    }

    public record Piece(@Nullable String text, Area box, int stratum, boolean fill) {
        public String describe() {
            return (this.text != null ? "Text \"" + this.text + "\"" : this.fill ? "Fläche" : "Element") + " " + this.box.describe();
        }
    }

    public record Panel(String text, Area box, int stratum) {
    }

    public record Frame(List<Piece> pieces, int width, int height) {
        public List<Panel> panels() {
            List<Panel> out = new ArrayList<>();
            for (Piece text : this.pieces) {
                if (text.text() == null) {
                    continue;
                }
                Piece best = null;
                for (Piece fill : this.pieces) {
                    if (!fill.fill() || fill.stratum() != text.stratum() || this.overlay(fill.box()) || !fill.box().contains(text.box(), SLACK)
                            || fill.box().size() <= text.box().size()) {
                        continue;
                    }
                    if (best == null || fill.box().size() > best.box().size()) {
                        best = fill;
                    }
                }
                if (best != null) {
                    Area box = best.box();
                    if (out.stream().noneMatch(p -> p.box().equals(box) && p.stratum() == text.stratum())) {
                        out.add(new Panel(text.text(), box, text.stratum()));
                    }
                }
            }
            return out;
        }

        public List<String> covered(Panel panel) {
            List<String> out = new ArrayList<>();
            for (Piece piece : this.pieces) {
                if (this.overlay(piece.box()) || !panel.box().covers(piece.box())) {
                    continue;
                }
                boolean own = piece.stratum() == panel.stratum() && (piece.fill() || piece.text() != null)
                        && panel.box().contains(piece.box(), SLACK);
                if (!own) {
                    out.add(piece.describe());
                }
            }
            return out;
        }

        public int osdElements() {
            int count = 0;
            for (Piece piece : this.pieces) {
                if (piece.text() == null && !piece.fill() && !this.overlay(piece.box())) {
                    count++;
                }
            }
            return count;
        }

        private boolean overlay(Area box) {
            return box.size() * 4 > (long) this.width * this.height;
        }
    }

    public static Frame capture(Minecraft mc) {
        Recorder recorder = new Recorder();
        Graphics graphics = new Graphics(mc, recorder);
        mc.gui.hud.extractRenderState(graphics, mc.getDeltaTracker());
        return new Frame(List.copyOf(recorder.pieces), graphics.guiWidth(), graphics.guiHeight());
    }

    private static final class Survey {
        private int frames;
        private int withPanel;
        private int minOsd = Integer.MAX_VALUE;
        private final Set<String> panels = new LinkedHashSet<>();
        private final Map<String, Integer> covered = new LinkedHashMap<>();
        private final Set<String> warnings = new LinkedHashSet<>();
        private int limitFrames;
    }

    private static void setup(MinecraftServer server, Map<String, Object> state) {
        Pilot.spawn(server, state, CamPresets.TOUR, Arena.ground(DX, DZ), 0.0F);
        Arena.player(server).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        CamFlights.pair(server, state);
        Entity drone = Pilot.serverDrone(server, state);
        double y = drone.getY() + HEIGHT;
        List<Waypoint> points = new ArrayList<>();
        for (int i = 0; i <= LEGS; i++) {
            points.add(Waypoint.at(drone.getX(), y, drone.getZ() + 4.0 + i * LEG).withHeading(Waypoint.Heading.FREE, 0.0));
        }
        CamFlights.mission(server, state, Mission.of(points).withEnd(Mission.End.HOVER).withBaseSpeed(SPEED));
    }

    private static StepResult airborne(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        Optional<MissionStatus> status = CamFlights.clientMissionStatus(ctx.state());
        double height = drone.map(d -> d.getY() - CamFlights.surface()).orElse(Double.NaN);
        boolean running = status.map(s -> s.state() == NavLogic.MissionState.RUNNING).orElse(false);
        if (running && height > HEIGHT - 1.5) {
            return StepResult.pass("Mission läuft ohne Pilot, " + Flight.format(height) + " m über dem Boden");
        }
        return ticks > 600 ? StepResult.fail("Mission steigt nicht (Höhe " + Flight.format(height) + " m, Zustand "
                + status.map(s -> s.state().name()).orElse("-") + ")") : StepResult.pending();
    }

    private static StepResult openLive(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        input(NEUTRAL);
        if (!PlannerClient.liveView(drone)) {
            return StepResult.fail("Knopf Livebild im Flugplaner lehnt ab");
        }
        return StepResult.pass();
    }

    private static StepResult operating(SelfTest.StepContext ctx, int ticks, boolean solo) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        input(NEUTRAL);
        Minecraft mc = Minecraft.getInstance();
        GimbalControl control = GimbalClient.control();
        boolean watching = mc.getCameraEntity() == drone.get();
        boolean operator = PropwashClientApi.input().operating() && control.role() == GimbalControl.Role.OPERATOR
                && control.droneId() == drone.get().getId();
        boolean shown = osdWarnings().contains(CamOsd.Warning.OPERATOR.text());
        boolean alone = mc.player != null && CrewData.get(drone.get()).soloBy(mc.player.getUUID()) && control.solo();
        if (watching && operator && shown && alone == solo && ticks >= SETTLE_TICKS) {
            return StepResult.pass();
        }
        return ticks > 400 ? StepResult.fail("kein Kameramann-Bild (Bild der Drohne " + watching + ", operating "
                + PropwashClientApi.input().operating() + ", Rolle " + control.role() + ", allein " + alone + ", OSD " + osdWarnings() + ")")
                : StepResult.pending();
    }

    private static StepResult panToStop(SelfTest.StepContext ctx, int ticks, String stick) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        Integer reached = (Integer) ctx.state().get(REACHED);
        if (reached == null && ticks >= PAN_TICKS && limitShown(drone.get())) {
            reached = ticks;
            ctx.state().put(REACHED, reached);
        }
        input(reached == null ? stick : NEUTRAL);
        if (reached == null) {
            return ticks > PAN_MAX_TICKS ? StepResult.fail("kein GIMBAL LIMIT nach " + ticks + " Ticks Schwenk (" + gimbal(ctx) + ")")
                    : StepResult.pending();
        }
        if (ticks < reached + SETTLE_TICKS) {
            return StepResult.pending();
        }
        ctx.state().remove(REACHED);
        return limitShown(drone.get()) ? StepResult.pass() : StepResult.fail("GIMBAL LIMIT nach dem Loslassen aus (" + gimbal(ctx) + ")");
    }

    private static StepResult survey(SelfTest.StepContext ctx, int ticks, String view) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        input(NEUTRAL);
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            ctx.state().put(SURVEY, new Survey());
            mc.gui.hud.getChat().clearMessages(false);
            mc.gui.hud.setOverlayMessage(Component.empty(), false);
            mc.gui.hud.clearTitles();
        }
        if (ticks < QUIET_TICKS) {
            return StepResult.pending();
        }
        Survey survey = (Survey) ctx.state().get(SURVEY);
        Frame frame;
        try {
            frame = capture(mc);
        } catch (RuntimeException e) {
            return StepResult.fail(view + ": HUD lässt sich nicht zeichnen: " + e);
        }
        survey.frames++;
        survey.warnings.addAll(osdWarnings());
        if (limitShown(drone.get())) {
            survey.limitFrames++;
        }
        survey.minOsd = Math.min(survey.minOsd, frame.osdElements());
        List<Panel> panels = frame.panels();
        if (!panels.isEmpty()) {
            survey.withPanel++;
        }
        for (Panel panel : panels) {
            String name = "\"" + panel.text() + "\" " + panel.box().describe();
            survey.panels.add(name);
            for (String piece : frame.covered(panel)) {
                survey.covered.merge("Banner " + name + " über " + piece, 1, Integer::sum);
            }
        }
        if (survey.frames < WINDOW_TICKS) {
            return StepResult.pending();
        }
        String measured = view + ": " + survey.frames + " Bilder, Banner " + (survey.panels.isEmpty() ? "keins" : String.join(", ", survey.panels))
                + " in " + survey.withPanel + " Bildern, OSD-Elemente mindestens " + survey.minOsd + ", Warnungen " + survey.warnings
                + ", GIMBAL LIMIT in " + survey.limitFrames + " Bildern";
        if (survey.minOsd == 0) {
            return StepResult.fail(measured + "; OSD nicht erfasst");
        }
        if (!survey.warnings.contains(CamOsd.Warning.OPERATOR.text()) || !survey.warnings.contains(CamOsd.Warning.GIMBAL_LIMIT.text())) {
            return StepResult.fail(measured + "; erwartet OPERATOR und GIMBAL LIMIT");
        }
        if (survey.covered.isEmpty()) {
            return StepResult.pass(note(ctx, measured + ", nichts verdeckt"));
        }
        List<String> lines = new ArrayList<>();
        survey.covered.forEach((what, count) -> lines.add(what + " in " + count + " von " + survey.frames + " Bildern"));
        for (String line : lines) {
            ctx.log(view + ": " + line);
        }
        String found = measured + "; verdeckt: " + String.join("; ", lines.subList(0, Math.min(3, lines.size())));
        covered(ctx).add(found);
        return StepResult.pass(note(ctx, found));
    }

    private static StepResult result(SelfTest.StepContext ctx, int ticks) {
        List<String> found = covered(ctx);
        if (!found.isEmpty()) {
            return StepResult.fail(String.join(" | ", found));
        }
        return StepResult.pass(String.join("; ", notes(ctx)));
    }

    private static StepResult leave(SelfTest.StepContext ctx, int ticks) {
        Optional<Entity> drone = CamFlights.clientDrone(ctx.state());
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne fehlt am Client");
        }
        input(ticks < LONG_TICKS ? NEUTRAL + ",4" : NEUTRAL);
        if (ticks < LONG_TICKS) {
            return StepResult.pending();
        }
        Minecraft mc = Minecraft.getInstance();
        boolean back = mc.getCameraEntity() == mc.player && !PropwashClientApi.input().operating();
        boolean running = CamFlights.clientMissionStatus(ctx.state()).map(s -> s.state() == NavLogic.MissionState.RUNNING).orElse(false);
        boolean released = !CrewData.get(drone.get()).operated() && !CrewData.get(drone.get()).solo();
        if (back && running && released && ticks >= LONG_TICKS + SETTLE_TICKS) {
            return StepResult.pass();
        }
        return ticks > LONG_TICKS + 200 ? StepResult.fail("nicht zurück (Kamera am Spieler " + (mc.getCameraEntity() == mc.player)
                + ", operating " + PropwashClientApi.input().operating() + ", Mission läuft " + running + ", Kamera frei " + released + ")")
                : StepResult.pending();
    }

    private static void bind(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = Pilot.serverDrone(server, state);
        long now = System.currentTimeMillis();
        CrewBook.Facts facts = new CrewBook.Facts(true, true, true, 0.0, false);
        CrewBook book = CrewService.book();
        CrewBook.Verdict invited = book.invite(drone.getUUID(), PILOT, player.getUUID(), facts, now);
        CrewBook.Verdict accepted = book.accept(player.getUUID(), facts, now, (droneId, operator) -> PropwashApi.drones().operators().bind(drone, player));
        if (!invited.ok() || !accepted.ok()) {
            throw new IllegalStateException("Kameramann nicht gebunden: Einladung " + invited + ", Annahme " + accepted);
        }
        CrewData.set(drone, CrewData.get(drone).withOperator(Optional.of(player.getUUID())));
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        restoreInput();
        StepResult result = Script.server(ctx, ctx.tick(), (server, state) -> {
            for (Entity drone : Arena.drones(Arena.level(server))) {
                if (PropwashApi.drones().operators().operator(drone).isPresent()) {
                    PropwashApi.drones().operators().unbind(drone);
                }
                if (CrewData.get(drone).operated()) {
                    CrewService.book().release(drone.getUUID());
                    CrewData.set(drone, CrewData.get(drone).withOperator(Optional.empty()));
                }
            }
            CamFlights.stopFlights(server);
        });
        if (result.done()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.getCameraEntity() != mc.player && !PropwashClientApi.input().operating()) {
                mc.setCameraEntity(mc.player);
            }
        }
        return result;
    }

    private static String gimbal(SelfTest.StepContext ctx) {
        Optional<Double> frame = CamFlights.clientDrone(ctx.state()).flatMap(d -> PropwashApi.drones().view(d)).map(DroneView::forward)
                .map(f -> Compass.headingTo(f.x, f.z));
        Vector3fc forward = Minecraft.getInstance().gameRenderer.mainCamera().forwardVector();
        double camera = Compass.headingTo(forward.x(), forward.z());
        GimbalControl control = GimbalClient.control();
        return "Bild " + frame.map(f -> Flight.format(Compass.wrap180(camera - f)) + "° neben der Nase").orElse("ohne Drohne") + ", Rolle " + control.role()
                + ", allein " + control.solo() + ", Einstellung " + control.setting() + ", OSD " + osdWarnings();
    }

    private static boolean limitShown(Entity drone) {
        return HudHooks.gimbalStop(drone) && osdWarnings().contains(CamOsd.Warning.GIMBAL_LIMIT.text());
    }

    private static List<String> osdWarnings() {
        return CamOsd.warnings(CamOsdSource.last()).stream().map(OsdText::text).toList();
    }

    private static synchronized void input(String value) {
        if (!inputSaved) {
            savedInput = System.getProperty(OPERATOR_INPUT);
            inputSaved = true;
        }
        System.setProperty(OPERATOR_INPUT, value);
    }

    private static synchronized void restoreInput() {
        if (!inputSaved) {
            return;
        }
        if (savedInput == null) {
            System.clearProperty(OPERATOR_INPUT);
        } else {
            System.setProperty(OPERATOR_INPUT, savedInput);
        }
        savedInput = null;
        inputSaved = false;
    }

    private static String note(SelfTest.StepContext ctx, String text) {
        notes(ctx).add(text);
        return text;
    }

    @SuppressWarnings("unchecked")
    private static List<String> notes(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(NOTES, key -> new ArrayList<String>());
    }

    @SuppressWarnings("unchecked")
    private static List<String> covered(SelfTest.StepContext ctx) {
        return (List<String>) ctx.state().computeIfAbsent(COVERED, key -> new ArrayList<String>());
    }

    private static final class Recorder extends GuiRenderState {
        private final List<Piece> pieces = new ArrayList<>();
        private int stratum;
        private @Nullable FormattedCharSequence pending;

        @Override
        public void nextStratum() {
            this.stratum++;
            super.nextStratum();
        }

        @Override
        public void addGuiElement(GuiElementRenderState element) {
            super.addGuiElement(element);
            this.element(element, element instanceof ColoredRectangleRenderState);
        }

        @Override
        public void addBlitToCurrentLayer(BlitRenderState blit) {
            super.addBlitToCurrentLayer(blit);
            this.element(blit, false);
        }

        @Override
        public void addText(GuiTextRenderState text) {
            super.addText(text);
            if (this.pending == null) {
                return;
            }
            ScreenRectangle bounds = text.ensurePrepared().bounds();
            if (bounds == null) {
                return;
            }
            int advance = Minecraft.getInstance().font.width(this.pending) + 1;
            if (advance < bounds.width()) {
                bounds = new ScreenRectangle(bounds.left(), bounds.top(), advance, bounds.height());
            }
            ScreenRectangle full = bounds.transformMaxBounds(text.pose);
            if (full.width() <= 0 || full.height() <= 0) {
                return;
            }
            String value = LayoutCheck.plain(this.pending);
            if (!value.isBlank()) {
                this.pieces.add(new Piece(value, Area.of(full), this.stratum, false));
            }
        }

        private void element(GuiElementRenderState element, boolean fill) {
            ScreenRectangle bounds = element.bounds();
            if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                this.pieces.add(new Piece(null, Area.of(bounds), this.stratum, fill));
            }
        }
    }

    private static final class Graphics extends GuiGraphicsExtractor {
        private final Recorder recorder;

        private Graphics(Minecraft minecraft, Recorder recorder) {
            super(minecraft, recorder, -1, -1);
            this.recorder = recorder;
        }

        @Override
        public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow) {
            this.recorder.pending = text;
            try {
                super.text(font, text, x, y, color, dropShadow);
            } finally {
                this.recorder.pending = null;
            }
        }
    }
}
