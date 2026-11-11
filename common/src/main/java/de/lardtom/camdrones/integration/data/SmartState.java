package de.lardtom.camdrones.integration.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.camdrones.logic.nav.AutoShots;
import de.lardtom.camdrones.logic.nav.Compass;
import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.logic.nav.NavOutput;
import de.lardtom.camdrones.logic.nav.OrbitController;
import net.minecraft.core.UUIDUtil;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SmartState(Function function, Optional<UUID> target, double x, double y, double z, double radius, double speed,
                         boolean clockwise, double course, FollowController.Style followStyle, AutoShots.Shot shot) {
    public enum Function {
        NONE,
        HOLD,
        FOLLOW,
        POI,
        COURSE_LOCK,
        AUTO_SHOT
    }

    public static final SmartState NONE = new SmartState(Function.NONE, Optional.empty(), 0.0, 0.0, 0.0, 0.0, 0.0, true, 0.0,
            FollowController.Style.BEHIND);
    public static final Codec<Function> FUNCTION_CODEC = EnumCodec.of(Function.class, Function.NONE);
    public static final Codec<FollowController.Style> STYLE_CODEC = EnumCodec.of(FollowController.Style.class, FollowController.Style.BEHIND);
    public static final Codec<AutoShots.Shot> SHOT_CODEC = EnumCodec.of(AutoShots.Shot.class, AutoShots.Shot.RETREAT);
    public static final Codec<SmartState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FUNCTION_CODEC.optionalFieldOf("function", Function.NONE).forGetter(SmartState::function),
            UUIDUtil.CODEC.optionalFieldOf("target").forGetter(SmartState::target),
            Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(SmartState::x),
            Codec.DOUBLE.optionalFieldOf("y", 0.0).forGetter(SmartState::y),
            Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(SmartState::z),
            Codec.DOUBLE.optionalFieldOf("radius", 0.0).forGetter(SmartState::radius),
            Codec.DOUBLE.optionalFieldOf("speed", 0.0).forGetter(SmartState::speed),
            Codec.BOOL.optionalFieldOf("clockwise", true).forGetter(SmartState::clockwise),
            Codec.DOUBLE.optionalFieldOf("course", 0.0).forGetter(SmartState::course),
            STYLE_CODEC.optionalFieldOf("follow_style", FollowController.Style.BEHIND).forGetter(SmartState::followStyle),
            SHOT_CODEC.optionalFieldOf("shot", AutoShots.Shot.RETREAT).forGetter(SmartState::shot)
    ).apply(instance, SmartState::new));

    public SmartState {
        function = Objects.requireNonNullElse(function, Function.NONE);
        target = Objects.requireNonNullElse(target, Optional.empty());
        x = finite(x);
        y = finite(y);
        z = finite(z);
        radius = Math.clamp(finite(radius), 0.0, OrbitController.MAX_RADIUS);
        speed = Math.clamp(finite(speed), 0.0, NavOutput.MAX_SPEED);
        course = Compass.normalize(finite(course));
        followStyle = Objects.requireNonNullElse(followStyle, FollowController.Style.BEHIND);
        shot = Objects.requireNonNullElse(shot, AutoShots.Shot.RETREAT);
    }

    public SmartState(Function function, Optional<UUID> target, double x, double y, double z, double radius, double speed, boolean clockwise,
                      double course, FollowController.Style followStyle) {
        this(function, target, x, y, z, radius, speed, clockwise, course, followStyle, AutoShots.Shot.RETREAT);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    public static SmartState hold(double x, double y, double z) {
        return new SmartState(Function.HOLD, Optional.empty(), x, y, z, 0.0, 0.0, true, 0.0, FollowController.Style.BEHIND);
    }

    public static SmartState follow(UUID target, FollowController.Style style) {
        return new SmartState(Function.FOLLOW, Optional.of(target), 0.0, 0.0, 0.0, 0.0, 0.0, true, 0.0, style);
    }

    public static SmartState pointOfInterest(double x, double y, double z, double radius, double speed, boolean clockwise) {
        return new SmartState(Function.POI, Optional.empty(), x, y, z, radius, speed, clockwise, 0.0, FollowController.Style.BEHIND);
    }

    public static SmartState courseLock(double course) {
        return new SmartState(Function.COURSE_LOCK, Optional.empty(), 0.0, 0.0, 0.0, 0.0, 0.0, true, course,
                FollowController.Style.BEHIND);
    }

    public static SmartState autoShot(AutoShots.Shot shot, double subjectX, double subjectY, double subjectZ) {
        return new SmartState(Function.AUTO_SHOT, Optional.empty(), subjectX, subjectY, subjectZ, 0.0, 0.0, true, 0.0,
                FollowController.Style.BEHIND, shot);
    }

    public static SmartState autoShot(AutoShots.Shot shot) {
        return autoShot(shot, 0.0, 0.0, 0.0);
    }

    public boolean active() {
        return this.function != Function.NONE;
    }
}
