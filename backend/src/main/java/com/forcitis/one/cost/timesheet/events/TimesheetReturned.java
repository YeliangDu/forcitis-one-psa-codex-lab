package com.forcitis.one.cost.timesheet.events;

import com.forcitis.one.delivery.execution.api.WorkPackageId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;

/** Sent back for changes (by an approver or by the employee's own withdrawal); the employee revises and submits the next round. */
public record TimesheetReturned(EventEnvelope envelope, DocumentReference document, UserId employee, WorkPackageId workPackageId,
                                Hours submittedHours, int round, String reason) implements DomainEvent {
}
