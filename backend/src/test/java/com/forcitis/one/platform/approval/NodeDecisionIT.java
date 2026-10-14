package com.forcitis.one.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The decision table of the process type says which routes run, what may be skipped or passed automatically, and the node deadlines. */
class NodeDecisionIT extends ApprovalIntegrationSupport {

    @Autowired
    DecideTasks decideTasks;

    @Autowired
    ScriptedHook hook;

    @Autowired
    org.flowable.dmn.api.DmnRepositoryService dmn;

    ApprovalInstanceId begin(String key, long initiator, long manager, TimesheetRoutingFlag... flags) {
        Set<String> names = new java.util.HashSet<>();
        for (TimesheetRoutingFlag flag : flags) {
            names.add(flag.flagName());
        }
        return tx.execute(s -> approvals.start(startRequest(key, new UserId(initiator), names,
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(String.valueOf(manager)))))));
    }

    @Test
    @DisplayName("the production table answers per node for the routing flags given: routed, skip, auto-approve, deadline and escalation interval")
    void productionTableAnswers() {
        long unit = newId();
        long head = newId();
        long initiator = newId();
        long manager = newId();
        organisation.unit(unit, null, head).person(head, unit, null).person(initiator, unit, head).person(manager, unit, null);
        ApprovalInstanceId instance = begin(newKey("table"), initiator, manager, TimesheetRoutingFlag.PROJECT_HOURS,
                TimesheetRoutingFlag.OVERHEAD_HOURS);

        Map<String, Object> variables = runtime.getVariables(instance.value());
        assertThat(variables).containsEntry("routed_projectManagerReview", true).containsEntry("routed_regionalHeadReview", false)
                .containsEntry("routed_supervisorReview", true);
        for (String node : List.of("projectManagerReview", "regionalHeadReview", "supervisorReview")) {
            boolean routed = (Boolean) variables.get("routed_" + node);
            assertThat(variables.get("skipWhenEmpty_" + node)).as(node).isEqualTo(routed);
            assertThat(variables.get("autoApproveRepeat_" + node)).as(node).isEqualTo(routed);
            assertThat(((Number) variables.get("deadlineWorkdays_" + node)).intValue()).as(node).isEqualTo(1);
            assertThat(((Number) variables.get("escalationWorkdays_" + node)).intValue()).as(node).isEqualTo(1);
        }
        assertThat(taskService.createTaskQuery().processInstanceId(instance.value()).list()).extracting(Task::getTaskDefinitionKey)
                .containsExactlyInAnyOrder("projectManagerReview", "supervisorReview");
    }

    @Test
    @DisplayName("the version of the table deployed at the start is the one that applies to that instance; a newer version applies to later starts only")
    void newVersionAppliesToLaterStartsOnly() {
        long unit = newId();
        long initiator = newId();
        long manager = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(manager, unit, null);
        ApprovalInstanceId before = begin(newKey("version-old"), initiator, manager, TimesheetRoutingFlag.PROJECT_HOURS);

        deployVariant("deadline3");
        ApprovalInstanceId after = begin(newKey("version-new"), initiator, manager, TimesheetRoutingFlag.PROJECT_HOURS);

        assertThat(((Number) runtime.getVariable(before.value(), "deadlineWorkdays_projectManagerReview")).intValue()).isEqualTo(1);
        assertThat(((Number) runtime.getVariable(after.value(), "deadlineWorkdays_projectManagerReview")).intValue()).isEqualTo(3);
        assertThat(((Number) runtime.getVariable(after.value(), "escalationWorkdays_projectManagerReview")).intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("RULE-300 whether a node may be skipped when nobody is found is the table's answer: the same chain is skipped under one version and goes to the administrator under another")
    void skipPermissionComesFromTheTable() {
        long unit = newId();
        long initiator = newId();
        long manager = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(manager, unit, null);

        ApprovalInstanceId skipping = begin(newKey("skip"), initiator, manager, TimesheetRoutingFlag.PROJECT_HOURS,
                TimesheetRoutingFlag.OVERHEAD_HOURS);
        assertThat(taskService.createTaskQuery().processInstanceId(skipping.value()).list()).extracting(Task::getTaskDefinitionKey)
                .containsExactly("projectManagerReview");

        deployVariant("noskip");
        ApprovalInstanceId notSkipping = begin(newKey("noskip"), initiator, manager, TimesheetRoutingFlag.PROJECT_HOURS,
                TimesheetRoutingFlag.OVERHEAD_HOURS);
        Task supervisorTask = taskService.createTaskQuery().processInstanceId(notSkipping.value()).taskDefinitionKey("supervisorReview")
                .includeIdentityLinks().singleResult();
        assertThat(supervisorTask).as("nobody above the initiator, skipping not allowed: the process administrator gets it").isNotNull();
        assertThat(taskService.getIdentityLinksForTask(supervisorTask.getId()).stream().filter(l -> "candidate".equals(l.getType()))
                .map(l -> l.getUserId()).toList()).containsExactly(String.valueOf(ADMINISTRATOR));
    }

    @Test
    @DisplayName("RULE-302 whether a repeat approver passes automatically is the table's answer too")
    void autoApprovePermissionComesFromTheTable() {
        deployVariant("repeat");
        deployVariant("repeat-manual");
        hook.next = com.forcitis.one.platform.approval.api.HookDecision.allow();
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        ApprovalInstanceId instance = tx.execute(s -> approvals.start(startRequest(newKey("manual-repeat"), new UserId(initiator), Set.of("again"),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(String.valueOf(manager)))))));

        decideTasks.execute(new UserId(manager), List.of(new TaskDecision(openTaskId(instance), DecisionOutcome.APPROVE, null)));

        assertThat(approvals.isFinished(instance)).as("unchanged content, but the table does not allow the automatic pass").isFalse();
        assertThat(openTask(instance).getTaskDefinitionKey()).isEqualTo("repeatReview");
    }

    @Test
    @DisplayName("G-1 a node the table has no rule for fails the start; nothing is assumed and nothing is left behind")
    void uncoveredNodeFailsTheStart() {
        deployVariant("uncovered");
        long initiator = newId();
        long manager = newId();
        organisation.person(initiator, 1, null).person(manager, 1, null);
        String key = newKey("uncovered");

        assertThatThrownBy(() -> begin(key, initiator, manager, TimesheetRoutingFlag.PROJECT_HOURS)).hasStackTraceContaining("has no rule for node");
        assertThat(jdbc.queryForObject("select count(*) from platform_approval.approval_requests where document_key = ?", Integer.class, key)).isZero();
        assertThat(runtime.createProcessInstanceQuery().processInstanceBusinessKey("TIMESHEET:" + key).count()).isZero();
    }

    @Test
    @DisplayName("the shipped table is deployed with the application and named after the process type")
    void shippedTableIsDeployed() {
        assertThat(dmn.createDecisionQuery().decisionKey("cost_timesheet_approval_nodes").latestVersion().singleResult()).isNotNull();
    }
}
