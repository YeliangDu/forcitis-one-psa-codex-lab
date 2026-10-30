package com.forcitis.one.organization.workforce.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.api.EmploymentPhaseKind;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.organization.workforce.events.EmployeeCorrected;
import com.forcitis.one.organization.workforce.events.EmployeeHired;
import com.forcitis.one.organization.workforce.events.EmployeeRegularized;
import com.forcitis.one.organization.workforce.events.EmployeeReinstated;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import com.forcitis.one.organization.workforce.events.EmployeeTransferred;
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
    static final Instant NOW = Instant.parse("2026-11-02T02:00:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 11, 2);
    static final EmployeeId EMPLOYEE = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000001"));
    static final OrgUnitId OLD_UNIT = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000b1"));
    static final OrgUnitId NEW_UNIT = new OrgUnitId(UUID.fromString("019a0000-0000-7000-8000-0000000000b2"));
    static final PositionId POSITION = new PositionId(UUID.fromString("019a0000-0000-7000-8000-0000000000b3"));
    static final long SEQUENCE = 4;

    private static PersonnelCommand command(PersonnelCommandKind kind, EnterPersonnelCommand request) {
        return PersonnelCommand.enter(new PersonnelCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1")), request,
                new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000aa")), NOW, DAY, SEQUENCE);
    }

    private static EnterPersonnelCommand request(PersonnelCommandKind kind, LocalDate effective, LocalDate lastWorkingDay, OrgUnitId unit, PositionId position,
                                                 Boolean feeEarner, EmploymentPhaseKind phase) {
        return new EnterPersonnelCommand(EMPLOYEE, kind, effective, lastWorkingDay, unit, position, null, feeEarner, phase);
    }

    private static DomainEvent published(PersonnelCommand command, VersionRow before) {
        command.recordEffect(before, null);
        List<DomainEvent> events = command.drainEvents(TestEnvelopes.INSTANCE);
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
        PersonnelCommand hire = command(PersonnelCommandKind.HIRE, request(PersonnelCommandKind.HIRE, DAY, null, NEW_UNIT, POSITION, false, EmploymentPhaseKind.PROBATION));
        assertThat(published(hire, null)).isInstanceOfSatisfying(EmployeeHired.class, e -> {
            assertThat(e.personnelCommandId()).isEqualTo(hire.id());
            assertThat(e.employeeId()).isEqualTo(EMPLOYEE);
            assertThat(e.effectiveDate()).isEqualTo(DAY);
        });
        assertThat(published(command(PersonnelCommandKind.REGULARIZE, request(PersonnelCommandKind.REGULARIZE, DAY, null, null, null, null, null)), null))
                .isInstanceOf(EmployeeRegularized.class);
        PersonnelCommand separation = command(PersonnelCommandKind.SEPARATE, request(PersonnelCommandKind.SEPARATE, null, DAY.minusDays(1), null, null, null, null));
        assertThat(published(separation, null)).isInstanceOfSatisfying(EmployeeSeparated.class, e -> assertThat(e.effectiveDate()).as("the day after the last working day").isEqualTo(DAY));
        assertThat(published(command(PersonnelCommandKind.REINSTATE, request(PersonnelCommandKind.REINSTATE, DAY, null, NEW_UNIT, POSITION, true, EmploymentPhaseKind.REGULAR)), null))
                .isInstanceOf(EmployeeReinstated.class);
        assertThat(published(command(PersonnelCommandKind.CORRECTION, request(PersonnelCommandKind.CORRECTION, DAY, null, NEW_UNIT, null, null, null)), null))
                .isInstanceOfSatisfying(EmployeeCorrected.class, e -> assertThat(e.affectedFrom()).isEqualTo(DAY));
    }

    @Test
    @DisplayName("IMP-1030-PAR-B3 a transfer names what changed and the department and position of the day before; unpaid leave publishes nothing")
    void aTransferCarriesItsTriggersAndUnpaidLeaveNothing() {
        VersionRow before = new VersionRow(UUID.fromString("019a0000-0000-7000-8000-0000000000d1"), LocalDate.of(2026, 1, 1), null,
                Map.of(CommandEffects.ORG_UNIT, OLD_UNIT.value(), CommandEffects.POSITION, POSITION.value()), ChangeSource.MIGRATION, null, false);
        PersonnelCommand transfer = command(PersonnelCommandKind.TRANSFER, request(PersonnelCommandKind.TRANSFER, DAY, null, NEW_UNIT, null, null, null));

        assertThat(published(transfer, before)).isInstanceOfSatisfying(EmployeeTransferred.class, e -> {
            assertThat(e.triggerFieldKinds()).containsExactly(TriggerFieldKind.ORG_UNIT);
            assertThat(e.previousOrgUnitId()).isEqualTo(OLD_UNIT);
            assertThat(e.newOrgUnitId()).isEqualTo(NEW_UNIT);
            assertThat(e.previousPositionId()).isEqualTo(POSITION);
            assertThat(e.newPositionId()).as("the position did not change").isEqualTo(POSITION);
        });
        PersonnelCommand leave = command(PersonnelCommandKind.UNPAID_LEAVE_START, request(PersonnelCommandKind.UNPAID_LEAVE_START, DAY, null, null, null, null, null));
        leave.recordEffect(null, null);
        assertThat(leave.hasPendingEvents()).isFalse();
    }

    @Test
    @DisplayName("IMP-1030-PAR-B6 (ADR-0068 item 2): a held separation records SeparationHeld and a release records SeparationHoldReleased, both about the employee and dated by the original effective date")
    void aHoldAndItsReleaseAreEventsOfTheEmployee() {
        PersonnelCommand separation = command(PersonnelCommandKind.SEPARATE, request(PersonnelCommandKind.SEPARATE, null, DAY.minusDays(1), null, null, null, null));
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
        separation.recordEffect(null, null);
        separation.recordReleased(hold, releaser, null);
        List<DomainEvent> events = separation.drainEvents(TestEnvelopes.INSTANCE);
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(EmployeeSeparated.class);
        assertThat(events.get(1)).isInstanceOfSatisfying(SeparationHoldReleased.class, e -> {
            assertThat(e.releasedBy()).isEqualTo(releaser);
            assertThat(e.effectiveDate()).as("the original effective date").isEqualTo(DAY);
        });
        assertThat(events.get(1).envelope().aggregateVersion()).as("numbered after the separation event").isEqualTo(events.get(0).envelope().aggregateVersion() + 1);
    }
}
