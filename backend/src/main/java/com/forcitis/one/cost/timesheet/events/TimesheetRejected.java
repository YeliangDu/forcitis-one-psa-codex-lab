package com.forcitis.one.cost.timesheet.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.RoundNumber;

/** Final refusal; the hours do not stand. */
public record TimesheetRejected(EventEnvelope envelope, DocumentReference document, UserId employee,
                                Hours submittedHours, RoundNumber round, Reason reason) implements DomainEvent {
}
