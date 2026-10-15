package com.forcitis.one.cost.timesheet.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import java.time.LocalDate;

/**
 * The timesheet entered a round of approval: the business fact. Telling the approval module what it needs to run the round is a separate event,
 * DocumentSubmittedForApproval. The envelope's business date is the work date; submittedOn is the day the employee sent it.
 */
public record TimesheetSubmitted(EventEnvelope envelope, DocumentReference document, DocumentNumber number, EmployeeId employee,
                                 EmployeeId projectManager, Hours submittedHours, LocalDate submittedOn, RoundNumber round)
        implements DomainEvent {
}
