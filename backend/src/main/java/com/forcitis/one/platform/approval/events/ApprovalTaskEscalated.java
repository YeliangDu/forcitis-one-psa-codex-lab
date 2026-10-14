package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.EscalationTarget;
import com.forcitis.one.platform.approval.api.NodeKey;
import java.util.List;

/** Still unhandled after the escalation point: handed to the approvers' superiors, or to the process administrator when none is known. */
public record ApprovalTaskEscalated(EventEnvelope envelope, ApprovalTaskId taskId, ApprovalInstanceId instanceId, DocumentReference document,
                                    NodeKey nodeKey, List<UserId> escalatedTo, EscalationTarget target) implements DomainEvent {

    public ApprovalTaskEscalated {
        escalatedTo = List.copyOf(escalatedTo);
    }
}
