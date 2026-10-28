package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.NodeName;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ProcessName;
import com.forcitis.one.platform.approval.api.TaskRecordAction;
import com.forcitis.one.platform.approval.api.TaskRecordId;
import com.forcitis.one.kernel.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * A task the person decided. reason is empty when the person gave none (RULE-280). processName is the name of the process definition,
 * empty when the definition is no longer deployed.
 */
public record HandledTask(TaskRecordId recordId, ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey,
                          Optional<ProcessName> processName, DocumentReference document,
                          NodeKey nodeKey, NodeName nodeName, TaskRecordAction action, UserId actor, String actorName,
                          Optional<String> onBehalfOfName, Optional<Reason> reason, Instant decidedAt, int round) {
}
