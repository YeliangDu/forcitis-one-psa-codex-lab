package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.NodeName;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ProcessName;
import java.time.Instant;
import java.util.List;

/** Drives the WeCom card: one card per candidate (Q-3). dueAt is empty for a node without a deadline; summary comes from the business module. */
public record TaskAssigned(EventEnvelope envelope, ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey,
                           ProcessName processName, DocumentReference document, ApprovalSummary summary, NodeKey nodeKey,
                           NodeName nodeName, EmployeeId initiator, List<UserId> candidates, Instant dueAt) implements DomainEvent {

    public TaskAssigned {
        candidates = List.copyOf(candidates);
    }
}
