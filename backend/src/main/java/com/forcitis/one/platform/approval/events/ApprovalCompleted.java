package com.forcitis.one.platform.approval.events;

import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ProcessKey;

/** Delivered at least once through the event publication registry: listeners must be idempotent per (processKey, businessKey, round). */
public record ApprovalCompleted(ApprovalInstanceId instanceId, ProcessKey processKey, String businessKey, int round,
                                ApprovalOutcome outcome, String reason) {
}
