package de.lardtom.camdrones.client.record;

import de.lardtom.propwash.api.client.CameraChunkRequests;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.CameraChunkResult;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class CaptureChunks {
    public static final int RADIUS = 4;
    public static final int MARGIN_CHUNKS = 1;
    public static final int HYSTERESIS_CHUNKS = 1;

    private final Set<Integer> requested = new HashSet<>();
    private final Set<Integer> wanted = new HashSet<>();
    private int requests;

    public static boolean far(Minecraft minecraft, Entity drone, boolean alreadyRequested) {
        if (drone == null) {
            return true;
        }
        if (minecraft.player == null) {
            return false;
        }
        int chunks = Math.max(2, minecraft.options.getEffectiveRenderDistance()) - MARGIN_CHUNKS - (alreadyRequested ? HYSTERESIS_CHUNKS : 0);
        double dx = drone.getX() - minecraft.player.getX();
        double dz = drone.getZ() - minecraft.player.getZ();
        double limit = Math.max(16.0, chunks * 16.0);
        return dx * dx + dz * dz > limit * limit;
    }

    public boolean requested(int droneId) {
        return this.requested.contains(droneId);
    }

    public void want(int droneId) {
        this.wanted.add(droneId);
    }

    public void flush() {
        CameraChunkRequests chunks = PropwashClientApi.cameraChunks();
        for (int id : this.wanted) {
            if (!this.requested.contains(id) && chunks.supported() && chunks.request(id, RADIUS)) {
                this.requested.add(id);
                this.requests++;
            }
        }
        this.requested.removeIf(id -> {
            if (this.wanted.contains(id)) {
                return false;
            }
            chunks.release(id);
            return true;
        });
        this.wanted.clear();
    }

    public void releaseAll() {
        CameraChunkRequests chunks = PropwashClientApi.cameraChunks();
        for (int id : this.requested) {
            chunks.release(id);
        }
        this.requested.clear();
        this.wanted.clear();
    }

    public int radius(int droneId) {
        return PropwashClientApi.cameraChunks().radius(droneId);
    }

    public Optional<CameraChunkResult.Status> answer(int droneId) {
        return PropwashClientApi.cameraChunks().answer(droneId);
    }

    public int requests() {
        return this.requests;
    }

    public Set<Integer> active() {
        return Set.copyOf(this.requested);
    }
}
