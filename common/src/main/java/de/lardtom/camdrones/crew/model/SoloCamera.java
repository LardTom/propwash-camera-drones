package de.lardtom.camdrones.crew.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SoloCamera {
    public enum Verdict {
        OK,
        NO_GIMBAL,
        NOT_OWNER,
        PILOTED,
        NOT_AUTONOMOUS,
        CREW,
        REFUSED;

        public boolean ok() {
            return this == OK;
        }
    }

    public enum End {
        LEFT,
        DRONE_GONE,
        OFFLINE,
        UNBOUND,
        PILOTED,
        NOT_AUTONOMOUS,
        CREW
    }

    public record Facts(boolean gimbal, boolean owner, boolean piloted, boolean autonomous, boolean crew) {
    }

    public record Watch(boolean drone, boolean online, boolean bound, boolean piloted, boolean autonomous, boolean crew) {
    }

    private final Map<UUID, UUID> sessions = new HashMap<>();

    public static boolean offered(boolean gimbal, boolean flying, boolean armed, boolean piloted, boolean operated, boolean mine) {
        return gimbal && !flying && armed && !piloted && (!operated || mine);
    }

    public static Verdict check(Facts facts) {
        if (!facts.gimbal()) {
            return Verdict.NO_GIMBAL;
        }
        if (!facts.owner()) {
            return Verdict.NOT_OWNER;
        }
        if (facts.piloted()) {
            return Verdict.PILOTED;
        }
        if (!facts.autonomous()) {
            return Verdict.NOT_AUTONOMOUS;
        }
        return facts.crew() ? Verdict.CREW : Verdict.OK;
    }

    public static Optional<End> keep(Watch watch) {
        if (!watch.drone()) {
            return Optional.of(End.DRONE_GONE);
        }
        if (!watch.online()) {
            return Optional.of(End.OFFLINE);
        }
        if (watch.crew()) {
            return Optional.of(End.CREW);
        }
        if (watch.piloted()) {
            return Optional.of(End.PILOTED);
        }
        if (!watch.autonomous()) {
            return Optional.of(End.NOT_AUTONOMOUS);
        }
        return watch.bound() ? Optional.empty() : Optional.of(End.UNBOUND);
    }

    public synchronized void start(UUID drone, UUID player) {
        this.sessions.values().remove(player);
        this.sessions.put(drone, player);
    }

    public synchronized Optional<UUID> player(UUID drone) {
        return Optional.ofNullable(this.sessions.get(drone));
    }

    public synchronized Optional<UUID> droneOf(UUID player) {
        for (Map.Entry<UUID, UUID> entry : this.sessions.entrySet()) {
            if (entry.getValue().equals(player)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public synchronized Optional<UUID> end(UUID drone) {
        return Optional.ofNullable(this.sessions.remove(drone));
    }

    public synchronized Map<UUID, UUID> sessions() {
        return Map.copyOf(this.sessions);
    }

    public synchronized void clear() {
        this.sessions.clear();
    }
}
