package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.NodePolicy;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.approval.domain.TaskView;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Flowable's own claim / complete check nothing about the actor, so I-7 is decided by the use cases with TaskActors. */
@Component
class FlowableApprovalEngine implements ApprovalEngine {

    private static final Logger log = LoggerFactory.getLogger(FlowableApprovalEngine.class);

    static final String VARIABLE_INITIATOR = "initiatorId";
    static final String VARIABLE_BUSINESS_KEY = "businessKey";
    static final String VARIABLE_SKIPPED = "skippedWhenEmpty";
    static final String VARIABLE_ASSIGNED_AT = "assignedAtMillis";

    private final RuntimeService runtime;
    private final TaskService tasks;
    private final RepositoryService repository;
    private final MeterRegistry meters;
    private final Map<String, Definition> definitions = new ConcurrentHashMap<>();

    FlowableApprovalEngine(RuntimeService runtime, TaskService tasks, RepositoryService repository, MeterRegistry meters) {
        this.meters = meters;
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
        String instanceId = runtime.startProcessInstanceByKey(processKey.key(), businessKey, engine).getId();
        rejectWhenEveryNodeWasSkipped(instanceId);
        completeSkippedTasks(instanceId);
        return new ApprovalInstanceId(instanceId);
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
        tasks.complete(taskId.value());
        completeSkippedTasks(task.getProcessInstanceId());
    }

    private void rejectWhenEveryNodeWasSkipped(String instanceId) {
        long open = tasks.createTaskQuery().processInstanceId(instanceId).count();
        if (open > 0 && skippedTasks(instanceId).size() == open) {
            log.error("Every node of instance {} was skipped for lack of an approver: the approval is refused, not passed", instanceId);
            meters.counter("platform.approval.all.nodes.skipped").increment();
            throw new IllegalStateException("Every node of approval instance " + instanceId + " found nobody to assign");
        }
    }

    private void completeSkippedTasks(String instanceId) {
        List<Task> skipped = skippedTasks(instanceId);
        while (!skipped.isEmpty()) {
            skipped.forEach(task -> tasks.complete(task.getId()));
            skipped = skippedTasks(instanceId);
        }
    }

    private List<Task> skippedTasks(String instanceId) {
        return tasks.createTaskQuery().processInstanceId(instanceId).taskVariableValueEquals(VARIABLE_SKIPPED, Boolean.TRUE).list();
    }

    @Override
    public void endAsReturned(ApprovalInstanceId instanceId) {
        runtime.deleteProcessInstance(instanceId.value(), "RETURNED");
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
                createdAt(task), node.getSubProcess() != null, policy.beforeHookKey());
    }

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
}
