package de.lardtom.camdrones.client.gimbal;

import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.CrewPackets;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class NoticeProbe {
    private static final List<CrewPackets.Notice> NOTICES = new CopyOnWriteArrayList<>();
    private static boolean installed;

    private NoticeProbe() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        CamNetwork.onClient(CrewPackets.NOTICE, notice -> {
            NOTICES.add(notice);
            GimbalClient.notice(notice);
        });
    }

    public static int mark() {
        return NOTICES.size();
    }

    public static Optional<CrewPackets.Notice> first(int mark, CrewPackets.Kind kind) {
        List<CrewPackets.Notice> all = List.copyOf(NOTICES);
        for (int i = Math.max(0, mark); i < all.size(); i++) {
            if (all.get(i).kind() == kind) {
                return Optional.of(all.get(i));
            }
        }
        return Optional.empty();
    }
}
