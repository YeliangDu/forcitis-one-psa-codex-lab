package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.TaskView;
import org.springframework.stereotype.Component;

/**
 * On a node with several tasks in one round (a countersign) one person takes one share: this says whether a person already has another
 * task of the node, either open (candidate or holder) or decided. Escalating and claiming ask it; handing over (TransferTask, ReassignTask) keeps its own check for now.
 */
@Component
class NodeShares {

    private final ApprovalEngine engine;

    NodeShares(ApprovalEngine engine) {
        this.engine = engine;
    }

    boolean takenElsewhere(ApprovalRequest request, TaskView task, UserId person) {
        return request.hasDecidedOnNodeOf(task.taskId(), person) || engine.findOpenTasks(task.instanceId()).stream()
                .filter(other -> other.nodeKey().equals(task.nodeKey()) && !other.taskId().equals(task.taskId()))
                .anyMatch(other -> other.candidates().contains(person) || other.assignee().map(person::equals).orElse(false));
    }
}
