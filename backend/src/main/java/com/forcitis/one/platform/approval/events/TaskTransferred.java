package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.NodeKey;

/** The current handler handed the task to somebody else (RULE-285). */
public record TaskTransferred(EventEnvelope envelope, ApprovalTaskId taskId, ApprovalInstanceId instanceId, DocumentReference document,
                              NodeKey nodeKey, UserId from, UserId to) implements DomainEvent {
}
