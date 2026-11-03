package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.PendingEvent;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.organization.workforce.events.EmployeeCorrected;
import com.forcitis.one.organization.workforce.events.EmployeeHired;
import com.forcitis.one.organization.workforce.events.EmployeeLegalEntityChanged;
import com.forcitis.one.organization.workforce.events.EmployeeRegularized;
import com.forcitis.one.organization.workforce.events.EmployeeReinstated;
import com.forcitis.one.organization.workforce.events.EmployeeSeparated;
import com.forcitis.one.organization.workforce.events.EmployeeTransferred;
import com.forcitis.one.organization.workforce.events.SeparationHeld;
import com.forcitis.one.organization.workforce.events.SeparationHoldReleased;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The event of each kind of command that took effect (ADR-0068 item 1.9, contract A); the envelope is about the employee, dated by the effective date. */
final class PersonnelCommandEvents {

    private PersonnelCommandEvents() {
    }

    static Optional<PendingEvent> of(PersonnelCommand c, @Nullable VersionRow assignmentBefore, @Nullable VersionRow assignmentAfter, @Nullable LegalEntityId legalEntity) {
        if (c.isOnConcurrentPost()) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (c.kind()) {
            case HIRE -> (PendingEvent) (envelopes, sequence) -> new EmployeeHired(envelopes.wrap(draft("EmployeeHired", c, legalEntity, sequence)), c.id(),
                    c.employeeId(), c.effectiveDate());
            case REGULARIZE -> (envelopes, sequence) -> new EmployeeRegularized(envelopes.wrap(draft("EmployeeRegularized", c, legalEntity, sequence)), c.id(),
                    c.employeeId(), c.effectiveDate());
            case SEPARATE -> (envelopes, sequence) -> new EmployeeSeparated(envelopes.wrap(draft("EmployeeSeparated", c, legalEntity, sequence)), c.id(),
                    c.employeeId(), c.effectiveDate());
            case REINSTATE -> (envelopes, sequence) -> new EmployeeReinstated(envelopes.wrap(draft("EmployeeReinstated", c, legalEntity, sequence)), c.id(),
                    c.employeeId(), c.effectiveDate());
            case CORRECTION -> corrected(c, assignmentBefore, assignmentAfter, legalEntity);
            case TRANSFER -> transferred(c, assignmentBefore, legalEntity);
            case UNPAID_LEAVE_START, UNPAID_LEAVE_END, CHANGE_LEGAL_ENTITY, START_CONCURRENT, END_CONCURRENT -> null;
        });
    }

    /** The cost legal entity changed when the command took effect (ADR-0073 item 3.7), whatever the kind; the envelope names the new entity. */
    static PendingEvent legalEntityChanged(PersonnelCommand c, LegalEntityId previous, LegalEntityId next) {
        return (envelopes, sequence) -> new EmployeeLegalEntityChanged(envelopes.wrap(draft("EmployeeLegalEntityChanged", c, next, sequence)), c.id(), null, c.employeeId(),
                c.effectiveDate(), previous, next);
    }

    static PendingEvent held(PersonnelCommand c, SeparationHoldId hold, @Nullable LegalEntityId legalEntity) {
        return (envelopes, sequence) -> new SeparationHeld(envelopes.wrap(draft("SeparationHeld", c, legalEntity, sequence)), c.id(), c.employeeId(),
                c.effectiveDate(), hold);
    }

    static PendingEvent released(PersonnelCommand c, SeparationHoldId hold, @Nullable UserId releasedBy, @Nullable LegalEntityId legalEntity) {
        return (envelopes, sequence) -> new SeparationHoldReleased(envelopes.wrap(draft("SeparationHoldReleased", c, legalEntity, sequence)), c.id(),
                c.employeeId(), c.effectiveDate(), hold, releasedBy);
    }

    private static PendingEvent transferred(PersonnelCommand c, @Nullable VersionRow before, @Nullable LegalEntityId legalEntity) {
        OrgUnitId previousUnit = unitOf(before);
        PositionId previousPosition = positionOf(before);
        OrgUnitId newUnit = c.targetOrgUnitId() != null ? c.targetOrgUnitId() : previousUnit;
        PositionId newPosition = c.targetPositionId() != null ? c.targetPositionId() : previousPosition;
        List<TriggerFieldKind> triggers = new ArrayList<>();
        if (c.targetOrgUnitId() != null && !c.targetOrgUnitId().equals(previousUnit)) {
            triggers.add(TriggerFieldKind.ORG_UNIT);
        }
        if (c.targetPositionId() != null && !c.targetPositionId().equals(previousPosition)) {
            triggers.add(TriggerFieldKind.POSITION);
        }
        OrgUnitId from = Objects.requireNonNull(previousUnit != null ? previousUnit : newUnit, "department");
        OrgUnitId to = Objects.requireNonNull(newUnit, "department");
        return (envelopes, sequence) -> new EmployeeTransferred(envelopes.wrap(draft("EmployeeTransferred", c, legalEntity, sequence)), c.id(), c.employeeId(),
                c.effectiveDate(), triggers, from, to, previousPosition, newPosition);
    }

    private static PendingEvent corrected(PersonnelCommand c, @Nullable VersionRow before, @Nullable VersionRow after, @Nullable LegalEntityId legalEntity) {
        OrgUnitId previousUnit = unitOf(before);
        OrgUnitId newUnit = unitOf(after);
        PositionId previousPosition = positionOf(before);
        PositionId newPosition = positionOf(after);
        List<TriggerFieldKind> triggers = new ArrayList<>();
        if (!Objects.equals(previousUnit, newUnit)) {
            triggers.add(TriggerFieldKind.ORG_UNIT);
        }
        // Reinstating by correction gives the employee a position too, but it is one review of the department (ADR-0082 decision 2).
        if (before != null && !Objects.equals(previousPosition, newPosition)) {
            triggers.add(TriggerFieldKind.POSITION);
        }
        return (envelopes, sequence) -> new EmployeeCorrected(envelopes.wrap(draft("EmployeeCorrected", c, legalEntity, sequence)), c.id(), c.employeeId(),
                c.effectiveDate(), null, triggers, previousUnit, newUnit, previousPosition, newPosition);
    }

    private static @Nullable OrgUnitId unitOf(@Nullable VersionRow row) {
        return row == null ? null : new OrgUnitId((UUID) row.values().get(CommandEffects.ORG_UNIT));
    }

    private static @Nullable PositionId positionOf(@Nullable VersionRow row) {
        return row == null || row.values().get(CommandEffects.POSITION) == null ? null : new PositionId((UUID) row.values().get(CommandEffects.POSITION));
    }

    private static EventDraft draft(String name, PersonnelCommand c, @Nullable LegalEntityId legalEntity, long sequence) {
        return new EventDraft("organization.workforce." + name, 1, "Employee", c.employeeId().value(), sequence, legalEntity, c.effectiveDate(), List.of());
    }
}
