package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalStatus;

public record DecisionResult(ApprovalInstanceId instanceId, ApprovalStatus status) {
}
