package com.forcitis.one.platform.approval.events;

import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalOutcome;
import com.forcitis.one.platform.approval.api.ProcessKey;

/**
 * An approval ended (RULE-289/306): approved, rejected (top level) or cancelled. Published through the Modulith event
 * publication registry, so a business module's listener is retried until it succeeds (at least once, RULE-295/303):
 * listeners must be idempotent per (processKey, businessKey, round) (RULE-279/312). Replaces the write-back of
 * approve_status by table name (RULE-308).
 */
public record ApprovalCompleted(ApprovalInstanceId instanceId, ProcessKey processKey, String businessKey, int round,
                                ApprovalOutcome outcome, String reason) {
}
