package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.NodeName;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.TaskRecordAction;
import com.forcitis.one.platform.approval.api.TaskRecordId;
import java.time.Instant;
import java.util.Optional;

/**
 * One line of the append-only trail of what was done to a task. onBehalfOf is the person whose task the actor acted on, when that is
 * somebody else (a process administrator reassigning it); assignee is the person who holds the task afterwards, for a transfer or a
 * reassignment.
 */
public record TaskHandling(TaskRecordId recordId, ProcessKey processKey, DocumentReference document, int round, ApprovalTaskId taskId,
                           ApprovalInstanceId instanceId, NodeKey nodeKey, NodeName nodeName, TaskRecordAction action, UserId actor,
                           Optional<UserId> onBehalfOf, Optional<UserId> assignee, Optional<Reason> reason, Instant decidedAt) {
}
