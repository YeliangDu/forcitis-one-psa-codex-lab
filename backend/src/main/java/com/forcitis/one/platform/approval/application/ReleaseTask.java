package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The person holding a task gives it back to its candidates, for instance before going on leave (M-6). */
@Service
public class ReleaseTask {

    private final TaskGuard guard;
    private final ApprovalEngine engine;
    private final Approvals approvals;
    private final BusinessTime time;

    ReleaseTask(TaskGuard guard, ApprovalEngine engine, Approvals approvals, BusinessTime time) {
        this.guard = guard;
        this.engine = engine;
        this.approvals = approvals;
        this.time = time;
    }

    @Transactional
    public void execute(ApprovalTaskId taskId, UserId actor, OptionalLong expectedVersion) {
        TaskView first = guard.requireHandler(taskId, actor, "release");
        ApprovalRequest request = approvals.lock(first.instanceId());
        approvals.requireVersion(request, expectedVersion);
        TaskView task = guard.requireHandler(taskId, actor, "release");
        engine.releaseClaim(task.taskId());
        request.releaseTask(taskId, actor, time.moment());
        approvals.save(request);
    }
}
