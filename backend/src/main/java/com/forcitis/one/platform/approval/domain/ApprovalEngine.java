package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.util.List;
import java.util.Optional;

/** Does NOT check who the actor is: callers decide with TaskActors first (I-7). */
public interface ApprovalEngine {

    ApprovalInstanceId startInstance(ProcessKey processKey, String businessKey, UserId initiator, ProcessVariables variables);

    boolean isRunning(ApprovalInstanceId instanceId);

    Optional<TaskView> findTask(ApprovalTaskId taskId);

    List<TaskView> findOpenTasks(ApprovalInstanceId instanceId);

    List<TaskView> findOpenTasksFor(UserId user);

    /** False when somebody else claimed the task meanwhile (never an exception for a lost race). */
    boolean claimTask(ApprovalTaskId taskId, UserId user);

    void completeTask(ApprovalTaskId taskId);

    void endAsReturned(ApprovalInstanceId instanceId);

    void terminate(ApprovalInstanceId instanceId, String reason);

    void updateVariables(ApprovalInstanceId instanceId, ProcessVariables variables);
}
