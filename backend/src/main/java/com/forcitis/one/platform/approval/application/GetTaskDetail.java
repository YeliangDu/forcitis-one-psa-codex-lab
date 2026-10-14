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

/** Anybody but the initiator, assignee and candidates is told the task does not exist (fail closed). */
@Service
public class GetTaskDetail {

    private final ApprovalEngine engine;

    GetTaskDetail(ApprovalEngine engine) {
        this.engine = engine;
    }

    @Transactional(readOnly = true)
    public TaskDetail execute(ApprovalTaskId taskId, UserId viewer) {
        TaskView task = engine.findTask(taskId).filter(t -> isParticipant(t, viewer)).orElseThrow(
                () -> ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND.refusal("Task " + taskId.value() + " does not exist"));
        List<UserId> candidates = task.candidates().stream().sorted(Comparator.comparingLong(UserId::value)).toList();
        return new TaskDetail(task.taskId(), task.instanceId(), task.processKey(), task.document(), task.nodeKey(), task.nodeName(),
                task.initiator(), task.assignee(), candidates, task.createdAt(), TaskActors.mayAct(task, viewer));
    }

    private static boolean isParticipant(TaskView task, UserId viewer) {
        return task.initiator().equals(viewer) || task.assignee().map(viewer::equals).orElse(false) || task.candidates().contains(viewer);
    }
}
