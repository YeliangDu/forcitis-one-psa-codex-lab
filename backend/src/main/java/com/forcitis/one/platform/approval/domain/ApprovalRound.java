package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;

public record ApprovalRound(int round, EmployeeId initiator, ApprovalInstanceId instanceId) {
}
