package com.forcitis.one.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalRequest;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TimesheetBranchesIT extends ApprovalIntegrationSupport {

    @Autowired
    DecideTasks decideTasks;

    record Staff(long initiator, long projectManager, long regionalHead, long supervisor) {
    }

    Staff staff() {
        long unit = newId();
        Staff s = new Staff(newId(), newId(), newId(), newId());
        organisation.unit(unit, null, s.supervisor()).person(s.supervisor(), unit, null).person(s.initiator(), unit, s.supervisor())
                .person(s.projectManager(), unit, null).person(s.regionalHead(), unit, null);
        return s;
    }

    ApprovalInstanceId start(String key, Staff s, List<String> regionalHeads, TimesheetRoutingFlag... flags) {
        List<ApprovalField> fields = new ArrayList<>();
        fields.add(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(String.valueOf(s.projectManager()))));
        fields.add(new ApprovalField(TimesheetApprovalField.REGIONAL_HEAD.fieldName(), regionalHeads));
        Set<String> names = new java.util.HashSet<>();
        for (TimesheetRoutingFlag flag : flags) {
            names.add(flag.flagName());
        }
        return tx.execute(status -> approvals.start(new ApprovalRequest(ProcessKey.COST_TIMESHEET_APPROVAL, key,
                new UserId(s.initiator()), names, fields)));
    }

    List<String> openNodes(ApprovalInstanceId instance) {
        return taskService.createTaskQuery().processInstanceId(instance.value()).list().stream().map(Task::getTaskDefinitionKey)
                .sorted().toList();
    }

    void approve(long actor, ApprovalInstanceId instance, String node) {
        decideTasks.execute(new UserId(actor), List.of(new TaskDecision(openTaskId(instance, node), DecisionOutcome.APPROVE, "ok")));
    }

    @Test
    @DisplayName("TIMESHEET v15 only project hours: one parallel branch opens, the other two do not")
    void onlyProjectHours() {
        Staff s = staff();
        String key = newKey("branches");
        ApprovalInstanceId instance = start(key, s, List.of(String.valueOf(s.regionalHead())), TimesheetRoutingFlag.PROJECT_HOURS);

        assertThat(openNodes(instance)).containsExactly("projectManagerReview");
        approve(s.projectManager(), instance, "projectManagerReview");
        assertThat(requestStatus(key)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("TIMESHEET v15 project and pre-sales hours: both branches are open at once and the approval ends when both are approved")
    void projectAndPreSalesRunInParallel() {
        Staff s = staff();
        String key = newKey("branches");
        ApprovalInstanceId instance = start(key, s, List.of(String.valueOf(s.regionalHead())), TimesheetRoutingFlag.PROJECT_HOURS,
                TimesheetRoutingFlag.PRE_SALES_HOURS);

        assertThat(openNodes(instance)).containsExactly("projectManagerReview", "regionalHeadReview");
        approve(s.regionalHead(), instance, "regionalHeadReview");
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
        approve(s.projectManager(), instance, "projectManagerReview");
        assertThat(requestStatus(key)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("TIMESHEET v15 all three hour types: three branches in parallel, joined before the approval ends")
    void allThreeBranches() {
        Staff s = staff();
        String key = newKey("branches");
        ApprovalInstanceId instance = start(key, s, List.of(String.valueOf(s.regionalHead())), TimesheetRoutingFlag.PROJECT_HOURS,
                TimesheetRoutingFlag.PRE_SALES_HOURS, TimesheetRoutingFlag.OVERHEAD_HOURS);

        assertThat(openNodes(instance)).containsExactly("projectManagerReview", "regionalHeadReview", "supervisorReview");
        approve(s.supervisor(), instance, "supervisorReview");
        approve(s.projectManager(), instance, "projectManagerReview");
        assertThat(requestStatus(key)).isEqualTo("RUNNING");
        approve(s.regionalHead(), instance, "regionalHeadReview");
        assertThat(requestStatus(key)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("TIMESHEET v15 a rejection in any branch returns the timesheet and ends the round with the other branches withdrawn")
    void rejectionInOneBranchEndsTheRound() {
        Staff s = staff();
        String key = newKey("branches");
        ApprovalInstanceId instance = start(key, s, List.of(String.valueOf(s.regionalHead())), TimesheetRoutingFlag.PROJECT_HOURS,
                TimesheetRoutingFlag.OVERHEAD_HOURS);

        decideTasks.execute(new UserId(s.supervisor()),
                List.of(new TaskDecision(openTaskId(instance, "supervisorReview"), DecisionOutcome.REJECT, "wrong hours")));

        assertThat(requestStatus(key)).isEqualTo("RETURNED");
        assertThat(openNodes(instance)).isEmpty();
        assertThat(approvals.isFinished(instance)).isTrue();
    }

    @Test
    @DisplayName("RULE-300 a node that allows skipping and finds nobody passes; the approval goes on without it")
    void emptyNodeIsSkipped() {
        Staff s = staff();
        String key = newKey("branches");
        ApprovalInstanceId instance = start(key, s, List.of(), TimesheetRoutingFlag.PROJECT_HOURS, TimesheetRoutingFlag.PRE_SALES_HOURS);

        assertThat(openNodes(instance)).containsExactly("projectManagerReview");
        approve(s.projectManager(), instance, "projectManagerReview");
        assertThat(requestStatus(key)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("RULE-300 when every branch is skipped the approval is APPROVED at once")
    void allBranchesSkipped() {
        Staff s = staff();
        String key = newKey("branches");
        ApprovalInstanceId instance = tx.execute(status -> approvals.start(new ApprovalRequest(ProcessKey.COST_TIMESHEET_APPROVAL, key,
                new UserId(s.initiator()), Set.of(TimesheetRoutingFlag.PRE_SALES_HOURS.flagName()), List.of())));

        assertThat(openNodes(instance)).isEmpty();
        assertThat(requestStatus(key)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("a timesheet with no hour type routes nowhere: the start fails loudly instead of approving silently")
    void noRoutingFlagFails() {
        Staff s = staff();
        assertThatThrownBy(() -> start(newKey("branches"), s, List.of(), new TimesheetRoutingFlag[0])).isInstanceOf(RuntimeException.class);
    }
}
