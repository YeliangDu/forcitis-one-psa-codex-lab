package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalProgress;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.RetractionResult;
import com.forcitis.one.platform.approval.api.WithdrawCheck;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.TaskActors;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Offers nothing that acts on a task (I-5, I-7). */
@Component
class ApprovalProcessesFacade implements ApprovalProcesses {

    private final StartApproval startApproval;
    private final WithdrawApproval withdrawApproval;
    private final CancelApproval cancelApproval;
    private final CheckWithdraw checkWithdraw;
    private final GetApprovalProgress getProgress;
    private final ApprovalEngine engine;

    ApprovalProcessesFacade(StartApproval startApproval, WithdrawApproval withdrawApproval, CancelApproval cancelApproval,
            CheckWithdraw checkWithdraw, GetApprovalProgress getProgress, ApprovalEngine engine) {
        this.startApproval = startApproval;
        this.withdrawApproval = withdrawApproval;
        this.cancelApproval = cancelApproval;
        this.checkWithdraw = checkWithdraw;
        this.getProgress = getProgress;
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
    public Map<String, List<String>> formFields(ApprovalInstanceId instance) {
        return engine.submittedFormFields(instance);
    }

    @Override
    public Optional<ApprovalInstanceId> instanceOfTaskOpenTo(ProcessKey processKey, ApprovalTaskId taskId, UserId actor) {
        return engine.findTask(taskId).filter(task -> task.processKey().equals(processKey)).filter(task -> TaskActors.mayAct(task, actor))
                .map(TaskView::instanceId);
    }

    @Override
    public Optional<ApprovalProgress> progress(ProcessKey processKey, DocumentReference document) {
        return getProgress.execute(processKey, document);
    }

    @Override
    public WithdrawCheck checkWithdraw(ProcessKey processKey, DocumentReference document, UserId actor) {
        return checkWithdraw.execute(processKey, document, actor);
    }

    @Override
    public RetractionResult withdraw(ProcessKey processKey, DocumentReference document, UserId actor, String reason) {
        return withdrawApproval.execute(processKey, document, actor, reason);
    }

    @Override
    public RetractionResult cancel(ProcessKey processKey, DocumentReference document, UserId actor, String reason) {
        return cancelApproval.execute(processKey, document, actor, reason);
    }
}
