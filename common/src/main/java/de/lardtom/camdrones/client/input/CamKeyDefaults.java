package de.lardtom.camdrones.client.input;

import de.lardtom.camdrones.CamDrones;
import de.lardtom.camdrones.integration.CamControl;
import org.lwjgl.sdl.SDLScancode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CamKeyDefaults {
    public static final int UNBOUND = 0;
    public static final int KEYPAD_DIVIDE = SDLScancode.SDL_SCANCODE_KP_DIVIDE;
    public static final int KEYPAD_MULTIPLY = SDLScancode.SDL_SCANCODE_KP_MULTIPLY;
    public static final int KEYPAD_MINUS = SDLScancode.SDL_SCANCODE_KP_MINUS;
    public static final int KEYPAD_COMMA = SDLScancode.SDL_SCANCODE_KP_PERIOD;
    public static final int INSERT = SDLScancode.SDL_SCANCODE_INSERT;
    public static final int PAGE_UP = SDLScancode.SDL_SCANCODE_PAGEUP;
    public static final int PAGE_DOWN = SDLScancode.SDL_SCANCODE_PAGEDOWN;
    public static final int DELETE = SDLScancode.SDL_SCANCODE_DELETE;
    public static final Set<Integer> ALLOWED = Set.of(KEYPAD_DIVIDE, KEYPAD_MULTIPLY, KEYPAD_MINUS, KEYPAD_COMMA, INSERT, PAGE_UP, PAGE_DOWN, DELETE);

    public enum Role {
        PRESS,
        INCREASE,
        DECREASE
    }

    public record Entry(CamControl control, Role role, String name, int defaultKey) {
        public Entry {
            Objects.requireNonNull(control, "control");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(name, "name");
            if (control.axis() == (role == Role.PRESS)) {
                throw new IllegalArgumentException(control + " passt nicht zu " + role);
            }
        }

        public String translationKey() {
            return "key." + CamDrones.MOD_ID + "." + this.name;
        }

        public boolean bound() {
            return this.defaultKey != UNBOUND;
        }
    }

    private static final Map<CamControl, Integer> SWITCH_KEYS = Map.of(
            CamControl.MODE_CYCLE, KEYPAD_DIVIDE,
            CamControl.PAUSE, KEYPAD_MINUS,
            CamControl.MISSION_START, KEYPAD_MULTIPLY,
            CamControl.WAYPOINT_ADD, INSERT,
            CamControl.GIMBAL_RECENTER, KEYPAD_COMMA,
            CamControl.PILOT_VIEW, DELETE);

    private static final Map<CamControl, String[]> AXIS_NAMES = Map.of(
            CamControl.GIMBAL_TILT, new String[]{"gimbal_tilt_up", "gimbal_tilt_down"},
            CamControl.GIMBAL_PAN, new String[]{"gimbal_pan_right", "gimbal_pan_left"},
            CamControl.ZOOM, new String[]{"zoom_in", "zoom_out"});

    private static final Map<CamControl, int[]> AXIS_KEYS = Map.of(
            CamControl.GIMBAL_TILT, new int[]{PAGE_UP, PAGE_DOWN});

    public static final List<Entry> ALL = build();

    private CamKeyDefaults() {
    }

    private static List<Entry> build() {
        List<Entry> entries = new ArrayList<>();
        for (CamControl control : CamControl.values()) {
            if (control.axis()) {
                String[] names = Objects.requireNonNull(AXIS_NAMES.get(control), control.path());
                int[] keys = AXIS_KEYS.getOrDefault(control, new int[]{UNBOUND, UNBOUND});
                entries.add(new Entry(control, Role.INCREASE, names[0], keys[0]));
                entries.add(new Entry(control, Role.DECREASE, names[1], keys[1]));
            } else {
                entries.add(new Entry(control, Role.PRESS, control.path(), SWITCH_KEYS.getOrDefault(control, UNBOUND)));
            }
        }
        return List.copyOf(entries);
    }

    public static Optional<Entry> find(CamControl control, Role role) {
        for (Entry entry : ALL) {
            if (entry.control() == control && entry.role() == role) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public static int defaultKey(CamControl control, Role role) {
        return find(control, role).map(Entry::defaultKey).orElse(UNBOUND);
    }
}
