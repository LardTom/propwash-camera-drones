package de.lardtom.camdrones.client.hud;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.client.planner.CamClientConfig;
import de.lardtom.camdrones.logic.nav.FlightClass;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.hud.FlightTelemetry;
import de.lardtom.propwash.api.client.hud.HudContext;
import de.lardtom.propwash.api.client.hud.HudElement;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.client.osd.OsdTone;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

public final class CameraView implements HudElement {
    public static final String ID = CamDrones.MOD_ID + ":camera_view";
    public static final int TEXT = 0xFFFFFFFF;
    public static final int DIM = 0xFFB4B4B4;
    public static final int CAUTION = 0xFFFFD24D;
    public static final int WARNING = 0xFFFF4D4D;
    public static final int ACCENT = 0xFF5CFF8A;
    public static final int BAR = 0x80000000;
    public static final int BACKDROP = 0x50000000;
    public static final int MAP = 0x90101418;
    public static final int MAP_EDGE = 0xC0FFFFFF;
    public static final int RECORD = 0xFFFF3030;
    public static final int EDGE_RGB = 0xFF2020;
    public static final int GAP = 8;
    public static final int TICK_STEP = 30;

    private final RecordingClock clock = new RecordingClock();

    public static boolean shown(HudContext context, CamOsdState state) {
        return (context.fpv() || context.spectating()) && state.cameraDrone() && state.cameraView();
    }

    private static CamOsdState state(HudContext context) {
        return CamOsdSource.state(context.drone(), context.telemetry());
    }

    @Override
    public boolean active(HudContext context) {
        return shown(context, state(context));
    }

    @Override
    public boolean replacesOsd(HudContext context) {
        return this.active(context);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, HudContext context) {
        CamOsdState state = state(context);
        FlightTelemetry telemetry = context.telemetry();
        Font font = context.font();
        CameraViewLayout layout = CameraViewLayout.of(context.imageX0(), context.imageY0(), context.imageX1(), context.imageY1(), font.lineHeight);
        if (layout.image().empty()) {
            return;
        }
        obstacleEdge(graphics, layout, state);
        topBar(graphics, font, layout, state, telemetry);
        this.recording(graphics, font, layout);
        info(graphics, font, layout, telemetry);
        map(graphics, font, layout, telemetry);
        tiltScale(graphics, font, layout, state);
        warnings(graphics, font, layout, state);
    }

    public static int color(OsdTone tone) {
        return switch (tone) {
            case NORMAL -> TEXT;
            case ACCENT -> ACCENT;
            case CAUTION -> CAUTION;
            case WARNING -> WARNING;
        };
    }

    private static void box(GuiGraphicsExtractor graphics, CameraViewLayout.Box box, int color) {
        if (!box.empty()) {
            graphics.fill(box.x0(), box.y0(), box.x1(), box.y1(), color);
        }
    }

    private static void frame(GuiGraphicsExtractor graphics, CameraViewLayout.Box box, int color) {
        graphics.fill(box.x0(), box.y0(), box.x1(), box.y0() + 1, color);
        graphics.fill(box.x0(), box.y1() - 1, box.x1(), box.y1(), color);
        graphics.fill(box.x0(), box.y0(), box.x0() + 1, box.y1(), color);
        graphics.fill(box.x1() - 1, box.y0(), box.x1(), box.y1(), color);
    }

    private record Item(String text, int color) {
    }

