package de.lardtom.camdrones.dev.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.dev.SelfTest;
import de.lardtom.camdrones.dev.SelfTest.StepResult;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.platform.Services;
import de.lardtom.propwash.api.client.ClientInput;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.event.PropwashEvents;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CovCamBKit {
    public static final String PHOTO = "covcamb_";
    public static final String OUT = "covcamb";
    public static final int WIDE_WIDTH = 1920;
    public static final int WIDE_HEIGHT = 1080;
    public static final int WIDE_SCALE = 3;
    private static final List<SwitchEvent> SWITCH_EVENTS = new CopyOnWriteArrayList<>();
    private static boolean listening;

    public record SwitchEvent(UUID player, UUID drone, Identifier id, boolean active, long time) {
    }

    private CovCamBKit() {
    }

    public static synchronized void listen() {
        if (listening) {
            return;
        }
        listening = true;
        PropwashEvents.ADDON_SWITCH_CHANGED.register((player, drone, id, active) -> {
            if (CamDrones.MOD_ID.equals(id.getNamespace())) {
                SWITCH_EVENTS.add(new SwitchEvent(player.getUUID(), drone.getUUID(), id, active, System.nanoTime()));
            }
        });
    }

    public static int switchMark() {
        return SWITCH_EVENTS.size();
    }

    public static List<SwitchEvent> switchEvents(int mark) {
        List<SwitchEvent> all = new ArrayList<>(SWITCH_EVENTS);
        return mark >= all.size() ? List.of() : List.copyOf(all.subList(Math.max(0, mark), all.size()));
    }

    public static Path out() {
        return Services.PLATFORM.gameDir().resolve(OUT);
    }

    public static void write(String name, List<String> lines) {
        try {
            Files.createDirectories(out());
            Files.writeString(out().resolve(name), String.join(System.lineSeparator(), lines) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CamDrones.LOG.warn("[Kamera-Selbsttest] {} nicht geschrieben: {}", name, e.toString());
        }
    }

    public static Script wide() {
        return Script.create().then("Fenster " + WIDE_WIDTH + "x" + WIDE_HEIGHT, (ctx, ticks) -> Shots.window(ctx, ticks, WIDE_WIDTH, WIDE_HEIGHT,
                WIDE_SCALE));
    }

    public static Script goTo(int dx, int dz) {
        return Script.create()
                .server("hin zum Platz " + dx + "/" + dz, (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    Vec3 stand = Vec3.atBottomCenterOf(Arena.ground(dx, dz).above()).add(0.0, 0.0, -3.0);
                    player.teleportTo(player.level(), stand.x, stand.y, stand.z, Set.<Relative>of(), 0.0F, 10.0F, true);
                })
                .sleep(40);
    }

    public static Script gui(int width, int height) {
        return Script.create().then("GUI " + width + "x" + height, (ctx, ticks) -> Shots.window(ctx, ticks, width * 2, height * 2, 2));
    }

    public static Map<CamControl, String> controls() {
        ClientInput input = PropwashClientApi.input();
        Map<CamControl, String> out = new LinkedHashMap<>();
        for (CamControl control : CamControl.values()) {
            if (control.axis()) {
                out.put(control, String.format(Locale.ROOT, "%.1f", input.axis(control.id())));
            } else {
                out.put(control, input.switchActive(control.id()) ? "an" : "aus");
            }
        }
        return out;
    }

    public static List<CamControl> changed(Map<CamControl, String> before, Map<CamControl, String> after) {
        List<CamControl> out = new ArrayList<>();
        for (CamControl control : CamControl.values()) {
            if (!before.get(control).equals(after.get(control))) {
                out.add(control);
            }
        }
        return out;
    }

    public static Set<String> propwashDown() {
        Set<String> out = new TreeSet<>();
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if ("propwash".equals(mapping.getCategory().id().getNamespace()) && mapping.isDown()) {
                out.add(mapping.getName());
            }
        }
        return out;
    }

    public static List<KeyMapping> boundTo(InputConstants.Key key) {
        List<KeyMapping> out = new ArrayList<>();
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (!mapping.isUnbound() && mapping.saveString().equals(key.getName())) {
                out.add(mapping);
            }
        }
        return out;
    }

    public static InputConstants.Key key(int code) {
        return InputConstants.Type.KEYBOARD.getOrCreate(code);
    }

    public static void down(InputConstants.Key key, boolean down) {
        if (down) {
            KeyMapping.click(key);
        }
        KeyMapping.set(key, down);
    }

    public static StepResult press(SelfTest.StepContext ctx, int ticks, InputConstants.Key key, int hold) {
        if (ticks == 0) {
            down(key, true);
        } else if (ticks == hold) {
            down(key, false);
        }
        return ticks >= hold + 4 ? StepResult.pass() : StepResult.pending();
    }

    public static ServerPlayer guest(MinecraftServer server, ServerLevel level, Vec3 position) {
        ServerPlayer existing = server.getPlayerList().getPlayer(JoinEnv.GUEST_ID);
        if (existing != null) {
            existing.teleportTo(level, position.x, position.y, position.z, Set.<Relative>of(), 0.0F, 0.0F, true);
            return existing;
        }
        GameProfile profile = new GameProfile(JoinEnv.GUEST_ID, JoinEnv.GUEST_NAME);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer guest = new ServerPlayer(server, level, profile, ClientInformation.createDefault()) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, guest, cookie);
        guest.teleportTo(level, position.x, position.y, position.z, Set.<Relative>of(), 0.0F, 0.0F, true);
        guest.snapTo(position.x, position.y, position.z, 0.0F, 0.0F);
        return guest;
    }

    public static void leave(MinecraftServer server) {
        @Nullable ServerPlayer guest = server.getPlayerList().getPlayer(JoinEnv.GUEST_ID);
        if (guest != null) {
            server.getPlayerList().remove(guest);
        }
    }

    public static double angle(Vec3 a, Vec3 b) {
        double la = a.length();
        double lb = b.length();
        if (la < 1.0E-9 || lb < 1.0E-9) {
            return Double.NaN;
        }
        return Math.toDegrees(Math.acos(Math.clamp(a.dot(b) / (la * lb), -1.0, 1.0)));
    }

    public static Vec3 direction(double headingDeg, double tiltDeg) {
        double h = Math.toRadians(headingDeg);
        double t = Math.toRadians(tiltDeg);
        return new Vec3(Math.sin(h) * Math.cos(t), Math.sin(t), -Math.cos(h) * Math.cos(t));
    }

    public static String f(double value) {
        return Flight.format(value);
    }
}
