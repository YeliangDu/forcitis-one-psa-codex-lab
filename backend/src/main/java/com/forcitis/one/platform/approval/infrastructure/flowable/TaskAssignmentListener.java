package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.NodeName;
import com.forcitis.one.platform.approval.api.ProcessName;
import com.forcitis.one.platform.approval.application.TaskRegistrar;
import com.forcitis.one.platform.approval.application.TaskRegistration;
import com.forcitis.one.platform.approval.domain.NodePolicy;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.approval.domain.Resolution;
import java.time.Clock;
import java.util.List;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.stereotype.Component;

/**
 * Runs inside the engine command creating the task. A countersign task already carries its one approver; any other node is resolved
 * here. The task is registered with the approval module, which also tells whether the system passes it itself (RULE-302).
 */
@Component
class TaskAssignmentListener implements FlowableEventListener {

    private final NodeAssignment assignment;
    private final TaskRegistrar registrar;
    private final Clock clock;

    TaskAssignmentListener(NodeAssignment assignment, TaskRegistrar registrar, Clock clock) {
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
        NodePolicy policy = NodePolicyReader.read(node);

        List<UserId> candidates;
        if (node.getLoopCharacteristics() != null) {
            candidates = List.of(EngineUserIds.parse(variables.firstVariable(ApproverResolution.ELEMENT_VARIABLE)
                    .orElseThrow(() -> new IllegalStateException("Countersign task of node " + node.getId() + " has no approver")),
                    "the approver variable of countersign node " + node.getId()));
        } else {
            Resolution resolution = assignment.resolve(node, task.getProcessInstanceId(), task.getVariables());
            if (NodeAssignment.isSkipped(resolution)) {
                if (resolution.namedApproverInvalid()) {
                    task.setVariable(FlowableApprovalEngine.VARIABLE_APPROVER_INVALID, Boolean.TRUE);
                }
                task.setVariableLocal(FlowableApprovalEngine.VARIABLE_SYSTEM_COMPLETE, Boolean.TRUE);
                return;
            }
            candidates = resolution.candidates().stream().sorted(EngineUserIds.ORDER).toList();
        }

        task.setVariableLocal(FlowableApprovalEngine.VARIABLE_ASSIGNED_AT, clock.millis());
        candidates.forEach(candidate -> task.addCandidateUser(EngineUserIds.text(candidate)));

        boolean passedBySystem = registrar.register(new TaskRegistration(EngineIds.task(task.getId()),
                EngineIds.instance(task.getProcessInstanceId()),
                new ProcessName(ProcessDefinitionUtil.getProcessDefinition(task.getProcessDefinitionId()).getName()),
                new NodeKey(node.getId()), new NodeName(task.getName()), candidates, policy.autoApproveRepeat(), policy.deadline(),
                new ContentHash(required(variables, FlowableApprovalEngine.VARIABLE_CONTENT_HASH))));
        if (passedBySystem) {
            task.setVariableLocal(FlowableApprovalEngine.VARIABLE_SYSTEM_COMPLETE, Boolean.TRUE);
        }
    }

    private static String required(ProcessVariables variables, String name) {
        return variables.firstVariable(name).orElseThrow(() -> new IllegalStateException("Process variable " + name + " is missing"));
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
