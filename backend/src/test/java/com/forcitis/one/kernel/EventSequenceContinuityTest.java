package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.attendance.domain.CalendarDay;
import com.forcitis.one.organization.attendance.api.DayKind;
import com.forcitis.one.organization.attendance.domain.WorkCalendarKey;
import com.forcitis.one.organization.attendance.domain.WorkCalendarYear;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.domain.LegalEntity;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttribution;
import com.forcitis.one.organization.workforce.domain.RunStats;
import com.forcitis.one.organization.workforce.domain.SnapshotChange;
import com.forcitis.one.organization.workforce.domain.SyncRun;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.UserAccount;
import com.forcitis.one.platform.numbering.domain.NumberingPattern;
import com.forcitis.one.platform.numbering.domain.NumberingRule;
import com.forcitis.one.platform.numbering.domain.TemplateVersion;
import com.forcitis.one.platform.numbering.domain.VersionStatus;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import com.forcitis.one.platform.numbering.api.RuleVersion;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SESSION_PLAYBOOK section 14 item 3 (review H-5, guard 4): whatever operations are tried on an aggregate, the sequence numbers of its
 * events run 1..n without a gap, and a save without events does not move them. Illegal operations are refused and leave no trace.
 */
class EventSequenceContinuityTest {
    private static final Instant NOW = Instant.parse("2026-10-14T02:00:00Z");
    private static final int UNITS = 40;
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 1);

    private static <A extends AggregateRoot> void check(long seed, Supplier<A> fresh, List<Consumer<A>> operations) {
        Random random = new Random(seed);
        A aggregate = fresh.get();
        long expectedSequence = aggregate.eventSequence();
        long expectedVersion = aggregate.version();
        for (int unit = 0; unit < UNITS; unit++) {
            int attempts = random.nextInt(4);
            for (int i = 0; i < attempts; i++) {
                try {
                    operations.get(random.nextInt(operations.size())).accept(aggregate);
                } catch (DomainException | IllegalArgumentException refused) {
                    continue;
                }
            }
            List<DomainEvent> events = aggregate.drainEvents(TestEnvelopes.INSTANCE);
            for (DomainEvent event : events) {
                assertThat(event.envelope().aggregateVersion()).as("seed %d unit %d", seed, unit).isEqualTo(++expectedSequence);
            }
            assertThat(aggregate.eventSequence()).isEqualTo(expectedSequence);
            assertThat(aggregate.version()).as("the lock version moves once per save, with or without events").isEqualTo(++expectedVersion);
        }
    }

    private static <A extends AggregateRoot> void checkMany(Supplier<A> fresh, List<Consumer<A>> operations) {
        for (long seed = 1; seed <= 25; seed++) {
            check(seed, fresh, operations);
        }
    }

    @Test
    @DisplayName("UserAccount: events are numbered 1..n and a save without events leaves the sequence alone")
    void userAccount() {
        checkMany(() -> UserAccount.restore(new UserId(1), "ENABLED", Set.of(), 0, 0), List.of(
                UserAccount::enable, UserAccount::disable, a -> a.declareEnabled(true), a -> a.declareEnabled(false),
                a -> a.replaceRoles(Set.of(new RoleId(TestIds.uuid(2)), new RoleId(TestIds.uuid(3))))));
        checkMany(() -> UserAccount.open(new UserId(2), Set.of(new RoleId(TestIds.uuid(1)))), List.of(UserAccount::disable, UserAccount::enable));
    }

    @Test
    @DisplayName("Role: events are numbered 1..n")
    void role() {
        checkMany(() -> Role.newCustom(new RoleCode("analyst"), "Analyst", Set.of()).withId(new RoleId(TestIds.uuid(7))), List.of(
                Role::enable, Role::disable, r -> r.declareEnabled(true), r -> r.revise("Renamed", AccessScopeKind.SELF, Set.of())));
    }

    @Test
    @DisplayName("EmergencyAdmin: events are numbered 1..n")
    void emergencyAdmin() {
        checkMany(() -> com.forcitis.one.platform.identity.domain.EmergencyAdmin.restore(new UserId(1), "breakglass", "hash", "sealed", 0, 0, null, true, 0, 0),
                List.of(a -> a.disable(com.forcitis.one.platform.identity.api.EmergencyAdminDisableCause.SEPARATION),
                        com.forcitis.one.platform.identity.domain.EmergencyAdmin::enable, a -> a.resetCredentials("other-hash", "other-sealed")));
    }

    @Test
    @DisplayName("AuthorizationRequest: events are numbered 1..n")
    void authorizationRequest() {
        checkMany(() -> com.forcitis.one.platform.identity.domain.AuthorizationRequest.replaceRoles(
                        new com.forcitis.one.platform.identity.api.AuthorizationRequestId(java.util.UUID.randomUUID()), new UserId(1), NOW, new UserId(2), Set.of("employee")),
                List.of(r -> r.confirm(new UserId(3), NOW), r -> r.reject(new UserId(4), NOW)));
    }

    @Test
    @DisplayName("LegalEntity: events are numbered 1..n")
    void legalEntity() {
        checkMany(() -> LegalEntity.register(new LegalEntityCode("FST"), "Synthetic").withId(new LegalEntityId(TestIds.uuid(1))), List.of(
                LegalEntity::activate, LegalEntity::deactivate, e -> e.rename("Other"), e -> e.rename("Synthetic"),
                e -> e.correctCode(new LegalEntityCode("FIT"), false), e -> e.declareStatus("ACTIVE")));
    }

    @Test
    @DisplayName("SyncRun: events are numbered 1..n")
    void syncRun() {
        RunStats stats = new RunStats(1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0);
        checkMany(() -> SyncRun.restore(new SyncRunId(TestIds.uuid(1)), SyncTrigger.SCHEDULED, "RUNNING", NOW, null, null, null, null, null, null, null, 0, 0),
                List.of(r -> r.succeed(NOW, stats, new SnapshotChange(List.of(), List.of(), List.of()), LocalDate.of(2026, 10, 13)), r -> r.hold(NOW, stats, "shrunk"), r -> r.fail(NOW, "down"),
                        r -> r.release(null, new SyncRunId(TestIds.uuid(2)), NOW, null)));
    }

    @Test
    @DisplayName("WorkCalendarYear: events are numbered 1..n")
    void workCalendarYear() {
        checkMany(() -> WorkCalendarYear.unmaintained(WorkCalendarKey.defaultFor(2026)), List.of(
                y -> y.maintain(List.of(), LocalDate.of(2026, 6, 1), true),
                y -> y.maintain(List.of(new CalendarDay(LocalDate.of(2026, 5, 1), DayKind.HOLIDAY)), LocalDate.of(2026, 6, 1), true),
                y -> y.maintain(List.of(new CalendarDay(LocalDate.of(2027, 5, 1), DayKind.HOLIDAY)), LocalDate.of(2026, 6, 1), true)));
    }

    @Test
    @DisplayName("NumberingRule: events are numbered 1..n")
    void numberingRule() {
        NumberingPattern pattern = new NumberingPattern("{TYPE}-{LE}-{yyyyMM}-{seq:4}", ResetPolicy.MONTHLY);
        TemplateVersion seeded = new TemplateVersion(1, pattern, 1, LocalDate.of(2026, 10, 15), null, NOW, VersionStatus.PENDING_CONFIRMATION, null, null, null, null);
        checkMany(() -> NumberingRule.restore(DocumentType.QUOTE, List.of(seeded), 0, 0), List.of(
                r -> r.confirm(new UserId(2), NOW, LocalDate.of(2026, 10, 15), new RuleVersion(1)), r -> r.propose(pattern, 5, LocalDate.of(2026, 10, 16), new UserId(3), NOW),
                r -> r.propose(pattern, 0, LocalDate.of(2026, 10, 16), new UserId(3), NOW),
                r -> r.confirm(new UserId(3), NOW, LocalDate.of(2026, 10, 15), new RuleVersion(2)), r -> r.confirm(new UserId(4), NOW, LocalDate.of(2026, 10, 15), new RuleVersion(2))));
    }

    @Test
    @DisplayName("LegalEntityAttribution: events are numbered 1..n")
    void legalEntityAttribution() {
        Random dates = new Random(7);
        checkMany(() -> LegalEntityAttribution.empty(new AttributionSubject(AttributionKind.ORG_UNIT, 3)), List.of(
                a -> a.attribute(new LegalEntityId(TestIds.uuid(1 + dates.nextInt(2))), LocalDate.of(2026, 1, 1).plusDays(dates.nextInt(400))),
                a -> a.end(LocalDate.of(2026, 1, 1).plusDays(dates.nextInt(800))),
                a -> a.correct(LocalDate.of(2026, 1, 1).plusDays(dates.nextInt(400)), LocalDate.of(2026, 1, 1).plusDays(dates.nextInt(800)),
                        new Reason("typo"), new UserId(1))));
    }

    @Test
    @DisplayName("RegisteredEmployee: its one event is numbered after the stored sequence")
    void registeredEmployee() {
        UserId id = new UserId(42);
        var staff = new com.forcitis.one.organization.workforce.domain.StaffEmployee(
                new com.forcitis.one.organization.workforce.domain.Employee(id, "E42", "Synthetic", "synthetic", null, null, null,
                        com.forcitis.one.organization.workforce.api.EmploymentStatus.EMPLOYED, false),
                new com.forcitis.one.organization.workforce.domain.Assignment(id, new com.forcitis.one.organization.workforce.api.OrgUnitId(3), null, null));
        var registered = com.forcitis.one.organization.workforce.domain.RegisteredEmployee.onFirstLogin(staff, 4, 6);

        assertThat(registered.drainEvents(TestEnvelopes.INSTANCE)).extracting(e -> e.envelope().aggregateVersion()).containsExactly(7L);
        assertThat(registered.eventSequence()).isEqualTo(7);
        assertThat(registered.version()).isEqualTo(5);
    }

    @Test
    @DisplayName("OutboundDelivery: it announces no event, so its sequence stays where it was while the lock version moves")
    void outboundDelivery() {
        checkMany(() -> com.forcitis.one.platform.integration.domain.OutboundDelivery.restore(
                        new com.forcitis.one.platform.integration.domain.OutboundDeliveryId(TestIds.uuid(1)), "approval-task:42",
                        List.of(new WecomUserId("alice")), "title", "description", null, "PENDING", 0, null, null, null,
                        com.forcitis.one.platform.integration.domain.DeliveryKind.CARD, null, java.util.UUID.randomUUID(), 0, 0),
                List.of(d -> d.markSent("0", NOW), d -> d.scheduleRetry(NOW, "later"), d -> d.deadLetter("gave up"), d -> d.skip("not allowed")));
    }
}
