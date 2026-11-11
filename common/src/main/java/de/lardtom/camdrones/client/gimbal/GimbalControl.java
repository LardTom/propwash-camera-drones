package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.client.gimbal.model.GimbalLimits;
import de.lardtom.camdrones.client.gimbal.model.OperatorGimbalInput;
import de.lardtom.camdrones.client.gimbal.model.PilotGimbalInput;
import de.lardtom.camdrones.client.gimbal.model.SendGate;
import de.lardtom.camdrones.client.gimbal.model.SoloPickup;
import de.lardtom.camdrones.crew.GimbalData;
import de.lardtom.camdrones.crew.model.GimbalSetting;
import de.lardtom.camdrones.integration.CamControl;
import de.lardtom.camdrones.integration.CamSwitches;
import de.lardtom.propwash.api.client.ClientInput;
import de.lardtom.propwash.api.client.camera.GimbalContext;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class GimbalControl {
    public static final long ACTIVE_NANOS = 500_000_000L;

    public enum Role {
        NONE,
        PILOT,
        OPERATOR
    }

    private final PilotGimbalInput pilot = new PilotGimbalInput();
    private final OperatorGimbalInput operator = new OperatorGimbalInput();
    private final List<OperatorGimbalInput.Event> events = new ArrayList<>();
    private final SendGate gate = new SendGate();
    private final SoloPickup pickup = new SoloPickup();
    private Role role = Role.NONE;
    private boolean solo;
    private int droneId = -1;
    private GimbalSetting setting = GimbalSetting.CENTER;
    private long updatedNanos;
    private boolean recentered;

    public static Role roleFor(GimbalData data, UUID me, boolean flyingThis) {
        if (me == null) {
            return Role.NONE;
        }
        if (data.operatedBy(me)) {
            return Role.OPERATOR;
        }
        return !data.operated() && flyingThis ? Role.PILOT : Role.NONE;
    }

    public Role role() {
        return this.role;
    }

    public int droneId() {
        return this.droneId;
    }

    public GimbalSetting setting() {
        return this.setting;
    }

    public boolean solo() {
        return this.role == Role.OPERATOR && this.solo;
    }

    public boolean steering(int drone) {
        return this.solo() && this.droneId == drone && this.pickup.steering();
    }

    public GimbalSetting update(Entity drone, GimbalContext context, GimbalData data, GimbalLimits limits, double frameHeading,
                                UUID me, ClientInput input, long nowNanos, long nowMillis) {
        return this.update(drone, context, data, limits, frameHeading, Optional.empty(), me, input, nowNanos, nowMillis);
    }

    public GimbalSetting update(Entity drone, GimbalContext context, GimbalData data, GimbalLimits limits, double frameHeading,
                                Optional<GimbalSetting> automatic, UUID me, ClientInput input, long nowNanos, long nowMillis) {
        boolean flyingThis = !context.spectating() && input.flying() && input.drone().filter(d -> d.getId() == drone.getId()).isPresent();
        Role next = roleFor(data, me, flyingThis);
        if (next == Role.NONE) {
            if (this.droneId == drone.getId()) {
                this.release();
            }
            return null;
        }
        boolean[] actions = actions(context);
        double[] axes = new double[4];
        if (next == Role.OPERATOR) {
            for (int i = 0; i < axes.length; i++) {
                axes[i] = context.operatorAxis(i);
            }
        }
        boolean solo = next == Role.OPERATOR && data.solo();
        if (next != this.role || drone.getId() != this.droneId || solo != this.solo) {
            this.role = next;
            this.droneId = drone.getId();
            this.solo = solo;
            this.events.clear();
            this.recentered = false;
            this.gate.start(data.setting(), nowMillis);
            if (next == Role.PILOT) {
                this.pilot.takeOver(data.setting(), axis(drone, CamControl.GIMBAL_TILT), axis(drone, CamControl.GIMBAL_PAN),
                        CamSwitches.active(drone, CamControl.GIMBAL_LOCK), CamSwitches.active(drone, CamControl.GIMBAL_FPV));
            } else {
                this.operator.missionControl(solo);
                this.operator.takeOver(data.setting(), actions);
                this.pickup.start(axes[OperatorGimbalInput.AXIS_ZOOM], data.steered());
            }
        }
        if (next == Role.PILOT) {
            this.setting = this.pilot.update(limits, axis(drone, CamControl.GIMBAL_TILT), axis(drone, CamControl.GIMBAL_PAN),
                    axis(drone, CamControl.ZOOM), CamSwitches.active(drone, CamControl.GIMBAL_LOCK),
                    CamSwitches.active(drone, CamControl.GIMBAL_FPV), CamSwitches.active(drone, CamControl.GIMBAL_RECENTER), frameHeading);
            this.recentered |= this.pilot.recentered();
        } else {
            boolean pending = this.solo && !this.pickup.steering() && !data.steered();
            GimbalSetting seen = automatic.orElse(data.setting());
            if (pending) {
                this.operator.adopt(seen);
            }
            List<OperatorGimbalInput.Event> fired = this.operator.update(limits, axes, actions, context.deltaSeconds(), nowMillis, frameHeading);
            this.events.addAll(fired);
            boolean recenter = fired.contains(OperatorGimbalInput.Event.RECENTER);
            this.recentered |= recenter;
            if (pending && !this.pickup.update(axes[OperatorGimbalInput.AXIS_PAN], axes[OperatorGimbalInput.AXIS_TILT],
                    axes[OperatorGimbalInput.AXIS_ZOOM], recenter)) {
                this.operator.adopt(seen);
                this.setting = seen;
            } else {
                if (pending) {
                    this.gate.reset();
                }
                this.setting = this.operator.setting();
            }
        }
        this.updatedNanos = nowNanos;
        return this.setting;
    }

    private static boolean[] actions(GimbalContext context) {
        boolean[] actions = new boolean[OperatorGimbalInput.ACTIONS];
        for (int n = 1; n <= actions.length; n++) {
            actions[n - 1] = context.operatorAction(n);
        }
        return actions;
    }

    private static double axis(Entity drone, CamControl control) {
        double value = CamSwitches.value(drone, control);
        return Double.isFinite(value) ? value : control.center();
    }

    public Optional<GimbalSetting> local(int drone, long nowNanos) {
        if (this.role == Role.NONE || this.droneId != drone || nowNanos - this.updatedNanos > ACTIVE_NANOS) {
            return Optional.empty();
        }
        return Optional.of(this.setting);
    }

    public boolean active(long nowNanos) {
        return this.role != Role.NONE && nowNanos - this.updatedNanos <= ACTIVE_NANOS;
    }

    public List<OperatorGimbalInput.Event> drainEvents() {
        List<OperatorGimbalInput.Event> out = List.copyOf(this.events);
        this.events.clear();
        return out;
    }

    public boolean drainRecentered() {
        boolean out = this.recentered;
        this.recentered = false;
        return out;
    }

    public Optional<GimbalSetting> due(GimbalSetting stored, long nowMillis) {
        if (this.role == Role.NONE || this.solo && !this.pickup.steering()) {
            return Optional.empty();
        }
        return this.gate.due(this.setting, stored, nowMillis);
    }

    public void release() {
        this.role = Role.NONE;
        this.droneId = -1;
        this.solo = false;
        this.pickup.reset();
        this.operator.missionControl(false);
        this.events.clear();
        this.recentered = false;
        this.gate.reset();
    }
}
