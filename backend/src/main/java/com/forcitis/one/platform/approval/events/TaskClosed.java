package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.NotAudited;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.NodeKey;

/** The round ended before anybody decided this task, so its holders lose the right to act on it. */
@NotAudited(reason = "a consequence of the audited end of the round; it changes nobody's authority beyond what that end already did")
public record TaskClosed(EventEnvelope envelope, ApprovalTaskId taskId, ApprovalInstanceId instanceId, DocumentReference document,
                         NodeKey nodeKey) implements DomainEvent {
}
