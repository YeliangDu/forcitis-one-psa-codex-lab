package com.forcitis.one.cost.timesheet.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.RoundNumber;

/** Sent back for changes (by an approver or by the employee's own withdrawal); the employee revises and submits the next round. */
public record TimesheetReturned(EventEnvelope envelope, DocumentReference document, UserId employee,
                                Hours submittedHours, RoundNumber round, Reason reason) implements DomainEvent {
}
