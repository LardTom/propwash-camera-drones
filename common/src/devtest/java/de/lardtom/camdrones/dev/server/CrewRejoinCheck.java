package de.lardtom.camdrones.dev.server;

import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.CrewService;
import de.lardtom.camdrones.crew.CrewTexts;
import de.lardtom.camdrones.crew.model.CrewBook;
import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class CrewRejoinCheck extends ServerTest.Scenario {
    static final String NAME = JoinEnv.CREW_REJOIN;
    static final int SETUP_TICKS = 60;
    static final int WAIT_TICKS = 20 * 90;
    static final int CHECK_LIMIT = CrewService.CHECK_TICKS + 2;
    static final int RESTORE_LIMIT = 20 * 5;
    static final long HALF_MILLIS = CrewBook.GRACE_MILLIS / 2L;
    static final long EARLY_MILLIS = 1_000L;
    static final long LATE_MILLIS = 4_000L;
    static final int GUEST_AWAY_TICKS = 20 * 5;
    static final int COUNTDOWN_MIN = 3;
    private int stage;
    private int joinedAt = -1;
    private int mark;
    private int backAt = -1;
    private @Nullable UUID clientId;
    private @Nullable ServerPlayer guest;
    private @Nullable Entity droneA;
    private @Nullable Entity droneB;
    private @Nullable Entity droneC;
    private long guestAwayAt = -1L;
    private int guestMark;
    private long guestLeftAt = -1L;
    private boolean halfSeen;
    private boolean aBound;
    private boolean aKept;
    private boolean aRestored;
    private boolean aLeft;
    private boolean bBound;
    private boolean bKept;
    private boolean bHalf;
    private boolean bEnded;
    private boolean cBound;
    private boolean cBlocked;
    private boolean cEnded;

    @Override
    void tick(MinecraftServer server, int tick) {
        ServerPlayer client = ServerSetup.client(server);
        boolean away = this.stage == 20 || this.stage == 30 || this.stage == 40;
        if (client == null && this.stage > 0 && this.stage < 99 && !away) {
            this.fail(server, "Client trennt in Stufe " + this.stage);
            return;
        }
        int waited = tick - this.mark;
        switch (this.stage) {
            case 0 -> {
                if (client == null) {
                    return;
                }
                if (this.joinedAt < 0) {
                    this.joinedAt = tick;
                }
                if (tick - this.joinedAt >= SETUP_TICKS) {
                    this.setupA(server, client);
                    this.next(tick, 10);
                }
            }
            case 10 -> {
                Entity drone = this.droneA;
                if (this.boundTo(drone, this.clientId) && PropwashApi.drones().operators().operator(drone).filter(client::equals).isPresent()) {
                    this.aBound = true;
                    ServerTest.line(NAME + " a: " + client.getPlainTextName() + " ist Operator der Drohne von " + JoinEnv.GUEST_NAME
                            + ", Propwash-Operator " + client.getPlainTextName());
                    this.next(tick, 20);
                } else if (waited > WAIT_TICKS) {
                    this.fail(server, "a: Client wird nicht Operator");
                }
            }
            case 20 -> {
                if (client == null) {
                    this.next(tick, 30);
                } else if (waited > WAIT_TICKS) {
                    this.fail(server, "a: Client trennt nicht");
                }
            }
            case 30 -> {
                if (client != null) {
                    this.fail(server, "a: Client schon nach " + waited + " Ticks zurück");
                    return;
                }
                if (waited < CHECK_LIMIT) {
                    return;
                }
                Entity drone = this.droneA;
                boolean crew = this.boundTo(drone, this.clientId);
                boolean gimbal = CrewData.get(drone).operatedBy(this.clientId);
                Optional<CrewBook.Wait> wait = CrewService.book().waiting(drone.getUUID());
                long left = wait.map(w -> w.remaining(Util.getMillis())).orElse(-1L);
                ServerTest.line(NAME + " a: Operator getrennt, nach " + waited + " Ticks besteht die Crew: " + crew + ", Gimbal beim Operator: "
                        + gimbal + ", wartet noch " + (left < 0 ? "nicht" : ServerSetup.format(left / 1000.0) + " s"));
                if (!crew || !gimbal) {
                    this.fail(server, "a: Crew endet beim Trennen des Operators sofort (Crew " + crew + ", Gimbal " + gimbal + ")");
                    return;
                }
                if (left < CrewBook.GRACE_MILLIS - 5_000L || left > CrewBook.GRACE_MILLIS) {
                    this.fail(server, "a: keine Frist von 2:00 nach dem Trennen (Rest " + left + " ms)");
                    return;
                }
                this.aKept = true;
                this.next(tick, 40);
            }
            case 40 -> this.awaitReturn(server, client, tick, waited);
            case 50 -> {
                Entity drone = this.droneA;
                if (CrewService.book().operator(drone.getUUID()).isEmpty()) {
                    this.aLeft = !CrewData.get(drone).operated() && PropwashApi.drones().operators().operator(drone).isEmpty();
                    ServerTest.line(NAME + " a: " + client.getPlainTextName() + " gibt die Kamera nach dem Wiederbeitritt per Befehl ab, Gimbal frei: "
                            + this.aLeft);
                    drone.discard();
                    this.setupC(client);
                    this.next(tick, 80);
                } else if (waited > WAIT_TICKS) {
                    this.fail(server, "a: Client gibt die Kamera nach dem Wiederbeitritt nicht ab");
                }
            }
            case 60 -> {
                Entity drone = this.droneB;
                if (this.boundTo(drone, JoinEnv.GUEST_ID) && PropwashApi.drones().operators().operator(drone).isPresent()) {
                    this.bBound = true;
                    ServerTest.line(NAME + " b: " + JoinEnv.GUEST_NAME + " ist Operator der Drohne von " + client.getPlainTextName()
                            + " und verlässt das Spiel");
                    ServerSetup.leave(server, this.guest);
                    this.guestLeftAt = Util.getMillis();
                    this.next(tick, 70);
                } else if (waited > WAIT_TICKS) {
                    this.fail(server, "b: " + JoinEnv.GUEST_NAME + " wird nicht Operator");
                }
            }
            case 70 -> this.awaitExpiry(server, waited);
            case 80 -> {
                Entity drone = this.droneC;
                if (this.boundTo(drone, JoinEnv.GUEST_ID) && PropwashApi.drones().operators().operator(drone).isPresent()) {
                    this.cBound = true;
                    ServerTest.line(NAME + " c: " + JoinEnv.GUEST_NAME + " ist Operator der Drohne von " + client.getPlainTextName()
                            + " und verlässt das Spiel");
                    ServerSetup.leave(server, this.guest);
                    this.guestAwayAt = Util.getMillis();
                    this.next(tick, 90);
                } else if (waited > WAIT_TICKS) {
                    this.fail(server, "c: " + JoinEnv.GUEST_NAME + " wird nicht Operator");
                }
            }
            case 90 -> {
                if (waited < GUEST_AWAY_TICKS) {
                    return;
                }
                ServerLevel nether = server.getLevel(Level.NETHER);
                if (nether == null) {
                    this.fail(server, "c: kein Nether am Server");
                    return;
                }
                this.guestMark = ServerSetup.GUEST_MESSAGES.size();
                this.guest = ServerSetup.guest(server, nether, new Vec3(this.droneC.getX(), 70.0, this.droneC.getZ()));
                ServerTest.line(NAME + " c: " + JoinEnv.GUEST_NAME + " kommt nach " + ServerSetup.format(waited / 20.0)
                        + " s im Nether zurück, Propwash muss die Bindung ablehnen (andere Dimension)");
                this.next(tick, 100);
            }
            case 100 -> this.awaitRefusal(server, tick, waited);
            case 110 -> this.awaitUnreachable(server, client, tick);
            default -> {
            }
        }
    }

    private void awaitRefusal(MinecraftServer server, int tick, int waited) {
        Entity drone = this.droneC;
        if (!this.boundTo(drone, JoinEnv.GUEST_ID)) {
            this.fail(server, "c: Crew endet bei der Rückkehr in einer anderen Dimension (nach " + waited + " Ticks)");
            return;
        }
        List<Component> notices = this.guestTexts(CrewTexts.BLOCKED_OPERATOR, false);
        boolean propwash = PropwashApi.drones().operators().operator(drone).isPresent();
        Optional<CrewBook.Wait> wait = CrewService.book().waiting(drone.getUUID());
        if (notices.isEmpty()) {
            if (waited > RESTORE_LIMIT) {
                this.fail(server, "c: kein Hinweis an den Operator nach der Rückkehr (Propwash-Operator " + propwash + ", wartet " + wait.isPresent()
                        + ", Nachrichten an " + JoinEnv.GUEST_NAME + ": " + this.guestKeys() + ")");
            }
            return;
        }
        long left = wait.map(w -> w.remaining(Util.getMillis())).orElse(-1L);
        this.cBlocked = !propwash && left > 0L && CrewData.get(drone).operatedBy(JoinEnv.GUEST_ID);
        ServerTest.line(NAME + " c: Propwash lehnt die Bindung ab, " + JoinEnv.GUEST_NAME + " liest nach " + waited + " Ticks den Hinweis "
                + notices.getFirst().getString() + " " + args(notices.getFirst()) + ", Crew wartet noch " + ServerSetup.format(left / 1000.0)
                + " s, Propwash-Operator: " + propwash);
        if (!this.cBlocked) {
            this.fail(server, "c: nach der abgelehnten Neubindung wartet die Crew nicht weiter (Propwash-Operator " + propwash + ", Rest " + left + " ms)");
            return;
        }
        this.next(tick, 110);
    }

    private void awaitUnreachable(MinecraftServer server, ServerPlayer client, int tick) {
        Entity drone = this.droneC;
        long since = Util.getMillis() - this.guestAwayAt;
        if (this.boundTo(drone, JoinEnv.GUEST_ID)) {
            if (since > CrewBook.GRACE_MILLIS + LATE_MILLIS) {
                this.fail(server, "c: Crew besteht nach " + ServerSetup.format(since / 1000.0) + " s noch");
            }
            return;
        }
        boolean free = !CrewData.get(drone).operated() && PropwashApi.drones().operators().operator(drone).isEmpty()
                && CrewService.book().waiting(drone.getUUID()).isEmpty();
        int notices = this.guestTexts(CrewTexts.BLOCKED_OPERATOR, false).size();
        List<String> clocks = new ArrayList<>();
        for (Component overlay : this.guestTexts(CrewTexts.BLOCKED_OPERATOR_OVERLAY, true)) {
            String clock = args(overlay);
            if (!clocks.contains(clock)) {
                clocks.add(clock);
            }
        }
        List<Component> ended = this.guestTexts(CrewTexts.UNREACHABLE_OPERATOR, false);
        boolean onTime = since >= CrewBook.GRACE_MILLIS - EARLY_MILLIS && since <= CrewBook.GRACE_MILLIS + LATE_MILLIS;
        this.cEnded = free && onTime && notices == 1 && clocks.size() >= COUNTDOWN_MIN && ended.size() == 1;
        ServerTest.line(NAME + " c: Crew nach " + ServerSetup.format(since / 1000.0) + " s beendet, Gimbal frei: " + free + ", Hinweis " + notices
                + "-mal, Restzeit in der Aktionsleiste " + clocks.size() + "-mal (" + String.join(" | ", clocks.subList(0, Math.min(3, clocks.size())))
                + " …), Ende an " + JoinEnv.GUEST_NAME + ": " + ended.stream().map(Component::getString).toList());
        if (!this.cEnded) {
            this.fail(server, "c: Ende nach abgelehnter Neubindung falsch (frei " + free + ", nach " + ServerSetup.format(since / 1000.0)
                    + " s, Hinweis " + notices + "-mal, Restzeit " + clocks.size() + "-mal, Ende " + ended.size() + "-mal, Nachrichten "
                    + this.guestKeys() + ")");
            return;
        }
        drone.discard();
        ServerSetup.leave(server, this.guest);
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        this.guest = ServerSetup.guest(server, level, Vec3.atBottomCenterOf(ServerSetup.ground(level, arena.getX() + 3, arena.getZ() + 3).above()));
        this.setupB(client);
        this.next(tick, 60);
    }

    private List<Component> guestTexts(String key, boolean overlay) {
        List<ServerSetup.GuestMessage> all = ServerSetup.GUEST_MESSAGES;
        List<Component> out = new ArrayList<>();
        for (ServerSetup.GuestMessage message : all.subList(Math.min(this.guestMark, all.size()), all.size())) {
            if (message.overlay() == overlay && message.text().getContents() instanceof TranslatableContents contents && contents.getKey().equals(key)) {
                out.add(message.text());
            }
        }
        return out;
    }

    private List<String> guestKeys() {
        List<ServerSetup.GuestMessage> all = ServerSetup.GUEST_MESSAGES;
        List<String> keys = new ArrayList<>();
        for (ServerSetup.GuestMessage message : all.subList(Math.min(this.guestMark, all.size()), all.size())) {
            String key = message.text().getContents() instanceof TranslatableContents contents ? contents.getKey() : message.text().getString();
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static String args(Component text) {
        if (!(text.getContents() instanceof TranslatableContents contents)) {
            return "";
        }
        List<String> out = new ArrayList<>();
        for (Object arg : contents.getArgs()) {
            out.add(arg instanceof Component component ? component.getString() : String.valueOf(arg));
        }
        return String.join(", ", out);
    }

    private void setupC(ServerPlayer client) {
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        BlockPos ground = ServerSetup.ground(level, arena.getX() - 6, arena.getZ() + 4);
        this.droneC = ServerSetup.spawn(client, CamPresets.TOUR, ground);
        ServerSetup.pair(this.droneC, client.getUUID());
        this.guest.snapTo(arena.getX() - 3.5, client.getY(), arena.getZ() + 3.5, 0.0F, 0.0F);
        CrewBook.Verdict verdict = CrewService.invite(client, this.droneC, this.guest);
        ServerTest.line(NAME + " c: " + client.getPlainTextName() + " (Besitzer) lädt " + JoinEnv.GUEST_NAME + " als Operator ein: " + verdict);
        if (!verdict.ok()) {
            ServerTest.fail(NAME + " c: Einladung abgelehnt: " + verdict);
            return;
        }
        ServerSetup.command(this.guest, CrewService.ACCEPT_COMMAND);
    }

    private void awaitExpiry(MinecraftServer server, int waited) {
        Entity drone = this.droneB;
        long since = Util.getMillis() - this.guestLeftAt;
        boolean crew = this.boundTo(drone, JoinEnv.GUEST_ID);
        if (waited == CHECK_LIMIT) {
            Optional<CrewBook.Wait> wait = CrewService.book().waiting(drone.getUUID());
            this.bKept = crew && wait.isPresent() && CrewData.get(drone).operatedBy(JoinEnv.GUEST_ID);
            ServerTest.line(NAME + " b: " + JoinEnv.GUEST_NAME + " getrennt, Crew wartet: " + this.bKept + ", Rest "
                    + wait.map(w -> ServerSetup.format(w.remaining(Util.getMillis()) / 1000.0) + " s").orElse("keiner"));
            if (!this.bKept) {
                this.fail(server, "b: Crew wartet nach dem Trennen nicht");
                return;
            }
        }
        if (!this.halfSeen && since >= HALF_MILLIS) {
            this.halfSeen = true;
            this.bHalf = crew;
            ServerTest.line(NAME + " b: nach " + ServerSetup.format(since / 1000.0) + " s wartet die Crew noch: " + crew);
            if (!crew) {
                this.fail(server, "b: Crew endet vor Ablauf der Frist");
                return;
            }
        }
        if (!crew) {
            boolean free = !CrewData.get(drone).operated() && PropwashApi.drones().operators().operator(drone).isEmpty()
                    && CrewService.book().waiting(drone.getUUID()).isEmpty();
            this.bEnded = free && since >= CrewBook.GRACE_MILLIS - EARLY_MILLIS && since <= CrewBook.GRACE_MILLIS + LATE_MILLIS;
            ServerTest.line(NAME + " b: Crew nach " + ServerSetup.format(since / 1000.0) + " s beendet, Gimbal frei: " + free);
            if (!this.bEnded) {
                this.fail(server, "b: Crew endet nicht nach 2:00 (nach " + ServerSetup.format(since / 1000.0) + " s, frei " + free + ")");
                return;
            }
            drone.discard();
            ServerTest.line(NAME + ": fertig");
            this.cleanup(server);
            this.stage = 99;
        } else if (since > CrewBook.GRACE_MILLIS + LATE_MILLIS) {
            this.fail(server, "b: Crew besteht nach " + ServerSetup.format(since / 1000.0) + " s noch");
        }
    }

    private void setupB(ServerPlayer client) {
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        BlockPos ground = ServerSetup.ground(level, arena.getX() + 8, arena.getZ() + 4);
        this.droneB = ServerSetup.spawn(client, CamPresets.TOUR, ground);
        ServerSetup.pair(this.droneB, client.getUUID());
        this.guest.snapTo(arena.getX() + 5.5, client.getY(), arena.getZ() + 3.5, 0.0F, 0.0F);
        CrewBook.Verdict verdict = CrewService.invite(client, this.droneB, this.guest);
        ServerTest.line(NAME + " b: " + client.getPlainTextName() + " (Besitzer) lädt " + JoinEnv.GUEST_NAME + " als Operator ein: " + verdict);
        if (!verdict.ok()) {
            ServerTest.fail(NAME + " b: Einladung abgelehnt: " + verdict);
            return;
        }
        ServerSetup.command(this.guest, CrewService.ACCEPT_COMMAND);
    }

    private void awaitReturn(MinecraftServer server, @Nullable ServerPlayer client, int tick, int waited) {
        Entity drone = this.droneA;
        if (!this.boundTo(drone, this.clientId)) {
            this.fail(server, "a: Crew endet, während der Operator weg ist (nach " + waited + " Ticks)");
            return;
        }
        if (client == null) {
            if (waited > WAIT_TICKS) {
                this.fail(server, "a: Client kommt nicht zurück");
            }
            return;
        }
        if (this.backAt < 0) {
            this.backAt = tick;
            ServerTest.line(NAME + " a: " + client.getPlainTextName() + " ist nach " + ServerSetup.format(waited / 20.0) + " s wieder da");
        }
        boolean propwash = PropwashApi.drones().operators().operator(drone).filter(client::equals).isPresent();
        boolean gimbal = CrewData.get(drone).operatedBy(client.getUUID());
        boolean waiting = CrewService.book().waiting(drone.getUUID()).isPresent();
        if (propwash && gimbal && !waiting) {
            this.aRestored = true;
            ServerTest.line(NAME + " a: Crew nach " + (tick - this.backAt) + " Ticks automatisch wiederhergestellt, Propwash-Operator "
                    + client.getPlainTextName());
            this.next(tick, 50);
        } else if (tick - this.backAt > RESTORE_LIMIT) {
            this.fail(server, "a: Crew nach dem Wiederbeitritt nicht wiederhergestellt (Propwash-Operator " + propwash + ", Gimbal " + gimbal
                    + ", wartet " + waiting + ")");
        }
    }

    private boolean boundTo(@Nullable Entity drone, @Nullable UUID player) {
        if (drone == null || player == null) {
            return false;
        }
        Optional<UUID> operator = CrewService.book().operator(drone.getUUID());
        return operator.filter(player::equals).isPresent();
    }

    private void setupA(MinecraftServer server, ServerPlayer client) {
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        ServerSetup.teleport(client, arena, 0.0F);
        this.clientId = client.getUUID();
        this.guest = ServerSetup.guest(server, level, Vec3.atBottomCenterOf(ServerSetup.ground(level, arena.getX() + 3, arena.getZ() + 3).above()));
        BlockPos ground = ServerSetup.ground(level, arena.getX(), arena.getZ() + 6);
        this.droneA = ServerSetup.spawn(this.guest, CamPresets.TOUR, ground);
        ServerSetup.pair(this.droneA, this.guest.getUUID());
        CrewBook.Verdict verdict = CrewService.invite(this.guest, this.droneA, client);
        ServerTest.line(NAME + " a: " + JoinEnv.GUEST_NAME + " (Besitzer) lädt " + client.getPlainTextName() + " als Operator ein: " + verdict);
        if (!verdict.ok()) {
            ServerTest.fail(NAME + " a: Einladung abgelehnt: " + verdict);
        }
    }

    private void next(int tick, int stage) {
        this.stage = stage;
        this.mark = tick;
    }

    private void fail(MinecraftServer server, String why) {
        ServerTest.fail(NAME + " " + why);
        this.cleanup(server);
        this.stage = 99;
    }

    private void cleanup(MinecraftServer server) {
        ServerSetup.leave(server, this.guest);
    }

    @Override
    void abort() {
        super.abort();
        if (this.guest != null) {
            ServerSetup.leave(this.guest.level().getServer(), this.guest);
        }
    }

    @Override
    boolean done() {
        return this.stage == 99;
    }

    @Override
    @Nullable String missing() {
        if (!this.aBound || !this.aKept || !this.aRestored || !this.aLeft) {
            return "a: Operator " + this.aBound + ", Crew besteht nach dem Trennen " + this.aKept + ", wiederhergestellt " + this.aRestored
                    + ", abgegeben " + this.aLeft;
        }
        if (!this.cBound || !this.cBlocked || !this.cEnded) {
            return "c: Operator " + this.cBound + ", Hinweis nach abgelehnter Neubindung " + this.cBlocked + ", nach 2:00 beendet mit dem richtigen Text "
                    + this.cEnded;
        }
        if (!this.bBound || !this.bKept || !this.bHalf || !this.bEnded) {
            return "b: Operator " + this.bBound + ", wartet nach dem Trennen " + this.bKept + ", nach 60 s " + this.bHalf + ", nach 2:00 beendet "
                    + this.bEnded;
        }
        return null;
    }
}
