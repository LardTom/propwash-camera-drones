package de.lardtom.camdrones.award.client;

import de.lardtom.camdrones.award.AwardRules;
import de.lardtom.camdrones.client.gimbal.GimbalClient;
import de.lardtom.camdrones.client.gimbal.GimbalControl;
import de.lardtom.camdrones.client.record.GimbalPathRecorder;
import de.lardtom.camdrones.crew.CrewDrones;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.VideoRecorded;
import de.lardtom.propwash.api.client.ClientInput;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalInt;

public final class VideoAwards {
    private static final AwardRules.Take DIRECT = new AwardRules.Take();
    private static final Handovers FLASHBACK = new Handovers();
    private static int reported;

    private VideoAwards() {
    }

    public static int reported() {
        return reported;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            DIRECT.reset();
            FLASHBACK.reset();
            return;
        }
        FLASHBACK.update(GimbalPathRecorder.INSTANCE.lastHandover()).ifPresent(VideoAwards::report);
        boolean recording;
        try {
            recording = PropwashClientApi.recording().recording();
        } catch (RuntimeException e) {
            recording = false;
        }
        DIRECT.update(recording, recording ? cameraDrone(minecraft) : AwardRules.NO_DRONE).ifPresent(VideoAwards::report);
    }

    public static int cameraDrone(Minecraft minecraft) {
        Entity camera = minecraft.getCameraEntity();
        if (camera != null && camera != minecraft.player && CrewDrones.hasGimbal(camera)) {
            return camera.getId();
        }
        ClientInput input = PropwashClientApi.input();
        Optional<Entity> flown = input.flying() ? input.drone() : Optional.empty();
        if (flown.isPresent() && CrewDrones.hasGimbal(flown.get())) {
            return flown.get().getId();
        }
        GimbalControl control = GimbalClient.control();
        Entity operated = control.role() == GimbalControl.Role.NONE || minecraft.level == null ? null : minecraft.level.getEntity(control.droneId());
        return operated != null && CrewDrones.hasGimbal(operated) ? operated.getId() : AwardRules.NO_DRONE;
    }

    public static boolean counts(GimbalPathRecorder.Handover handover) {
        return !handover.photo() && handover.samples() > 0;
    }

    private static void report(int drone) {
        if (CamNetwork.sendToServer(new VideoRecorded.Report(drone))) {
            reported++;
        }
    }

    public static final class Handovers {
        private GimbalPathRecorder.@Nullable Handover seen;
        private boolean primed;

        public OptionalInt update(Optional<GimbalPathRecorder.Handover> last) {
            if (!this.primed) {
                this.primed = true;
                this.seen = last.orElse(null);
                return OptionalInt.empty();
            }
            if (last.isEmpty() || last.get() == this.seen) {
                return OptionalInt.empty();
            }
            GimbalPathRecorder.Handover next = last.get();
            this.seen = next;
            return counts(next) ? OptionalInt.of(next.droneId()) : OptionalInt.empty();
        }

        public void reset() {
            this.primed = false;
            this.seen = null;
        }
    }
}
