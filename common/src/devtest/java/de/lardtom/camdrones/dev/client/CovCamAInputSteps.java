package de.lardtom.camdrones.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.GimbalControl;
import de.lardtom.camdrones.client.input.CamKeys;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.CamSwitches;
import de.lardtom.camdrones.logic.nav.NavLogic;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLTimer;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.system.MemoryStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class CovCamAInputSteps {
    public static final String WHEEL = "tasten: Mausrad bleibt Gas";
    public static final String FPV_MODE = "gimbal: Modus FPV rollt mit dem Rahmen";
    public static final String DEVICE_KEY = "propwash.input.device";
    public static final String MOUSE_KEY = "propwash.input.mouse_keyboard";
    public static final double THROTTLE_CHANGE = 0.02;
    public static final double TILT_STILL = 0.5;
    public static final double FPV_ROLL_TOLERANCE = 5.0;
    private static final String THROTTLE = "covcama.throttle";
    private static final String TILT = "covcama.tilt";
    private static final String AXIS = "covcama.axis";
    private static final String NOTE = "covcama.note";
    private static final String STAGE = "covcama.deviceStage";
    private static final String SINCE = "covcama.deviceSince";
    private static final String MAX_ROLL = "covcama.maxRoll";
    private static final String CAMERA_ROLL = "covcama.cameraRoll";
    private static final String FPV_OFF_SINCE = "covcama.fpvOffSince";
    private static final String FPV_TEST_KEY = "key.keyboard.scroll.lock";
    private static final int STEP_TICKS = 20 * 180;
    private static @Nullable String savedDevice;
    private static @Nullable String savedFpvKey;

    private CovCamAInputSteps() {
    }

    static void wheel() {
        SelfTest.clientStep(330, WHEEL, STEP_TICKS, Script.create()
                .then("Tab Empfänger öffnen", (ctx, ticks) -> OsdSteps.openTab(ctx, ticks, KeyboardDeflection.TAB))
                .then("Gerät „Maus + Tastatur“", (ctx, ticks) -> device(ctx, ticks, true))
                .then("speichern", CovCamAInputSteps::save)
                .append(Pilot.launch(CamPresets.TOUR, 60, -100, 0.0F))
                .append(Flight.takeoff(4.0))
                .then("Profil Maus aktiv", (ctx, ticks) -> mouseProfile())
                .then("Mausrad hoch", (ctx, ticks) -> scroll(ctx, ticks, 1.0F))
                .then("Mausrad runter", (ctx, ticks) -> scroll(ctx, ticks, -1.0F))
                .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.valueOf(ctx.state().get(NOTE)))), CovCamAInputSteps::restoreDevice);
    }

    static void fpvMode() {
        SelfTest.clientStep(331, FPV_MODE, STEP_TICKS, Script.create()
                .append(KeyboardDeflection.set(KeyboardDeflection.MAX_PERCENT))
                .append(Shots.photoWindow())
                .run("Taste für Gimbal FPV belegen", () -> bindFpv(true))
                .append(Pilot.launch(CamPresets.TOUR, -40, 52, 0.0F))
                .append(PilotViewSteps.gimbalImage())
                .append(Flight.takeoff(8.0))
                .then("Schalter Gimbal FPV an", (ctx, ticks) -> fpvSwitch(ctx, ticks, true))
                .append(Flight.mode(NavLogic.StickMode.CINE))
                .append(Flight.mode(NavLogic.StickMode.SPORT))
                .then("nach rechts rollen", CovCamAInputSteps::rollFpv)
                .append(Shots.photos(CovCamAKit.PHOTO + "gimbal_fpv_roll"))
                .then("loslassen", (ctx, ticks) -> {
                    Pilot.hold(GimbalSteps.ROLL_RIGHT, false);
                    return ticks >= 10 ? StepResult.pass() : StepResult.pending();
                })
                .append(Flight.mode(NavLogic.StickMode.NORMAL))
                .then("bremsen", Flight::brake)
                .then("Schalter Gimbal FPV aus", (ctx, ticks) -> fpvSwitch(ctx, ticks, false)), CovCamAInputSteps::fpvCleanup);
    }

    private static StepResult fpvCleanup(SelfTest.StepContext ctx) {
        Optional<Entity> drone = Pilot.drone();
        if (savedFpvKey != null && drone.isPresent() && CamSwitches.active(drone.get(), CamControl.GIMBAL_FPV)) {
            int since = (int) ctx.state().computeIfAbsent(FPV_OFF_SINCE, key -> ctx.tick());
            int ticks = ctx.tick() - since;
            if (ticks % 40 == 0) {
                KeyMapping.click(InputConstants.getKey(FPV_TEST_KEY));
            }
            if (ticks <= 130) {
                return StepResult.pending();
            }
        }
        bindFpv(false);
        return KeyboardDeflection.restore(ctx);
    }

    private static void bindFpv(boolean on) {
        KeyMapping key = CamKeys.key(CamControl.GIMBAL_FPV).orElseThrow(() -> new IllegalStateException("Taste für Gimbal FPV fehlt"));
        if (on && savedFpvKey == null) {
            savedFpvKey = key.saveString();
            key.setKey(InputConstants.getKey(FPV_TEST_KEY));
            KeyMapping.resetMapping();
        } else if (!on && savedFpvKey != null) {
            key.setKey(InputConstants.getKey(savedFpvKey));
            savedFpvKey = null;
            KeyMapping.resetMapping();
        }
    }

    private static StepResult fpvSwitch(SelfTest.StepContext ctx, int ticks, boolean on) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        boolean active = CamSwitches.active(drone.get(), CamControl.GIMBAL_FPV);
        if (ticks % 40 == 0 && active != on) {
            KeyMapping.click(InputConstants.getKey(FPV_TEST_KEY));
        }
        GimbalControl control = GimbalClient.control();
        boolean pilot = control.role() == GimbalControl.Role.PILOT && control.droneId() == drone.get().getId();
        GimbalSetting.Mode mode = control.setting().mode();
        if (active == on && pilot && mode == (on ? GimbalSetting.Mode.FPV : GimbalSetting.Mode.FOLLOW) && ticks >= 4) {
            return StepResult.pass("Schalter Gimbal FPV " + (on ? "an" : "aus") + ", Gimbal-Modus " + mode);
        }
        return ticks > 130 ? StepResult.fail("Schalter Gimbal FPV " + (active ? "an" : "aus") + ", Gimbal-Modus " + mode + (pilot ? "" : ", Pilot ohne Gimbal")
                + " statt " + (on ? "FPV" : "FOLLOW")) : StepResult.pending();
    }

    private static Optional<AbstractWidget> deviceWidget(Screen screen) {
        String label = I18n.get(DEVICE_KEY);
        for (AbstractWidget widget : KeyboardDeflection.widgets(screen)) {
            if (widget.visible && widget.active && widget.getMessage().getString().startsWith(label)) {
                return Optional.of(widget);
            }
        }
        return Optional.empty();
    }

    private static StepResult device(SelfTest.StepContext ctx, int ticks, boolean mouse) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) {
            return StepResult.fail("Tab Empfänger geschlossen");
        }
        Optional<AbstractWidget> widget = deviceWidget(screen);
        if (widget.isEmpty()) {
            return ticks > 60 ? StepResult.fail("Auswahl „" + I18n.get(DEVICE_KEY) + "“ fehlt, Knöpfe " + CovCamAKit.labels(screen)) : StepResult.pending();
        }
        String now = widget.get().getMessage().getString();
        if (mouse && savedDevice == null) {
            savedDevice = now;
        }
        boolean done = mouse ? now.contains(I18n.get(MOUSE_KEY)) || now.contains(I18n.get(MOUSE_KEY + ".short")) : now.equals(savedDevice);
        if (done) {
            return ticks >= 4 ? StepResult.pass(now) : StepResult.pending();
        }
        if (ticks % 4 == 0) {
            AbstractWidget w = widget.get();
            net.minecraft.client.input.MouseButtonEvent event = new net.minecraft.client.input.MouseButtonEvent(w.getX() + w.getWidth() / 2.0,
                    w.getY() + w.getHeight() / 2.0, new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0));
            w.mouseClicked(event, false);
            w.mouseReleased(event);
            ctx.log("Gerät: " + now);
        }
        return ticks > 200 ? StepResult.fail("Gerät bleibt bei „" + now + "“") : StepResult.pending();
    }

    private static StepResult save(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.pass();
        }
        if (ticks == 0) {
            for (String key : List.of(KeyboardDeflection.SAVE_KEY, KeyboardDeflection.CLOSE_KEY)) {
                Optional<AbstractWidget> widget = CovCamAKit.widget(screen, I18n.get(key));
                if (widget.isPresent() && widget.get().active) {
                    CovCamAKit.click(screen, widget.get());
                    return StepResult.pending();
                }
            }
            mc.gui.setScreen(null);
            return StepResult.pending();
        }
        return ticks > 60 ? StepResult.fail("Einstellungen schließen nicht nach dem Speichern") : StepResult.pending();
    }

    private static String inputFile() {
        Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("propwash").resolve("input.json");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static StepResult mouseProfile() {
        String saved = inputFile();
        return saved.replace(" ", "").contains("\"selectedGuid\":\"mouse\"") ? StepResult.pass("Propwash-Profil Maus + Tastatur gespeichert")
                : StepResult.fail("Profil Maus nicht gespeichert");
    }

    private static void push(float notches) {
        Minecraft mc = Minecraft.getInstance();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SDL_Event event = SDL_Event.calloc(stack);
            event.type(SDLEvents.SDL_EVENT_MOUSE_WHEEL);
            event.wheel().type(SDLEvents.SDL_EVENT_MOUSE_WHEEL).timestamp(SDLTimer.SDL_GetTicksNS())
                    .windowID(SDLVideo.SDL_GetWindowID(mc.getWindow().handle())).x(0.0F).y(notches).direction(SDLMouse.SDL_MOUSEWHEEL_NORMAL);
            if (!SDLEvents.SDL_PushEvent(event)) {
                throw new IllegalStateException("SDL_PushEvent abgelehnt");
            }
        }
    }

    private static StepResult scroll(SelfTest.StepContext ctx, int ticks, float notches) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        if (ticks == 0) {
            ctx.state().put(THROTTLE, PropwashClientApi.input().throttle());
            ctx.state().put(TILT, GimbalClient.tilt(drone.get()));
            ctx.state().put(AXIS, PropwashClientApi.input().axis(CamControl.GIMBAL_TILT.id()));
        }
        if (ticks == 2 || ticks == 4 || ticks == 6) {
            push(notches);
        }
        if (ticks < 26) {
            return StepResult.pending();
        }
        double throttleBefore = (double) ctx.state().get(THROTTLE);
        double throttle = PropwashClientApi.input().throttle();
        double tiltBefore = (double) ctx.state().get(TILT);
        double tilt = GimbalClient.tilt(drone.get());
        double axisBefore = (double) ctx.state().get(AXIS);
        double axis = PropwashClientApi.input().axis(CamControl.GIMBAL_TILT.id());
        double change = (throttle - throttleBefore) * Math.signum(notches);
        String line = "3 Rasten " + (notches > 0 ? "hoch" : "runter") + ": Gas " + Flight.format(throttleBefore) + " → " + Flight.format(throttle)
                + ", Gimbal-Neigung " + Flight.format(tiltBefore) + "° → " + Flight.format(tilt) + "°, Achse camdrones:gimbal_tilt "
                + Flight.format(axisBefore) + " → " + Flight.format(axis);
        String previous = (String) ctx.state().getOrDefault(NOTE, "");
        ctx.state().put(NOTE, previous.isEmpty() ? line : previous + "; " + line);
        if (Math.abs(tilt - tiltBefore) > TILT_STILL || Math.abs(axis - axisBefore) > 0.01) {
            return StepResult.fail("Mausrad bewegt den Gimbal: " + line);
        }
        if (change < THROTTLE_CHANGE) {
            return StepResult.fail("Mausrad ändert das Gas nicht: " + line);
        }
        return StepResult.pass();
    }

    private static StepResult restoreDevice(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (savedDevice == null) {
            return StepResult.pass();
        }
        if (PropwashClientApi.input().flying()) {
            Pilot.releaseKeys();
            if (ctx.tick() % 30 == 0) {
                ctx.onServer(Pilot::release);
            }
            return ctx.tick() > 400 ? StepResult.fail("Flug endet nicht vor dem Zurückstellen") : StepResult.pending();
        }
        int stage = (int) ctx.state().getOrDefault(STAGE, 0);
        int since = (int) ctx.state().computeIfAbsent(SINCE, key -> ctx.tick());
        int ticks = ctx.tick() - since;
        StepResult result = switch (stage) {
            case 0 -> OsdSteps.openTab(ctx, ticks, KeyboardDeflection.TAB);
            case 1 -> device(ctx, ticks, false);
            case 2 -> save(ctx, ticks);
            default -> StepResult.pass();
        };
        if (!result.done()) {
            return result;
        }
        if (result.failed() || stage >= 2) {
            if (mc.gui.screen() != null) {
                mc.gui.setScreen(null);
            }
            if (!result.failed()) {
                savedDevice = null;
            }
            return result;
        }
        ctx.state().put(STAGE, stage + 1);
        ctx.state().put(SINCE, ctx.tick() + 1);
        return StepResult.pending();
    }

    private static StepResult rollFpv(SelfTest.StepContext ctx, int ticks) {
        Flight.track(ctx);
        Optional<Entity> drone = Pilot.drone();
        if (drone.isEmpty()) {
            return StepResult.fail("Drohne verloren");
        }
        Pilot.hold(GimbalSteps.ROLL_RIGHT, true);
        double frame = GimbalSteps.frameTilt(drone.get());
        double camera = GimbalSteps.cameraHorizon();
        if (frame > (double) ctx.state().getOrDefault(MAX_ROLL, 0.0)) {
            ctx.state().put(MAX_ROLL, frame);
            ctx.state().put(CAMERA_ROLL, camera);
        }
        if (ticks < 30) {
            return StepResult.pending();
        }
        double maxRoll = (double) ctx.state().getOrDefault(MAX_ROLL, 0.0);
        double cameraRoll = (double) ctx.state().getOrDefault(CAMERA_ROLL, 0.0);
        if (maxRoll < 20.0) {
            return StepResult.fail("Rahmen rollt nur " + Flight.format(maxRoll) + "°");
        }
        if (Math.abs(cameraRoll - maxRoll) > FPV_ROLL_TOLERANCE) {
            return StepResult.fail("Modus FPV: Bild rollt " + Flight.format(cameraRoll) + "° bei " + Flight.format(maxRoll) + "° Rahmen-Rollen");
        }
        return StepResult.pass("Modus FPV: Bild rollt " + Flight.format(cameraRoll) + "° mit dem Rahmen (" + Flight.format(maxRoll) + "°), Neigung "
                + Flight.format(GimbalClient.tilt(drone.get())) + "°");
    }
}
