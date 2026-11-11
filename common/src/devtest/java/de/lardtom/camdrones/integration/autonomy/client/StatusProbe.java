package de.lardtom.camdrones.integration.autonomy.client;

import de.lardtom.camdrones.integration.autonomy.AutonomyStatus;
import de.lardtom.camdrones.net.CamNetwork;
import de.lardtom.camdrones.net.FollowConsent;
import de.lardtom.camdrones.net.StatusMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public final class StatusProbe {
    private static final List<StatusMessage.Payload> STATUS = new CopyOnWriteArrayList<>();
    private static final List<FollowConsent.Request> CONSENTS = new CopyOnWriteArrayList<>();
    private static boolean installed;

    private StatusProbe() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        CamNetwork.onClient(StatusMessage.TYPE, payload -> {
            STATUS.add(payload);
            AutonomyClient.onStatus(payload);
        });
        CamNetwork.onClient(FollowConsent.REQUEST, request -> {
            CONSENTS.add(request);
            AutonomyClient.onConsentRequest(request);
        });
    }

    public static int mark() {
        return STATUS.size();
    }

    public static List<StatusMessage.Payload> since(int mark) {
        List<StatusMessage.Payload> all = new ArrayList<>(STATUS);
        return mark >= all.size() ? List.of() : List.copyOf(all.subList(Math.max(0, mark), all.size()));
    }

    public static Optional<StatusMessage.Payload> first(int mark, AutonomyStatus status) {
        return since(mark).stream().filter(p -> p.status() == status).findFirst();
    }

    public static boolean seen(int mark, AutonomyStatus status) {
        return first(mark, status).isPresent();
    }

    public static String describe(int mark) {
        List<String> out = new ArrayList<>();
        for (StatusMessage.Payload payload : since(mark)) {
            out.add(payload.status().name());
        }
        return out.isEmpty() ? "keine Meldung" : String.join(", ", out);
    }

    public static int consents() {
        return CONSENTS.size();
    }

    public static List<FollowConsent.Request> consentRequests() {
        return List.copyOf(CONSENTS);
    }
}
