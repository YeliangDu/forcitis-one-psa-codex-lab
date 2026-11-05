package com.forcitis.one.kernel;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.attendance.domain.CalendarDay;
import com.forcitis.one.organization.attendance.api.DayKind;
import com.forcitis.one.organization.attendance.domain.WorkCalendarKey;
import com.forcitis.one.organization.attendance.domain.WorkCalendarYear;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.domain.LegalEntity;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttribution;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.UserAccount;
import com.forcitis.one.platform.numbering.domain.NumberingPattern;
import com.forcitis.one.platform.numbering.domain.NumberingRule;
import com.forcitis.one.platform.numbering.domain.SeededDocumentTypes;
import com.forcitis.one.platform.numbering.domain.TemplateVersion;
import com.forcitis.one.platform.numbering.domain.VersionStatus;
import com.forcitis.one.platform.numbering.api.ResetPolicy;
import com.forcitis.one.platform.numbering.api.NumberingRuleVersionId;
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
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.organization.workforce.api.AttributionId;
import com.forcitis.one.platform.numbering.api.NumberingRuleId;

/**
 * DATA_DESIGN_CHECKLIST section 14 item 3 (review H-5, guard 4): whatever operations are tried on an aggregate, the sequence numbers of its
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
        checkMany(() -> UserAccount.restore(TestAccounts.account(1), TestAccounts.employee(1), "ENABLED", Set.of(), null, 0, 0), List.of(
                UserAccount::enable, UserAccount::disable, a -> a.declareEnabled(true), a -> a.declareEnabled(false),
                a -> a.replaceRoles(Set.of(new RoleId(TestIds.uuid(2)), new RoleId(TestIds.uuid(3))))));
        checkMany(() -> UserAccount.open(TestAccounts.account(2), TestAccounts.employee(2), Set.of(new RoleId(TestIds.uuid(1))), com.forcitis.one.platform.identity.api.AccountOpeningRoute.HIRE), List.of(UserAccount::disable, UserAccount::enable));
    }

    @Test
    @DisplayName("Role: events are numbered 1..n")
    void role() {
        checkMany(() -> {
            Role role = Role.newCustom(new RoleId(TestIds.uuid(7)), new RoleCode("analyst"), "Analyst", Set.of());
            role.versionWritten(new RoleVersionId(TestIds.uuid(70)));
            return role;
        }, List.of(
                Role::enable, Role::disable, r -> r.declareEnabled(true), r -> r.revise("Renamed", AccessScopeKind.SELF, Set.of())));
    }

    @Test
    @DisplayName("EmergencyAdmin: events are numbered 1..n")
    void emergencyAdmin() {
        checkMany(() -> com.forcitis.one.platform.identity.domain.EmergencyAdmin.restore(TestAccounts.account(1), "breakglass", "hash", "sealed", 0, 0, null, true, 0, 0),
                List.of(a -> a.disable(com.forcitis.one.platform.identity.api.EmergencyAdminDisableCause.SEPARATION),
                        com.forcitis.one.platform.identity.domain.EmergencyAdmin::enable, a -> a.resetCredentials("other-hash", "other-sealed")));
    }

    @Test
    @DisplayName("AuthorizationRequest: events are numbered 1..n")
    void authorizationRequest() {
        checkMany(() -> com.forcitis.one.platform.identity.domain.AuthorizationRequest.replaceRoles(
                        new com.forcitis.one.platform.identity.api.AuthorizationRequestId(java.util.UUID.randomUUID()), TestAccounts.account(1), NOW, TestAccounts.account(2), Set.of("employee"),
                        new com.forcitis.one.platform.identity.domain.AuthorizationBase(1L, List.of())),
                List.of(r -> r.confirm(TestAccounts.account(3), NOW), r -> r.reject(TestAccounts.account(4), NOW)));
    }

    @Test
    @DisplayName("PermissionAdjustment: the event of raising it is numbered 1")
    void permissionAdjustment() {
        checkMany(() -> com.forcitis.one.platform.identity.domain.PermissionAdjustment.assignmentChangeReview(
                        new com.forcitis.one.platform.identity.api.PermissionAdjustmentId(java.util.UUID.randomUUID()), TestAccounts.account(1),
                        new com.forcitis.one.organization.workforce.api.PersonnelCommandId(java.util.UUID.randomUUID()),
                        com.forcitis.one.organization.workforce.api.TriggerFieldKind.ORG_UNIT, null,
                        new com.forcitis.one.organization.workforce.api.OrgUnitId(java.util.UUID.randomUUID()), null, null, NOW.plusSeconds(86_400), NOW),
                List.of());
    }

    @Test
    @DisplayName("LegalEntity: events are numbered 1..n")
    void legalEntity() {
        checkMany(() -> LegalEntity.register(new LegalEntityId(TestIds.uuid(1)), new LegalEntityCode("FST"), "Synthetic"), List.of(
                LegalEntity::activate, LegalEntity::deactivate, e -> e.rename("Other"), e -> e.rename("Synthetic"),
                e -> e.correctCode(new LegalEntityCode("FIT"), false), e -> e.declareStatus("ACTIVE")));
    }

    @Test
    @DisplayName("PersonnelCommand: events are numbered 1..n, continuing the employee's sequence")
    void personnelCommand() {
        checkMany(() -> com.forcitis.one.organization.workforce.domain.PersonnelCommand.enter(
                        new com.forcitis.one.organization.workforce.api.PersonnelCommandId(TestIds.uuid(1)), new EmployeeId(TestIds.uuid(2)),
                        new com.forcitis.one.organization.workforce.api.EnterPersonnelCommand(new EmployeeId(TestIds.uuid(2)),
                                com.forcitis.one.organization.workforce.api.PersonnelCommandKind.REGULARIZE, TODAY, null, null, null, null, null, null, null, null, null, null),
                        new UserId(TestIds.uuid(3)), NOW, TODAY, 7),
                List.of(c -> c.recordEffect(null, null, null), c -> c.withdraw(new UserId(TestIds.uuid(3)), "synthetic", NOW), c -> c.withdraw(new UserId(TestIds.uuid(3)), " ", NOW)));
    }

    @Test
    @DisplayName("OrgStructureCommand: events are numbered 1..n")
    void orgStructureCommand() {
        checkMany(() -> com.forcitis.one.organization.workforce.domain.OrgStructureCommand.enter(
                        new com.forcitis.one.organization.workforce.api.OrgStructureCommandId(TestIds.uuid(1)),
                        new com.forcitis.one.organization.workforce.api.EnterOrgStructureCommand(com.forcitis.one.organization.workforce.api.OrgStructureTargetKind.ORG_UNIT,
                                com.forcitis.one.organization.workforce.api.OrgStructureCommandKind.SET_ATTRIBUTES,
                                new com.forcitis.one.organization.workforce.api.OrgUnitId(TestIds.uuid(2)), null, TODAY, null, "Synthetic", null, null, null, null, null, null, null, null),
                        TestIds.uuid(4), new UserId(TestIds.uuid(3)), NOW, TODAY),
                List.of(com.forcitis.one.organization.workforce.domain.OrgStructureCommand::recordEffect, c -> c.withdraw(new UserId(TestIds.uuid(3)), "synthetic", NOW)));
    }

    @Test
    @DisplayName("EmployeeLegalEntityChange: the event continues the employee's sequence")
    void employeeLegalEntityChange() {
        checkMany(() -> com.forcitis.one.organization.workforce.domain.EmployeeLegalEntityChange.of(
                        new com.forcitis.one.organization.workforce.api.OrgStructureCommandId(TestIds.uuid(1)), TODAY,
                        new com.forcitis.one.organization.workforce.domain.OrgCountersignBasis(new EmployeeId(TestIds.uuid(2)),
                                new com.forcitis.one.kernel.LegalEntityId(TestIds.uuid(5)), new com.forcitis.one.kernel.LegalEntityId(TestIds.uuid(6))), 7),
                List.of(c -> { }));
    }

    @Test
    @DisplayName("StoredFile: no events, the lock version still moves once per save")
    void storedFile() {
        checkMany(() -> com.forcitis.one.platform.file.domain.StoredFile.restore(new com.forcitis.one.platform.file.api.FileId(TestIds.uuid(4)),
                        com.forcitis.one.platform.file.api.FileSubjectKind.USER, NOW.minus(java.time.Duration.ofDays(3)), false,
                        com.forcitis.one.platform.file.api.FileStatus.AVAILABLE, 0),
                List.of(f -> f.expire(NOW, java.time.Duration.ofDays(2))));
    }

    @Test
    @DisplayName("WorkCalendarYear: events are numbered 1..n")
    void workCalendarYear() {
        checkMany(() -> WorkCalendarYear.unmaintained(TestIds.uuid(3), WorkCalendarKey.defaultFor(2026)), List.of(
                y -> y.maintain(List.of(), LocalDate.of(2026, 6, 1), true),
                y -> y.maintain(List.of(new CalendarDay(LocalDate.of(2026, 5, 1), DayKind.HOLIDAY)), LocalDate.of(2026, 6, 1), true),
                y -> y.maintain(List.of(new CalendarDay(LocalDate.of(2027, 5, 1), DayKind.HOLIDAY)), LocalDate.of(2026, 6, 1), true)));
    }

    @Test
    @DisplayName("StandardHours: events are numbered 1..n")
    void standardHours() {
        checkMany(() -> com.forcitis.one.organization.attendance.domain.StandardHours.publish(TestIds.uuid(5), null,
                        com.forcitis.one.organization.attendance.domain.StandardHoursScopeTail.empty(), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1), 480, 420,
                        List.of(), "Synthetic basis", TestAccounts.account(1), NOW, new com.forcitis.one.platform.audit.api.AuditEntryId(TestIds.uuid(50))),
                List.of(v -> { }));
    }

    @Test
    @DisplayName("NumberingRule: events are numbered 1..n")
    void numberingRule() {
        NumberingPattern pattern = new NumberingPattern("{TYPE}-{LE}-{yyyyMM}-{seq:4}", ResetPolicy.MONTHLY);
        TemplateVersion seeded = new TemplateVersion(new NumberingRuleVersionId(TestIds.uuid(41)), 1, pattern, 1, LocalDate.of(2026, 10, 15), null, NOW, VersionStatus.PENDING_CONFIRMATION, null, null, null, null);
        checkMany(() -> NumberingRule.restore(new NumberingRuleId(TestIds.uuid(4)), SeededDocumentTypes.of("QUOTE"), List.of(seeded), 0, 0), List.of(
                r -> r.confirm(TestAccounts.account(2), NOW, LocalDate.of(2026, 10, 15), new RuleVersion(1)), r -> r.propose(new NumberingRuleVersionId(TestIds.uuid(61)), pattern, 5, LocalDate.of(2026, 10, 16), TestAccounts.account(3), NOW),
                r -> r.propose(new NumberingRuleVersionId(TestIds.uuid(62)), pattern, 0, LocalDate.of(2026, 10, 16), TestAccounts.account(3), NOW),
                r -> r.confirm(TestAccounts.account(3), NOW, LocalDate.of(2026, 10, 15), new RuleVersion(2)), r -> r.confirm(TestAccounts.account(4), NOW, LocalDate.of(2026, 10, 15), new RuleVersion(2))));
    }

    @Test
    @DisplayName("LegalEntityAttribution: events are numbered 1..n")
    void legalEntityAttribution() {
        Random dates = new Random(7);
        checkMany(() -> LegalEntityAttribution.empty(new AttributionId(TestIds.uuid(5)), AttributionSubject.of(TestOrgIds.orgUnit(3))), List.of(
                a -> a.attribute(new LegalEntityId(TestIds.uuid(1 + dates.nextInt(2))), LocalDate.of(2026, 1, 1).plusDays(dates.nextInt(400))),
                a -> a.end(LocalDate.of(2026, 1, 1).plusDays(dates.nextInt(800))),
                a -> a.correct(LocalDate.of(2026, 1, 1).plusDays(dates.nextInt(400)), LocalDate.of(2026, 1, 1).plusDays(dates.nextInt(800)),
                        new Reason("typo"), TestAccounts.account(1))));
    }

    @Test
    @DisplayName("SourceReference: events are numbered 1..n")
    void sourceReference() {
        var system = com.forcitis.one.platform.integration.api.ExternalSource.ACCOUNTING_SYSTEM;
        var kind = com.forcitis.one.platform.integration.api.SourceObjectKind.LEGAL_ENTITY;
        var own = new com.forcitis.one.platform.integration.api.OwnObjectId(TestIds.uuid(5));
        checkMany(() -> com.forcitis.one.platform.integration.domain.SourceReference.register(
                        new com.forcitis.one.platform.integration.domain.SourceReferenceId(TestIds.uuid(1)), system, kind,
                        new com.forcitis.one.platform.integration.api.SourceObjectId(1021), own, TODAY, TestAccounts.account(1)),
                List.of(r -> r.confirm(TestAccounts.account(2), NOW), r -> r.reject(TestAccounts.account(3), NOW), r -> r.supersede(TODAY.plusDays(30))));
    }

    @Test
    @DisplayName("SourceReferenceRefusal: it announces its one event at the first number")
    void sourceReferenceRefusal() {
        var refusal = com.forcitis.one.platform.integration.domain.SourceReferenceRefusal.of(
                new com.forcitis.one.platform.integration.domain.SourceReferenceId(TestIds.uuid(1)),
                com.forcitis.one.platform.integration.api.ExternalSource.ORG_SOURCE, com.forcitis.one.platform.integration.api.SourceObjectKind.EMPLOYEE);

        assertThat(refusal.drainEvents(TestEnvelopes.INSTANCE)).extracting(e -> e.envelope().aggregateVersion()).containsExactly(1L);
    }

    @Test
    @DisplayName("OutboundDelivery: it announces no event, so its sequence stays where it was while the lock version moves")
    void outboundDelivery() {
        checkMany(() -> com.forcitis.one.platform.integration.domain.OutboundDelivery.restore(
                        new com.forcitis.one.platform.integration.domain.OutboundDeliveryId(TestIds.uuid(1)), com.forcitis.one.platform.integration.api.DeliverySubject.of(com.forcitis.one.platform.integration.api.DeliveryPurpose.TASK_ASSIGNED, TestIds.uuid(42)),
                        List.of(new WecomUserId("alice")), "title", "description", null, "PENDING", 0, null, null, null,
                        com.forcitis.one.platform.integration.domain.DeliveryKind.CARD, null, java.util.UUID.randomUUID(), 0, 0),
                List.of(d -> d.markSent("0", NOW), d -> d.scheduleRetry(NOW, "later"), d -> d.deadLetter("gave up"), d -> d.skip("not allowed")));
    }
}
