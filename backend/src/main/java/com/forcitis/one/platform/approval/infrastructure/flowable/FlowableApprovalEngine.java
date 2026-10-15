package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import com.forcitis.one.platform.approval.domain.InstanceStart;
import com.forcitis.one.platform.approval.domain.NodePolicy;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.approval.domain.TaskView;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;
import org.flowable.engine.HistoryService;
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
    static final String VARIABLE_DOCUMENT_TYPE = "documentType";
    static final String VARIABLE_DOCUMENT_ID = "documentId";
    static final String VARIABLE_LEGAL_ENTITY = "legalEntityId";
    static final String VARIABLE_CONTENT_HASH = "contentHash";
    static final String VARIABLE_SYSTEM_COMPLETE = "systemComplete";
    static final String VARIABLE_APPROVER_INVALID = "approverInvalid";
    static final String VARIABLE_ASSIGNED_AT = "assignedAtMillis";
    static final String VARIABLE_ADVISORY = "advisory";
    static final String VARIABLE_ROUTED_PREFIX = "routed_";

    private final RuntimeService runtime;
    private final TaskService tasks;
    private final RepositoryService repository;
    private final HistoryService history;
    private final MeterRegistry meters;
    private final Map<String, Definition> definitions = new ConcurrentHashMap<>();

    FlowableApprovalEngine(RuntimeService runtime, TaskService tasks, RepositoryService repository, HistoryService history,
            MeterRegistry meters) {
        this.runtime = runtime;
        this.tasks = tasks;
        this.repository = repository;
        this.history = history;
        this.meters = meters;
    }

    private record Definition(ProcessKey processKey, BpmnModel model) {
    }

    static DocumentReference documentOf(ProcessVariables variables) {
        return new DocumentReference(DocumentType.valueOf(variables.firstVariable(VARIABLE_DOCUMENT_TYPE).orElseThrow()),
                new DocumentId(variables.firstVariable(VARIABLE_DOCUMENT_ID).orElseThrow()));
    }

    @Override
    public Set<String> routingFlagsOf(ProcessKey processKey) {
        Set<String> flags = new LinkedHashSet<>();
        for (UserTask node : latestNodes(processKey)) {
            NodePolicyReader.read(node).routingFlag().ifPresent(flags::add);
        }
        return flags;
    }

    @Override
    public void startInstance(InstanceStart start) {
        Map<String, Object> engine = new LinkedHashMap<>(EngineVariables.toEngine(start.variables()));
        String businessKey = start.document().documentType().name() + ":" + start.document().documentId().value();
        engine.put(VARIABLE_INITIATOR, String.valueOf(start.initiator().value()));
        engine.put(VARIABLE_BUSINESS_KEY, businessKey);
        engine.put(VARIABLE_DOCUMENT_TYPE, start.document().documentType().name());
        engine.put(VARIABLE_DOCUMENT_ID, start.document().documentId().value());
        engine.put(VARIABLE_LEGAL_ENTITY, String.valueOf(start.legalEntityId().value()));
        engine.put(VARIABLE_CONTENT_HASH, start.contentHash().value());
        engine.putAll(routes(start));
        String instanceId = start.instanceId().value();
        refusing(() -> runtime.createProcessInstanceBuilder().processDefinitionKey(start.processKey().key()).businessKey(businessKey)
                .variables(engine).predefineProcessInstanceId(instanceId).start().getId());
        rejectWhenEveryNodeWasSkipped(instanceId);
        completeSystemTasks(instanceId);
    }

    private List<UserTask> latestNodes(ProcessKey processKey) {
        ProcessDefinition definition = repository.createProcessDefinitionQuery().processDefinitionKey(processKey.key())
                .latestVersion().singleResult();
        if (definition == null) {
            throw new IllegalStateException("No deployed process definition " + processKey.key());
        }
        return repository.getBpmnModel(definition.getId()).getMainProcess().findFlowElementsOfType(UserTask.class);
    }

    private Map<String, Object> routes(InstanceStart start) {
        Set<String> facts = new HashSet<>();
        start.variables().variables().forEach((name, values) -> {
            if (values.size() == 1 && "true".equals(values.getFirst())) {
                facts.add(name);
            }
        });
        Map<String, Object> variables = new LinkedHashMap<>();
        for (UserTask node : latestNodes(start.processKey())) {
            variables.put(VARIABLE_ROUTED_PREFIX + node.getId(), NodePolicyReader.read(node).routingFlag().map(facts::contains).orElse(true));
        }
        return variables;
    }

    @Override
    public boolean isRunning(ApprovalInstanceId instanceId) {
        return runtime.createProcessInstanceQuery().processInstanceId(instanceId.value()).count() > 0;
    }

    @Override
    public Optional<TaskView> findTask(ApprovalTaskId taskId) {
        return Optional.ofNullable(tasks.createTaskQuery().taskId(taskId.value()).includeIdentityLinks().includeProcessVariables()
                .includeTaskLocalVariables().singleResult()).map(this::view);
    }

    @Override
    public List<TaskView> findOpenTasks(ApprovalInstanceId instanceId) {
        return tasks.createTaskQuery().processInstanceId(instanceId.value()).includeIdentityLinks().includeProcessVariables()
                .includeTaskLocalVariables().orderByTaskCreateTime().asc().list().stream().map(this::view).toList();
    }

    @Override
    public List<TaskView> findOpenTasksFor(UserId user) {
        String id = EngineUserIds.text(user);
        Map<String, Task> found = new LinkedHashMap<>();
        tasks.createTaskQuery().taskAssignee(id).includeIdentityLinks().includeProcessVariables().includeTaskLocalVariables().list()
                .forEach(t -> found.putIfAbsent(t.getId(), t));
        tasks.createTaskQuery().taskCandidateUser(id).includeIdentityLinks().includeProcessVariables().includeTaskLocalVariables().list()
                .forEach(t -> found.putIfAbsent(t.getId(), t));
        return found.values().stream().map(this::view)
                .filter(t -> t.assignee().map(user::equals).orElseGet(() -> t.candidates().contains(user)))
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt())).toList();
    }

    @Override
    public boolean claimTask(ApprovalTaskId taskId, UserId user) {
        try {
            tasks.claim(taskId.value(), EngineUserIds.text(user));
            return true;
        } catch (FlowableTaskAlreadyClaimedException | FlowableOptimisticLockingException e) {
            return false;
        }
    }

    @Override
    public void releaseClaim(ApprovalTaskId taskId) {
        tasks.unclaim(taskId.value());
    }

    @Override
    public void reassignTask(ApprovalTaskId taskId, UserId to) {
        tasks.setAssignee(taskId.value(), EngineUserIds.text(to));
    }

    @Override
    public void addCandidates(ApprovalTaskId taskId, Collection<UserId> users) {
        users.forEach(user -> tasks.addCandidateUser(taskId.value(), EngineUserIds.text(user)));
    }

    @Override
    public void completeTask(ApprovalTaskId taskId) {
        Task task = tasks.createTaskQuery().taskId(taskId.value()).singleResult();
        if (task == null) {
            throw new IllegalStateException("Task " + taskId.value() + " is gone");
        }
        refusing(() -> {
            tasks.complete(taskId.value());
            return null;
        });
        completeSystemTasks(task.getProcessInstanceId());
    }

    /** A refusal raised while the engine assigns the next node reaches the caller as itself, not wrapped in the engine's exception. */
    private static <T> T refusing(Supplier<T> command) {
        try {
            return command.get();
        } catch (FlowableException failed) {
            for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
                if (cause instanceof DomainException refusal) {
                    throw refusal;
                }
            }
            throw failed;
        }
    }

    private void rejectWhenEveryNodeWasSkipped(String instanceId) {
        long open = tasks.createTaskQuery().processInstanceId(instanceId).count();
        boolean nothingEverAssigned = open == 0 && history.createHistoricTaskInstanceQuery().processInstanceId(instanceId).count() == 0;
        if (nothingEverAssigned || (open > 0 && systemTasks(instanceId).size() == open)) {
            log.error("Every node of instance {} was skipped for lack of an approver: the approval is refused, not passed", instanceId);
            meters.counter("forcitis.platform.approval.all.nodes.skipped").increment();
            boolean initiatorCanFix = !history.createHistoricVariableInstanceQuery().processInstanceId(instanceId)
                    .variableName(VARIABLE_APPROVER_INVALID).list().isEmpty();
            if (initiatorCanFix) {
                throw ApprovalError.PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID.refusal("Every node of approval instance " + instanceId
                        + " was skipped because the approver named on the document cannot act");
            }
            throw ApprovalError.PLATFORM_APPROVAL_NO_ASSIGNEE_FOUND.refusal("Every node of approval instance " + instanceId + " found nobody to assign");
        }
    }

    private void completeSystemTasks(String instanceId) {
        List<Task> pending = systemTasks(instanceId);
        while (!pending.isEmpty()) {
            pending.forEach(task -> refusing(() -> {
                tasks.complete(task.getId());
                return null;
            }));
            pending = systemTasks(instanceId);
        }
    }

    private List<Task> systemTasks(String instanceId) {
        return tasks.createTaskQuery().processInstanceId(instanceId).taskVariableValueEquals(VARIABLE_SYSTEM_COMPLETE, Boolean.TRUE).list();
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

    @Override
    public boolean cutoffPassed(ApprovalInstanceId instanceId) {
        var instance = runtime.createProcessInstanceQuery().processInstanceId(instanceId.value()).singleResult();
        if (instance == null) {
            return false;
        }
        BpmnModel model = repository.getBpmnModel(instance.getProcessDefinitionId());
        Set<String> cutoffNodes = new LinkedHashSet<>();
        for (UserTask node : model.getMainProcess().findFlowElementsOfType(UserTask.class)) {
            if (NodePolicyReader.read(node).cutoff()) {
                cutoffNodes.add(node.getId());
            }
        }
        return !cutoffNodes.isEmpty() && history.createHistoricTaskInstanceQuery().processInstanceId(instanceId.value()).finished()
                .list().stream().anyMatch(task -> cutoffNodes.contains(task.getTaskDefinitionKey()));
    }

    private TaskView view(Task task) {
        Definition definition = definition(task.getProcessDefinitionId());
        NodePolicy policy = nodeOf(task);
        UserTask node = (UserTask) definition.model().getFlowElement(task.getTaskDefinitionKey());
        ProcessVariables variables = EngineVariables.fromEngine(task.getProcessVariables());
        Set<UserId> candidates = new LinkedHashSet<>();
        for (IdentityLinkInfo link : task.getIdentityLinks()) {
            if (IdentityLinkType.CANDIDATE.equals(link.getType()) && link.getUserId() != null) {
                candidates.add(EngineUserIds.parse(link.getUserId(), "a candidate of task " + task.getId()));
            }
        }
        return new TaskView(new ApprovalTaskId(task.getId()), new ApprovalInstanceId(task.getProcessInstanceId()),
                definition.processKey(), documentOf(variables),
                new LegalEntityId(UUID.fromString(variables.firstVariable(VARIABLE_LEGAL_ENTITY).orElseThrow())),
                task.getTaskDefinitionKey(), task.getName(),
                EmployeeId.parse((variables.firstVariable(VARIABLE_INITIATOR).orElseThrow())),
                Optional.ofNullable(task.getAssignee()).map(a -> EngineUserIds.parse(a, "the assignee of task " + task.getId())), candidates, createdAt(task),
                node.getSubProcess() != null, policy.beforeHookKey(), Boolean.TRUE.equals(task.getTaskLocalVariables().get(VARIABLE_ADVISORY)),
                new ContentHash(variables.firstVariable(VARIABLE_CONTENT_HASH).orElseThrow()));
    }

    private static Instant createdAt(Task task) {
        Object millis = task.getTaskLocalVariables().get(VARIABLE_ASSIGNED_AT);
        if (!(millis instanceof Number assignedAt)) {
            throw new IllegalStateException("Task " + task.getId() + " has no assignment time");
        }
        return Instant.ofEpochMilli(assignedAt.longValue());
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
