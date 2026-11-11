package de.lardtom.camdrones.crew.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class CrewBook {
    public static final long INVITE_MILLIS = 60_000L;
    public static final double MAX_DISTANCE = 16.0;
    public static final long GRACE_MILLIS = 120_000L;

    public enum Verdict {
        OK,
        RULE_OFF,
        NO_DRONE,
        NO_GIMBAL,
        NOT_ALLOWED,
        SELF,
        OFFLINE,
        TOO_FAR,
        ALREADY,
        TAKEN,
        BUSY,
        NO_INVITE,
        EXPIRED,
        REFUSED,
        NOT_OPERATOR,
        NO_OPERATOR,
        RATE;

        public boolean ok() {
            return this == OK;
        }
    }

    public interface Binder {
        boolean bind(UUID drone, UUID operator);
    }

    public record Invite(UUID drone, UUID inviter, UUID invitee, long expires) {
        public Invite {
            Objects.requireNonNull(drone, "drone");
            Objects.requireNonNull(inviter, "inviter");
            Objects.requireNonNull(invitee, "invitee");
        }

        public boolean expired(long now) {
            return now >= this.expires;
        }
    }

    public record Wait(UUID operator, String name, long until, boolean back) {
        public Wait {
            Objects.requireNonNull(operator, "operator");
            name = name == null ? "" : name;
        }

        public Wait(UUID operator, String name, long until) {
            this(operator, name, until, false);
        }

        public boolean expired(long now) {
            return now >= this.until;
        }

        public long remaining(long now) {
            return Math.max(0L, this.until - now);
        }
    }

    public record Facts(boolean ruleOn, boolean gimbal, boolean controls, double distance, boolean busy) {
        public boolean near() {
            return Double.isFinite(this.distance) && this.distance <= MAX_DISTANCE;
        }
    }

    private final Map<UUID, Invite> invites = new LinkedHashMap<>();
    private final Map<UUID, UUID> operators = new HashMap<>();
    private final Map<UUID, UUID> inviters = new HashMap<>();
    private final Map<UUID, Wait> waits = new HashMap<>();

    public synchronized Verdict invite(UUID drone, UUID inviter, UUID invitee, Facts facts, long now) {
        if (!facts.ruleOn()) {
            return Verdict.RULE_OFF;
        }
        if (drone == null) {
            return Verdict.NO_DRONE;
        }
        if (!facts.gimbal()) {
            return Verdict.NO_GIMBAL;
        }
        if (!facts.controls()) {
            return Verdict.NOT_ALLOWED;
        }
        if (invitee == null) {
            return Verdict.OFFLINE;
        }
        if (invitee.equals(inviter)) {
            return Verdict.SELF;
        }
        if (!facts.near()) {
            return Verdict.TOO_FAR;
        }
        UUID current = this.operators.get(drone);
        if (invitee.equals(current)) {
            return Verdict.ALREADY;
        }
        if (current != null) {
            return Verdict.TAKEN;
        }
        if (facts.busy() || this.operators.containsValue(invitee)) {
            return Verdict.BUSY;
        }
        this.invites.put(invitee, new Invite(drone, inviter, invitee, now + INVITE_MILLIS));
        return Verdict.OK;
    }

    public synchronized Optional<Invite> pending(UUID invitee, long now) {
        Invite invite = this.invites.get(invitee);
        if (invite != null && invite.expired(now)) {
            this.invites.remove(invitee);
            return Optional.empty();
        }
        return Optional.ofNullable(invite);
    }

    public synchronized Verdict accept(UUID invitee, Facts facts, long now, Binder binder) {
        Invite invite = this.invites.remove(invitee);
        if (invite == null) {
            return Verdict.NO_INVITE;
        }
        if (invite.expired(now)) {
            return Verdict.EXPIRED;
        }
        if (!facts.ruleOn()) {
            return Verdict.RULE_OFF;
        }
        if (!facts.gimbal()) {
            return Verdict.NO_GIMBAL;
        }
        if (!facts.controls()) {
            return Verdict.NOT_ALLOWED;
        }
        if (!facts.near()) {
            return Verdict.TOO_FAR;
        }
        if (this.operators.containsKey(invite.drone())) {
            return Verdict.TAKEN;
        }
        if (facts.busy() || this.operators.containsValue(invitee)) {
            return Verdict.BUSY;
        }
        if (!binder.bind(invite.drone(), invitee)) {
            return Verdict.REFUSED;
        }
        this.operators.put(invite.drone(), invitee);
        this.inviters.put(invite.drone(), invite.inviter());
        this.waits.remove(invite.drone());
        this.invites.values().removeIf(other -> other.drone().equals(invite.drone()));
        return Verdict.OK;
    }

    public synchronized Optional<Invite> decline(UUID invitee) {
        return Optional.ofNullable(this.invites.remove(invitee));
    }

    public synchronized Optional<UUID> leave(UUID operator) {
        Iterator<Map.Entry<UUID, UUID>> it = this.operators.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            if (entry.getValue().equals(operator)) {
                it.remove();
                this.inviters.remove(entry.getKey());
                this.waits.remove(entry.getKey());
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public synchronized Optional<UUID> release(UUID drone) {
        this.inviters.remove(drone);
        this.waits.remove(drone);
        return Optional.ofNullable(this.operators.remove(drone));
    }

    public synchronized boolean hold(UUID drone, String name, long now) {
        UUID operator = this.operators.get(drone);
        if (operator == null || this.waits.containsKey(drone)) {
            return false;
        }
        this.waits.put(drone, new Wait(operator, name, now + GRACE_MILLIS));
        return true;
    }

    public synchronized boolean refuse(UUID drone, String name, long now) {
        UUID operator = this.operators.get(drone);
        Wait wait = this.waits.get(drone);
        if (operator == null || wait != null && wait.back()) {
            return false;
        }
        this.waits.put(drone, new Wait(operator, name, wait == null ? now + GRACE_MILLIS : wait.until(), true));
        return true;
    }

    public synchronized void away(UUID drone) {
        Wait wait = this.waits.get(drone);
        if (wait != null && wait.back()) {
            this.waits.put(drone, new Wait(wait.operator(), wait.name(), wait.until(), false));
        }
    }

    public synchronized Optional<Wait> waiting(UUID drone) {
        return Optional.ofNullable(this.waits.get(drone));
    }

    public synchronized boolean resume(UUID drone) {
        return this.waits.remove(drone) != null;
    }

    public synchronized Optional<UUID> inviter(UUID drone) {
        return Optional.ofNullable(this.inviters.get(drone));
    }

    public synchronized Optional<UUID> operator(UUID drone) {
        return Optional.ofNullable(this.operators.get(drone));
    }

    public synchronized Optional<UUID> droneOf(UUID operator) {
        for (Map.Entry<UUID, UUID> entry : this.operators.entrySet()) {
            if (entry.getValue().equals(operator)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public synchronized Map<UUID, UUID> bindings() {
        return Map.copyOf(this.operators);
    }

    public synchronized List<Invite> invites() {
        return List.copyOf(this.invites.values());
    }

    public synchronized List<Invite> expire(long now) {
        List<Invite> gone = new ArrayList<>();
        Iterator<Invite> it = this.invites.values().iterator();
        while (it.hasNext()) {
            Invite invite = it.next();
            if (invite.expired(now)) {
                gone.add(invite);
                it.remove();
            }
        }
        return gone;
    }

    public synchronized void forgetPlayer(UUID player) {
        this.invites.values().removeIf(invite -> invite.invitee().equals(player) || invite.inviter().equals(player));
    }

    public synchronized void clear() {
        this.invites.clear();
        this.operators.clear();
        this.inviters.clear();
        this.waits.clear();
    }
}
