package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.logic.nav.FollowController;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

public final class FollowConsents {
    public static final long REQUEST_MILLIS = 60_000L;

    public record Request(int id, UUID requester, UUID target, UUID drone, FollowController.Style style, Object requesterSession,
                          Object targetSession, long expiresAt) {
        public Request {
            Objects.requireNonNull(requester, "requester");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(drone, "drone");
            style = Objects.requireNonNullElse(style, FollowController.Style.BEHIND);
            Objects.requireNonNull(requesterSession, "requesterSession");
            Objects.requireNonNull(targetSession, "targetSession");
        }

        public boolean expired(long now) {
            return now >= this.expiresAt;
        }

        public int secondsLeft(long now) {
            return (int) Math.max(0L, (this.expiresAt - now + 999L) / 1000L);
        }
    }

    private record Grant(UUID requester, UUID target, Object requesterSession, Object targetSession) {
    }

    private final long requestMillis;
    private final Map<Integer, Request> requests = new LinkedHashMap<>();
    private final List<Grant> grants = new ArrayList<>();
    private int nextId = 1;

    public FollowConsents() {
        this(REQUEST_MILLIS);
    }

    public FollowConsents(long requestMillis) {
        if (requestMillis <= 0L) {
            throw new IllegalArgumentException("Dauer ungültig: " + requestMillis);
        }
        this.requestMillis = requestMillis;
    }

    public synchronized Request request(UUID requester, UUID target, UUID drone, FollowController.Style style, Object requesterSession,
                                        Object targetSession, long now) {
        this.requests.values().removeIf(r -> r.requester().equals(requester) && r.target().equals(target));
        int id = this.nextId;
        this.nextId = this.nextId == Integer.MAX_VALUE ? 1 : this.nextId + 1;
        Request request = new Request(id, requester, target, drone, style, requesterSession, targetSession, now + this.requestMillis);
        this.requests.put(id, request);
        return request;
    }

    public synchronized Optional<Request> answer(UUID target, OptionalInt id, Object targetSession, long now) {
        Optional<Request> found = id.isPresent() ? Optional.ofNullable(this.requests.get(id.getAsInt())) : this.latest(target, now);
        if (found.isEmpty() || !found.get().target().equals(target) || found.get().targetSession() != targetSession) {
            return Optional.empty();
        }
        Request request = found.get();
        this.requests.remove(request.id());
        return request.expired(now) ? Optional.empty() : Optional.of(request);
    }

    private Optional<Request> latest(UUID target, long now) {
        Request best = null;
        for (Request request : this.requests.values()) {
            if (request.target().equals(target) && !request.expired(now) && (best == null || request.id() > best.id())) {
                best = request;
            }
        }
        return Optional.ofNullable(best);
    }

    public synchronized void grant(Request request) {
        this.grants.removeIf(g -> g.requester().equals(request.requester()) && g.target().equals(request.target()));
        this.grants.add(new Grant(request.requester(), request.target(), request.requesterSession(), request.targetSession()));
    }

    public synchronized boolean granted(UUID requester, UUID target, Object requesterSession, Object targetSession) {
        Iterator<Grant> it = this.grants.iterator();
        boolean found = false;
        while (it.hasNext()) {
            Grant grant = it.next();
            if (!grant.requester().equals(requester) || !grant.target().equals(target)) {
                continue;
            }
            if (grant.requesterSession() == requesterSession && grant.targetSession() == targetSession) {
                found = true;
            } else {
                it.remove();
            }
        }
        return found;
    }

    public synchronized List<UUID> revoke(UUID target) {
        List<UUID> requesters = new ArrayList<>();
        Iterator<Grant> it = this.grants.iterator();
        while (it.hasNext()) {
            Grant grant = it.next();
            if (grant.target().equals(target)) {
                requesters.add(grant.requester());
                it.remove();
            }
        }
        this.requests.values().removeIf(r -> r.target().equals(target));
        return requesters;
    }

    public synchronized List<Request> expire(long now) {
        List<Request> expired = new ArrayList<>();
        Iterator<Request> it = this.requests.values().iterator();
        while (it.hasNext()) {
            Request request = it.next();
            if (request.expired(now)) {
                expired.add(request);
                it.remove();
            }
        }
        return expired;
    }

    public synchronized int pending() {
        return this.requests.size();
    }

    public synchronized int grants() {
        return this.grants.size();
    }

    public synchronized void clear() {
        this.requests.clear();
        this.grants.clear();
    }
}
