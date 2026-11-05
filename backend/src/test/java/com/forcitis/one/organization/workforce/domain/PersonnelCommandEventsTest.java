package com.forcitis.one.organization.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.EmploymentPhaseKind;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.organization.workforce.events.EmployeeCorrected;
import com.forcitis.one.organization.workforce.events.EmployeeHired;
import com.forcitis.one.organization.workforce.events.EmployeeLegalEntityChanged;
import com.forcitis.one.organization.workforce.events.EmployeeRegularized;
import com.forcitis.one.organization.workforce.events.EmployeeReinstated;
import com.forcitis.one.organization.workforce.events.EmployeeUnpaidLeaveStarted;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import com.forcitis.one.organization.workforce.events.EmployeeTransferred;
import com.forcitis.one.organization.workforce.events.EmploymentRelationshipCorrected;
import com.forcitis.one.organization.workforce.events.EmploymentRelationshipEnded;
import com.forcitis.one.organization.workforce.events.EmploymentRelationshipPhaseChanged;
import com.forcitis.one.organization.workforce.events.EmploymentRelationshipStarted;
import com.forcitis.one.organization.workforce.events.EmploymentRelationshipUnpaidLeaveChanged;
import com.forcitis.one.organization.workforce.events.SeparationHeld;
import com.forcitis.one.organization.workforce.events.SeparationHoldReleased;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * IMP-1030-PAR-B3 (contract A, ADR-0068 item 1.9): the publisher test of EmployeeHired, EmployeeRegularized, EmployeeTransferred, EmployeeSeparated,
 * EmployeeReinstated and EmployeeCorrected. A command that takes effect records exactly its event, about the employee, dated by the effective date,
 * numbered after the employee's own event sequence; unpaid leave records none.
 */
class PersonnelCommandEventsTest {
    private static final EmploymentRelationshipId RELATIONSHIP = new EmploymentRelationshipId(UUID.fromString("019a0000-0000-7000-8000-0000000000e1"));
    private static final PersonnelHistory EMPTY_HISTORY = new PersonnelHistory(List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());

