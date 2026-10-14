package com.forcitis.one.cost.timesheet.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.RoundNumber;

/** Every route approved: the hours stand. The envelope's business date is the work date. */
public record TimesheetApproved(EventEnvelope envelope, DocumentReference document, UserId employee,
                                Hours submittedHours, RoundNumber round) implements DomainEvent {
}
