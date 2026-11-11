package de.lardtom.camdrones.planner;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.data.CamDroneData;
import de.lardtom.camdrones.integration.data.DroneSettings;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.ViewSettingUpload;
import de.lardtom.camdrones.planner.model.ExitSetting;
import de.lardtom.camdrones.planner.model.RateLimiter;
import de.lardtom.camdrones.planner.model.UploadCheck;
import de.lardtom.camdrones.planner.model.ViewSetting;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ViewSettings {
    private static final RateLimiter RATE = new RateLimiter(ViewSetting.RATE_PER_SECOND, ViewSetting.RATE_BURST);

    private ViewSettings() {
    }

    public static void handle(ViewSettingUpload.Request request, ServerPlayer player) {
        boolean withinRate = RATE.tryAcquire(player.getUUID(), Util.getMillis());
        UUID serial = request.serial();
        boolean linked = !CamDroneData.NO_SERIAL.equals(serial);
        ServerLevel level = (ServerLevel) player.level();
        Optional<Entity> drone = withinRate && linked ? PlannerTablets.findDrone(level, serial) : Optional.empty();
        UploadCheck.Drone info = drone.flatMap(entity -> PropwashApi.drones().view(entity)).map(view -> MissionUploads.describe(view, player))
                .orElse(UploadCheck.Drone.MISSING);
        boolean operator = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        ExitSetting.Result result = ViewSetting.check(withinRate, request.view(), linked, info, operator);
        Optional<ViewSetting> wanted = ViewSetting.byId(request.view());
        if (result.ok() && drone.isPresent() && wanted.isPresent()) {
            result = store(drone.get(), wanted.get());
        }
        Optional<ViewSetting> now = drone.flatMap(ViewSettings::read);
        CamNetwork.sendToPlayer(player, new ViewSettingUpload.Reply(request.requestId(), result, now.map(Enum::ordinal).orElse(-1)));
        if (result.ok()) {
            CamDrones.LOG.info("{}: Kamera-Ansicht von Drohne {} auf {} gestellt ({})", CamDrones.MOD_NAME, serial,
                    wanted.map(ViewSetting::key).orElse("?"), player.getGameProfile().name());
        }
    }

    public static ExitSetting.Result store(Entity drone, ViewSetting value) {
        if (!CamDroneData.registered()) {
            return ExitSetting.Result.UNAVAILABLE;
        }
        try {
            DroneSettings current = Objects.requireNonNullElse(PropwashApi.droneData().get(drone, CamDroneData.settings()), DroneSettings.DEFAULT);
            PropwashApi.droneData().set(drone, CamDroneData.settings(), current.withView(cameraView(value)));
        } catch (RuntimeException e) {
            CamDrones.LOG.warn("{}: Kamera-Ansicht nicht an der Drohne gespeichert: {}", CamDrones.MOD_NAME, e.toString());
            return ExitSetting.Result.UNAVAILABLE;
        }
        return ExitSetting.Result.OK;
    }

    public static Optional<ViewSetting> read(Entity drone) {
        if (drone == null || !CamDroneData.registered()) {
            return Optional.empty();
        }
        try {
            DroneSettings settings = PropwashApi.droneData().get(drone, CamDroneData.settings());
            return Optional.of(of(settings == null ? DroneSettings.DEFAULT.view() : settings.view()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static DroneSettings.CameraView cameraView(ViewSetting value) {
        return switch (value) {
            case OSD -> DroneSettings.CameraView.OSD;
            case CAMERA -> DroneSettings.CameraView.HUD;
        };
    }

    public static ViewSetting of(DroneSettings.CameraView value) {
        return switch (value) {
            case OSD -> ViewSetting.OSD;
            case HUD -> ViewSetting.CAMERA;
        };
    }
}
