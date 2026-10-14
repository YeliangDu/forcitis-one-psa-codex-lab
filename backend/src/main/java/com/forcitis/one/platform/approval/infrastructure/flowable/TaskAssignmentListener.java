package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.application.TaskRegistrar;
import com.forcitis.one.platform.approval.application.TaskRegistration;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.approval.domain.Resolution;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Runs inside the engine command creating the task. A countersign task already carries its one approver; any other node is resolved
 * here. The task is registered with the approval module, which also tells whether the system passes it itself (RULE-302).
 */
@Component
class TaskAssignmentListener implements FlowableEventListener {

    private final NodeAssignment assignment;
    private final TaskRegistrar registrar;
    private final ObjectProvider<Clock> clock;

    TaskAssignmentListener(NodeAssignment assignment, TaskRegistrar registrar, ObjectProvider<Clock> clock) {
        this.assignment = assignment;
        this.registrar = registrar;
        this.clock = clock;
    }

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEngineEntityEvent entityEvent) || !(entityEvent.getEntity() instanceof TaskEntity task)
                || task.getProcessInstanceId() == null) {
            return;
        }
        BpmnModel model = ProcessDefinitionUtil.getBpmnModel(task.getProcessDefinitionId());
        UserTask node = (UserTask) model.getFlowElement(task.getTaskDefinitionKey());
        ProcessVariables variables = EngineVariables.fromEngine(task.getVariables());

        List<UserId> candidates;
        if (node.getLoopCharacteristics() != null) {
            candidates = List.of(new UserId(Long.parseLong(variables.firstVariable(ApproverResolution.ELEMENT_VARIABLE)
                    .orElseThrow(() -> new IllegalStateException("Countersign task of node " + node.getId() + " has no approver")))));
        } else {
            Resolution resolution = assignment.resolve(node, task.getProcessInstanceId(), task.getVariables());
            if (NodeAssignment.isSkipped(resolution)) {
                task.setVariableLocal(FlowableApprovalEngine.VARIABLE_SYSTEM_COMPLETE, Boolean.TRUE);
                return;
            }
            candidates = resolution.candidates().stream().sorted(Comparator.comparingLong(UserId::value)).toList();
        }

        task.setVariableLocal(FlowableApprovalEngine.VARIABLE_ASSIGNED_AT, clock.getIfAvailable(Clock::systemUTC).millis());
        candidates.forEach(candidate -> task.addCandidateUser(String.valueOf(candidate.value())));

        ProcessKey processKey = ProcessKey.valueOf(ProcessDefinitionUtil.getProcessDefinition(task.getProcessDefinitionId())
                .getKey().toUpperCase(Locale.ROOT));
        String nodeKey = node.getId();
        boolean passedBySystem = registrar.register(new TaskRegistration(new ApprovalTaskId(task.getId()),
                new ApprovalInstanceId(task.getProcessInstanceId()), processKey,
                ProcessDefinitionUtil.getProcessDefinition(task.getProcessDefinitionId()).getName(),
                FlowableApprovalEngine.documentOf(variables), new LegalEntityId(Long.parseLong(required(variables, FlowableApprovalEngine.VARIABLE_LEGAL_ENTITY))),
                nodeKey, task.getName(), new UserId(Long.parseLong(required(variables, FlowableApprovalEngine.VARIABLE_INITIATOR))), candidates,
                LocalDate.parse(required(variables, FlowableApprovalEngine.VARIABLE_SUBMITTED_ON)),
                workdays(variables, NodeDecisions.DEADLINE_WORKDAYS + nodeKey), workdays(variables, NodeDecisions.ESCALATION_WORKDAYS + nodeKey),
                variables.firstVariable(NodeDecisions.AUTO_APPROVE_REPEAT + nodeKey).map(Boolean::parseBoolean).orElse(false),
                required(variables, FlowableApprovalEngine.VARIABLE_CONTENT_DIGEST)));
        if (passedBySystem) {
            task.setVariableLocal(FlowableApprovalEngine.VARIABLE_SYSTEM_COMPLETE, Boolean.TRUE);
        }
    }

    private static String required(ProcessVariables variables, String name) {
        return variables.firstVariable(name).orElseThrow(() -> new IllegalStateException("Process variable " + name + " is missing"));
    }

    private static OptionalInt workdays(ProcessVariables variables, String name) {
        return variables.firstVariable(name).map(raw -> OptionalInt.of(Integer.parseInt(raw))).orElse(OptionalInt.empty());
    }

    @Override
    public boolean isFailOnException() {
        return true;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }
}
