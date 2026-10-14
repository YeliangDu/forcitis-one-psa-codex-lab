package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.TaskActors;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task details for its participants: the initiator, the assignee and the candidates (RULE-310, fail closed). Anybody else is
 * told the task does not exist.
 */
@Service
public class GetTaskDetail {

    private final ApprovalEngine engine;

    GetTaskDetail(ApprovalEngine engine) {
        this.engine = engine;
    }

    @Transactional(readOnly = true)
    public TaskDetail execute(ApprovalTaskId taskId, UserId viewer) {
        TaskView task = engine.findTask(taskId).filter(t -> isParticipant(t, viewer)).orElseThrow(
                () -> new ApprovalOperationException(ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND,
                        "Task " + taskId.value() + " does not exist"));
        List<String> candidates = task.candidates().stream().map(UserId::value).sorted(Comparator.naturalOrder()).map(String::valueOf).toList();
        return new TaskDetail(task.taskId().value(), task.instanceId().value(), task.processKey().name(), task.businessKey(),
                task.nodeKey(), task.nodeName(), String.valueOf(task.initiator().value()), task.assignee() == null ? null : String.valueOf(task.assignee().value()),
                candidates, task.createdAt(), TaskActors.mayAct(task, viewer));
    }

    private static boolean isParticipant(TaskView task, UserId viewer) {
        return task.initiator().equals(viewer) || viewer.equals(task.assignee()) || task.candidates().contains(viewer);
    }
}
