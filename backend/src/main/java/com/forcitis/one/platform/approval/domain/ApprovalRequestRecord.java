package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ProcessKey;

/** created tells the caller that its lock-or-create call inserted the row; instanceId is null before the first start. */
public record ApprovalRequestRecord(ProcessKey processKey, String businessKey, UserId initiator, int round,
                                    ApprovalInstanceId instanceId, ApprovalStatus status, boolean created) {
}