    private static void topBar(GuiGraphicsExtractor graphics, Font font, CameraViewLayout layout, CamOsdState state, FlightTelemetry t) {
        CameraViewLayout.Box bar = layout.topBar();
        box(graphics, bar, BAR);
        int y = bar.y0() + 2;
        String mode = state.label().isBlank() ? HudText.UNKNOWN : HudText.ascii(state.label());
        int left = bar.x0() + CameraViewLayout.MARGIN;
        graphics.text(font, mode, left, y, state.atti() ? CAUTION : TEXT, true);
        int modeEnd = left + font.width(mode) + GAP;
        List<Item> items = new ArrayList<>();
        items.add(t.gpsFix() ? new Item("GPS " + Math.max(0, t.satellites()), TEXT) : new Item("NO GPS", CAUTION));
        String battery = "BAT " + HudText.percent(t.batteryCharge())
                + (Double.isFinite(state.flightSecondsLeft()) ? " " + HudText.minutes(state.flightSecondsLeft()) : "");
        OsdText remaining = CamOsd.flightLeft(state);
        items.add(new Item(battery, remaining == null ? TEXT : color(remaining.tone())));
        items.add(new Item("SIG " + HudText.percent(t.linkQuality()), t.linkQuality() < 0.3 ? CAUTION : TEXT));
        int available = bar.x1() - CameraViewLayout.MARGIN - modeEnd;
        while (items.size() > 1 && width(font, items) > available) {
            items.remove(items.size() > 2 ? 2 : 0);
        }
        if (width(font, items) > available) {
            return;
        }
        int x = bar.x1() - CameraViewLayout.MARGIN;
        for (int i = items.size() - 1; i >= 0; i--) {
            Item item = items.get(i);
            x -= font.width(item.text());
            graphics.text(font, item.text(), x, y, item.color(), true);
            x -= GAP;
        }
    }

    private static int width(Font font, List<Item> items) {
        int total = 0;
        for (Item item : items) {
            total += font.width(item.text());
        }
        return total + GAP * Math.max(0, items.size() - 1);
    }

    private void recording(GuiGraphicsExtractor graphics, Font font, CameraViewLayout layout) {
        boolean on = PropwashClientApi.recording().recording();
        double seconds = this.clock.update(on, Util.getMillis());
        if (!on) {
            return;
        }
        CameraViewLayout.Box box = layout.recording();
        int dot = Math.max(3, layout.lineHeight() - 4);
        int top = box.y0() + (layout.lineHeight() - dot) / 2;
        graphics.fill(box.x0(), top, box.x0() + dot, top + dot, RECORD);
        graphics.text(font, "REC " + HudText.duration(seconds), box.x0() + dot + 3, box.y0(), TEXT, true);
    }

    private static void info(GuiGraphicsExtractor graphics, Font font, CameraViewLayout layout, FlightTelemetry t) {
        CameraViewLayout.Box info = layout.info();
        if (info.empty()) {
            return;
        }
        String first = "H " + HudText.hudMetres(t.homeHeight()) + "  D " + HudText.hudMetres(t.homeDistance());
        SpeedUnit unit = CamClientConfig.get().speedUnit();
        String second = "H.S. " + HudText.hudSpeed(CamOsdSource.horizontalSpeed(t), unit) + "  V.S. " + HudText.hudSpeed(t.verticalSpeed(), unit);
        int width = Math.max(font.width(first), font.width(second)) + 4;
        graphics.fill(info.x0() - 2, info.y0() - 1, Math.min(info.x1(), info.x0() - 2 + width), info.y1(), BACKDROP);
        graphics.text(font, first, info.x0(), info.y0(), TEXT, true);
        graphics.text(font, second, info.x0(), info.y0() + layout.lineHeight() + 2, TEXT, true);
    }

