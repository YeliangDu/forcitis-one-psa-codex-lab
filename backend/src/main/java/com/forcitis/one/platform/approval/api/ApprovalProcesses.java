package com.forcitis.one.platform.approval.api;

import com.forcitis.one.kernel.UserId;

public interface ApprovalProcesses {

    ApprovalInstanceId start(ApprovalRequest request);

    void approve(String taskId, UserId approver);

    boolean isFinished(ApprovalInstanceId instance);
}