    static final Instant NOW = Instant.parse("2026-11-02T02:00:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 11, 2);
    static final EmployeeId EMPLOYEE = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000001"));
    static final OrgUnitId OLD_UNIT = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000b1"));
    static final OrgUnitId NEW_UNIT = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000b2"));
    static final PositionId POSITION = new PositionId(UUID.fromString("019a0000-0000-7000-8000-0000000000b3"));
    static final long SEQUENCE = 4;

    private static PersonnelCommand command(EnterPersonnelCommand request) {
        PersonnelCommand command = PersonnelCommand.enter(new PersonnelCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1")), EMPLOYEE, request,
                new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000aa")), NOW, DAY, SEQUENCE);
        if (command.kind() != PersonnelCommandKind.CHANGE_LEGAL_ENTITY) {
            command.bindTo(RELATIONSHIP);
        }
        return command;
    }

    private static List<DomainEvent> relationshipEvents(PersonnelCommand command) {
        command.recordEffect(null, null, null, EMPTY_HISTORY);
        return command.drainEvents(TestEnvelopes.INSTANCE).stream().filter(e -> e.getClass().getSimpleName().startsWith("EmploymentRelationship")).toList();
    }

    private static EnterPersonnelCommand request(PersonnelCommandKind kind, LocalDate effective, LocalDate lastWorkingDay, OrgUnitId unit, PositionId position,
                                                 Boolean feeEarner, EmploymentPhaseKind phase) {
        return new EnterPersonnelCommand(EMPLOYEE, kind, effective, lastWorkingDay, unit, position, null, feeEarner, phase, null, null, null, null);
    }

    private static DomainEvent published(PersonnelCommand command, VersionRow before) {
        return published(command, before, null);
    }

    private static DomainEvent published(PersonnelCommand command, VersionRow before, VersionRow after) {
        command.recordEffect(before, after, null, EMPTY_HISTORY);
        List<DomainEvent> events = command.drainEvents(TestEnvelopes.INSTANCE).stream().filter(e -> !e.getClass().getSimpleName().startsWith("EmploymentRelationship")).toList();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().envelope().aggregateType()).as("the envelope is about the employee").isEqualTo("Employee");
        assertThat(events.getFirst().envelope().aggregateId()).isEqualTo(EMPLOYEE.value());
        assertThat(events.getFirst().envelope().aggregateVersion()).as("numbered after the employee's own events").isEqualTo(SEQUENCE + 1);
        assertThat(events.getFirst().envelope().businessDate()).isEqualTo(command.effectiveDate());
        return events.getFirst();
    }

    @Test
    @DisplayName("IMP-1030-PAR-B3 each kind that takes effect publishes its own event with the command id, the employee and the effective date")
    void eachKindPublishesItsEvent() {
        PersonnelCommand hire = command(request(PersonnelCommandKind.HIRE, DAY, null, NEW_UNIT, POSITION, false, EmploymentPhaseKind.PROBATION));
        assertThat(published(hire, null)).isInstanceOfSatisfying(EmployeeHired.class, e -> {
            assertThat(e.personnelCommandId()).isEqualTo(hire.id());
            assertThat(e.employeeId()).isEqualTo(EMPLOYEE);
            assertThat(e.effectiveDate()).isEqualTo(DAY);
        });
        assertThat(published(command(request(PersonnelCommandKind.REGULARIZE, DAY, null, null, null, null, null)), null))
                .isInstanceOf(EmployeeRegularized.class);
        PersonnelCommand separation = command(request(PersonnelCommandKind.SEPARATE, null, DAY.minusDays(1), null, null, null, null));
        assertThat(published(separation, null)).isInstanceOfSatisfying(EmployeeSeparated.class, e -> assertThat(e.effectiveDate()).as("the day after the last working day").isEqualTo(DAY));
        assertThat(published(command(request(PersonnelCommandKind.REINSTATE, DAY, null, NEW_UNIT, POSITION, true, EmploymentPhaseKind.REGULAR)), null))
                .isInstanceOf(EmployeeReinstated.class);
        assertThat(published(command(request(PersonnelCommandKind.CORRECTION, DAY, null, NEW_UNIT, null, null, null)), null))
                .isInstanceOfSatisfying(EmployeeCorrected.class, e -> assertThat(e.affectedFrom()).isEqualTo(DAY));
    }

    @Test
    @DisplayName("IMP-1030-PAR-B3 a transfer names what changed and the department and position of the day before; the end of an unpaid leave publishes no person-level event")
    void aTransferCarriesItsTriggersAndTheEndOfLeaveNothing() {
        VersionRow before = new VersionRow(UUID.fromString("019a0000-0000-7000-8000-0000000000d1"), LocalDate.of(2026, 1, 1), null,
                Map.of(CommandEffects.ORG_UNIT, OLD_UNIT.value(), CommandEffects.POSITION, POSITION.value()), ChangeSource.MIGRATION, null, false);
        PersonnelCommand transfer = command(request(PersonnelCommandKind.TRANSFER, DAY, null, NEW_UNIT, null, null, null));

        assertThat(published(transfer, before)).isInstanceOfSatisfying(EmployeeTransferred.class, e -> {
            assertThat(e.triggerFieldKinds()).containsExactly(TriggerFieldKind.ORG_UNIT);
            assertThat(e.previousOrgUnitId()).isEqualTo(OLD_UNIT);
            assertThat(e.newOrgUnitId()).isEqualTo(NEW_UNIT);
            assertThat(e.previousPositionId()).isEqualTo(POSITION);
            assertThat(e.newPositionId()).as("the position did not change").isEqualTo(POSITION);
        });
        PersonnelCommand leaveEnd = command(request(PersonnelCommandKind.UNPAID_LEAVE_END, DAY, null, null, null, null, null));
        assertThat(relationshipEvents(leaveEnd)).singleElement().isInstanceOfSatisfying(EmploymentRelationshipUnpaidLeaveChanged.class,
                e -> assertThat(e.commandKind()).isEqualTo(PersonnelCommandKind.UNPAID_LEAVE_END));
    }

    @Test
    @DisplayName("#1372 each kind that changes a relationship publishes its relationship event with the relationship id and the day")
    void eachRelationshipChangePublishesItsEvent() {
        assertThat(relationshipEvents(command(request(PersonnelCommandKind.HIRE, DAY, null, NEW_UNIT, POSITION, false, EmploymentPhaseKind.PROBATION))))
                .singleElement().isInstanceOfSatisfying(EmploymentRelationshipStarted.class, e -> {
                    assertThat(e.employmentRelationshipId()).isEqualTo(RELATIONSHIP);
                    assertThat(e.effectiveDate()).isEqualTo(DAY);
                });
        assertThat(relationshipEvents(command(request(PersonnelCommandKind.REINSTATE, DAY, null, NEW_UNIT, POSITION, true, EmploymentPhaseKind.REGULAR))))
                .singleElement().isInstanceOf(EmploymentRelationshipStarted.class);
        assertThat(relationshipEvents(command(request(PersonnelCommandKind.REGULARIZE, DAY, null, null, null, null, null))))
                .singleElement().isInstanceOfSatisfying(EmploymentRelationshipPhaseChanged.class, e -> assertThat(e.phaseKind()).isEqualTo(EmploymentPhaseKind.REGULAR));
        assertThat(relationshipEvents(command(request(PersonnelCommandKind.SEPARATE, null, DAY.minusDays(1), null, null, null, null))))
                .singleElement().isInstanceOfSatisfying(EmploymentRelationshipEnded.class, e -> assertThat(e.effectiveDate()).isEqualTo(DAY));
        assertThat(relationshipEvents(command(request(PersonnelCommandKind.UNPAID_LEAVE_START, DAY, null, null, null, null, null))))
                .singleElement().isInstanceOfSatisfying(EmploymentRelationshipUnpaidLeaveChanged.class,
                        e -> assertThat(e.commandKind()).isEqualTo(PersonnelCommandKind.UNPAID_LEAVE_START));
        assertThat(relationshipEvents(command(request(PersonnelCommandKind.CORRECTION, DAY, null, NEW_UNIT, null, null, null))))
                .as("a correction of the assignment alone changes nothing at the relationship").isEmpty();
        assertThat(relationshipEvents(command(new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.CORRECTION, DAY, null, null, null, null, true, null, null, null, null, null))))
                .singleElement().isInstanceOf(EmploymentRelationshipCorrected.class);
    }

