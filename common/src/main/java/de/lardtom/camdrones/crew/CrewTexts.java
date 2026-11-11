package de.lardtom.camdrones.crew;

import de.lardtom.camdrones.crew.model.CrewBook;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CrewTexts {
    public static final String PREFIX = "camdrones.crew.";
    public static final String INVITE_SENT = PREFIX + "invite.sent";
    public static final String INVITE_RECEIVED = PREFIX + "invite.received";
    public static final String INVITE_ACCEPT = PREFIX + "invite.accept";
    public static final String INVITE_ACCEPT_HOVER = PREFIX + "invite.accept.hover";
    public static final String INVITE_DECLINE = PREFIX + "invite.decline";
    public static final String INVITE_DECLINE_HOVER = PREFIX + "invite.decline.hover";
    public static final String INVITE_DECLINED = PREFIX + "invite.declined";
    public static final String INVITE_DECLINED_SELF = PREFIX + "invite.declined_self";
    public static final String INVITE_EXPIRED = PREFIX + "invite.expired";
    public static final String BOUND_OPERATOR = PREFIX + "bound.operator";
    public static final String BOUND_PILOT = PREFIX + "bound.pilot";
    public static final String LEFT_OPERATOR = PREFIX + "left.operator";
    public static final String LEFT_PILOT = PREFIX + "left.pilot";
    public static final String RELEASED_OPERATOR = PREFIX + "released.operator";
    public static final String RELEASED_PILOT = PREFIX + "released.pilot";
    public static final String ENDED_OPERATOR = PREFIX + "ended.operator";
    public static final String ENDED_PILOT = PREFIX + "ended.pilot";
    public static final String WAITING_PILOT = PREFIX + "waiting.pilot";
    public static final String WAITING_OVERLAY = PREFIX + "waiting.overlay";
    public static final String RESTORED_OPERATOR = PREFIX + "restored.operator";
    public static final String RESTORED_PILOT = PREFIX + "restored.pilot";
    public static final String EXPIRED_PILOT = PREFIX + "expired.pilot";
    public static final String BLOCKED_OPERATOR = PREFIX + "blocked.operator";
    public static final String BLOCKED_OPERATOR_OVERLAY = PREFIX + "blocked.operator.overlay";
    public static final String BLOCKED_PILOT = PREFIX + "blocked.pilot";
    public static final String BLOCKED_PILOT_OVERLAY = PREFIX + "blocked.pilot.overlay";
    public static final String UNREACHABLE_OPERATOR = PREFIX + "unreachable.operator";
    public static final String UNREACHABLE_PILOT = PREFIX + "unreachable.pilot";
    public static final String SERVO_VOLUME = PREFIX + "servo.volume";
    public static final String SERVO_VOLUME_TOOLTIP = PREFIX + "servo.volume.tooltip";
    public static final String BUTTON = PREFIX + "button";
    public static final String SCREEN_TITLE = PREFIX + "screen.title";
    public static final String SCREEN_OPERATOR = PREFIX + "screen.operator";
    public static final String SCREEN_NO_OPERATOR = PREFIX + "screen.no_operator";
    public static final String SCREEN_NEARBY = PREFIX + "screen.nearby";
    public static final String SCREEN_NOBODY = PREFIX + "screen.nobody";
    public static final String SCREEN_INVITE = PREFIX + "screen.invite";
    public static final String SCREEN_INVITED = PREFIX + "screen.invited";
    public static final String SCREEN_RELEASE = PREFIX + "screen.release";
    public static final String SCREEN_VIEW = PREFIX + "screen.view";
    public static final String SCREEN_VIEW_FAILED = PREFIX + "screen.view_failed";
    public static final String SCREEN_CLOSE = PREFIX + "screen.close";
    public static final String SCREEN_MORE = PREFIX + "screen.more";
    public static final String SCREEN_DISTANCE = PREFIX + "screen.distance";
    public static final String SCREEN_HINT = PREFIX + "screen.hint";
    public static final String SCREEN_DECIMAL = PREFIX + "screen.decimal";
    public static final String CAMERA_PHOTO = PREFIX + "camera.photo";
    public static final String CAMERA_RECORDING = PREFIX + "camera.recording";
    public static final String CAMERA_STOPPED = PREFIX + "camera.stopped";
    public static final String CAMERA_REFUSED = PREFIX + "camera.refused";
    public static final String VIEW_FPV = PREFIX + "view.fpv";
    public static final String VIEW_GIMBAL = PREFIX + "view.gimbal";
    public static final String VIEW_OPERATOR = PREFIX + "view.operator";
    public static final String VIEW_RECORDING = PREFIX + "view.recording";
    public static final String SOLO_BOUND = PREFIX + "solo.bound";
    public static final String SOLO_ENDED = PREFIX + "solo.ended";

    private CrewTexts() {
    }

    public static String clock(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    public static String verdict(CrewBook.Verdict verdict) {
        return PREFIX + "verdict." + verdict.name().toLowerCase(Locale.ROOT);
    }

    public static List<String> all() {
        List<String> keys = new ArrayList<>(List.of(INVITE_SENT, INVITE_RECEIVED, INVITE_ACCEPT, INVITE_ACCEPT_HOVER, INVITE_DECLINE,
                INVITE_DECLINE_HOVER, INVITE_DECLINED, INVITE_DECLINED_SELF, INVITE_EXPIRED, BOUND_OPERATOR, BOUND_PILOT, LEFT_OPERATOR,
                LEFT_PILOT, RELEASED_OPERATOR, RELEASED_PILOT, ENDED_OPERATOR, ENDED_PILOT, WAITING_PILOT, WAITING_OVERLAY, RESTORED_OPERATOR,
                RESTORED_PILOT, EXPIRED_PILOT, BLOCKED_OPERATOR, BLOCKED_OPERATOR_OVERLAY, BLOCKED_PILOT, BLOCKED_PILOT_OVERLAY,
                UNREACHABLE_OPERATOR, UNREACHABLE_PILOT, SERVO_VOLUME, SERVO_VOLUME_TOOLTIP, BUTTON, SCREEN_TITLE, SCREEN_OPERATOR,
                SCREEN_NO_OPERATOR, SCREEN_NEARBY, SCREEN_NOBODY, SCREEN_INVITE, SCREEN_INVITED, SCREEN_RELEASE, SCREEN_VIEW,
                SCREEN_VIEW_FAILED, SCREEN_CLOSE, SCREEN_MORE, SCREEN_DISTANCE, SCREEN_HINT, SCREEN_DECIMAL, CAMERA_PHOTO, CAMERA_RECORDING,
                CAMERA_STOPPED, CAMERA_REFUSED, VIEW_FPV, VIEW_GIMBAL, VIEW_OPERATOR, VIEW_RECORDING, SOLO_BOUND, SOLO_ENDED));
        for (CrewBook.Verdict verdict : CrewBook.Verdict.values()) {
            if (!verdict.ok()) {
                keys.add(verdict(verdict));
            }
        }
        return List.copyOf(keys);
    }
}
