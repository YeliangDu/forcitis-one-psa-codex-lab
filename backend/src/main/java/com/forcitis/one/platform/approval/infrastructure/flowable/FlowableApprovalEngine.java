package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.NodePolicy;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.identitylink.api.IdentityLinkInfo;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

/**
 * The only adapter of the Flowable engine services (together with the listener / verifier next to it). It translates typed
 * calls into engine calls and engine tasks into {@link TaskView}s; it does not decide who may act (I-7 is decided by the
 * use cases with {@code TaskActors}), because Flowable's own claim / complete check nothing about the actor.
 */
@Component
class FlowableApprovalEngine implements ApprovalEngine {

    static final String VARIABLE_INITIATOR = "initiatorId";
    static final String VARIABLE_BUSINESS_KEY = "businessKey";
    static final String VARIABLE_RETURNED = "returned";
    /** Task-local variable: epoch milliseconds at which the task was assigned. */
    static final String VARIABLE_ASSIGNED_AT = "assignedAtMillis";
    /** Flowable only evaluates skipExpression on a node when this variable is true. */
    private static final String SKIP_EXPRESSION_ENABLED = "_FLOWABLE_SKIP_EXPRESSION_ENABLED";

    private final RuntimeService runtime;
    private final TaskService tasks;
    private final RepositoryService repository;
    private final Map<String, Definition> definitions = new ConcurrentHashMap<>();

    FlowableApprovalEngine(RuntimeService runtime, TaskService tasks, RepositoryService repository) {
        this.runtime = runtime;
        this.tasks = tasks;
        this.repository = repository;
    }

    private record Definition(ProcessKey processKey, BpmnModel model) {
    }

    @Override
    public ApprovalInstanceId startInstance(ProcessKey processKey, String businessKey, UserId initiator, ProcessVariables variables) {
        Map<String, Object> engine = new LinkedHashMap<>(EngineVariables.toEngine(variables));
        engine.put(VARIABLE_INITIATOR, String.valueOf(initiator.value()));
        engine.put(VARIABLE_BUSINESS_KEY, businessKey);
        engine.put(VARIABLE_RETURNED, Boolean.FALSE);
        engine.put(SKIP_EXPRESSION_ENABLED, Boolean.TRUE);
        return new ApprovalInstanceId(runtime.startProcessInstanceByKey(processKey.key(), businessKey, engine).getId());
    }

    @Override
    public boolean isRunning(ApprovalInstanceId instanceId) {
        return runtime.createProcessInstanceQuery().processInstanceId(instanceId.value()).count() > 0;
    }

    @Override
    public Optional<TaskView> findTask(ApprovalTaskId taskId) {
        return Optional.ofNullable(tasks.createTaskQuery().taskId(taskId.value()).includeIdentityLinks().includeProcessVariables().includeTaskLocalVariables()
                .singleResult()).map(this::view);
    }

    @Override
    public List<TaskView> findOpenTasks(ApprovalInstanceId instanceId) {
        return tasks.createTaskQuery().processInstanceId(instanceId.value()).includeIdentityLinks().includeProcessVariables().includeTaskLocalVariables()
                .orderByTaskCreateTime().asc().list().stream().map(this::view).toList();
    }