    @Test
    @DisplayName("#918 the start of an unpaid leave publishes EmployeeUnpaidLeaveStarted with the command id, the employee and the first day on leave")
    void theStartOfLeavePublishesItsEvent() {
        PersonnelCommand leave = command(request(PersonnelCommandKind.UNPAID_LEAVE_START, DAY, null, null, null, null, null));

        assertThat(published(leave, null)).isInstanceOfSatisfying(EmployeeUnpaidLeaveStarted.class, e -> {
            assertThat(e.personnelCommandId()).isEqualTo(leave.id());
            assertThat(e.employeeId()).isEqualTo(EMPLOYEE);
            assertThat(e.effectiveDate()).isEqualTo(DAY);
        });
    }

    private static VersionRow assignment(OrgUnitId unit, PositionId position) {
        java.util.Map<String, Object> values = new java.util.HashMap<>();
        values.put(CommandEffects.ORG_UNIT, unit.value());
        values.put(CommandEffects.POSITION, position == null ? null : position.value());
        return new VersionRow(UUID.fromString("019a0000-0000-7000-8000-0000000000d2"), LocalDate.of(2026, 1, 1), null, values, ChangeSource.MIGRATION, null, false);
    }

    private static EmployeeCorrected corrected(VersionRow before, VersionRow after) {
        return (EmployeeCorrected) published(command(request(PersonnelCommandKind.CORRECTION, DAY, null, NEW_UNIT, null, null, null)), before, after);
    }

