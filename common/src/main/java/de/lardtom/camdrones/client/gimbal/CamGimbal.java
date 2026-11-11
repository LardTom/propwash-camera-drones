package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.GimbalOptics;
import de.lardtom.camdrones.client.gimbal.model.GimbalResolver;
import de.lardtom.camdrones.client.gimbal.model.PilotView;
import de.lardtom.camdrones.crew.CrewData;
import de.lardtom.camdrones.crew.CrewDrones;
import de.lardtom.camdrones.crew.GimbalData;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.camera.GimbalContext;
import de.lardtom.propwash.api.client.camera.GimbalProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;

import java.util.Optional;
import java.util.UUID;

public final class CamGimbal implements GimbalProvider {
    private final GimbalViews views;
    private final GimbalControl control;
    private final PilotView pilotView;
    private final Quaternionf world = new Quaternionf();
    private final Quaternionf gimbal = new Quaternionf();

    public CamGimbal(GimbalViews views, GimbalControl control, PilotView pilotView) {
        this.views = views;
        this.control = control;
        this.pilotView = pilotView;
    }

    public PilotView.Image image(Entity drone, boolean spectating) {
        if (drone == null || !CrewDrones.hasGimbal(drone)) {
            return PilotView.Image.FPV;
        }
        return this.pilotView.shown(drone.getId(), spectating, CrewData.get(drone).operated(), recording(), System.nanoTime());
    }

    public static boolean recording() {
        return PropwashClientApi.recording().recording();
    }

    @Override
    public boolean appliesTo(Entity drone) {
        return CrewDrones.hasGimbal(drone);
    }

    @Override
    public void orient(GimbalContext context, Quaternionf rotation) {
        Entity drone = context.drone();
        Optional<GimbalLimits> limits = GimbalScenes.limits(drone);
        if (drone == null || limits.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        GimbalViews.View view = this.views.view(drone.getId(), limits.get());
        double frameHeading = GimbalPose.heading(context.frame());
        GimbalData data = CrewData.get(drone);
        GimbalResolver.Scene plan = GimbalScenes.plan(drone, view.limits(), frameHeading);
        GimbalSetting local = this.control.update(drone, context, data, view.limits(), frameHeading,
                GimbalResolver.automaticSetting(plan, data.setting().zoom()), me(), PropwashClientApi.input(), now, System.currentTimeMillis());
        GimbalResolver.Aim aim = view.cameraGlide().apply(GimbalResolver.resolve(GimbalScenes.shown(drone, plan),
                local != null ? local : data.setting()), local == null, now);
        double stepped = view.cameraStep(now);
        double dt = context.deltaSeconds() > 0.0 ? context.deltaSeconds() : stepped;
        GimbalPose.aim(view.camera(), aim, frameHeading);
        GimbalPose.step(view.camera(), dt, context.frame());
        GimbalPose.rotation(view.camera(), this.gimbal);
        view.aimed(aim, view.camera().pitchDeg());
        view.stop(aim.locked() && view.limits().atStop(aim.heading(), frameHeading));
        if (this.image(drone, context.spectating()) == PilotView.Image.GIMBAL) {
            rotation.set(this.gimbal);
        }
        this.pilotView.frame(drone.getId());
    }

    @Override
    public boolean modelPose(GimbalContext context, Quaternionf rotation) {
        Entity drone = context.drone();
        Optional<GimbalLimits> limits = GimbalScenes.limits(drone);
        if (drone == null || limits.isEmpty()) {
            return false;
        }
        long now = System.nanoTime();
        GimbalViews.View view = this.views.view(drone.getId(), limits.get());
        double frameHeading = GimbalPose.heading(context.frame());
        Optional<GimbalSetting> local = this.control.local(drone.getId(), now);
        GimbalSetting manual = local.orElseGet(() -> CrewData.get(drone).setting());
        GimbalResolver.Aim aim = view.headGlide().apply(GimbalResolver.resolve(GimbalScenes.scene(drone, view.limits(), frameHeading), manual),
                local.isEmpty(), now);
        GimbalPose.aim(view.head(), aim, frameHeading);
        GimbalPose.step(view.head(), view.headStep(now), context.frame());
        GimbalPose.rotation(view.head(), this.world);
        GimbalPose.relative(context.frame(), this.world, rotation);
        if (!view.freshCamera(now)) {
            view.aimed(aim, view.head().pitchDeg());
        }
        return true;
    }

    @Override
    public double fovDegrees(GimbalContext context) {
        Entity drone = context.drone();
        if (this.image(drone, context.spectating()) == PilotView.Image.FPV) {
            return Double.NaN;
        }
        return this.gimbalFov(drone);
    }

    @Override
    public boolean showsProps(GimbalContext context) {
        return this.image(context.drone(), context.spectating()) == PilotView.Image.FPV;
    }

    @Override
    public boolean gimbalRotation(GimbalContext context, Quaternionf rotation) {
        Entity drone = context.drone();
        GimbalViews.View view = drone == null ? null : this.views.peek(drone.getId());
        if (view == null) {
            return false;
        }
        long now = System.nanoTime();
        if (view.freshCamera(now)) {
            GimbalPose.rotation(view.camera(), rotation);
            return true;
        }
        if (view.freshHead(now)) {
            GimbalPose.rotation(view.head(), rotation);
            return true;
        }
        return false;
    }

    @Override
    public double gimbalFovDegrees(GimbalContext context) {
        Entity drone = context.drone();
        if (drone == null || !CrewDrones.hasGimbal(drone)) {
            return Double.NaN;
        }
        return this.gimbalFov(drone);
    }

    public double gimbalFov(Entity drone) {
        GimbalViews.View view = this.views.peek(drone.getId());
        double zoom;
        if (view != null && view.aim() != null && view.fresh(System.nanoTime())) {
            zoom = view.aim().zoom();
        } else {
            zoom = this.control.local(drone.getId(), System.nanoTime()).orElseGet(() -> CrewData.get(drone).setting()).zoom();
        }
        return GimbalOptics.fov(GimbalScenes.optics(drone), zoom);
    }

    @Override
    public boolean suppressesShake() {
        return true;
    }

    @Override
    public boolean stabilizesRecording() {
        return true;
    }

    private static UUID me() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null : minecraft.player.getUUID();
    }
}
