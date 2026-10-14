package com.forcitis.one.cost.timesheet.events;

import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Hours;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import java.time.LocalDate;
import java.util.List;

/**
 * The timesheet entered a round of approval; it carries what the approval needs to start, so nobody asks the timesheet back. The
 * envelope's business date is the work date; submittedOn is the day the employee sent it.
 */
public record TimesheetSubmitted(EventEnvelope envelope, DocumentReference document, DocumentNumber number, UserId employee,
                                 UserId projectManager, Hours submittedHours, LocalDate submittedOn,
                                 RoundNumber round, List<TimesheetRoutingFlag> routingFlags, ContentHash contentHash,
                                 ApprovalSummary summary) implements DomainEvent {

    public TimesheetSubmitted {
        routingFlags = List.copyOf(routingFlags);
    }
}