    @Test
    @DisplayName("#1089 a correction that changes today's department lists ORG_UNIT with the department of today before and after; one that changes the position lists POSITION")
    void aCorrectionNamesWhatChangedToday() {
        PositionId other = new PositionId(UUID.fromString("019a0000-0000-7000-8000-0000000000b4"));

        EmployeeCorrected unitOnly = corrected(assignment(OLD_UNIT, POSITION), assignment(NEW_UNIT, POSITION));
        assertThat(unitOnly.triggerFieldKinds()).containsExactly(TriggerFieldKind.ORG_UNIT);
        assertThat(unitOnly.previousOrgUnitId()).isEqualTo(OLD_UNIT);
        assertThat(unitOnly.newOrgUnitId()).isEqualTo(NEW_UNIT);
        assertThat(unitOnly.previousPositionId()).isEqualTo(POSITION);
        assertThat(unitOnly.newPositionId()).isEqualTo(POSITION);

        EmployeeCorrected both = corrected(assignment(OLD_UNIT, POSITION), assignment(NEW_UNIT, other));
        assertThat(both.triggerFieldKinds()).containsExactly(TriggerFieldKind.ORG_UNIT, TriggerFieldKind.POSITION);
        assertThat(both.previousPositionId()).isEqualTo(POSITION);
        assertThat(both.newPositionId()).isEqualTo(other);
    }

    @Test
    @DisplayName("#1089 a correction that changes neither department nor position publishes an empty list and the same ids on both sides; no assignment today on a side leaves that side null")
    void aCorrectionOfSomethingElseListsNothing() {
        EmployeeCorrected manager = corrected(assignment(OLD_UNIT, POSITION), assignment(OLD_UNIT, POSITION));
        assertThat(manager.triggerFieldKinds()).isEmpty();
        assertThat(manager.previousOrgUnitId()).isEqualTo(OLD_UNIT);
        assertThat(manager.newOrgUnitId()).isEqualTo(OLD_UNIT);

        EmployeeCorrected nobody = corrected(null, null);
        assertThat(nobody.triggerFieldKinds()).isEmpty();
        assertThat(nobody.previousOrgUnitId()).isNull();
        assertThat(nobody.newPositionId()).isNull();

        EmployeeCorrected noPositionBefore = corrected(assignment(OLD_UNIT, null), assignment(OLD_UNIT, POSITION));
        assertThat(noPositionBefore.triggerFieldKinds()).containsExactly(TriggerFieldKind.POSITION);
        assertThat(noPositionBefore.previousPositionId()).isNull();
    }

    @Test
    @DisplayName("#1089 (ADR-0082 decision 2) a correction that undoes a separation lists ORG_UNIT only, never POSITION, with the previous ids null and the new ones set")
    void undoingASeparationListsTheDepartmentOnly() {
        EmployeeCorrected undone = corrected(null, assignment(NEW_UNIT, POSITION));

        assertThat(undone.triggerFieldKinds()).containsExactly(TriggerFieldKind.ORG_UNIT);
        assertThat(undone.previousOrgUnitId()).isNull();
        assertThat(undone.previousPositionId()).isNull();
        assertThat(undone.newOrgUnitId()).isEqualTo(NEW_UNIT);
        assertThat(undone.newPositionId()).isEqualTo(POSITION);
    }

