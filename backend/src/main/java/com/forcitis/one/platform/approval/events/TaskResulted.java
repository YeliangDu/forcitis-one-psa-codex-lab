package com.forcitis.one.platform.approval.events;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.TaskOutcome;

/**
 * One task was decided (RULE-305): approved, rejected or returned to the initiator. Not published for automatic
 * completions (RULE-304 old reconcile behaviour is gone). advisory is reserved for added co-signers (RULE-291, 1b)
 * and is always false in 1a.
 */
public record TaskResulted(ApprovalTaskId taskId, ApprovalInstanceId instanceId, ProcessKey processKey, String businessKey,
                           String nodeKey, UserId assignee, TaskOutcome outcome, boolean advisory, String reason) {
}
