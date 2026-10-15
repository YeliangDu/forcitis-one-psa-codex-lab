package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.RoutingFlag;
import com.forcitis.one.platform.approval.api.RoundNumber;
import java.util.List;

/**
 * A document's owner submitted it for a round of approval, in the words of this module: the owner's aggregate records it, this
 * module subscribes and starts the round. It carries all the approval needs to start, so nobody asks the owner back. The envelope
 * names the owner's aggregate and its legal entity; initiator is the user who submitted the document.
 */
public record DocumentSubmittedForApproval(EventEnvelope envelope, ProcessKey processKey, DocumentReference document, EmployeeId initiator,
                                           RoundNumber round, List<RoutingFlag> routingFlags, List<ApprovalField> formFields,
                                           ContentHash contentHash, ApprovalSummary summary) implements DomainEvent {

    public DocumentSubmittedForApproval {
        routingFlags = List.copyOf(routingFlags);
        formFields = List.copyOf(formFields);
    }
}
