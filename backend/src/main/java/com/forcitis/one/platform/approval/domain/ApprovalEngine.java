package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.util.List;
import java.util.Optional;

/**
 * Port to the process engine (implemented in infrastructure.flowable; Flowable appears nowhere else). It does NOT check
 * who the actor is: callers decide with {@link TaskActors} before using it (I-7).
 */
public interface ApprovalEngine {

    ApprovalInstanceId startInstance(ProcessKey processKey, String businessKey, UserId initiator, ProcessVariables variables);

    boolean isRunning(ApprovalInstanceId instanceId);

    Optional<TaskView> findTask(ApprovalTaskId taskId);

    List<TaskView> findOpenTasks(ApprovalInstanceId instanceId);

    /** Open tasks assigned to the user, plus unclaimed tasks the user is a candidate for. */
    List<TaskView> findOpenTasksFor(UserId user);

    /** Makes the user the assignee; false when somebody else claimed the task in the meantime (never an exception for a lost race). */
    boolean claimTask(ApprovalTaskId taskId, UserId user);

    void completeTask(ApprovalTaskId taskId);

    /** Moves the live instance to its initiator node (RULE-271/275); the instance stays alive. */
    void returnToInitiator(ApprovalInstanceId instanceId);

    /** Ends the instance for good (top-level reject, RULE-271/290); history stays (RULE-288). */
    void terminate(ApprovalInstanceId instanceId, String reason);

    /** Overwrites the business fields and engine variables named in the argument (resubmission, hook updates). */
    void updateVariables(ApprovalInstanceId instanceId, ProcessVariables variables);
}
