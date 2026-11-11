package de.lardtom.camdrones.part;

import de.lardtom.propwash.api.build.BuildSpec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class CameraCheck {
    public static final String PREFIX = "camdrones.check.";
    public static final String TITLE = PREFIX + "title";
    public static final String SHORT = PREFIX + "short";
    public static final String TOOLTIP = PREFIX + "tooltip";
    public static final String COMPLETE = PREFIX + "complete";
    public static final String COMPLETE_HINT = PREFIX + "complete.hint";
    public static final String INCOMPLETE = PREFIX + "incomplete";
    public static final String INCOMPLETE_HINT = PREFIX + "incomplete.hint";
    public static final String FITTED = PREFIX + "fitted";
    public static final String MISSING = PREFIX + "missing";
    public static final String WRONG = PREFIX + "wrong";
    public static final String OTHER = PREFIX + "other";
    public static final String ADVICE = PREFIX + "advice";
    public static final String RECOMMENDED = PREFIX + "recommended";
    public static final String NO_CAMERA_FRAME = PREFIX + "no_camera_frame";

    private static final Set<Identifier> SMART_BATTERIES = parts(CamPresets.Entry::battery);
    private static final Set<Identifier> CAMERA_LINKS = parts(CamPresets.Entry::video);

    private CameraCheck() {
    }

    public enum Role {
        NAV_FC("nav_fc", false),
        GIMBAL("gimbal", false),
        BATTERY("battery", true),
        LINK("link", true);

        private final String key;
        private final boolean acceptsOthers;

        Role(String key, boolean acceptsOthers) {
            this.key = key;
            this.acceptsOthers = acceptsOthers;
        }

        public String key() {
            return this.key;
        }

        public String nameKey() {
            return PREFIX + "role." + this.key;
        }

        public boolean acceptsOthers() {
            return this.acceptsOthers;
        }

        public String wrongKey() {
            return PREFIX + "wrong." + this.key;
        }
    }

    public enum State {
        FITTED,
        OTHER,
        MISSING,
        WRONG
    }

    public record Input(Optional<Identifier> frame, Optional<Identifier> stack, Optional<Identifier> video, Optional<Identifier> battery,
                        List<Identifier> accessories) {
        public Input {
            frame = Objects.requireNonNullElse(frame, Optional.empty());
            stack = Objects.requireNonNullElse(stack, Optional.empty());
            video = Objects.requireNonNullElse(video, Optional.empty());
            battery = Objects.requireNonNullElse(battery, Optional.empty());
            accessories = List.copyOf(Objects.requireNonNullElse(accessories, List.of()));
        }

        public static Input of(BuildSpec spec) {
            return new Input(Optional.of(spec.frame()), Optional.of(spec.stack()), Optional.of(spec.video()), Optional.of(spec.battery()),
                    List.copyOf(spec.accessories().values()));
        }

        public Input withStack(Optional<Identifier> value) {
            return new Input(this.frame, value, this.video, this.battery, this.accessories);
        }

        public Input withVideo(Optional<Identifier> value) {
            return new Input(this.frame, this.stack, value, this.battery, this.accessories);
        }

        public Input withBattery(Optional<Identifier> value) {
            return new Input(this.frame, this.stack, this.video, value, this.accessories);
        }

        public Input withAccessories(List<Identifier> value) {
            return new Input(this.frame, this.stack, this.video, this.battery, value);
        }
    }

    public record Line(Role role, State state, Optional<Identifier> fitted, Optional<Identifier> advice) {
        public boolean ok() {
            return this.state == State.FITTED || this.state == State.OTHER;
        }
    }

    public record Result(Optional<CamPresets.Entry> preset, List<Line> lines) {
        public Result {
            lines = List.copyOf(lines);
        }

        public boolean complete() {
            return this.lines.stream().allMatch(Line::ok);
        }

        public List<Line> problems() {
            return this.lines.stream().filter(line -> !line.ok()).toList();
        }

        public Line line(Role role) {
            return this.lines.stream().filter(line -> line.role() == role).findFirst().orElseThrow();
        }
    }

    public static Result check(Input input) {
        Optional<CamPresets.Entry> preset = input.frame().flatMap(CameraCheck::presetFor);
        List<Line> lines = new ArrayList<>();
        lines.add(slot(Role.NAV_FC, input.stack(), SensorTable::isNavBoard, preset.map(e -> CamPresets.part(e.stack()))));
        Optional<Identifier> gimbal = input.accessories().stream().filter(GimbalTable::isGimbal).findFirst();
        lines.add(gimbal.isPresent() ? new Line(Role.GIMBAL, State.FITTED, gimbal, Optional.empty())
                : new Line(Role.GIMBAL, State.MISSING, Optional.empty(), preset.map(e -> CamPresets.part(e.gimbal()))));
        lines.add(slot(Role.BATTERY, input.battery(), SMART_BATTERIES::contains, preset.map(e -> CamPresets.part(e.battery()))));
        lines.add(slot(Role.LINK, input.video(), CAMERA_LINKS::contains, preset.map(e -> CamPresets.part(e.video()))));
        return new Result(preset, lines);
    }

    public static Result check(BuildSpec spec) {
        return check(Input.of(spec));
    }

    public static Optional<CamPresets.Entry> presetFor(Identifier frame) {
        for (CamPresets.Entry entry : CamPresets.ALL) {
            if (CamPresets.part(entry.frame()).equals(frame)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public static boolean isSmartBattery(Identifier battery) {
        return SMART_BATTERIES.contains(battery);
    }

    public static boolean isCameraLink(Identifier video) {
        return CAMERA_LINKS.contains(video);
    }

    public static List<String> keys() {
        List<String> out = new ArrayList<>(List.of(TITLE, SHORT, TOOLTIP, COMPLETE, COMPLETE_HINT, INCOMPLETE, INCOMPLETE_HINT, FITTED, MISSING,
                WRONG, OTHER, ADVICE, RECOMMENDED, NO_CAMERA_FRAME));
        for (Role role : Role.values()) {
            out.add(role.nameKey());
            if (role == Role.NAV_FC) {
                out.add(role.wrongKey());
            }
        }
        return List.copyOf(out);
    }

    private static Line slot(Role role, Optional<Identifier> fitted, Predicate<Identifier> fits, Optional<Identifier> advice) {
        if (fitted.isEmpty()) {
            return new Line(role, State.MISSING, Optional.empty(), advice);
        }
        if (fits.test(fitted.get())) {
            return new Line(role, State.FITTED, fitted, Optional.empty());
        }
        return new Line(role, role.acceptsOthers() ? State.OTHER : State.WRONG, fitted, advice);
    }

    private static Set<Identifier> parts(Function<CamPresets.Entry, String> part) {
        Set<Identifier> out = new HashSet<>();
        for (CamPresets.Entry entry : CamPresets.ALL) {
            out.add(CamPresets.part(part.apply(entry)));
        }
        return Set.copyOf(out);
    }
}
