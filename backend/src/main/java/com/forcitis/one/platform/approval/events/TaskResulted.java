package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.TaskOutcome;

/** Not published for automatic completions. advisory: the rejection of an added co-signer, recorded without ending the round (RULE-273). */
public record TaskResulted(EventEnvelope envelope, ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey,
                           DocumentReference document, String nodeKey, UserId assignee, TaskOutcome outcome, boolean advisory,
                           String reason) implements DomainEvent {
}
