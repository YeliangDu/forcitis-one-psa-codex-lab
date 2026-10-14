package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.TaskOutcome;

/** Not published for automatic completions; advisory is reserved for co-signers (1b) and always false in 1a. */
public record TaskResulted(ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey, String businessKey,
                           String nodeKey, UserId assignee, TaskOutcome outcome, boolean advisory, String reason) {
}
