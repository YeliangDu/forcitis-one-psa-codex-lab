package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #732 part B (user decision "加，同离职一样", DD sign-off section C, ADR-0068 items 2.2, 2.3, 3): taking a super_admin or security_auditor grant
 * away by hand (replace-roles, two-person) and disabling an account are floor-protected like a separation. Refused when the available super
 * administrators left would be 0 (only the two-person operations command may then proceed); one card and an ALERT log line when 1 is left; each
 * card has the subject, purpose and trigger of DD section C and names nobody. The people in a HELD separation, and those released with a date in
 * the future, are not counted. Pool and desk: see {@link RevokeDisableFloorSupport}.
 */
class RevokeDisableFloorIT extends RevokeDisableFloorSupport {

    static final TestDatabase postgres = TestPostgres.database(RevokeDisableFloorIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private void assertNothingSent(int mark) {
        assertThat(cardsAfterwards(0)).as("no floor card").isEmpty();
        assertThat(alertsSince(mark)).as("no ALERT line").isEmpty();
    }

    // ---- manual revoke ----

    @Test
    @DisplayName("#732B revoke at 0: taking the only available super_admin grant away is refused (409/422), nothing is written: no grant closed, no audit row, account unchanged, no card, no alert; held desk administrators are not counted")
    void revokeAtZeroIsRefusedAndWritesNothing() throws Exception {
        Desk desk = desk();
        Person a = admin();
        forgetCards();
        int mark = logMark();

        Outcome outcome = revoke(desk.requester(), desk.confirmer(), a, SUPER_ADMIN);

        assertRefusedByTheFloor(outcome);
        assertThat(snapshot(a)).as("nothing changed since just before the applying call").isEqualTo(outcome.beforeApply());
        assertThat(holds(a, SUPER_ADMIN)).isTrue();
        assertNothingSent(mark);
    }

    @Test
    @DisplayName("#732B revoke at 1: done; exactly one card, subject ACCOUNT_ROLE_GRANT = the closed super_admin grant id, purpose ROLE_REVOCATION_NOTICE, no trigger; recipients are the usable super_admin and security_auditor holders each once (not the held desk); one ALERT; no names")
    void revokeAtOneIsDoneWithOneCard() throws Exception {
        Desk desk = desk();
        Person a = admin();
        Person b = admin(AUDITOR);
        Person u = auditor();
        forgetCards();
        int mark = logMark();

        Outcome outcome = revoke(desk.requester(), desk.confirmer(), a, SUPER_ADMIN);

        assertDone(outcome);
        assertThat(holds(a, SUPER_ADMIN)).isFalse();
        UUID closed = closedGrant(a, SUPER_ADMIN);
        List<Card> cards = cardsAfterwards(1);
        assertThat(cards).hasSize(1);
        Card card = cards.get(0);
        assertThat(card.subjectKind()).isEqualTo(ACCOUNT_ROLE_GRANT);
        assertThat(card.subjectId()).isEqualTo(closed);
        assertThat(card.purpose()).isEqualTo(ROLE_REVOCATION_NOTICE);
        assertThat(card.trigger()).isEqualTo(Optional.empty());
        assertThat(card.recipients()).as("B holds both roles and receives once").isEqualTo(wecomIds(b, u).stream().sorted().toList());
        assertNoPersonalData(card);
        assertThat(alertsSince(mark)).as("one ALERT line").hasSize(1);
    }

    @Test
    @DisplayName("#732B revoke at 2 or more: done, no card, no alert")
    void revokeAtTwoOrMoreSendsNothing() throws Exception {
        Desk desk = desk();
        Person a = admin();
        admin();
        admin();
        forgetCards();
        int mark = logMark();

        assertDone(revoke(desk.requester(), desk.confirmer(), a, SUPER_ADMIN));

        assertThat(holds(a, SUPER_ADMIN)).isFalse();
        assertNothingSent(mark);
    }

    @Test
    @DisplayName("#732B one act closing a person's super_admin and security_auditor grants gives one card, about the super_admin grant (DD section C)")
    void oneActClosingTwoAdministratorGrantsGivesOneCardAboutSuperAdmin() throws Exception {
        Desk desk = desk();
        Person a = admin(AUDITOR);
        admin();
        forgetCards();

        assertDone(revoke(desk.requester(), desk.confirmer(), a, SUPER_ADMIN, AUDITOR));

        UUID superAdminGrant = closedGrant(a, SUPER_ADMIN);
        closedGrant(a, AUDITOR);
        List<Card> cards = cardsAfterwards(1);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).subjectId()).isEqualTo(superAdminGrant);
        assertThat(cards.get(0).purpose()).isEqualTo(ROLE_REVOCATION_NOTICE);
    }

    @Test
    @DisplayName("#732B ordinary roles are unaffected at 0 and at 1 available super administrators: revoking business_admin is done, no card, no alert (doubtful: ordinary revoke may be single-person)")
    void revokingAnOrdinaryRoleIsUnaffected() throws Exception {
        Desk desk = desk();
        Person x = account(ORDINARY_ROLE);
        forgetCards();
        int mark = logMark();

        assertDone(revoke(desk.requester(), desk.confirmer(), x, ORDINARY_ROLE));
        assertThat(holds(x, ORDINARY_ROLE)).isFalse();
        assertNothingSent(mark);

        admin();
        Person y = account(ORDINARY_ROLE);
        assertDone(revoke(desk.requester(), desk.confirmer(), y, ORDINARY_ROLE));
        assertThat(holds(y, ORDINARY_ROLE)).isFalse();
        assertNothingSent(mark);
    }

    @Test
    @DisplayName("#732B held and released-pending administrators are not counted: with A and a released-pending P the revoke of A is refused; with B added it is done, one card to B and P (a released-pending person counts as gone but still receives)")
    void releasedPendingAdministratorsCountAsGoneButReceive() throws Exception {
        Desk desk = desk();
        Person a = admin();
        Person p = admin();
        Person u = auditor();
        releasedPending(p, u);
        forgetCards();
        int mark = logMark();

        Outcome refused = revoke(desk.requester(), desk.confirmer(), a, SUPER_ADMIN);

        assertRefusedByTheFloor(refused);
        assertThat(snapshot(a)).isEqualTo(refused.beforeApply());
        assertNothingSent(mark);

        Person b = admin();
        forgetCards();
        assertDone(revoke(desk.requester(), desk.confirmer(), a, SUPER_ADMIN));
        List<Card> cards = cardsAfterwards(1);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).recipients()).isEqualTo(wecomIds(b, p, u).stream().sorted().toList());
    }

    @Test
    @DisplayName("#732B auditor-only holder, literal rule (count of super administrators after the act): at 1 the revoke is done with one card about the security_auditor grant; at 0 it is refused (doubtful point 3)")
    void auditorOnlyHolderFollowsTheLiteralRule() throws Exception {
        Desk desk = desk();
        Person u = auditor();
        Person v = auditor();
        forgetCards();

        Outcome atZero = revoke(desk.requester(), desk.confirmer(), u, AUDITOR);

        assertRefusedByTheFloor(atZero);
        assertThat(snapshot(u)).isEqualTo(atZero.beforeApply());
        assertThat(cardsAfterwards(0)).isEmpty();

        admin();
        assertDone(revoke(desk.requester(), desk.confirmer(), u, AUDITOR));
        List<Card> cards = cardsAfterwards(1);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).subjectId()).isEqualTo(closedGrant(u, AUDITOR));
        assertThat(cards.get(0).purpose()).isEqualTo(ROLE_REVOCATION_NOTICE);
        assertThat(v).isNotNull();
    }

    // ---- disable ----

    @Test
    @DisplayName("#732B disable at 0: refused (409/422), nothing written: status and version unchanged, no audit row, no card, no alert")
    void disableAtZeroIsRefusedAndWritesNothing() throws Exception {
        Desk desk = desk();
        Person a = admin();
        forgetCards();
        int mark = logMark();

        Outcome outcome = disable(desk.requester(), a);

        assertRefusedByTheFloor(outcome);
        assertThat(snapshot(a)).isEqualTo(outcome.beforeApply());
        assertThat(accountStatus(a)).isEqualTo("ENABLED");
        assertNothingSent(mark);
    }

    @Test
    @DisplayName("#732B disable at 1: done; exactly one card, subject USER_ACCOUNT = the user id, purpose ACCOUNT_DISABLE_NOTICE, trigger = that disable's audit entry id; recipients the usable holders each once; one ALERT; no names")
    void disableAtOneIsDoneWithOneCard() throws Exception {
        Desk desk = desk();
        Person a = admin();
        Person b = admin(AUDITOR);
        Person u = auditor();
        Set<UUID> auditBefore = accountAuditIds(a);
        forgetCards();
        int mark = logMark();

        assertDone(disable(desk.requester(), a));

        assertThat(accountStatus(a)).isEqualTo("DISABLED");
        List<Card> cards = cardsAfterwards(1);
        assertThat(cards).hasSize(1);
        Card card = cards.get(0);
        assertThat(card.subjectKind()).isEqualTo(USER_ACCOUNT);
        assertThat(card.subjectId()).isEqualTo(a.userId());
        assertThat(card.purpose()).isEqualTo(ACCOUNT_DISABLE_NOTICE);
        assertThat(card.trigger()).contains(disableAuditEntry(a, auditBefore));
        assertThat(card.recipients()).isEqualTo(wecomIds(b, u).stream().sorted().toList());
        assertNoPersonalData(card);
        assertThat(alertsSince(mark)).hasSize(1);
    }

    @Test
    @DisplayName("#732B disable at 2 or more: done, no card, no alert")
    void disableAtTwoOrMoreSendsNothing() throws Exception {
        Desk desk = desk();
        Person a = admin();
        admin();
        admin();
        forgetCards();
        int mark = logMark();

        assertDone(disable(desk.requester(), a));

        assertThat(accountStatus(a)).isEqualTo("DISABLED");
        assertNothingSent(mark);
    }

    @Test
    @DisplayName("#732B disabling an ordinary account is unaffected at 0 and at 1: done, no card, no alert")
    void disablingAnOrdinaryAccountIsUnaffected() throws Exception {
        Desk desk = desk();
        Person x = account(ORDINARY_ROLE);
        forgetCards();
        int mark = logMark();

        assertDone(disable(desk.requester(), x));
        assertNothingSent(mark);

        admin();
        Person y = account();
        assertDone(disable(desk.requester(), y));
        assertNothingSent(mark);
    }

    @Test
    @DisplayName("#732B disabling the same account twice at 1 remaining (re-enabled in between) gives two cards, each with its own disable's audit entry as trigger (perEvent)")
    void disablingTheSameAccountTwiceGivesTwoCards() throws Exception {
        Desk desk = desk();
        Person a = admin();
        admin();
        forgetCards();
        Set<UUID> before = accountAuditIds(a);

        assertDone(disable(desk.requester(), a));
        UUID first = disableAuditEntry(a, before);
        assertThat(cardsAfterwards(1)).hasSize(1);
        assertDone(enable(desk.requester(), a));
        Set<UUID> betweenAudit = accountAuditIds(a);
        assertDone(disable(desk.requester(), a));
        UUID second = disableAuditEntry(a, betweenAudit);

        List<Card> cards = cardsAfterwards(2);
        assertThat(first).isNotEqualTo(second);
        assertThat(cards).hasSize(2);
        assertThat(cards).extracting(Card::subjectId).containsOnly(a.userId());
        assertThat(cards).extracting(card -> card.trigger().orElseThrow()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    @DisplayName("#732B disabling an auditor-only account, literal rule: at 1 super administrator one card (USER_ACCOUNT, ACCOUNT_DISABLE_NOTICE) (doubtful point 3)")
    void disablingAnAuditorOnlyAccountAtOneFollowsTheLiteralRule() throws Exception {
        Desk desk = desk();
        admin();
        Person u = auditor();
        forgetCards();

        assertDone(disable(desk.requester(), u));

        List<Card> cards = cardsAfterwards(1);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).subjectId()).isEqualTo(u.userId());
        assertThat(cards.get(0).purpose()).isEqualTo(ACCOUNT_DISABLE_NOTICE);
    }

    // ---- the operations command ----

    @Test
    @DisplayName("#732B the two-person operations command revoke-role may take the last available super_admin grant away (user decision: only that command proceeds at 0): exit 0, grant closed by OPERATIONS; whether it alerts at 1 is not specified (doubtful point 4)")
    void theOperationsCommandMayProceedAtZero() throws Exception {
        desk();
        Person a = admin();

        int exit = revokeByOperations(a, SUPER_ADMIN);

        assertThat(exit).as(lastOperationsText).isZero();
        assertThat(holds(a, SUPER_ADMIN)).isFalse();
        assertThat(grantsOf(a, SUPER_ADMIN).get(0).get("revocation_cause_kind")).isEqualTo("OPERATIONS");
    }

    // ---- decision 172337: an entered, not yet started unpaid-leave start counts as gone ----

    @Test
    @DisplayName("#732B decision 172337 revoke: A and B are the only available super administrators and B has an entered future UNPAID_LEAVE_START: revoking A is refused, nothing written")
    void revokeIsRefusedWhenTheOtherHasAFutureUnpaidLeave() throws Exception {
        Desk desk = desk();
        Person a = admin();
        Person b = admin();
        enterFutureUnpaidLeave(b);
        forgetCards();
        int mark = logMark();

        Outcome outcome = revoke(desk.requester(), desk.confirmer(), a, SUPER_ADMIN);

        assertRefusedByTheFloor(outcome);
        assertThat(snapshot(a)).isEqualTo(outcome.beforeApply());
        assertThat(holds(a, SUPER_ADMIN)).isTrue();
        assertNothingSent(mark);
    }

    @Test
    @DisplayName("#732B decision 172337 revoke: with a third super administrator C the same revoke is done, one card (ROLE_REVOCATION_NOTICE) to C among the recipients, one ALERT")
    void revokeWithAThirdIsDoneWithACard() throws Exception {
        Desk desk = desk();
        Person a = admin();
        Person b = admin();
        Person c = admin();
        enterFutureUnpaidLeave(b);
        forgetCards();
        int mark = logMark();

        assertDone(revoke(desk.requester(), desk.confirmer(), a, SUPER_ADMIN));

        List<Card> cards = cardsAfterwards(1);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).purpose()).isEqualTo(ROLE_REVOCATION_NOTICE);
        assertThat(cards.get(0).recipients()).contains(c.wecomId());
        assertThat(alertsSince(mark)).hasSize(1);
    }

    @Test
    @DisplayName("#732B decision 172337 disable: A and B only, B has an entered future UNPAID_LEAVE_START: disabling A is refused, nothing written")
    void disableIsRefusedWhenTheOtherHasAFutureUnpaidLeave() throws Exception {
        Desk desk = desk();
        Person a = admin();
        Person b = admin();
        enterFutureUnpaidLeave(b);
        forgetCards();
        int mark = logMark();

        Outcome outcome = disable(desk.requester(), a);

        assertRefusedByTheFloor(outcome);
        assertThat(snapshot(a)).isEqualTo(outcome.beforeApply());
        assertThat(accountStatus(a)).isEqualTo("ENABLED");
        assertNothingSent(mark);
    }

    @Test
    @DisplayName("#732B decision 172337 disable: with a third super administrator C the same disable is done, one card (ACCOUNT_DISABLE_NOTICE) to C among the recipients, one ALERT")
    void disableWithAThirdIsDoneWithACard() throws Exception {
        Desk desk = desk();
        Person a = admin();
        Person b = admin();
        Person c = admin();
        enterFutureUnpaidLeave(b);
        forgetCards();
        int mark = logMark();

        assertDone(disable(desk.requester(), a));

        List<Card> cards = cardsAfterwards(1);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).purpose()).isEqualTo(ACCOUNT_DISABLE_NOTICE);
        assertThat(cards.get(0).recipients()).contains(c.wecomId());
        assertThat(alertsSince(mark)).hasSize(1);
    }

    @Test
    @DisplayName("#732B decision 172337: a withdrawn future unpaid-leave start does not count: with A and B only, revoking A is done with one card and an ALERT")
    void aWithdrawnFutureUnpaidLeaveDoesNotCountForRevoke() throws Exception {
        Desk desk = desk();
        Person a = admin();
        Person b = admin();
        withdrawCommand(enterFutureUnpaidLeave(b));
        forgetCards();
        int mark = logMark();

        assertDone(revoke(desk.requester(), desk.confirmer(), a, SUPER_ADMIN));

        assertThat(cardsAfterwards(1)).hasSize(1);
        assertThat(alertsSince(mark)).hasSize(1);
    }

    @Test
    @DisplayName("#732B decision 172337: a withdrawn future unpaid-leave start does not count: with A and B only, disabling A is done with one card and an ALERT")
    void aWithdrawnFutureUnpaidLeaveDoesNotCountForDisable() throws Exception {
        Desk desk = desk();
        Person a = admin();
        Person b = admin();
        withdrawCommand(enterFutureUnpaidLeave(b));
        forgetCards();
        int mark = logMark();

        assertDone(disable(desk.requester(), a));

        assertThat(cardsAfterwards(1)).hasSize(1);
        assertThat(alertsSince(mark)).hasSize(1);
    }
}
