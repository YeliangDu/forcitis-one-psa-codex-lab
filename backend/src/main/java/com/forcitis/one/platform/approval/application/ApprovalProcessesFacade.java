package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.WithdrawCheck;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import org.springframework.stereotype.Component;

/** Offers nothing that acts on a task (I-5, I-7). */
@Component
class ApprovalProcessesFacade implements ApprovalProcesses {

    private final StartApproval startApproval;
    private final WithdrawApproval withdrawApproval;
    private final CancelApproval cancelApproval;
    private final CheckWithdraw checkWithdraw;
    private final ApprovalEngine engine;

    ApprovalProcessesFacade(StartApproval startApproval, WithdrawApproval withdrawApproval, CancelApproval cancelApproval,
            CheckWithdraw checkWithdraw, ApprovalEngine engine) {
        this.startApproval = startApproval;
        this.withdrawApproval = withdrawApproval;
        this.cancelApproval = cancelApproval;
        this.checkWithdraw = checkWithdraw;
        this.engine = engine;
    }

    @Override
    public ApprovalInstanceId start(ApprovalStartRequest request) {
        return startApproval.execute(request);
    }

    @Override
    public boolean isFinished(ApprovalInstanceId instance) {
        return !engine.isRunning(instance);
    }

    @Override
    public WithdrawCheck checkWithdraw(ProcessKey processKey, DocumentReference document, UserId actor) {
        return checkWithdraw.execute(processKey, document, actor);
    }

    @Override
    public void withdraw(ProcessKey processKey, DocumentReference document, UserId actor, String reason) {
        withdrawApproval.execute(processKey, document, actor, reason);
    }

    @Override
    public void cancel(ProcessKey processKey, DocumentReference document, UserId actor, String reason) {
        cancelApproval.execute(processKey, document, actor, reason);
    }
}
