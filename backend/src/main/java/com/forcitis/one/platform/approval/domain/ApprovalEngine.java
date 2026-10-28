package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ProcessName;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Does NOT check who the actor is: callers decide with TaskActors first (I-7). */
public interface ApprovalEngine {

    /** The routing flags the process definition reacts to; a flag outside this set means nothing to the process. */
    Set<String> routingFlagsOf(ProcessKey processKey);

    /** The form field names the process definition reads; a field outside this set would be ignored, so a start that carries one is refused. */
    Set<String> formFieldsOf(ProcessKey processKey);

    /** The document type the latest deployed definition of the process approves (its forcitis:documentType); a definition without one is a deployment fault. */
    DocumentTypeCode documentTypeOf(ProcessKey processKey);

    /**
     * The name a person reads for the process: the BPMN name of its latest deployed definition, so a renamed redeployment wins over the
     * earlier ones. A definition without a name is a deployment fault; a key with no deployed definition is refused with
     * PLATFORM_APPROVAL_PROCESS_NOT_DEPLOYED, as every other read of a definition here is.
     */
    ProcessName processNameOf(ProcessKey processKey);

    /** True when the branches are chosen by routing flags: a start with none would route nowhere and is refused (forcitis:routingFlagRequired). */
    boolean routingFlagRequiredOf(ProcessKey processKey);

    void startInstance(InstanceStart start);

    boolean isRunning(ApprovalInstanceId instanceId);

    Optional<TaskView> findTask(ApprovalTaskId taskId);

    List<TaskView> findOpenTasks(ApprovalInstanceId instanceId);

    List<TaskView> findOpenTasksFor(UserId user);

    /** False when somebody else claimed the task meanwhile (never an exception for a lost race). */
    boolean claimTask(ApprovalTaskId taskId, UserId user);

    void releaseClaim(ApprovalTaskId taskId);

    void reassignTask(ApprovalTaskId taskId, UserId to);

    void addCandidates(ApprovalTaskId taskId, Collection<UserId> users);

    void completeTask(ApprovalTaskId taskId);

    void endAsReturned(ApprovalInstanceId instanceId);

    void terminate(ApprovalInstanceId instanceId, String reason);

    void updateVariables(ApprovalInstanceId instanceId, ProcessVariables variables);

    /** Any node marked as cut-off has a finished task in this instance, automatic completions included (RULE-309). */
    boolean cutoffPassed(ApprovalInstanceId instanceId);
}