    @Test
    @DisplayName("IMP-1030-PAR-B6 (ADR-0068 item 2): a held separation records SeparationHeld and a release records SeparationHoldReleased, both about the employee and dated by the original effective date")
    void aHoldAndItsReleaseAreEventsOfTheEmployee() {
        PersonnelCommand separation = command(request(PersonnelCommandKind.SEPARATE, null, DAY.minusDays(1), null, null, null, null));
        SeparationHoldId hold = new SeparationHoldId(UUID.fromString("019a0000-0000-7000-8000-0000000000e1"));
        separation.holdAtEntry();

        separation.recordHeld(hold, null);
        assertThat(separation.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(SeparationHeld.class, e -> {
            assertThat(e.separationHoldId()).isEqualTo(hold);
            assertThat(e.effectiveDate()).isEqualTo(DAY);
            assertThat(e.envelope().aggregateType()).isEqualTo("Employee");
        });

        UserId releaser = new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000bb"));
        separation.releaseHeld(NOW, DAY);
        separation.recordEffect(null, null, null, EMPTY_HISTORY);
        separation.recordReleased(hold, releaser, null);
        List<DomainEvent> events = separation.drainEvents(TestEnvelopes.INSTANCE);
        assertThat(events).hasSize(3);
        assertThat(events.get(0)).isInstanceOf(EmployeeSeparated.class);
        assertThat(events.get(1)).isInstanceOf(EmploymentRelationshipEnded.class);
        assertThat(events.get(2)).isInstanceOfSatisfying(SeparationHoldReleased.class, e -> {
            assertThat(e.releasedBy()).isEqualTo(releaser);
            assertThat(e.effectiveDate()).as("the original effective date").isEqualTo(DAY);
        });
        assertThat(events.get(2).envelope().aggregateVersion()).as("numbered after the separation events").isEqualTo(events.get(1).envelope().aggregateVersion() + 1);
    }

    @Test
    @DisplayName("#895 (ADR-0073 item 3.7): a command that waited for the countersign publishes EmployeeLegalEntityChanged once, after its own event, with the new entity in the envelope")
    void aCountersignedCommandPublishesTheLegalEntityChange() {
        LegalEntityId previous = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000f1"));
        LegalEntityId next = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000f2"));
        PersonnelCommand change = command(new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.CHANGE_LEGAL_ENTITY, DAY, null,
                null, null, null, null, null, next, null, null, null, null, null));
        change.requireCountersign(previous, next);
        assertThat(change.status()).isEqualTo(CommandStatus.AWAITING_ATTRIBUTION_COUNTERSIGN);
        assertThat(change.hasPendingEvents()).as("nothing is published while it waits").isFalse();

        change.completeCountersign(NOW, DAY);
        change.recordEffect(null, null, next, EMPTY_HISTORY);

        assertThat(change.status()).isEqualTo(CommandStatus.EFFECTIVE);
        assertThat(change.drainEvents(TestEnvelopes.INSTANCE)).singleElement().isInstanceOfSatisfying(EmployeeLegalEntityChanged.class, e -> {
            assertThat(e.personnelCommandId()).isEqualTo(change.id());
            assertThat(e.employeeId()).isEqualTo(EMPLOYEE);
            assertThat(e.effectiveDate()).isEqualTo(DAY);
            assertThat(e.previousLegalEntityId()).isEqualTo(previous);
            assertThat(e.newLegalEntityId()).isEqualTo(next);
            assertThat(e.envelope().legalEntityId()).isEqualTo(next);
        });
    }

    @Test
    @DisplayName("#895 ck_personnel_commands_correction_immediate: a correction dated after today becomes effective when its countersign completes, never pending")
    void aFutureCorrectionIsEffectiveWhenCountersigned() {
        LegalEntityId next = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000f2"));
        PersonnelCommand correction = command(new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.CORRECTION, DAY.plusDays(3), null,
                null, null, null, null, null, next, null, null, null, null, null));
        correction.requireCountersign(new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000f1")), next);

        correction.completeCountersign(NOW, DAY);

        assertThat(correction.status()).isEqualTo(CommandStatus.EFFECTIVE);
        assertThat(correction.appliedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("#895 user answer Q2: a countersigned command can be asked to be withdrawn only while pending and before its effective day; on the day itself the versions are in force")
    void aWithdrawalRequestIsAllowedBeforeTheDayOnly() {
        LegalEntityId previous = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000f1"));
        LegalEntityId next = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000f2"));
        PersonnelCommand later = PersonnelCommand.restore(new PersonnelCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c2")), EMPLOYEE,
                PersonnelCommandKind.CHANGE_LEGAL_ENTITY, DAY.plusDays(1), CommandStatus.PENDING, null, null, null, null, null, next, previous, next, null, null,
                new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000aa")), NOW, null, 0, 0);
        PersonnelCommand sameDay = PersonnelCommand.restore(new PersonnelCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c3")), EMPLOYEE,
                PersonnelCommandKind.CHANGE_LEGAL_ENTITY, DAY, CommandStatus.PENDING, null, null, null, null, null, next, previous, next, null, null,
                new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000aa")), NOW, null, 0, 0);

        later.requireWithdrawalAllowed("plan changed", DAY);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sameDay.requireWithdrawalAllowed("plan changed", DAY))
                .isInstanceOf(com.forcitis.one.kernel.DomainException.class);
    }
}
