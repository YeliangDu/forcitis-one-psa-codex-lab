package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.TaskEffect;
import com.forcitis.one.platform.approval.api.TaskOutcome;

/** Not published for automatic completions. effect ADVISORY: the rejection of an added co-signer, recorded without ending the round (RULE-273). */
public record TaskHandled(EventEnvelope envelope, ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey,
                           DocumentReference document, NodeKey nodeKey, UserId assignee, TaskOutcome outcome, TaskEffect effect,
                           Reason reason) implements DomainEvent {
}
