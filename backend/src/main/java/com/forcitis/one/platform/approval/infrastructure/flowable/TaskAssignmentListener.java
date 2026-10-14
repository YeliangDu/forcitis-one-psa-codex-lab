package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.AssigneeResolver;
import com.forcitis.one.platform.approval.domain.NodePolicy;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import com.forcitis.one.platform.approval.domain.Resolution;
import com.forcitis.one.platform.approval.domain.ResolutionKind;
import com.forcitis.one.platform.approval.events.TaskAssigned;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Runs inside the engine command creating the task; nobody found goes to the process administrator, and without one the whole command rolls back (never a silent skip). */
@Component
class TaskAssignmentListener implements FlowableEventListener {

    private static final Logger log = LoggerFactory.getLogger(TaskAssignmentListener.class);

    private final OrgDirectory org;
    private final RoleDirectory roles;
    private final AssignmentProperties properties;
    private final ApplicationEventPublisher events;
    private final MeterRegistry meters;
    private final ObjectProvider<Clock> clock;

    TaskAssignmentListener(OrgDirectory org, RoleDirectory roles, AssignmentProperties properties, ApplicationEventPublisher events,
            MeterRegistry meters, ObjectProvider<Clock> clock) {
        this.clock = clock;
        this.org = org;
        this.roles = roles;
        this.properties = properties;
        this.events = events;
        this.meters = meters;
    }

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEngineEntityEvent entityEvent) || !(entityEvent.getEntity() instanceof TaskEntity task)
                || task.getProcessInstanceId() == null) {
            return;
        }
        BpmnModel model = ProcessDefinitionUtil.getBpmnModel(task.getProcessDefinitionId());
        UserTask node = (UserTask) model.getFlowElement(task.getTaskDefinitionKey());
        NodePolicy policy = NodePolicyReader.read(node);

        ProcessVariables variables = EngineVariables.fromEngine(task.getVariables());
        UserId initiator = new UserId(Long.parseLong(variables.firstVariable(FlowableApprovalEngine.VARIABLE_INITIATOR)
                .orElseThrow(() -> new IllegalStateException("Instance " + task.getProcessInstanceId() + " has no initiator"))));
        UserId administrator = properties.processAdministratorUserId() == null ? null : new UserId(properties.processAdministratorUserId());

        Resolution resolution = new AssigneeResolver(org, roles, administrator)
                .resolve(policy.rule(), policy.selfApprovalAllowed(), policy.skipWhenEmpty(), initiator, variables);
        if (resolution.kind() == ResolutionKind.SKIPPED) {
            log.info("Node {} (instance {}) has nobody to assign and is skipped", node.getId(), task.getProcessInstanceId());
            meters.counter("platform.approval.assignee.skipped").increment();
            task.setVariableLocal(FlowableApprovalEngine.VARIABLE_SKIPPED, Boolean.TRUE);
            return;
        }
        if (resolution.kind() == ResolutionKind.UNRESOLVED) {
            throw new IllegalStateException("Nobody can handle node " + node.getId() + " of instance " + task.getProcessInstanceId()
                    + " and no process administrator is configured (forcitis.platform.approval.assignment.process-administrator-user-id)");
        }
        if (resolution.kind() != ResolutionKind.RESOLVED) {
            log.warn("Assignment of node {} (instance {}) is {}: candidates {}", node.getId(), task.getProcessInstanceId(),
                    resolution.kind(), resolution.candidates());
            meters.counter("platform.approval.assignee.fallback", "kind", resolution.kind().name().toLowerCase(Locale.ROOT)).increment();
        }
        task.setVariableLocal(FlowableApprovalEngine.VARIABLE_ASSIGNED_AT, clock.getIfAvailable(Clock::systemUTC).millis());
        List<UserId> candidates = resolution.candidates().stream().sorted(Comparator.comparingLong(UserId::value)).toList();
        candidates.forEach(candidate -> task.addCandidateUser(String.valueOf(candidate.value())));

        ProcessKey processKey = ProcessKey.valueOf(ProcessDefinitionUtil.getProcessDefinition(task.getProcessDefinitionId())
                .getKey().toUpperCase(Locale.ROOT));
        events.publishEvent(new TaskAssigned(new ApprovalTaskId(task.getId()), new ApprovalInstanceId(task.getProcessInstanceId()),
                processKey, variables.firstVariable(FlowableApprovalEngine.VARIABLE_BUSINESS_KEY).orElse(""), node.getId(),
                task.getName(), initiator, candidates));
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