    private static void map(GuiGraphicsExtractor graphics, Font font, CameraViewLayout layout, FlightTelemetry t) {
        CameraViewLayout.Box map = layout.map();
        if (map.width() < CameraViewLayout.MIN_MAP / 2) {
            return;
        }
        box(graphics, map, MAP);
        frame(graphics, map, MAP_EDGE);
        int cx = map.centerX();
        int cy = map.centerY();
        double half = map.width() / 2.0 - 4.0;
        if (t.hasHome()) {
            double radius = MiniMap.radius(t.homeDistance());
            MiniMap.Point home = MiniMap.project(t.x(), t.z(), t.headingDeg(), t.homeX(), t.homeZ(), radius, half);
            int steps = (int) Math.floor(Math.hypot(home.x(), home.y()) / 3.0);
            for (int i = 1; i < steps; i++) {
                double f = i / (double) steps;
                int px = cx + (int) Math.round(home.x() * f);
                int py = cy + (int) Math.round(home.y() * f);
                graphics.fill(px, py, px + 1, py + 1, DIM);
            }
            int hx = cx + (int) Math.round(home.x());
            int hy = cy + (int) Math.round(home.y());
            graphics.text(font, "H", hx - font.width("H") / 2, hy - font.lineHeight / 2, CAUTION, true);
            if (map.width() >= 48) {
                graphics.text(font, HudText.number(radius) + "m", map.x0() + 2, map.y1() - font.lineHeight - 1, DIM, false);
            }
        }
        graphics.fill(cx, cy - 2, cx + 1, cy, TEXT);
        graphics.fill(cx - 1, cy, cx + 2, cy + 1, TEXT);
        graphics.fill(cx - 2, cy + 1, cx + 3, cy + 2, TEXT);
    }

    private static void tiltScale(GuiGraphicsExtractor graphics, Font font, CameraViewLayout layout, CamOsdState state) {
        CameraViewLayout.Box scale = layout.tiltScale();
        if (scale.height() < 3 * layout.lineHeight()) {
            return;
        }
        double min = FlightClass.GIMBAL_MIN_TILT;
        double max = state.gimbalMaxTilt();
        int mid = scale.x0() + scale.width() / 2;
        graphics.fill(mid, scale.y0(), mid + 1, scale.y1(), DIM);
        for (int tick = (int) Math.ceil(min / TICK_STEP) * TICK_STEP; tick <= max; tick += TICK_STEP) {
            int y = layout.tiltY(tick, min, max);
            graphics.fill(scale.x0() + 1, y, scale.x1(), y + 1, tick == 0 ? TEXT : DIM);
        }
        double tilt = state.gimbalTilt();
        if (!Double.isFinite(tilt)) {
            return;
        }
        int y = layout.tiltY(tilt, min, max);
        graphics.fill(scale.x0() - 1, y - 1, scale.x1() + 1, y + 2, TEXT);
        String label = Long.toString(Math.round(Math.clamp(tilt, min, max)));
        graphics.text(font, label, scale.x0() - 3 - font.width(label), y - font.lineHeight / 2, state.operator() ? ACCENT : TEXT, true);
    }

    private static void obstacleEdge(GuiGraphicsExtractor graphics, CameraViewLayout layout, CamOsdState state) {
        if (!CamOsd.obstacleShown(state)) {
            return;
        }
        double closeness = ObstacleBeeper.closeness(state.obstacleDistance());
        int alpha = 0x40 + (int) Math.round(0xA0 * closeness);
        int color = alpha << 24 | EDGE_RGB;
        int thick = 2 + (int) Math.round(6.0 * closeness);
        CameraViewLayout.Box image = layout.image();
        int top = layout.topBar().y1();
        switch (state.obstacleSensor().orElseThrow()) {
            case FORWARD -> graphics.fill(image.x0(), top, image.x1(), top + thick, color);
            case BACK -> graphics.fill(image.x0(), image.y1() - thick, image.x1(), image.y1(), color);
            case LEFT -> graphics.fill(image.x0(), top, image.x0() + thick, image.y1(), color);
            case RIGHT -> graphics.fill(image.x1() - thick, top, image.x1(), image.y1(), color);
            case UP, DOWN -> {
            }
        }
    }

    private static void warnings(GuiGraphicsExtractor graphics, Font font, CameraViewLayout layout, CamOsdState state) {
        OsdText warning = CamOsd.cycle(CamOsd.warnings(state), Util.getMillis());
        if (warning == null) {
            return;
        }
        int width = font.width(warning.text());
        int cx = layout.image().centerX();
        int y = layout.warningY();
        graphics.fill(cx - width / 2 - 3, y - 2, cx + (width + 1) / 2 + 3, y + layout.lineHeight() + 1, BAR);
        graphics.text(font, warning.text(), cx - width / 2, y, color(warning.tone()), true);
    }
}
