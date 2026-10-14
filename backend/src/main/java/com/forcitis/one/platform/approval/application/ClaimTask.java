package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Claiming a task one already holds is a harmless repeat; one somebody else holds is refused. */
@Service
public class ClaimTask {

    private final TaskGuard guard;
    private final Approvals approvals;

    ClaimTask(TaskGuard guard, Approvals approvals) {
        this.guard = guard;
        this.approvals = approvals;
    }

    @Transactional
    public void execute(ApprovalTaskId taskId, UserId actor, OptionalLong expectedVersion) {
        TaskView view = guard.requireActor(taskId, actor, "claim");
        if (view.isClaimed() && expectedVersion.isEmpty()) {
            return;
        }
        ApprovalRequest request = approvals.lock(view.instanceId());
        approvals.requireVersion(request, expectedVersion);
        TaskView current = guard.requireActor(taskId, actor, "claim");
        if (current.isClaimed()) {
            return;
        }
        approvals.claim(request, current, actor);
        approvals.save(request);
    }
}
