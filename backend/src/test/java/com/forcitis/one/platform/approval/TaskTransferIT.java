package com.forcitis.one.platform.approval;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static com.forcitis.one.platform.approval.OrdinarySessions.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.application.TransferTask;
import com.forcitis.one.platform.approval.events.TaskTransferred;
import com.forcitis.one.platform.identity.api.Permission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** RULE-285: only the person holding a task hands it over; the new handler is checked, the operation is audited and the new handler is told. */
class TaskTransferIT extends ApprovalIntegrationSupport {

    @Autowired
    TransferTask transferTask;

    @Autowired
    ClaimTask claimTask;

    @Autowired
    DecideTasks decideTasks;

    record Scene(String key, ApprovalInstanceId instance, ApprovalTaskId task, long initiator, long handler, long colleague, long other) {
    }

    Scene scene() {
        long unit = newId();
        long initiator = newId();
        long handler = newId();
        long colleague = newId();
        long other = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(handler, unit, null).person(colleague, unit, null)
                .person(other, unit, null);
        String key = newKey("transfer");
        ApprovalInstanceId instance = tx.execute(s -> approvals.start(startRequest(key, TestAccounts.employee(initiator),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(handler).toString()))))));
        return new Scene(key, instance, openTaskId(instance), initiator, handler, colleague, other);
    }

    @Test
    @DisplayName("RULE-285 the current handler hands the task over: the new handler decides, the old one no longer may; audited and announced")
    void handlerTransfersTheTask() {
        Scene s = scene();
        actAs(s.handler());
        claimTask.execute(s.task(), TestAccounts.account(s.handler()), java.util.OptionalLong.empty());

        transferTask.execute(s.task(), TestAccounts.account(s.handler()), TestAccounts.employee(s.colleague()), "on leave", java.util.OptionalLong.empty());

        assertThat(openTask(s.instance()).getAssignee()).isEqualTo(TestAccounts.account(s.colleague()).value().toString());
        assertThat(jdbc.queryForObject("select assignee_id from platform_approval.approval_tasks where task_id = ?", java.util.UUID.class, s.task().value()))
                .isEqualTo(TestAccounts.account(s.colleague()).value());
        assertThatThrownBy(() -> decideTasks.execute(TestAccounts.account(s.handler()), List.of(new TaskDecision(s.task(), DecisionOutcome.APPROVE, null)), java.util.OptionalLong.empty()))
                .isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> org.assertj.core.api.Assertions.assertThat(e.errorCode()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND));
        assertThat(events.of(TaskTransferred.class)).singleElement().satisfies(event -> {
            assertThat(event.from()).isEqualTo(TestAccounts.account(s.handler()));
            assertThat(event.to()).isEqualTo(TestAccounts.account(s.colleague()));
            assertThat(event.reason()).isEqualTo(new com.forcitis.one.kernel.Reason("on leave"));
            assertThat(event.envelope().actorId()).isEqualTo(TestAccounts.account(s.handler()).value().toString());
        });
        verify(wecom, timeout(10_000)).send(argThat(card -> card.wecomUserIds().equals(List.of(new WecomUserId(organisation.wecomOf(s.colleague()))))
                && card.deduplicationKey().startsWith("approval-task:" + s.task().value() + ":transfer:")));

        decideTasks.execute(TestAccounts.account(s.colleague()), List.of(new TaskDecision(s.task(), DecisionOutcome.APPROVE, null)), java.util.OptionalLong.empty());
        assertThat(requestStatus(s.key())).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("RULE-285/I-7 a candidate who has not claimed the task, and anybody else, may not hand it over")
    void onlyTheHolderMayTransfer() {
        Scene s = scene();

        assertThatThrownBy(() -> transferTask.execute(s.task(), TestAccounts.account(s.handler()), TestAccounts.employee(s.colleague()), "r", java.util.OptionalLong.empty()))
                .as("a candidate that has not claimed it").isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> org.assertj.core.api.Assertions.assertThat(e.errorCode()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND));
        claimTask.execute(s.task(), TestAccounts.account(s.handler()), java.util.OptionalLong.empty());
        assertThatThrownBy(() -> transferTask.execute(s.task(), TestAccounts.account(s.other()), TestAccounts.employee(s.colleague()), "r", java.util.OptionalLong.empty()))
                .as("a stranger").isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> org.assertj.core.api.Assertions.assertThat(e.errorCode()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND));
        assertThatThrownBy(() -> transferTask.execute(s.task(), TestAccounts.account(s.initiator()), TestAccounts.employee(s.colleague()), "r", java.util.OptionalLong.empty()))
                .as("the initiator").isInstanceOfSatisfying(com.forcitis.one.kernel.DomainException.class, e -> org.assertj.core.api.Assertions.assertThat(e.errorCode()).isEqualTo(com.forcitis.one.platform.approval.api.ApprovalError.PLATFORM_APPROVAL_TASK_NOT_FOUND));

        assertThat(openTask(s.instance()).getAssignee()).isEqualTo(TestAccounts.account(s.handler()).value().toString());
        verify(audit, atLeastOnce()).recordIndependently(argThat(entry -> entry.action().equals("platform.approval.task.denied")
                && entry.detail().equals("transfer")));
    }

    @Test
    @DisplayName("RULE-285 the new handler must be an employed person other than the handler and the initiator")
    void theTargetIsChecked() {
        Scene s = scene();
        long separated = newId();
        organisation.separated(separated, 1, null);
        claimTask.execute(s.task(), TestAccounts.account(s.handler()), java.util.OptionalLong.empty());

        for (long invalid : new long[] {424242L, separated, s.handler(), s.initiator()}) {
            assertThatThrownBy(() -> transferTask.execute(s.task(), TestAccounts.account(s.handler()), TestAccounts.employee(invalid), "r", java.util.OptionalLong.empty()))
                    .as("target %s", invalid).isInstanceOfSatisfying(DomainException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID));
        }
        assertThat(openTask(s.instance()).getAssignee()).isEqualTo(TestAccounts.account(s.handler()).value().toString());
    }

    @Test
    @DisplayName("RULE-285/RULE-248 a task cannot be handed to somebody who already has a task on the same countersign node")
    void notToAnotherApproverOfTheSameNode() {
        long unit = newId();
        long initiator = newId();
        long first = newId();
        long second = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(first, unit, null).person(second, unit, null);
        ApprovalInstanceId instance = tx.execute(st -> approvals.start(startRequest(newKey("transfer-twin"), TestAccounts.employee(initiator),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()), List.of(new ApprovalField(
                        TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(first).toString(), TestAccounts.employee(second).toString()))))));
        ApprovalTaskId firstTask = new ApprovalTaskId(taskService.createTaskQuery().processInstanceId(instance.value()).taskCandidateUser(TestAccounts.account(first).value().toString())
                .singleResult().getId());
        claimTask.execute(firstTask, TestAccounts.account(first), java.util.OptionalLong.empty());

        assertThatThrownBy(() -> transferTask.execute(firstTask, TestAccounts.account(first), TestAccounts.employee(second), "r", java.util.OptionalLong.empty())).isInstanceOfSatisfying(
                DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID));
    }

    @Test
    @DisplayName("RULE-285 D-1a-179 over HTTP the caller is the session user: the holder gets 204 whatever permission codes the session has, anybody else 404, the body names the new handler and the reason")
    void overHttp() throws Exception {
        Scene s = scene();
        claimTask.execute(s.task(), TestAccounts.account(s.handler()), java.util.OptionalLong.empty());
        String url = "/api/v1/platform/approval-tasks/" + s.task().value() + ":transfer";
        String body = "{\"assigneeId\":\"" + TestAccounts.employee(s.colleague()) + "\",\"reason\":\"on leave\"}";

        mvc.perform(post(url).with(csrf()).with(currentEtag()).with(user(TestAccounts.account(s.other()).value().toString()).authorities(com.forcitis.one.platform.approval.OrdinarySessions.with(Permission.Codes.PLATFORM_APPROVAL_TASK_APPROVE)))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(post(url).with(csrf()).with(currentEtag()).with(user(TestAccounts.account(s.handler()).value().toString()).authorities(com.forcitis.one.platform.approval.OrdinarySessions.with(Permission.Codes.PLATFORM_APPROVAL_TASK_READ)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":\"" + TestAccounts.employee(s.colleague()) + "\",\"reason\":\" \"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post(url).with(csrf()).with(currentEtag()).with(user(TestAccounts.account(s.handler()).value().toString()).authorities(com.forcitis.one.platform.approval.OrdinarySessions.with(Permission.Codes.PLATFORM_APPROVAL_TASK_READ)))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        assertThat(openTask(s.instance()).getAssignee()).isEqualTo(TestAccounts.account(s.colleague()).value().toString());
    }
}
