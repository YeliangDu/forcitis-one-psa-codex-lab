package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.platform.approval.domain.Resolution;
import java.util.Collection;
import java.util.function.Function;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

/** The multi-instance collection of a countersign node: one task per distinct approver. */
@Component("approverResolution")
class ApproverResolution implements Function<DelegateExecution, Collection<String>> {

    static final String ELEMENT_VARIABLE = "approver";

    private final NodeAssignment assignment;

    ApproverResolution(NodeAssignment assignment) {
        this.assignment = assignment;
    }

    @Override
    public Collection<String> apply(DelegateExecution execution) {
        UserTask node = (UserTask) execution.getCurrentFlowElement();
        Resolution resolution = assignment.resolve(node, execution.getProcessInstanceId(), execution.getVariables());
        if (resolution.namedApproverInvalid()) {
            execution.setVariable(FlowableApprovalEngine.VARIABLE_APPROVER_INVALID, Boolean.TRUE);
        }
        return resolution.candidates().stream().sorted(EngineUserIds.ORDER).map(EngineUserIds::text).toList();
    }
}
