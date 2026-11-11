package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.planner.MapProbe;
import de.lardtom.camdrones.client.planner.PlannerScreen;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.planner.PlannerItems;
import de.lardtom.camdrones.planner.model.FieldText;
import de.lardtom.camdrones.planner.model.MapCanvas;
import de.lardtom.camdrones.planner.model.Rect;
import de.lardtom.camdrones.platform.Services;
import de.lardtom.propwash.ui.client.theme.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class MapDragSteps {
    public static final int NUMBER = 611;
    public static final String NAME = "planer: Karte beim Ziehen und Zoomen ohne Naht, nur neue Streifen, Bildzeit gemessen";
    public static final String REPORT_FILE = "camdrones-map-drag.txt";
    public static final String PHOTO_DIR = "ownc3";
    private static final int WINDOW_WIDTH = 1920;
    private static final int WINDOW_HEIGHT = 1080;
    private static final int GUI_SCALE = 2;
    private static final int VIEW_CHUNKS = 12;
    private static final int FREE_FRAMERATE = 260;
    private static final int HALF = 96;
    private static final int CELL = 8;
    private static final int STRIDE = 4;
    private static final int TOLERANCE = 6;
    private static final double WRONG_LIMIT = 0.005;
    private static final double EMPTY_LIMIT = 0.02;
    private static final double WORK_SLACK = 1.5;
    private static final int MIN_CHECKED = 500;
    private static final MouseButtonInfo LEFT = new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
    private static final List<Block> PALETTE = palette();
    private static final List<Move> PATH = path();
    private static final int SAME_SCALE_MOVES = 64;
    private static final String CURSOR = "mapdrag.cursor";
    private static final String SAME_SCALE = "mapdrag.same";
    private static final String MEASURED = "mapdrag.measured";
    private static final String CAPTURES = "mapdrag.captures";
    private static final String MEASURED_MAP = "mapdrag.map";
    private static final int BIG_GUI_SCALE = 1;
    private static final int BIG_MOVES = 12;
    private static int savedDistance = -1;
    private static int savedFramerate = -1;
    private static boolean savedVsync;

    private MapDragSteps() {
    }

    private record Move(int dx, int dy, int zoom, @Nullable String photo, boolean capture) {
    }

    private record Capture(String label, boolean sameScale, @Nullable Path photo, MapProbe.Drawn drawn, Rect inner, double startX, double startZ,
                           int gui, List<Rect> widgets, CompletableFuture<int[]> pixels) {
    }

    private record Verdict(int checked, int wrong, int empty) {
        double wrongShare() {
            return this.checked == 0 ? 1.0 : this.wrong / (double) this.checked;
        }

        double emptyShare() {
            return this.checked == 0 ? 1.0 : this.empty / (double) this.checked;
        }
    }

    public static void register() {
        SelfTest.clientStep(NUMBER, NAME, 20 * 300, Script.create()
                .then("Fenster 1920×1080, GUI 2", (ctx, ticks) -> Shots.window(ctx, ticks, WINDOW_WIDTH, WINDOW_HEIGHT, GUI_SCALE))
                .then("Sichtweite " + VIEW_CHUNKS + " Chunks, Bildrate ohne Grenze", MapDragSteps::viewDistance)
                .server("Farbmuster und leeres Tablet", MapDragSteps::prepare)
                .await("Tablet am Client", 100, () -> held())
                .await("Farbmuster am Client", 400, MapDragSteps::patternVisible)
                .run("Editor öffnen", () -> Minecraft.getInstance().gameMode.useItem(Minecraft.getInstance().player, InteractionHand.MAIN_HAND))
                .await("Editor offen", 100, () -> Minecraft.getInstance().gui.screen() instanceof PlannerScreen)
                .then("Karte an", MapDragSteps::showMap)
                .then("Ansicht auf den Start", MapDragSteps::home)
                .then("Karte fertig", MapDragSteps::settle)
                .then("Messfahrt", MapDragSteps::measure)
                .then("Ansicht auf den Start", MapDragSteps::home)
                .then("Karte fertig", MapDragSteps::settle)
                .then("Bildfahrt", MapDragSteps::photograph)
                .then("Karte nach dem Ziehen fertig", MapDragSteps::settle)
                .then("GUI 1, Karte breiter als " + MapCanvas.MAX_SIZE + " Pixel",
                        (ctx, ticks) -> Shots.window(ctx, ticks, WINDOW_WIDTH, WINDOW_HEIGHT, BIG_GUI_SCALE))
                .then("Ansicht auf den Start", MapDragSteps::home)
                .then("Karte fertig", MapDragSteps::settle)
                .then("große Karte ziehen", MapDragSteps::bigDrag)
                .then("große Karte nach dem Ziehen fertig", MapDragSteps::settle)
                .then("Auswertung", MapDragSteps::evaluate), MapDragSteps::restoreDistance);
    }

    private static StepResult viewDistance(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            if (savedDistance < 0) {
                savedDistance = mc.options.renderDistance().get();
                savedFramerate = mc.options.framerateLimit().get();
                savedVsync = mc.options.enableVsync().get();
            }
            mc.options.renderDistance().set(VIEW_CHUNKS);
            mc.options.framerateLimit().set(FREE_FRAMERATE);
            mc.options.enableVsync().set(false);
            return StepResult.pending();
        }
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            return StepResult.fail("keine Welt");
        }
        int cx = mc.player.chunkPosition().x();
        int cz = mc.player.chunkPosition().z();
        int reach = VIEW_CHUNKS - 1;
        int[][] probes = {{reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}, {reach - 3, reach - 3}, {3 - reach, 3 - reach}};
        for (int[] p : probes) {
            if (!level.getChunkSource().hasChunk(cx + p[0], cz + p[1])) {
                return ticks > 1200 ? StepResult.fail("Chunks bis ±" + reach + " nach " + ticks + " Ticks nicht geladen") : StepResult.pending();
            }
        }
        return StepResult.pass("Sichtweite " + VIEW_CHUNKS + ", Chunks bis ±" + reach + " nach " + ticks + " Ticks geladen");
    }

    private static StepResult restoreDistance(SelfTest.StepContext ctx) {
        if (savedDistance >= 0) {
            Minecraft.getInstance().options.renderDistance().set(savedDistance);
            Minecraft.getInstance().options.framerateLimit().set(savedFramerate);
            Minecraft.getInstance().options.enableVsync().set(savedVsync);
            savedDistance = -1;
            savedFramerate = -1;
        }
        return StepResult.pass();
    }

    private static List<Block> palette() {
        List<Block> out = new ArrayList<>();
        Block black = Blocks.WOOL.pick(DyeColor.BLACK);
        for (Block wool : Blocks.WOOL.asList()) {
            out.add(wool == black ? Blocks.DIAMOND_BLOCK : wool);
        }
        return List.copyOf(out);
    }

    private static List<Move> path() {
        List<Move> out = new ArrayList<>();
        segment(out, 16, 6, 0, 0, "planner_map_drag_1");
        segment(out, 16, 0, 6, 0, null);
        segment(out, 16, -6, 0, 0, "planner_map_drag_2");
        segment(out, 16, 0, -6, 0, null);
        segment(out, 16, 2, 2, -1, "planner_map_zoom_out");
        segment(out, 16, -4, -4, 2, "planner_map_zoom_in");
        segment(out, 16, 5, -3, -1, "planner_map_drag_3");
        return List.copyOf(out);
    }

    private static void segment(List<Move> out, int ticks, int dx, int dy, int zoom, @Nullable String photo) {
        for (int i = 0; i < ticks; i++) {
            boolean capture = i == 0 || i == 1 || i == 3 || i == 7 || i == 12;
            out.add(new Move(dx, dy, i == 0 ? zoom : 0, photo != null && i == (zoom != 0 ? 0 : 7) ? photo : null, capture));
        }
    }

    static Block block(int x, int z) {
        int index = Math.floorMod(Math.floorDiv(x, CELL) * 7 + Math.floorDiv(z, CELL) * 3, PALETTE.size());
        return PALETTE.get(index);
    }

    private static int[] bounds() {
        BlockPos g = Arena.ground();
        int x0 = Math.floorDiv(g.getX() - HALF, CELL) * CELL;
        int z0 = Math.floorDiv(g.getZ() - HALF, CELL) * CELL;
        return new int[]{x0, z0, x0 + 2 * HALF, z0 + 2 * HALF};
    }

    private static void prepare(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = Arena.level(server);
        int y = Arena.ground().getY();
        int[] b = bounds();
        for (int x = b[0]; x < b[2]; x++) {
            for (int z = b[1]; z < b[3]; z++) {
                Arena.place(level, new BlockPos(x, y, z), block(x, z).defaultBlockState());
            }
        }
        ItemStack tablet = new ItemStack(PlannerItems.flightPlanner().orElseThrow(() -> new IllegalStateException("Tablet fehlt")));
        player.setItemInHand(InteractionHand.MAIN_HAND, tablet);
        Vec3 spawn = Arena.spawn();
        player.teleportTo(level, spawn.x, spawn.y, spawn.z, Set.<Relative>of(), 0.0F, 40.0F, true);
    }

    private static boolean held() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && PlannerItems.isPlanner(mc.player.getMainHandItem());
    }

    private static boolean patternVisible() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        int y = Arena.ground().getY();
        int[] b = bounds();
        int[][] probes = {{b[0], b[1]}, {b[2] - 1, b[1]}, {b[0], b[3] - 1}, {b[2] - 1, b[3] - 1}, {(b[0] + b[2]) / 2, (b[1] + b[3]) / 2}};
        for (int[] p : probes) {
            if (!level.getBlockState(new BlockPos(p[0], y, p[1])).is(block(p[0], p[1]))) {
                return false;
            }
        }
        return true;
    }

    private static @Nullable PlannerScreen screen() {
        return Minecraft.getInstance().gui.screen() instanceof PlannerScreen screen ? screen : null;
    }

    private static StepResult showMap(SelfTest.StepContext ctx, int ticks) {
        PlannerScreen screen = screen();
        if (screen == null) {
            return StepResult.fail("Editor geschlossen");
        }
        if (!screen.state().mapExpanded() && ticks == 0) {
            String toggle = I18n.get(FieldText.PREFIX + "map.show");
            if (!SelfTestRunner.clickWidget(screen, toggle)) {
                return StepResult.fail("Knopf \"" + toggle + "\" fehlt");
            }
        }
        if (ticks < 4) {
            return StepResult.pending();
        }
        if (!screen.layout().mapShown()) {
            return StepResult.fail("Karte nicht sichtbar");
        }
        Rect inner = screen.layout().map().inset(1);
        Shots.parkMouse(Minecraft.getInstance());
        return StepResult.pass("Karte " + inner.w() + "×" + inner.h() + " bei GUI " + screen.width + "×" + screen.height);
    }

    private static StepResult home(SelfTest.StepContext ctx, int ticks) {
        PlannerScreen screen = screen();
        if (screen == null) {
            return StepResult.fail("Editor geschlossen");
        }
        screen.state().view().fit(List.of(), List.of(), screen.state().startX(), screen.state().startZ(), screen.layout().map().inset(1));
        return StepResult.pass();
    }

    private static StepResult settle(SelfTest.StepContext ctx, int ticks) {
        PlannerScreen screen = screen();
        if (screen == null) {
            return StepResult.fail("Editor geschlossen");
        }
        if (ticks >= 10 && MapProbe.complete(screen)) {
            return StepResult.pass();
        }
        return ticks > 200 ? StepResult.fail("Karte nach " + ticks + " Ticks nicht fertig") : StepResult.pending();
    }

    private static void press(PlannerScreen screen, Map<String, Object> state) {
        Rect map = screen.layout().map();
        double[] cursor = {map.centerX(), map.centerY()};
        state.put(CURSOR, cursor);
        screen.mouseClicked(new MouseButtonEvent(cursor[0], cursor[1], LEFT), false);
    }

    private static void move(PlannerScreen screen, Map<String, Object> state, Move move) {
        Rect map = screen.layout().map();
        for (int i = 0; i < Math.abs(move.zoom()); i++) {
            screen.mouseScrolled(map.centerX(), map.centerY(), 0.0, Math.signum(move.zoom()));
        }
        double[] cursor = (double[]) state.get(CURSOR);
        cursor[0] += move.dx();
        cursor[1] += move.dy();
        screen.mouseDragged(new MouseButtonEvent(cursor[0], cursor[1], LEFT), move.dx(), move.dy());
    }

    private static void release(PlannerScreen screen, Map<String, Object> state) {
        double[] cursor = (double[]) state.get(CURSOR);
        screen.mouseReleased(new MouseButtonEvent(cursor[0], cursor[1], LEFT));
        Shots.parkMouse(Minecraft.getInstance());
    }

    private static StepResult measure(SelfTest.StepContext ctx, int ticks) {
        PlannerScreen screen = screen();
        if (screen == null) {
            return StepResult.fail("Editor geschlossen");
        }
        Map<String, Object> state = ctx.state();
        if (ticks == 0) {
            MapProbe.reset(screen);
            state.put(MEASURED_MAP, screen.layout().map().inset(1));
            press(screen, state);
        }
        if (ticks == SAME_SCALE_MOVES) {
            state.put(SAME_SCALE, MapProbe.timing(screen));
        }
        if (ticks < PATH.size()) {
            move(screen, state, PATH.get(ticks));
            return StepResult.pending();
        }
        release(screen, state);
        MapProbe.Timing timing = MapProbe.timing(screen);
        state.put(MEASURED, timing);
        return StepResult.pass(PATH.size() + " Ticks gezogen, " + timing.intervals().length + " Bilder");
    }

    private static StepResult photograph(SelfTest.StepContext ctx, int ticks) {
        PlannerScreen screen = screen();
        if (screen == null) {
            return StepResult.fail("Editor geschlossen");
        }
        Map<String, Object> state = ctx.state();
        @SuppressWarnings("unchecked")
        List<Capture> captures = (List<Capture>) state.computeIfAbsent(CAPTURES, key -> new ArrayList<Capture>());
        if (ticks == 0) {
            press(screen, state);
        }
        if (ticks > 0 && ticks <= PATH.size()) {
            Move last = PATH.get(ticks - 1);
            if (last.capture() || last.photo() != null) {
                captures.add(capture(screen, ticks, last.photo()));
            }
        }
        if (ticks < PATH.size()) {
            move(screen, state, PATH.get(ticks));
            return StepResult.pending();
        }
        release(screen, state);
        return StepResult.pass(captures.size() + " Aufnahmen während des Ziehens");
    }

    private static StepResult bigDrag(SelfTest.StepContext ctx, int ticks) {
        PlannerScreen screen = screen();
        if (screen == null) {
            return StepResult.fail("Editor geschlossen");
        }
        Rect inner = screen.layout().map().inset(1);
        if (inner.w() <= MapCanvas.MAX_SIZE) {
            return StepResult.fail("Karte nur " + inner.w() + " Pixel breit");
        }
        Map<String, Object> state = ctx.state();
        @SuppressWarnings("unchecked")
        List<Capture> captures = (List<Capture>) state.computeIfAbsent(CAPTURES, key -> new ArrayList<Capture>());
        if (ticks == 0) {
            press(screen, state);
        }
        if (ticks > 0 && ticks % 4 == 0) {
            captures.add(capture(screen, PATH.size() + ticks, ticks == 8 ? "planner_map_gui1" : null));
        }
        if (ticks < BIG_MOVES) {
            move(screen, state, new Move(6, 3, 0, null, false));
            return StepResult.pending();
        }
        release(screen, state);
        return StepResult.pass("große Karte " + inner.w() + "×" + inner.h() + " gezogen");
    }

    private static Capture capture(PlannerScreen screen, int tick, @Nullable String photo) {
        Minecraft mc = Minecraft.getInstance();
        Rect inner = screen.layout().map().inset(1);
        int gui = mc.getWindow().getGuiScale();
        MapProbe.Drawn drawn = MapProbe.drawn(screen);
        Path file = photo == null ? null : Services.PLATFORM.gameDir().resolve(PHOTO_DIR).resolve(photo + ".png");
        CompletableFuture<int[]> done = new CompletableFuture<>();
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        Screenshot.takeScreenshot(target, 1, image -> Util.ioPool().execute(() -> {
            try (image) {
                int x0 = inner.x() * gui;
                int y0 = inner.y() * gui;
                int w = inner.w() * gui;
                int h = inner.h() * gui;
                int[] out = new int[w * h];
                for (int j = 0; j < h; j++) {
                    for (int i = 0; i < w; i++) {
                        int x = x0 + i;
                        int y = y0 + j;
                        out[j * w + i] = x < image.getWidth() && y < image.getHeight() ? image.getPixel(x, y) : 0;
                    }
                }
                if (file != null) {
                    Files.createDirectories(file.getParent());
                    image.writeToFile(file);
                }
                done.complete(out);
            } catch (Exception e) {
                done.completeExceptionally(e);
            }
        }));
        List<Rect> widgets = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.visible) {
                widgets.add(new Rect(widget.getX() - 1, widget.getY() - 1, widget.getWidth() + 2, widget.getHeight() + 2));
            }
        }
        boolean sameScale = tick <= SAME_SCALE_MOVES || tick > PATH.size();
        String label = "Tick " + tick + " (" + Flight.format(drawn.scale()) + " Blöcke je Pixel, GUI " + gui + ", Karte " + inner.w() + "×" + inner.h()
                + (photo == null ? "" : ", " + photo) + ")";
        return new Capture(label, sameScale, file, drawn, inner, screen.state().startX(), screen.state().startZ(), gui, List.copyOf(widgets), done);
    }

    private static StepResult evaluate(SelfTest.StepContext ctx, int ticks) {
        PlannerScreen screen = screen();
        if (screen == null) {
            return StepResult.fail("Editor geschlossen");
        }
        Map<String, Object> state = ctx.state();
        @SuppressWarnings("unchecked")
        List<Capture> captures = (List<Capture>) state.getOrDefault(CAPTURES, List.of());
        if (captures.stream().anyMatch(c -> !c.pixels().isDone())) {
            return ticks > 200 ? StepResult.fail("Aufnahmen hängen") : StepResult.pending();
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return StepResult.fail("keine Welt");
        }
        Rect inner = (Rect) state.getOrDefault(MEASURED_MAP, screen.layout().map().inset(1));
        List<String> report = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        MapProbe.Timing all = (MapProbe.Timing) state.get(MEASURED);
        MapProbe.Timing same = (MapProbe.Timing) state.get(SAME_SCALE);
        if (all == null || same == null) {
            return StepResult.fail("Messfahrt fehlt");
        }
        long allowed = 0L;
        for (int i = 0; i < SAME_SCALE_MOVES; i++) {
            Move m = PATH.get(i);
            allowed += (long) Math.abs(m.dx()) * (inner.h() + 1) + (long) Math.abs(m.dy()) * (inner.w() + 1);
        }
        report.add("Messfahrt: Karte " + inner.w() + "×" + inner.h() + " GUI-Pixel, GUI-Faktor " + GUI_SCALE + ", Fenster " + WINDOW_WIDTH + "×"
                + WINDOW_HEIGHT);
        report.add("Ziehen bei 1 Block je Pixel (" + SAME_SCALE_MOVES + " Ticks): " + frames(same.intervals(), same.work()) + "; " + ticksLine(same));
        report.add("  abgetastete Kartenpixel " + same.written() + ", hochgeladene Texel " + same.uploaded() + ", neu sichtbare Streifen "
                + allowed + ", neu berechnete Blocksäulen " + same.columns());
        report.add("ganze Messfahrt mit Zoom (" + PATH.size() + " Ticks): " + frames(all.intervals(), all.work()) + "; " + ticksLine(all));
        report.add("  abgetastete Kartenpixel " + all.written() + ", hochgeladene Texel " + all.uploaded() + ", neu berechnete Blocksäulen "
                + all.columns());
        if (same.written() > WORK_SLACK * allowed) {
            problems.add("beim Ziehen " + same.written() + " Kartenpixel abgetastet, neu sichtbar waren nur " + allowed);
        }
        if (same.uploaded() > WORK_SLACK * allowed) {
            problems.add("beim Ziehen " + same.uploaded() + " Texel hochgeladen, neu sichtbar waren nur " + allowed);
        }
        int worst = 0;
        double worstShare = 0.0;
        for (Capture capture : captures) {
            int[] pixels = capture.pixels().join();
            Verdict verdict = judge(level, capture, pixels);
            String line = capture.label() + ": " + verdict.checked() + " Stellen, falsch " + percent(verdict.wrongShare()) + ", leer "
                    + percent(verdict.emptyShare()) + (capture.photo() == null ? "" : ", Foto " + capture.photo());
            report.add(line);
            if (verdict.checked() < MIN_CHECKED) {
                problems.add(capture.label() + ": nur " + verdict.checked() + " prüfbare Stellen");
            } else if (verdict.wrongShare() > WRONG_LIMIT) {
                problems.add(capture.label() + ": Naht, " + percent(verdict.wrongShare()) + " der Karte zeigen eine andere Stelle der Welt");
            } else if (capture.sameScale() && verdict.emptyShare() > EMPTY_LIMIT) {
                problems.add(capture.label() + ": " + percent(verdict.emptyShare()) + " der Karte beim Ziehen leer");
            }
            if (verdict.wrongShare() > worstShare) {
                worstShare = verdict.wrongShare();
                worst = captures.indexOf(capture);
            }
        }
        report.add("schlimmste Aufnahme: " + (captures.isEmpty() ? "-" : captures.get(worst).label() + " mit " + percent(worstShare) + " falsch"));
        report.add(problems.isEmpty() ? "Ergebnis: PASS" : "Ergebnis: FAIL – " + String.join(" | ", problems));
        writeReport(report);
        report.forEach(ctx::log);
        String summary = frames(same.intervals(), same.work()) + "; " + captures.size() + " Aufnahmen, schlimmste " + percent(worstShare) + " falsch";
        return problems.isEmpty() ? StepResult.pass(summary) : StepResult.fail(String.join(" | ", problems) + " (" + summary + ")");
    }

    private static Verdict judge(ClientLevel level, Capture capture, int[] pixels) {
        Rect in = capture.inner();
        int gui = capture.gui();
        int pw = in.w() * gui;
        int ph = in.h() * gui;
        double s = capture.drawn().scale();
        double cx = capture.drawn().centerX();
        double cz = capture.drawn().centerZ();
        double midX = in.x() + in.w() / 2.0;
        double midY = in.y() + in.h() / 2.0;
        double startX = midX + (capture.startX() - cx) / s;
        double startY = midY + (capture.startZ() - cz) / s;
        double texel = s * Math.ceilDiv(Math.max(in.w(), in.h()), MapCanvas.MAX_SIZE);
        Map<Long, Integer> expectedCache = new HashMap<>();
        Map<Long, Integer> texelCache = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int unloaded = MapProbe.unloaded();
        int checked = 0;
        int wrong = 0;
        int empty = 0;
        for (int py = STRIDE / 2; py < ph; py += STRIDE) {
            for (int px = STRIDE / 2; px < pw; px += STRIDE) {
                double gx = in.x() + (px + 0.5) / gui;
                double gy = in.y() + (py + 0.5) / gui;
                if (excluded(in, gx, gy, startX, startY) || capture.widgets().stream().anyMatch(r -> r.contains(gx, gy))) {
                    continue;
                }
                double wx = cx + (gx - midX) * s;
                double wz = cz + (gy - midY) * s;
                int expected = expected(level, pos, expectedCache, wx, wz, s);
                if (expected == unloaded) {
                    continue;
                }
                int shown = pixels[py * pw + px];
                checked++;
                if (close(shown, expected) || nearby(level, pos, expectedCache, shown, wx, wz, s)
                        || ownTexel(level, pos, texelCache, shown, wx, wz, texel, 0.5 * s / gui)) {
                    continue;
                }
                if (close(shown, unloaded) || close(shown, Palette.FIELD)) {
                    empty++;
                } else {
                    wrong++;
                }
            }
        }
        return new Verdict(checked, wrong, empty);
    }

    private static int expected(ClientLevel level, BlockPos.MutableBlockPos pos, Map<Long, Integer> cache, double wx, double wz, double s) {
        long key = ((long) Math.floor(wx) << 32) ^ ((long) Math.floor(wz) & 0xFFFFFFFFL);
        Integer known = cache.get(key);
        if (known != null) {
            return known;
        }
        int expected = MapProbe.expected(level, pos, wx, wz, s);
        cache.put(key, expected);
        return expected;
    }

    private static boolean nearby(ClientLevel level, BlockPos.MutableBlockPos pos, Map<Long, Integer> cache, int shown, double wx, double wz,
                                  double s) {
        double d = 0.5 * s;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if ((i != 0 || j != 0) && close(shown, expected(level, pos, cache, wx + i * d, wz + j * d, s))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean ownTexel(ClientLevel level, BlockPos.MutableBlockPos pos, Map<Long, Integer> cache, int shown, double wx, double wz,
                                    double texel, double edge) {
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                double tx = (Math.floor((wx + i * edge) / texel) + 0.5) * texel;
                double tz = (Math.floor((wz + j * edge) / texel) + 0.5) * texel;
                if (close(shown, expected(level, pos, cache, tx, tz, texel))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean excluded(Rect in, double gx, double gy, double startX, double startY) {
        if (gx < in.x() + 18 && gy < in.y() + 16) {
            return true;
        }
        if (gx < in.x() + 110 && gy > in.bottom() - 16) {
            return true;
        }
        return Math.abs(gx - startX) < 22 && Math.abs(gy - startY) < 22;
    }

    private static boolean close(int a, int b) {
        return Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) <= TOLERANCE && Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) <= TOLERANCE
                && Math.abs((a & 0xFF) - (b & 0xFF)) <= TOLERANCE;
    }

    private static String frames(long[] intervals, long[] work) {
        if (intervals.length == 0) {
            return "keine Bilder";
        }
        long[] sorted = intervals.clone();
        Arrays.sort(sorted);
        long sum = 0L;
        for (long v : intervals) {
            sum += v;
        }
        long workSum = 0L;
        long workMax = 0L;
        for (long v : work) {
            workSum += v;
            workMax = Math.max(workMax, v);
        }
        return intervals.length + " Bilder, Bildzeit Mittel " + ms(sum / intervals.length) + ", Median " + ms(sorted[sorted.length / 2]) + ", 95 % "
                + ms(sorted[Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.95) - 1)]) + ", größte " + ms(sorted[sorted.length - 1])
                + "; Kartenarbeit je Bild Mittel " + ms(workSum / work.length) + ", größte " + ms(workMax);
    }

    private static String ticksLine(MapProbe.Timing timing) {
        return "Kartenarbeit je Tick Mittel " + ms(timing.ticks() == 0 ? 0L : timing.tickWork() / timing.ticks()) + ", größte " + ms(timing.tickWorst())
                + " (" + timing.ticks() + " Ticks)";
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.2f ms", nanos / 1.0e6);
    }

    private static String percent(double share) {
        return String.format(Locale.ROOT, "%.2f %%", share * 100.0);
    }

    private static void writeReport(List<String> lines) {
        try {
            Files.writeString(Services.PLATFORM.gameDir().resolve(REPORT_FILE), String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            CamDrones.LOG.warn("[Kamera-Selbsttest] {} nicht geschrieben: {}", REPORT_FILE, e.toString());
        }
    }
}
