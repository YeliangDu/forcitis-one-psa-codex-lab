package com.forcitis.one.organization.workforce.events;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * A correction changed the employee's state as of today (department, position, employment status or manager; ADR-0068 item 1.9); a correction
 * of history only is audited, not published. The affected days run from affectedFrom up to but not including affectedTo, open-ended when
 * affectedTo is null.
 */
public record EmployeeCorrected(EventEnvelope envelope, PersonnelCommandId personnelCommandId, EmployeeId employeeId, LocalDate affectedFrom,
                                @Nullable LocalDate affectedTo) implements DomainEvent {
}
