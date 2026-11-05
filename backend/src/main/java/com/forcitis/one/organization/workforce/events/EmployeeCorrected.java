package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A correction changed the employee's state as of today (department, position, employment status or manager; ADR-0068 item 1.9); a correction
 * of history only is audited, not published. The affected days run from affectedFrom up to but not including affectedTo, open-ended when
 * affectedTo is null.
 *
 * <p>triggerFieldKinds are the assignment fields whose value today differs after the correction from before it (empty when neither differs); the
 * departments and positions are the employee's assignment today before and after, null on a side where the employee has none, and the same on
 * both sides when the field did not change. A correction that makes a separated employee employed again lists ORG_UNIT only, never POSITION
 * (ADR-0082 decision 2).
 */
public record EmployeeCorrected(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate affectedFrom,
                                @Nullable LocalDate affectedTo, List<TriggerFieldKind> triggerFieldKinds, @Nullable OrgUnitId previousOrgUnitId,
                                @Nullable OrgUnitId newOrgUnitId, @Nullable PositionId previousPositionId, @Nullable PositionId newPositionId,
                                EmploymentRelationshipId employmentRelationshipId)
        implements DomainEvent {

    public EmployeeCorrected {
        triggerFieldKinds = List.copyOf(triggerFieldKinds);
    }
}
