package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.NodeName;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ProcessName;
import java.util.List;

/** Drives the WeCom card: one card per candidate (Q-3). */
public record TaskAssigned(EventEnvelope envelope, ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey,
                           ProcessName processName, DocumentReference document, NodeKey nodeKey, NodeName nodeName, UserId initiator,
                           List<UserId> candidates) implements DomainEvent {

    public TaskAssigned {
        candidates = List.copyOf(candidates);
    }
}
