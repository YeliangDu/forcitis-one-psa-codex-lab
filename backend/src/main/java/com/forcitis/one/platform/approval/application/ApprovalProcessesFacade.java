package com.forcitis.one.platform.approval.application;

import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import org.springframework.stereotype.Component;

/** Offers nothing that acts on a task (I-5, I-7). */
@Component
class ApprovalProcessesFacade implements ApprovalProcesses {

    private final StartApproval startApproval;
    private final ApprovalEngine engine;

    ApprovalProcessesFacade(StartApproval startApproval, ApprovalEngine engine) {
        this.startApproval = startApproval;
        this.engine = engine;
    }

    @Override
    public ApprovalInstanceId start(ApprovalRequest request) {
        return startApproval.execute(request);
    }

    @Override
    public boolean isFinished(ApprovalInstanceId instance) {
        return !engine.isRunning(instance);
    }
}