    @Override
    public List<TaskView> findOpenTasksFor(UserId user) {
        String id = String.valueOf(user.value());
        Map<String, Task> found = new LinkedHashMap<>();
        tasks.createTaskQuery().taskAssignee(id).includeIdentityLinks().includeProcessVariables().includeTaskLocalVariables().list()
                .forEach(t -> found.putIfAbsent(t.getId(), t));
        tasks.createTaskQuery().taskCandidateUser(id).includeIdentityLinks().includeProcessVariables().includeTaskLocalVariables().list()
                .forEach(t -> found.putIfAbsent(t.getId(), t));
        return found.values().stream().map(this::view)
                .filter(t -> user.equals(t.assignee()) || (t.assignee() == null && t.candidates().contains(user)))
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt())).toList();
    }

    @Override
    public boolean claimTask(ApprovalTaskId taskId, UserId user) {
        try {
            tasks.claim(taskId.value(), String.valueOf(user.value()));
            return true;
        } catch (FlowableTaskAlreadyClaimedException | FlowableOptimisticLockingException e) {
            return false;
        }
    }

    @Override
    public void completeTask(ApprovalTaskId taskId) {
        Task task = tasks.createTaskQuery().taskId(taskId.value()).singleResult();
        if (task == null) {
            throw new IllegalStateException("Task " + taskId.value() + " is gone");
        }
        if (nodeOf(task).initiatorNode()) {
            runtime.setVariable(task.getProcessInstanceId(), VARIABLE_RETURNED, Boolean.FALSE);
        }
        tasks.complete(taskId.value());
    }

    @Override
    public void returnToInitiator(ApprovalInstanceId instanceId) {
        List<Task> open = tasks.createTaskQuery().processInstanceId(instanceId.value()).list();
        if (open.isEmpty()) {
            throw new IllegalStateException("Instance " + instanceId.value() + " has no open task to return");
        }
        Definition definition = definition(open.getFirst().getProcessDefinitionId());
        String initiatorNode = initiatorNodeKey(definition.model());
        List<String> from = open.stream().map(Task::getTaskDefinitionKey).distinct().toList();
        runtime.createChangeActivityStateBuilder().processInstanceId(instanceId.value())
                .moveActivityIdsToSingleActivityId(from, initiatorNode)
                .processVariable(VARIABLE_RETURNED, Boolean.TRUE).changeState();
    }

    @Override
    public void terminate(ApprovalInstanceId instanceId, String reason) {
        runtime.deleteProcessInstance(instanceId.value(), reason);
    }

    @Override
    public void updateVariables(ApprovalInstanceId instanceId, ProcessVariables variables) {
        Map<String, Object> engine = EngineVariables.toEngine(variables);
        if (!engine.isEmpty()) {
            runtime.setVariables(instanceId.value(), engine);
        }
    }

    // ---------- mapping ----------

    private TaskView view(Task task) {
        Definition definition = definition(task.getProcessDefinitionId());
        NodePolicy policy = nodeOf(task);
        UserTask node = (UserTask) definition.model().getFlowElement(task.getTaskDefinitionKey());
        Map<String, Object> variables = task.getProcessVariables();
        Set<UserId> candidates = new LinkedHashSet<>();
        for (IdentityLinkInfo link : task.getIdentityLinks()) {
            if (IdentityLinkType.CANDIDATE.equals(link.getType()) && link.getUserId() != null) {
                candidates.add(new UserId(Long.parseLong(link.getUserId())));
            }
        }
        return new TaskView(new ApprovalTaskId(task.getId()), new ApprovalInstanceId(task.getProcessInstanceId()),
                definition.processKey(), String.valueOf(variables.get(VARIABLE_BUSINESS_KEY)), task.getTaskDefinitionKey(),
                task.getName(), new UserId(Long.parseLong(String.valueOf(variables.get(VARIABLE_INITIATOR)))),
                task.getAssignee() == null ? null : new UserId(Long.parseLong(task.getAssignee())), candidates,
                createdAt(task), node.getSubProcess() != null, policy.initiatorNode(), policy.beforeHookKey());
    }

    /** Time the task was assigned, written by the assignment listener (no java.util.Date in this code base). */
    private static Instant createdAt(Task task) {
        Object millis = task.getTaskLocalVariables().get(VARIABLE_ASSIGNED_AT);
        return millis instanceof Number n ? Instant.ofEpochMilli(n.longValue()) : Instant.EPOCH;
    }

    private NodePolicy nodeOf(Task task) {
        BpmnModel model = definition(task.getProcessDefinitionId()).model();
        return NodePolicyReader.read((UserTask) model.getFlowElement(task.getTaskDefinitionKey()));
    }

    private Definition definition(String processDefinitionId) {
        return definitions.computeIfAbsent(processDefinitionId, id -> {
            ProcessDefinition definition = repository.getProcessDefinition(id);
            return new Definition(ProcessKey.valueOf(definition.getKey().toUpperCase(Locale.ROOT)), repository.getBpmnModel(id));
        });
    }

    /** The one rework node of a process (verified at startup); where a return parks the approval. */
    static String initiatorNodeKey(BpmnModel model) {
        List<String> found = new ArrayList<>();
        model.getMainProcess().findFlowElementsOfType(UserTask.class).forEach(task -> {
            if (NodePolicyReader.read(task).initiatorNode()) {
                found.add(task.getId());
            }
        });
        if (found.size() != 1) {
            throw new IllegalStateException("A process needs exactly one initiator node (initiatorNode=\"true\"), found " + found);
        }
        return found.getFirst();
    }
}
