package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.NodeKey;

/** The process administrator handed the task to somebody (D-1a-61); from is empty when nobody held it. */
public record TaskReassigned(EventEnvelope envelope, ApprovalTaskId taskId, ApprovalInstanceId instanceId, DocumentReference document,
                             NodeKey nodeKey, UserId from, UserId to, Reason reason) implements DomainEvent {
}
