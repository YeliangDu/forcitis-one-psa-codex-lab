package com.forcitis.one.platform.approval;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.domain.ApprovalEngine;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** D-1a-100: the node's own attributes in the process file say which routes run, what may be skipped or passed automatically, and the node deadlines. */
class NodeRoutingIT extends ApprovalIntegrationSupport {

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    ScriptedHook hook;

    @Autowired
    ApprovalEngine engine;

    ApprovalInstanceId begin(String key, long initiator, long manager, TimesheetRoutingFlag... flags) {
        return start(key, initiator, manager, flags);
    }

    @Test
    @DisplayName("D-1a-100 the shipped process answers per node for the routing flags given: routed, with the deadline recorded on each task it creates")
    void shippedProcessRoutesByItsAttributes() {
        long unit = newId();
        long head = newId();
        long initiator = newId();
        long manager = newId();
        organisation.unit(unit, null, head).person(head, unit, null).person(initiator, unit, head).person(manager, unit, null);
        ApprovalInstanceId instance = begin(newKey("route"), initiator, manager, TimesheetRoutingFlag.PROJECT_HOURS,
                TimesheetRoutingFlag.OVERHEAD_HOURS);

        Map<String, Object> variables = runtime.getVariables(instance.value().toString());
        assertThat(variables).containsEntry("routed_projectManagerReview", true).containsEntry("routed_orgUnitHeadApproval", false)
                .containsEntry("routed_supervisorReview", true);
        assertThat(taskService.createTaskQuery().processInstanceId(instance.value().toString()).list()).extracting(Task::getTaskDefinitionKey)
                .containsExactlyInAnyOrder("projectManagerReview", "supervisorReview");
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_tasks where instance_id = ? and due_at is not null"
                + " and remind_at is not null and escalate_at is not null", Integer.class, instance.value())).isEqualTo(2);
    }

    @Test
    @DisplayName("M-10 D-1a-100 the routing flags the process reacts to are the ones its nodes declare")
    void theProcessDeclaresItsFlags() {
        assertThat(engine.routingFlagsOf(TimesheetApprovalProcess.KEY)).containsExactlyInAnyOrder("projectHours", "preSalesHours",
                "overheadHours");
    }

    @Test
    @DisplayName("M-10 G-1 a routing flag no node declares is refused, not ignored; nothing is left behind")
    void anUnknownFlagIsRefused() {
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        String key = newKey("unknown-flag");

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> approvals.start(startRequest(key, TestAccounts.employee(initiator),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName(), "projectHoursTypo"),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(manager).toString())))))))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_ROUTING_FLAG_UNKNOWN));

        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_requests where document_id = cast(? as uuid)", Integer.class, key)).isZero();
        assertThat(runtime.createProcessInstanceQuery().processInstanceBusinessKey(key).count()).isZero();
    }

    @Test
    @DisplayName("G-1 a deadline without its escalation interval makes the node unreadable: the start fails and nothing is left behind")
    void anUnpairedDeadlineFailsTheStart() {
        deployVariant("unpaired");
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        String key = newKey("unpaired");

        assertThatThrownBy(() -> begin(key, initiator, manager, TimesheetRoutingFlag.PROJECT_HOURS)).hasStackTraceContaining("pair");

        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_requests where document_id = cast(? as uuid)", Integer.class, key)).isZero();
        assertThat(runtime.createProcessInstanceQuery().processInstanceBusinessKey(key).count()).isZero();
    }

    @Test
    @DisplayName("RULE-300 whether a node may be skipped when nobody is found is the node's attribute: the same chain is skipped under one version and goes to the administrator under another")
    void skipPermissionComesFromTheNode() {
        long unit = newId();
        long initiator = newId();
        long manager = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(manager, unit, null);

        ApprovalInstanceId skipping = begin(newKey("skip"), initiator, manager, TimesheetRoutingFlag.PROJECT_HOURS,
                TimesheetRoutingFlag.OVERHEAD_HOURS);
        assertThat(taskService.createTaskQuery().processInstanceId(skipping.value().toString()).list()).extracting(Task::getTaskDefinitionKey)
                .containsExactly("projectManagerReview");

        deployVariant("noskip");
        ApprovalInstanceId notSkipping = begin(newKey("noskip"), initiator, manager, TimesheetRoutingFlag.PROJECT_HOURS,
                TimesheetRoutingFlag.OVERHEAD_HOURS);
        Task supervisorTask = taskService.createTaskQuery().processInstanceId(notSkipping.value().toString()).taskDefinitionKey("supervisorReview")
                .includeIdentityLinks().singleResult();
        assertThat(supervisorTask).as("nobody above the initiator, skipping not allowed: the process administrator gets it").isNotNull();
        assertThat(taskService.getIdentityLinksForTask(supervisorTask.getId()).stream().filter(l -> "candidate".equals(l.getType()))
                .map(l -> l.getUserId()).toList()).containsExactly(TestAccounts.account(ADMINISTRATOR).value().toString());
    }

    @Test
    @DisplayName("RULE-302 whether a repeat approver passes automatically is the node's attribute too")
    void autoApprovePermissionComesFromTheNode() {
        deployVariant("repeat-manual");
        hook.next = com.forcitis.one.platform.approval.api.HookDecision.allow();
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        ApprovalInstanceId instance = tx.execute(s -> approvals.start(startRequest(newKey("manual-repeat"), TestAccounts.employee(initiator), Set.of("again"),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(manager).toString()))))));

        decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(openTaskId(instance), DecisionOutcome.APPROVE, null)), java.util.OptionalLong.empty());

        assertThat(approvals.isFinished(instance)).as("unchanged content, but the node does not allow the automatic pass").isFalse();
        assertThat(openTask(instance).getTaskDefinitionKey()).isEqualTo("repeatReview");
    }

    @Test
    @DisplayName("the shipped process is deployed with the application and named after the process type; no decision table is deployed with it")
    void shippedProcessIsDeployed() {
        assertThat(repository.createProcessDefinitionQuery().processDefinitionKey("cost_timesheet_approval").latestVersion().singleResult())
                .isNotNull();
    }
}
