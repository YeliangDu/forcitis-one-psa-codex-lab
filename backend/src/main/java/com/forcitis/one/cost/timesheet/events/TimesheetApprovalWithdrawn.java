package com.forcitis.one.cost.timesheet.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.RoundNumber;

/** The employee cancelled the approval: back to a draft, not counted as a refusal (RULE-279). */
public record TimesheetApprovalWithdrawn(EventEnvelope envelope, DocumentReference document, EmployeeId employee,
                                 Hours submittedHours, RoundNumber round, Reason reason) implements DomainEvent {
}
