package com.forcitis.one.cost.timesheet.events;

import com.forcitis.one.delivery.execution.api.WorkPackageId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;

/** The timesheet entered a round of approval; the envelope's business date is the work date. */
public record TimesheetSubmitted(EventEnvelope envelope, DocumentReference document, UserId employee, ApprovalInstanceId approvalInstanceId,
                                 WorkPackageId workPackageId, Hours submittedHours, int round) implements DomainEvent {
}
