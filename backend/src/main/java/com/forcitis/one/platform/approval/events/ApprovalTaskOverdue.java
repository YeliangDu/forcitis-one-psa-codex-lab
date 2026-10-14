package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import java.time.Instant;
import java.util.List;

/** The task passed its deadline unhandled; holders are the people reminded. Counts toward timeliness statistics, never approves. */
public record ApprovalTaskOverdue(EventEnvelope envelope, ApprovalTaskId taskId, ApprovalInstanceId instanceId, DocumentReference document,
                                  String nodeKey, Instant dueAt, List<UserId> holders) implements DomainEvent {

    public ApprovalTaskOverdue {
        holders = List.copyOf(holders);
    }
}
