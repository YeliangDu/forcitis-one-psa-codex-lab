package com.forcitis.one.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.application.ApprovalError;
import com.forcitis.one.platform.approval.application.ApprovalOperationException;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.application.TransferTask;
import com.forcitis.one.platform.identity.api.Permission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;

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
        ApprovalInstanceId instance = tx.execute(s -> approvals.start(startRequest(key, new UserId(initiator),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(String.valueOf(handler)))))));
        return new Scene(key, instance, openTaskId(instance), initiator, handler, colleague, other);
    }

    @Test
    @DisplayName("RULE-285 the current handler hands the task over: the new handler decides, the old one no longer may; audited and announced")
    void handlerTransfersTheTask() {
        Scene s = scene();
        claimTask.execute(s.task(), new UserId(s.handler()));

        transferTask.execute(s.task(), new UserId(s.handler()), new UserId(s.colleague()));

        assertThat(openTask(s.instance()).getAssignee()).isEqualTo(String.valueOf(s.colleague()));
        assertThat(jdbc.queryForObject("select assignee_id from platform_approval.approval_tasks where task_id = ?", Long.class, s.task().value()))
                .isEqualTo(s.colleague());
        assertThatThrownBy(() -> decideTasks.execute(new UserId(s.handler()), List.of(new TaskDecision(s.task(), DecisionOutcome.APPROVE, null))))
                .isInstanceOf(AccessDeniedException.class);
        verify(audit, atLeastOnce()).record(argThat(entry -> entry.action().equals("platform.approval.task.transfer")
                && entry.actorId().equals(new UserId(s.handler())) && entry.detail().equals("to:" + s.colleague())));
        verify(wecom, timeout(10_000)).send(argThat(card -> card.wecomUserIds().equals(List.of(new WecomUserId(organisation.wecomOf(s.colleague()))))
                && card.deduplicationKey().startsWith("approval-task:" + s.task().value() + ":transfer:")));

        decideTasks.execute(new UserId(s.colleague()), List.of(new TaskDecision(s.task(), DecisionOutcome.APPROVE, null)));
        assertThat(requestStatus(s.key())).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("RULE-285/I-7 a candidate who has not claimed the task, and anybody else, may not hand it over")
    void onlyTheHolderMayTransfer() {
        Scene s = scene();

        assertThatThrownBy(() -> transferTask.execute(s.task(), new UserId(s.handler()), new UserId(s.colleague())))
                .as("a candidate that has not claimed it").isInstanceOf(AccessDeniedException.class);
        claimTask.execute(s.task(), new UserId(s.handler()));
        assertThatThrownBy(() -> transferTask.execute(s.task(), new UserId(s.other()), new UserId(s.colleague())))
                .as("a stranger").isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> transferTask.execute(s.task(), new UserId(s.initiator()), new UserId(s.colleague())))
                .as("the initiator").isInstanceOf(AccessDeniedException.class);

        assertThat(openTask(s.instance()).getAssignee()).isEqualTo(String.valueOf(s.handler()));
        verify(audit, atLeastOnce()).recordIndependently(argThat(entry -> entry.action().equals("platform.approval.task.denied")
                && entry.detail().equals("transfer")));
    }

    @Test
    @DisplayName("RULE-285 the new handler must be an employed person other than the handler and the initiator")
    void theTargetIsChecked() {
        Scene s = scene();
        long separated = newId();
        organisation.separated(separated, 1, null);
        claimTask.execute(s.task(), new UserId(s.handler()));

        for (long invalid : new long[] {424242L, separated, s.handler(), s.initiator()}) {
            assertThatThrownBy(() -> transferTask.execute(s.task(), new UserId(s.handler()), new UserId(invalid)))
                    .as("target %s", invalid).isInstanceOfSatisfying(ApprovalOperationException.class,
                            e -> assertThat(e.error()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID));
        }
        assertThat(openTask(s.instance()).getAssignee()).isEqualTo(String.valueOf(s.handler()));
    }

    @Test
    @DisplayName("RULE-285/RULE-248 a task cannot be handed to somebody who already has a task on the same countersign node")
    void notToAnotherApproverOfTheSameNode() {
        long unit = newId();
        long initiator = newId();
        long first = newId();
        long second = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(first, unit, null).person(second, unit, null);
        ApprovalInstanceId instance = tx.execute(st -> approvals.start(startRequest(newKey("transfer-twin"), new UserId(initiator),
                Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()), List.of(new ApprovalField(
                        TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(String.valueOf(first), String.valueOf(second)))))));
        ApprovalTaskId firstTask = new ApprovalTaskId(taskService.createTaskQuery().processInstanceId(instance.value()).taskCandidateUser(String.valueOf(first))
                .singleResult().getId());
        claimTask.execute(firstTask, new UserId(first));

        assertThatThrownBy(() -> transferTask.execute(firstTask, new UserId(first), new UserId(second))).isInstanceOfSatisfying(
                ApprovalOperationException.class, e -> assertThat(e.error()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID));
    }

    @Test
    @DisplayName("RULE-285 over HTTP the caller is the session user: the holder gets 204, anybody else 403, the body names only the new handler")
    void overHttp() throws Exception {
        Scene s = scene();
        claimTask.execute(s.task(), new UserId(s.handler()));
        String url = "/api/v1/platform/approval-tasks/" + s.task().value() + ":transfer";
        String body = "{\"assigneeId\":" + s.colleague() + "}";

        mvc.perform(post(url).with(csrf()).with(user(String.valueOf(s.other())).authorities(() -> Permission.Codes.PLATFORM_APPROVAL_TASK_APPROVE))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post(url).with(csrf()).with(user(String.valueOf(s.handler())).authorities(() -> Permission.Codes.PLATFORM_APPROVAL_TASK_READ))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post(url).with(csrf()).with(user(String.valueOf(s.handler())).authorities(() -> Permission.Codes.PLATFORM_APPROVAL_TASK_APPROVE))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        assertThat(openTask(s.instance()).getAssignee()).isEqualTo(String.valueOf(s.colleague()));
    }
}
