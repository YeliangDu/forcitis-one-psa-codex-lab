package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Claiming a task one already holds is a harmless repeat; one somebody else holds is refused, and so is one of a node whose other task the claimant already holds or decided (#1546). */
@Service
public class ClaimTask {

    private final TaskGuard guard;
    private final Approvals approvals;
    private final NodeShares shares;

    ClaimTask(TaskGuard guard, Approvals approvals, NodeShares shares) {
        this.guard = guard;
        this.approvals = approvals;
        this.shares = shares;
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
        if (shares.takenElsewhere(request, current, actor)) {
            throw ApprovalError.PLATFORM_APPROVAL_NODE_TASK_DUPLICATE.refusal("Task " + taskId.value() + " cannot be claimed by somebody who already has another task of node " + current.nodeKey());
        }
        approvals.claim(request, current, actor);
        approvals.save(request);
    }
}
