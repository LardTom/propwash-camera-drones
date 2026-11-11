package de.lardtom.camdrones.dev.server;

import de.lardtom.camdrones.dev.JoinEnv;
import de.lardtom.camdrones.integration.CamWorldRules;
import de.lardtom.camdrones.integration.autonomy.AutonomyServer;
import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.integration.data.SmartState;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.part.CamPresets;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalInt;

final class FollowCheck extends ServerTest.Scenario {
    static final int SETUP_TICKS = 60;
    static final int WAIT_TICKS = 20 * 90;
    static final int WALK_TICKS = 20 * 14;
    static final int SETTLE_TICKS = 20 * 4;
    static final double WALK_SPEED = 3.0;
    static final double TOLERANCE = 2.5;
    private int stage;
    private int joinedAt = -1;
    private int mark;
    private @Nullable ServerPlayer guest;
    private @Nullable Entity drone;
    private boolean refused;
    private boolean given;
    private boolean walked;
    private boolean ruleOff;
    private int samples;
    private double sum;
    private double min = Double.MAX_VALUE;
    private double max;

    @Override
    void tick(MinecraftServer server, int tick) {
        ServerPlayer client = ServerSetup.client(server);
        if (client == null && this.stage > 0 && this.stage < 9) {
            if (this.stage < 6) {
                ServerTest.fail("follow: Client trennt in Stufe " + this.stage);
            }
            this.cleanup(server);
            this.stage = 9;
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
                    this.setup(server, client);
                    this.next(tick, 1);
                }
            }
            case 1 -> {
                if (waited % 10 == 0) {
                    AutonomyServer.Reply reply = AutonomyServer.answer(this.guest, OptionalInt.empty(), false);
                    if (reply.status() == AutonomyStatus.CONSENT_REFUSED) {
                        this.refused = true;
                        ServerTest.line("follow: " + JoinEnv.GUEST_NAME + " lehnt die Anfrage von " + reply.detail() + " ab");
                        this.next(tick, 2);
                        return;
                    }
                }
                if (waited > WAIT_TICKS) {
                    this.timeout("follow: keine Anfrage an " + JoinEnv.GUEST_NAME, server);
                }
            }
            case 2 -> {
                if (waited % 10 == 0) {
                    AutonomyServer.Reply reply = AutonomyServer.answer(this.guest, OptionalInt.empty(), true);
                    if (reply.status() == AutonomyStatus.CONSENT_GIVEN) {
                        this.given = true;
                        ServerTest.line("follow: " + JoinEnv.GUEST_NAME + " stimmt der zweiten Anfrage zu");
                        this.next(tick, 3);
                        return;
                    }
                }
                if (waited > WAIT_TICKS) {
                    this.timeout("follow: keine zweite Anfrage", server);
                }
            }
            case 3 -> {
                Optional<Identifier> active = PropwashApi.drones().autonomy().active(this.drone);
                if (active.filter(id -> id.getPath().equals("follow")).isPresent() && ServerSetup.height(this.drone) > FollowController.HEIGHT - 1.5) {
                    ServerTest.line("follow: Drohne folgt " + JoinEnv.GUEST_NAME + " in " + ServerSetup.format(ServerSetup.height(this.drone)) + " m Höhe");
                    this.next(tick, 4);
                } else if (waited > WAIT_TICKS) {
                    this.timeout("follow: Follow me startet nicht (" + active.map(Identifier::toString).orElse("kein Flug") + ")", server);
                }
            }
            case 4 -> {
                if (waited < WALK_TICKS) {
                    ServerSetup.move(this.guest, WALK_SPEED / 20.0, 0.0);
                } else {
                    ServerSetup.move(this.guest, 0.0, 0.0);
                }
                double distance = Math.hypot(this.drone.getX() - this.guest.getX(), this.drone.getZ() - this.guest.getZ());
                if (waited >= 60 && waited < WALK_TICKS) {
                    this.samples++;
                    this.sum += distance;
                    this.min = Math.min(this.min, distance);
                    this.max = Math.max(this.max, distance);
                }
                if (waited >= WALK_TICKS + SETTLE_TICKS) {
                    double mean = this.samples == 0 ? Double.NaN : this.sum / this.samples;
                    this.walked = this.samples > 0 && Math.abs(mean - FollowController.DISTANCE) <= TOLERANCE;
                    ServerTest.line("follow: " + JoinEnv.GUEST_NAME + " geht " + ServerSetup.format(WALK_SPEED) + " m/s, Abstand im Mittel "
                            + ServerSetup.format(mean) + " m (" + ServerSetup.format(this.min) + " bis " + ServerSetup.format(this.max) + " m), danach "
                            + ServerSetup.format(distance) + " m");
                    if (!this.walked) {
                        ServerTest.fail("follow: Abstand im Mittel " + ServerSetup.format(mean) + " m statt " + FollowController.DISTANCE + " ± " + TOLERANCE);
                    }
                    ServerSetup.command(server, "gamerule " + CamWorldRules.ALLOW_FOLLOW_PLAYERS_ID + " false");
                    ServerTest.line("follow: Welt-Regel „Anderen Spielern folgen“ aus");
                    this.next(tick, 5);
                }
            }
            case 5 -> {
                SmartState smart = AutonomyServer.smart(this.drone);
                Optional<Identifier> active = PropwashApi.drones().autonomy().active(this.drone);
                boolean stopped = smart.function() != SmartState.Function.FOLLOW || active.filter(id -> id.getPath().equals("follow")).isEmpty();
                if (stopped && waited > 20) {
                    this.ruleOff = true;
                    ServerTest.line("follow: ohne Regel folgt die Drohne nicht mehr (" + smart.function() + ", Flug "
                            + active.map(Identifier::toString).orElse("keiner") + ")");
                    this.next(tick, 6);
                } else if (waited > 20 * 30) {
                    this.timeout("follow: Drohne folgt trotz ausgeschalteter Regel weiter", server);
                }
            }
            case 6 -> {
                if (waited >= 20 * 20) {
                    this.cleanup(server);
                    this.stage = 9;
                }
            }
            default -> {
            }
        }
    }

    private void next(int tick, int stage) {
        this.stage = stage;
        this.mark = tick;
    }

    private void timeout(String why, MinecraftServer server) {
        ServerTest.fail(why);
        this.cleanup(server);
        this.stage = 9;
    }

    private void setup(MinecraftServer server, ServerPlayer client) {
        ServerLevel level = client.level();
        BlockPos arena = ServerSetup.arena(level);
        ServerSetup.teleport(client, arena, 90.0F);
        ServerSetup.command(server, "gamerule " + CamWorldRules.ALLOW_FOLLOW_PLAYERS_ID + " true");
        BlockPos ground = ServerSetup.ground(level, arena.getX(), arena.getZ() + 4);
        this.drone = ServerSetup.spawn(client, CamPresets.TOUR, ground);
        ServerSetup.pair(this.drone, client.getUUID());
        Vec3 start = Vec3.atBottomCenterOf(ServerSetup.ground(level, arena.getX() + 2, arena.getZ() + 12).above());
        this.guest = ServerSetup.guest(server, level, start);
        ServerTest.line("follow: Welt-Regel „Anderen Spielern folgen“ an, Kamera-Drohne für " + client.getPlainTextName() + " gekoppelt");
    }

    private void cleanup(MinecraftServer server) {
        ServerSetup.command(server, "gamerule " + CamWorldRules.ALLOW_FOLLOW_PLAYERS_ID + " " + CamWorldRules.DEFAULT_ALLOW_FOLLOW_PLAYERS);
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
        return this.stage == 9;
    }

    @Override
    @Nullable String missing() {
        if (!this.refused) {
            return "keine Ablehnung";
        }
        if (!this.given) {
            return "keine Zustimmung";
        }
        if (!this.walked) {
            return "Abstand beim Gehen nicht gehalten";
        }
        if (!this.ruleOff) {
            return "Regel aus wirkt nicht";
        }
        return null;
    }
}
