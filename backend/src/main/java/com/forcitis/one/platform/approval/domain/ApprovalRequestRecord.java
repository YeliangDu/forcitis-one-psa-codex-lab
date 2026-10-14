package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ProcessKey;

/**
 * One row of approval_requests: the approval of one business document (processKey, businessKey). round counts the
 * starts (RULE-278); instanceId is the live or last instance, null before the first start. created tells the caller
 * that its lock-or-create call inserted the row.
 */
public record ApprovalRequestRecord(ProcessKey processKey, String businessKey, UserId initiator, int round,
                                    ApprovalInstanceId instanceId, ApprovalStatus status, boolean created) {
}
