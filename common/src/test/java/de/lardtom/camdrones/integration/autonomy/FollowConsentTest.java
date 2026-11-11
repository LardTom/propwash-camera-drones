package de.lardtom.camdrones.integration.autonomy;

import de.lardtom.camdrones.logic.nav.FollowController;
import de.lardtom.camdrones.net.FollowConsent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowConsentTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID DRONE = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final Identifier TOUR = Identifier.fromNamespaceAndPath("camdrones", "tour_fold_frame");
    private static final String TOUR_KEY = "camdrones.frame_short.tour_fold_frame";
    private static final Predicate<String> KNOWN = Set.of(TOUR_KEY, "part.otheraddon.big_frame")::contains;

    @Test
    void policyNeedsRuleAndConsentForOtherPlayers() {
        assertEquals(FollowPolicy.Verdict.ALLOWED, FollowPolicy.check(FollowPolicy.Target.self(), false, false));
        assertEquals(FollowPolicy.Verdict.RULE_OFF, FollowPolicy.check(FollowPolicy.Target.otherPlayer(), false, true));
        assertEquals(FollowPolicy.Verdict.NEEDS_CONSENT, FollowPolicy.check(FollowPolicy.Target.otherPlayer(), true, false));
        assertEquals(FollowPolicy.Verdict.ALLOWED, FollowPolicy.check(FollowPolicy.Target.otherPlayer(), true, true));
    }

    @Test
    void policyAllowsOwnAnimalsAndTheMountButNothingElse() {
        assertEquals(FollowPolicy.Verdict.ALLOWED, FollowPolicy.check(new FollowPolicy.Target(false, false, true, false, false), false, false));
        assertEquals(FollowPolicy.Verdict.ALLOWED, FollowPolicy.check(new FollowPolicy.Target(false, false, false, true, false), false, false));
        assertEquals(FollowPolicy.Verdict.NOT_ALLOWED, FollowPolicy.check(new FollowPolicy.Target(false, false, false, false, false), true, true));
        assertEquals(FollowPolicy.Verdict.NOT_ALLOWED, FollowPolicy.check(new FollowPolicy.Target(false, false, true, false, true), true, true),
                "eigenes Pferd mit fremdem Reiter ohne dessen Zustimmung");
        assertEquals(FollowPolicy.Verdict.NOT_ALLOWED, FollowPolicy.check(new FollowPolicy.Target(false, false, false, true, true), true, true),
                "Boot mit fremdem Mitfahrer ohne dessen Zustimmung");
        assertEquals(AutonomyStatus.FOLLOW_RULE_OFF, FollowPolicy.Verdict.RULE_OFF.status());
        assertEquals(AutonomyStatus.FOLLOW_REQUESTED, FollowPolicy.Verdict.NEEDS_CONSENT.status());
    }

    @Test
    void acceptedRequestGrantsConsentForThisSessionOnly() {
        FollowConsents consents = new FollowConsents(1000L);
        Object ownerSession = new Object();
        Object targetSession = new Object();
        FollowConsents.Request request = consents.request(OWNER, TARGET, DRONE, FollowController.Style.WATCH, ownerSession, targetSession, 0L);
        assertEquals(1, request.secondsLeft(0L));
        assertFalse(consents.granted(OWNER, TARGET, ownerSession, targetSession));
        FollowConsents.Request answered = consents.answer(TARGET, OptionalInt.of(request.id()), targetSession, 500L).orElseThrow();
        assertEquals(FollowController.Style.WATCH, answered.style());
        consents.grant(answered);
        assertTrue(consents.granted(OWNER, TARGET, ownerSession, targetSession));
        assertFalse(consents.granted(OWNER, TARGET, ownerSession, new Object()), "Zustimmung endet mit dem Ausloggen");
        assertFalse(consents.granted(OWNER, TARGET, ownerSession, targetSession), "nach neuer Sitzung verworfen");
        assertEquals(0, consents.grants());
    }

    @Test
    void answersMustComeFromTheTargetInTimeAndInTheSameSession() {
        FollowConsents consents = new FollowConsents(1000L);
        Object targetSession = new Object();
        FollowConsents.Request request = consents.request(OWNER, TARGET, DRONE, FollowController.Style.BEHIND, new Object(), targetSession, 0L);
        assertTrue(consents.answer(OWNER, OptionalInt.of(request.id()), targetSession, 10L).isEmpty(), "nur das Ziel antwortet");
        assertTrue(consents.answer(TARGET, OptionalInt.of(request.id()), new Object(), 10L).isEmpty(), "andere Sitzung");
        assertTrue(consents.answer(TARGET, OptionalInt.of(request.id()), targetSession, 1000L).isEmpty(), "abgelaufen");
        assertEquals(0, consents.pending());
    }

    @Test
    void latestOpenRequestIsAnsweredByCommand() {
        FollowConsents consents = new FollowConsents(10_000L);
        Object targetSession = new Object();
        consents.request(OWNER, TARGET, DRONE, FollowController.Style.BEHIND, new Object(), targetSession, 0L);
        FollowConsents.Request second = consents.request(OWNER, TARGET, DRONE, FollowController.Style.PARALLEL, new Object(), targetSession, 5L);
        assertEquals(1, consents.pending(), "eine Anfrage je Paar");
        assertEquals(second.id(), consents.answer(TARGET, OptionalInt.empty(), targetSession, 6L).orElseThrow().id());
    }

    @Test
    void revokeEndsAllConsentsAndRequestsOfTheTarget() {
        FollowConsents consents = new FollowConsents(10_000L);
        Object ownerSession = new Object();
        Object targetSession = new Object();
        FollowConsents.Request request = consents.request(OWNER, TARGET, DRONE, FollowController.Style.BEHIND, ownerSession, targetSession, 0L);
        consents.grant(request);
        consents.request(DRONE, TARGET, OWNER, FollowController.Style.BEHIND, new Object(), targetSession, 0L);
        List<UUID> requesters = consents.revoke(TARGET);
        assertEquals(List.of(OWNER), requesters);
        assertFalse(consents.granted(OWNER, TARGET, ownerSession, targetSession));
        assertEquals(0, consents.pending());
    }

    @Test
    void expiredRequestsAreReturnedOnce() {
        FollowConsents consents = new FollowConsents(100L);
        consents.request(OWNER, TARGET, DRONE, FollowController.Style.BEHIND, new Object(), new Object(), 0L);
        assertTrue(consents.expire(50L).isEmpty());
        assertEquals(1, consents.expire(100L).size());
        assertTrue(consents.expire(200L).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new FollowConsents(0L));
    }

    @Test
    void consentChatAndQuestionNameTheDroneByItsTranslatedFrameName() {
        Component name = DroneNames.component("", Optional.of(TOUR), DRONE, KNOWN);
        FollowConsent.Request request = FollowConsent.Request.of(7, "Steve", name, 30);
        assertEquals(new FollowConsent.Request(7, "Steve", TOUR_KEY, true, 30), request, "Schlüssel und Herkunft gehen an den Client");
        TranslatableContents chat = assertInstanceOf(TranslatableContents.class, AutonomyServer.consentMessage("Steve", name, 30).getContents());
        assertEquals(AutonomyServer.CONSENT_KEY + "chat", chat.getKey());
        assertEquals("Steve", chat.getArgs()[0]);
        assertFrameName(chat.getArgs()[1], TOUR_KEY, "Chat");
        assertEquals(30, chat.getArgs()[2]);
        TranslatableContents question = assertInstanceOf(TranslatableContents.class, AutonomyServer.consentQuestion(request).getContents());
        assertEquals(AutonomyServer.CONSENT_KEY + "question", question.getKey());
        assertEquals("Steve", question.getArgs()[0]);
        assertFrameName(question.getArgs()[1], TOUR_KEY, "Frage");
        assertEquals(30, question.getArgs()[2]);
        Component foreign = DroneNames.component("", Optional.of(Identifier.fromNamespaceAndPath("otheraddon", "big_frame")), DRONE, KNOWN);
        TranslatableContents other = assertInstanceOf(TranslatableContents.class,
                AutonomyServer.consentQuestion(FollowConsent.Request.of(8, "Steve", foreign, 30)).getContents());
        assertFrameName(other.getArgs()[1], "part.otheraddon.big_frame", "Frage, fremder Rahmen");
    }

    @Test
    void consentKeepsOwnNamesAndTheIdAsPlainTextEvenWhenTheyLookLikeKeys() {
        String id = DroneSelector.shortSerial(DRONE);
        for (String custom : List.of("Burg", TOUR_KEY, "gui.yes", "")) {
            Component name = DroneNames.component(custom, custom.isEmpty() ? Optional.empty() : Optional.of(TOUR), DRONE, KNOWN);
            String expected = custom.isEmpty() ? id : custom;
            FollowConsent.Request request = FollowConsent.Request.of(7, "Steve", name, 30);
            assertEquals(new FollowConsent.Request(7, "Steve", expected, false, 30), request, custom);
            TranslatableContents chat = assertInstanceOf(TranslatableContents.class, AutonomyServer.consentMessage("Steve", name, 30).getContents());
            TranslatableContents question = assertInstanceOf(TranslatableContents.class, AutonomyServer.consentQuestion(request).getContents());
            for (Object arg : List.of(chat.getArgs()[1], question.getArgs()[1])) {
                Component text = assertInstanceOf(Component.class, arg, "Drohnenname als Text");
                assertEquals(expected, assertInstanceOf(PlainTextContents.class, text.getContents(), "eigener Name bleibt Klartext: " + custom).text());
            }
        }
    }

    private static void assertFrameName(Object name, String key, String where) {
        Component text = assertInstanceOf(Component.class, name, where + ": Drohnenname als übersetzbarer Text statt des Schlüssels " + key);
        assertEquals(key, assertInstanceOf(TranslatableContents.class, text.getContents(), where).getKey());
    }
}
