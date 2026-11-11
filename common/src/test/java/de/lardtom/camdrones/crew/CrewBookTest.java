package de.lardtom.camdrones.crew;

import de.lardtom.camdrones.crew.model.CrewBook;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrewBookTest {
    private static final UUID DRONE = UUID.randomUUID();
    private static final UUID OTHER_DRONE = UUID.randomUUID();
    private static final UUID PILOT = UUID.randomUUID();
    private static final UUID OPERATOR = UUID.randomUUID();
    private static final UUID THIRD = UUID.randomUUID();

    private static CrewBook.Facts near(double distance) {
        return new CrewBook.Facts(true, true, true, distance, false);
    }

    private static final class FakeOperators implements CrewBook.Binder {
        final List<String> calls = new ArrayList<>();
        boolean accept = true;

        @Override
        public boolean bind(UUID drone, UUID operator) {
            this.calls.add(drone + ">" + operator);
            return this.accept;
        }
    }

    @Test
    void inviteAndAcceptBindsTheOperator() {
        CrewBook book = new CrewBook();
        FakeOperators operators = new FakeOperators();
        assertEquals(CrewBook.Verdict.OK, book.invite(DRONE, PILOT, OPERATOR, near(8.0), 1_000L));
        assertTrue(book.pending(OPERATOR, 2_000L).isPresent());
        assertEquals(CrewBook.Verdict.OK, book.accept(OPERATOR, near(9.0), 2_000L, operators));
        assertEquals(List.of(DRONE + ">" + OPERATOR), operators.calls);
        assertEquals(Optional.of(OPERATOR), book.operator(DRONE));
        assertEquals(Optional.of(DRONE), book.droneOf(OPERATOR));
        assertTrue(book.pending(OPERATOR, 2_000L).isEmpty());
    }

    @Test
    void distanceRuleAndGimbalAreChecked() {
        CrewBook book = new CrewBook();
        assertEquals(CrewBook.Verdict.TOO_FAR, book.invite(DRONE, PILOT, OPERATOR, near(16.01), 0L));
        assertEquals(CrewBook.Verdict.OK, book.invite(DRONE, PILOT, OPERATOR, near(16.0), 0L));
        assertEquals(CrewBook.Verdict.TOO_FAR, book.invite(DRONE, PILOT, THIRD, near(Double.POSITIVE_INFINITY), 0L));
        assertEquals(CrewBook.Verdict.RULE_OFF, book.invite(DRONE, PILOT, THIRD, new CrewBook.Facts(false, true, true, 2.0, false), 0L));
        assertEquals(CrewBook.Verdict.NO_GIMBAL, book.invite(DRONE, PILOT, THIRD, new CrewBook.Facts(true, false, true, 2.0, false), 0L));
        assertEquals(CrewBook.Verdict.NOT_ALLOWED, book.invite(DRONE, PILOT, THIRD, new CrewBook.Facts(true, true, false, 2.0, false), 0L));
        assertEquals(CrewBook.Verdict.NO_DRONE, book.invite(null, PILOT, THIRD, near(2.0), 0L));
        assertEquals(CrewBook.Verdict.OFFLINE, book.invite(DRONE, PILOT, null, near(2.0), 0L));
        assertEquals(CrewBook.Verdict.SELF, book.invite(DRONE, PILOT, PILOT, near(0.0), 0L));
        assertEquals(CrewBook.Verdict.BUSY, book.invite(DRONE, PILOT, THIRD, new CrewBook.Facts(true, true, true, 2.0, true), 0L));
    }

    @Test
    void acceptRechecksDistanceAndRule() {
        CrewBook book = new CrewBook();
        FakeOperators operators = new FakeOperators();
        book.invite(DRONE, PILOT, OPERATOR, near(4.0), 0L);
        assertEquals(CrewBook.Verdict.TOO_FAR, book.accept(OPERATOR, near(20.0), 10L, operators));
        assertEquals(CrewBook.Verdict.NO_INVITE, book.accept(OPERATOR, near(4.0), 10L, operators));
        book.invite(DRONE, PILOT, OPERATOR, near(4.0), 0L);
        assertEquals(CrewBook.Verdict.RULE_OFF, book.accept(OPERATOR, new CrewBook.Facts(false, true, true, 4.0, false), 10L, operators));
        book.invite(DRONE, PILOT, OPERATOR, near(4.0), 0L);
        assertEquals(CrewBook.Verdict.NOT_ALLOWED, book.accept(OPERATOR, new CrewBook.Facts(true, true, false, 4.0, false), 10L, operators),
                "Einladender fliegt die Drohne nicht mehr und besitzt sie nicht");
        assertTrue(operators.calls.isEmpty());
    }

    @Test
    void invitationsExpireAfterOneMinute() {
        CrewBook book = new CrewBook();
        book.invite(DRONE, PILOT, OPERATOR, near(4.0), 0L);
        book.invite(DRONE, PILOT, THIRD, near(4.0), 30_000L);
        assertTrue(book.pending(OPERATOR, CrewBook.INVITE_MILLIS - 1).isPresent());
        List<CrewBook.Invite> gone = book.expire(CrewBook.INVITE_MILLIS);
        assertEquals(1, gone.size());
        assertEquals(OPERATOR, gone.getFirst().invitee());
        assertTrue(book.pending(THIRD, CrewBook.INVITE_MILLIS).isPresent());
        assertEquals(CrewBook.Verdict.EXPIRED, book.accept(THIRD, near(4.0), 90_000L, new FakeOperators()));
    }

    @Test
    void refusedBindingLeavesNoOperatorAsInContractStateA() {
        CrewBook book = new CrewBook();
        FakeOperators operators = new FakeOperators();
        operators.accept = false;
        book.invite(DRONE, PILOT, OPERATOR, near(4.0), 0L);
        assertEquals(CrewBook.Verdict.REFUSED, book.accept(OPERATOR, near(4.0), 1L, operators));
        assertEquals(1, operators.calls.size());
        assertTrue(book.operator(DRONE).isEmpty());
        assertTrue(book.bindings().isEmpty());
    }

    @Test
    void oneOperatorPerDroneAndOneDronePerOperator() {
        CrewBook book = new CrewBook();
        FakeOperators operators = new FakeOperators();
        book.invite(DRONE, PILOT, OPERATOR, near(4.0), 0L);
        book.invite(DRONE, PILOT, THIRD, near(4.0), 0L);
        assertEquals(CrewBook.Verdict.OK, book.accept(OPERATOR, near(4.0), 1L, operators));
        assertTrue(book.pending(THIRD, 1L).isEmpty(), "andere Einladungen für dieselbe Drohne entfallen");
        assertEquals(CrewBook.Verdict.TAKEN, book.invite(DRONE, PILOT, THIRD, near(4.0), 2L));
        assertEquals(CrewBook.Verdict.ALREADY, book.invite(DRONE, PILOT, OPERATOR, near(4.0), 2L));
        assertEquals(CrewBook.Verdict.BUSY, book.invite(OTHER_DRONE, THIRD, OPERATOR, near(4.0), 2L));
    }

    @Test
    void declineLeaveAndRelease() {
        CrewBook book = new CrewBook();
        FakeOperators operators = new FakeOperators();
        book.invite(DRONE, PILOT, OPERATOR, near(4.0), 0L);
        assertEquals(Optional.of(PILOT), book.decline(OPERATOR).map(CrewBook.Invite::inviter));
        assertTrue(book.decline(OPERATOR).isEmpty());
        book.invite(DRONE, PILOT, OPERATOR, near(4.0), 0L);
        book.accept(OPERATOR, near(4.0), 0L, operators);
        assertEquals(Optional.of(DRONE), book.leave(OPERATOR));
        assertTrue(book.leave(OPERATOR).isEmpty());
        book.invite(DRONE, PILOT, OPERATOR, near(4.0), 0L);
        book.accept(OPERATOR, near(4.0), 0L, operators);
        assertEquals(Optional.of(OPERATOR), book.release(DRONE));
        assertTrue(book.release(DRONE).isEmpty());
    }

    @Test
    void forgettingAPlayerDropsTheirInvitations() {
        CrewBook book = new CrewBook();
        book.invite(DRONE, PILOT, OPERATOR, near(4.0), 0L);
        book.invite(OTHER_DRONE, THIRD, PILOT, near(4.0), 0L);
        book.forgetPlayer(PILOT);
        assertTrue(book.invites().isEmpty());
    }

    @Test
    void pilotOwnerOrAnyoneNextToAnUnownedParkedDroneMayInvite() {
        UUID noOwner = new UUID(0L, 0L);
        assertTrue(CrewService.mayInvite(PILOT, Optional.of(PILOT), noOwner, false), "Pilot auch aus der Ferne");
        assertTrue(CrewService.mayInvite(PILOT, Optional.empty(), PILOT, false), "Besitzer, auch ohne Pilot");
        assertTrue(CrewService.mayInvite(THIRD, Optional.empty(), noOwner, true), "abgestellt, ohne Besitzer, daneben");
        assertTrue(!CrewService.mayInvite(THIRD, Optional.empty(), noOwner, false), "ohne Besitzer, aber weit weg");
        assertTrue(!CrewService.mayInvite(THIRD, Optional.of(PILOT), noOwner, true), "fremder Pilot fliegt");
        assertTrue(!CrewService.mayInvite(THIRD, Optional.empty(), PILOT, true), "fremder Besitzer");
        assertTrue(CrewService.mayInvite(PILOT, Optional.of(PILOT), THIRD, false), "Pilot einer fremden Drohne");
    }

    @Test
    void onlyPilotOwnerOrTheInviterMayTakeTheCameraBack() {
        UUID noOwner = new UUID(0L, 0L);
        assertTrue(CrewService.controls(PILOT, Optional.of(PILOT), noOwner));
        assertTrue(CrewService.controls(PILOT, Optional.empty(), PILOT));
        assertTrue(!CrewService.controls(THIRD, Optional.empty(), noOwner), "ohne Besitzer darf nicht jeder zurücknehmen");
        assertTrue(!CrewService.controls(THIRD, Optional.empty(), null));
        CrewBook book = new CrewBook();
        book.invite(DRONE, THIRD, OPERATOR, near(4.0), 0L);
        book.accept(OPERATOR, near(4.0), 1L, new FakeOperators());
        assertEquals(Optional.of(THIRD), book.inviter(DRONE));
        book.release(DRONE);
        assertTrue(book.inviter(DRONE).isEmpty());
        book.invite(DRONE, THIRD, OPERATOR, near(4.0), 2L);
        book.accept(OPERATOR, near(4.0), 3L, new FakeOperators());
        book.leave(OPERATOR);
        assertTrue(book.inviter(DRONE).isEmpty());
    }

    @Test
    void aNewPilotWhoDidNotInviteEndsTheBinding() {
        UUID noOwner = new UUID(0L, 0L);
        assertEquals(null, CrewService.pilotChange(OPERATOR, PILOT, Optional.of(PILOT), noOwner), "Einladender startet");
        assertEquals(null, CrewService.pilotChange(OPERATOR, PILOT, Optional.of(THIRD), PILOT), "Besitzer startet");
        assertEquals(CrewService.End.NEW_PILOT, CrewService.pilotChange(OPERATOR, PILOT, Optional.of(THIRD), noOwner),
                "fremder Pilot bekommt keinen fremden Operator");
        assertEquals(CrewService.End.PILOT, CrewService.pilotChange(OPERATOR, OPERATOR, Optional.of(PILOT), PILOT), "Operator fliegt selbst");
    }

    @Test
    void onlyTheControllerMaySteerTheGimbal() {
        Optional<UUID> none = Optional.empty();
        assertTrue(CrewService.controller(none, Optional.of(PILOT), PILOT));
        assertTrue(!CrewService.controller(none, Optional.of(PILOT), OPERATOR));
        assertTrue(!CrewService.controller(none, none, PILOT));
        assertTrue(CrewService.controller(Optional.of(OPERATOR), Optional.of(PILOT), OPERATOR));
        assertTrue(!CrewService.controller(Optional.of(OPERATOR), Optional.of(PILOT), PILOT), "Pilot ohne Gimbal, solange ein Operator gebunden ist");
        assertTrue(CrewService.controller(Optional.of(OPERATOR), none, OPERATOR), "auch ohne Pilot (Mission am Server)");
    }

    private static CrewBook bound() {
        CrewBook book = new CrewBook();
        book.invite(DRONE, PILOT, OPERATOR, near(4.0), 0L);
        book.accept(OPERATOR, near(4.0), 0L, new FakeOperators());
        return book;
    }

    @Test
    void disconnectedOperatorKeepsTheCrewForTwoMinutes() {
        CrewBook book = bound();
        assertEquals(120_000L, CrewBook.GRACE_MILLIS);
        assertTrue(book.hold(DRONE, "Op", 10_000L));
        assertTrue(!book.hold(DRONE, "Op", 20_000L), "die Frist läuft nur einmal an");
        CrewBook.Wait wait = book.waiting(DRONE).orElseThrow();
        assertEquals(OPERATOR, wait.operator());
        assertEquals("Op", wait.name());
        assertEquals(130_000L, wait.until());
        assertEquals(Optional.of(OPERATOR), book.operator(DRONE), "Crew bleibt bestehen");
        assertEquals(Optional.of(PILOT), book.inviter(DRONE));
        assertEquals(CrewBook.Verdict.TAKEN, book.invite(DRONE, PILOT, THIRD, near(4.0), 20_000L), "Platz bleibt frei für den Operator");
        assertTrue(!wait.expired(129_999L));
        assertTrue(wait.expired(130_000L));
        assertEquals(60_000L, wait.remaining(70_000L));
        assertEquals(0L, wait.remaining(200_000L));
        assertTrue(!book.hold(OTHER_DRONE, "x", 0L), "ohne Operator keine Frist");
    }

    @Test
    void returningOperatorResumesAndReleaseOrLeaveEndTheWait() {
        CrewBook book = bound();
        book.hold(DRONE, "Op", 0L);
        assertTrue(book.resume(DRONE));
        assertTrue(book.waiting(DRONE).isEmpty());
        assertTrue(!book.resume(DRONE));
        assertEquals(Optional.of(OPERATOR), book.operator(DRONE));
        book.hold(DRONE, "Op", 0L);
        assertEquals(Optional.of(OPERATOR), book.release(DRONE), "Pilot nimmt die Kamera während der Frist zurück");
        assertTrue(book.waiting(DRONE).isEmpty());
        book = bound();
        book.hold(DRONE, "Op", 0L);
        assertEquals(Optional.of(DRONE), book.leave(OPERATOR));
        assertTrue(book.waiting(DRONE).isEmpty());
        book = bound();
        book.hold(DRONE, "Op", 0L);
        book.clear();
        assertTrue(book.waiting(DRONE).isEmpty() && book.bindings().isEmpty(), "Neustart vergisst Crew und Frist");
    }

    @Test
    void watchHoldsWaitsRestoresAndEndsAfterTheGrace() {
        Optional<CrewBook.Wait> none = Optional.empty();
        Optional<CrewBook.Wait> wait = Optional.of(new CrewBook.Wait(OPERATOR, "Op", 120_000L));
        assertEquals(CrewService.Watch.CHECK, CrewService.watch(true, true, none, 0L));
        assertEquals(CrewService.Watch.HOLD, CrewService.watch(false, true, none, 0L), "Trennen beendet die Crew nicht mehr sofort");
        assertEquals(CrewService.Watch.RESTORE, CrewService.watch(true, false, none, 0L), "schneller Wiederbeitritt zwischen zwei Prüfungen");
        assertEquals(CrewService.Watch.WAIT, CrewService.watch(false, true, wait, 119_999L));
        assertEquals(CrewService.Watch.RESTORE, CrewService.watch(true, true, wait, 60_000L));
        assertEquals(CrewService.Watch.EXPIRED, CrewService.watch(false, true, wait, 120_000L), "danach wie bisher beendet");
        assertEquals(CrewService.Watch.EXPIRED, CrewService.watch(true, true, wait, 120_000L));
    }

    @Test
    void refusedRebindKeepsTheGraceAndTellsOncePerReturn() {
        CrewBook book = bound();
        book.hold(DRONE, "Op", 10_000L);
        assertTrue(!book.waiting(DRONE).orElseThrow().back());
        assertTrue(book.refuse(DRONE, "Op", 15_000L), "erste Ablehnung nach der Rückkehr: Hinweis an Operator und Pilot");
        CrewBook.Wait wait = book.waiting(DRONE).orElseThrow();
        assertTrue(wait.back());
        assertEquals(130_000L, wait.until(), "die Frist läuft weiter und beginnt nicht neu");
        assertEquals(Optional.of(OPERATOR), book.operator(DRONE), "Crew bleibt bestehen");
        assertTrue(!book.refuse(DRONE, "Op", 16_000L), "jede weitere Ablehnung ohne neuen Hinweis");
        assertEquals(CrewService.Watch.RESTORE, CrewService.watch(true, true, book.waiting(DRONE), 16_000L), "Propwash wird jede Sekunde neu gefragt");
        book.away(DRONE);
        assertTrue(!book.waiting(DRONE).orElseThrow().back(), "Operator wieder getrennt");
        assertEquals(130_000L, book.waiting(DRONE).orElseThrow().until());
        assertTrue(book.refuse(DRONE, "Op", 20_000L), "neue Rückkehr, neuer Hinweis");
        assertTrue(book.resume(DRONE));
        assertTrue(book.waiting(DRONE).isEmpty());
    }

    @Test
    void refusedQuickRejoinStartsTheGrace() {
        CrewBook book = bound();
        assertTrue(book.refuse(DRONE, "Op", 5_000L), "Rückkehr zwischen zwei Prüfungen, Propwash lehnt ab");
        assertEquals(125_000L, book.waiting(DRONE).orElseThrow().until());
        assertTrue(book.waiting(DRONE).orElseThrow().back());
        assertTrue(!book.hold(DRONE, "Op", 6_000L));
        assertTrue(!new CrewBook().refuse(DRONE, "Op", 0L), "ohne Operator keine Frist");
    }

    @Test
    void expiryTextTellsWhetherTheOperatorCameBack() {
        assertEquals(CrewService.End.OFFLINE, CrewService.expired(false));
        assertEquals(CrewService.End.UNREACHABLE, CrewService.expired(true), "Operator ist zurück, Propwash lehnte die Bindung bis zum Ende ab");
        assertEquals(CrewTexts.EXPIRED_PILOT, CrewService.pilotText(CrewService.End.OFFLINE));
        assertEquals(CrewTexts.UNREACHABLE_PILOT, CrewService.pilotText(CrewService.End.UNREACHABLE));
        assertEquals(CrewTexts.ENDED_PILOT, CrewService.pilotText(CrewService.End.RULE_OFF));
        assertEquals(CrewTexts.UNREACHABLE_OPERATOR, CrewService.operatorText(CrewService.End.UNREACHABLE));
        assertEquals(CrewTexts.ENDED_OPERATOR, CrewService.operatorText(CrewService.End.OFFLINE));
        assertEquals(CrewTexts.ENDED_OPERATOR, CrewService.operatorText(CrewService.End.PILOT));
    }

    @Test
    void clockCountsDownInMinutesAndSeconds() {
        assertEquals("2:00", CrewTexts.clock(CrewBook.GRACE_MILLIS));
        assertEquals("2:00", CrewTexts.clock(119_001L));
        assertEquals("1:59", CrewTexts.clock(119_000L));
        assertEquals("0:05", CrewTexts.clock(4_200L));
        assertEquals("0:00", CrewTexts.clock(0L));
        assertEquals("0:00", CrewTexts.clock(-5L));
    }
}
