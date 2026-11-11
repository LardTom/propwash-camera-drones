package de.lardtom.camdrones.integration.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.camdrones.logic.nav.ObstacleGuard;

import java.util.Objects;

public record DroneSettings(ExitBehaviour exit, ObstacleGuard.Mode obstacle, CameraView view) {
    public enum ExitBehaviour {
        HOVER,
        HOME,
        LAND
    }

    public enum CameraView {
        OSD,
        HUD
    }

    public static final DroneSettings DEFAULT = new DroneSettings(ExitBehaviour.HOVER, ObstacleGuard.Mode.STOP, CameraView.OSD);
    public static final Codec<ExitBehaviour> EXIT_CODEC = EnumCodec.of(ExitBehaviour.class, ExitBehaviour.HOVER);
    public static final Codec<ObstacleGuard.Mode> OBSTACLE_CODEC = EnumCodec.of(ObstacleGuard.Mode.class, ObstacleGuard.Mode.STOP);
    public static final Codec<CameraView> VIEW_CODEC = EnumCodec.of(CameraView.class, CameraView.OSD);
    public static final Codec<DroneSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EXIT_CODEC.optionalFieldOf("exit", ExitBehaviour.HOVER).forGetter(DroneSettings::exit),
            OBSTACLE_CODEC.optionalFieldOf("obstacle", ObstacleGuard.Mode.STOP).forGetter(DroneSettings::obstacle),
            VIEW_CODEC.optionalFieldOf("view", CameraView.OSD).forGetter(DroneSettings::view)
    ).apply(instance, DroneSettings::new));

    public DroneSettings {
        exit = Objects.requireNonNullElse(exit, ExitBehaviour.HOVER);
        obstacle = Objects.requireNonNullElse(obstacle, ObstacleGuard.Mode.STOP);
        view = Objects.requireNonNullElse(view, CameraView.OSD);
    }

    public DroneSettings withExit(ExitBehaviour value) {
        return new DroneSettings(value, this.obstacle, this.view);
    }

    public DroneSettings withObstacle(ObstacleGuard.Mode value) {
        return new DroneSettings(this.exit, value, this.view);
    }

    public DroneSettings withView(CameraView value) {
        return new DroneSettings(this.exit, this.obstacle, value);
    }
}
