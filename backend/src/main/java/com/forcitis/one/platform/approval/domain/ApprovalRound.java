package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;

public record ApprovalRound(int round, UserId initiator, ApprovalInstanceId instanceId) {
}
