package com.forcitis.one.platform.approval;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static com.forcitis.one.platform.approval.OrdinarySessions.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.DecideTasks;
import com.forcitis.one.platform.approval.application.DecisionOutcome;
import com.forcitis.one.platform.approval.application.ReassignTask;
import com.forcitis.one.platform.approval.application.ReleaseTask;
import com.forcitis.one.platform.approval.application.TaskDecision;
import com.forcitis.one.platform.approval.application.TransferTask;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** D-1a-90 every handling of a task is one appended line, written in the save that changes the task; the done list reads from them. */
class TaskHandlingRecordsIT extends ApprovalIntegrationSupport {

    static final String RECORDS = "/api/v1/platform/approval-task-records";

    @Autowired
    ClaimTask claimTask;

    @Autowired
    ReleaseTask releaseTask;

    @Autowired
    TransferTask transferTask;

    @Autowired
    ReassignTask reassignTask;

    @Autowired
    DecideTasks decideTasks;

    List<String> actionsOf(String key) {
        return jdbc.queryForList("select action_kind from platform_approval.approval_task_records where document_key = ? order by decided_at, record_id",
                String.class, key);
    }

    @Test
    @DisplayName("claim, release, transfer, reassign and decide each append a line with the actor, the person acted for and the opinion")
    void everyHandlingIsRecorded() {
        long unit = newId();
        long initiator = newId();
        long manager = newId();
        long colleague = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(manager, unit, null).person(colleague, unit, null);
        String key = newKey("records");
        ApprovalInstanceId instance = start(key, initiator, manager);
        ApprovalTaskId task = openTaskId(instance);

        claimTask.execute(task, TestAccounts.account(manager), OptionalLong.empty());
        releaseTask.execute(task, TestAccounts.account(manager), OptionalLong.empty());
        claimTask.execute(task, TestAccounts.account(manager), OptionalLong.empty());
        transferTask.execute(task, TestAccounts.account(manager), TestAccounts.employee(colleague), "on leave", OptionalLong.empty());
        reassignTask.execute(task, TestAccounts.account(ADMINISTRATOR), TestAccounts.employee(manager), "back again", OptionalLong.empty());
        decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, "fine")), OptionalLong.empty());

        assertThat(actionsOf(key)).containsExactly("CLAIMED", "RELEASED", "CLAIMED", "TRANSFERRED", "REASSIGNED", "APPROVED");
        assertThat(jdbc.queryForMap("select actor_id, on_behalf_of_id, assignee_id, record_reason from platform_approval.approval_task_records"
                + " where document_key = ? and action_kind = 'REASSIGNED'", key))
                .containsEntry("actor_id", TestAccounts.account(ADMINISTRATOR).value()).containsEntry("on_behalf_of_id", TestAccounts.account(colleague).value())
                .containsEntry("assignee_id", TestAccounts.account(manager).value())
                .containsEntry("record_reason", "back again");
        assertThat(jdbc.queryForMap("select node_name, round_number, record_reason from platform_approval.approval_task_records"
                + " where document_key = ? and action_kind = 'APPROVED'", key)).containsEntry("round_number", 1).containsEntry("record_reason", "fine");
    }

    @Test
    @DisplayName("the lines are appended and never changed: the table refuses an update and a delete")
    void theTrailIsAppendOnly() {
        long unit = newId();
        long initiator = newId();
        long manager = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(manager, unit, null);
        String key = newKey("append");
        claimTask.execute(openTaskId(start(key, initiator, manager)), TestAccounts.account(manager), OptionalLong.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("update platform_approval.approval_task_records set actor_id = '01900000-0000-7000-8000-000000000001' where document_key = ?", key))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("delete from platform_approval.approval_task_records where document_key = ?", key))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.execute("truncate platform_approval.approval_task_records"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(actionsOf(key)).containsExactly("CLAIMED");
    }

    @Test
    @DisplayName("the history of the task detail is read from the trail: a transfer and the person who made it are in it, and the done list holds every action of the person")
    void theHistoryComesFromTheTrail() throws Exception {
        long unit = newId();
        long initiator = newId();
        long manager = newId();
        long colleague = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(manager, unit, null).person(colleague, unit, null);
        String key = newKey("history");
        ApprovalTaskId task = openTaskId(start(key, initiator, manager));
        claimTask.execute(task, TestAccounts.account(manager), OptionalLong.empty());
        transferTask.execute(task, TestAccounts.account(manager), TestAccounts.employee(colleague), "on leave", OptionalLong.empty());
        decideTasks.execute(TestAccounts.account(colleague), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, "fine")), OptionalLong.empty());

        mvc.perform(get("/api/v1/platform/approval-tasks/" + task.value()).with(user(TestAccounts.account(initiator).value().toString()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(3))
                .andExpect(jsonPath("$.history[0].action").value("CLAIMED"))
                .andExpect(jsonPath("$.history[1].action").value("TRANSFERRED"))
                .andExpect(jsonPath("$.history[1].actorId").value(TestAccounts.account(manager).value().toString()))
                .andExpect(jsonPath("$.history[1].assigneeName").exists())
                .andExpect(jsonPath("$.history[2].action").value("APPROVED"));
        mvc.perform(get(RECORDS).with(user(TestAccounts.account(manager).value().toString()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].action").value("TRANSFERRED"))
                .andExpect(jsonPath("$.items[1].action").value("CLAIMED"));
    }

    @Test
    @DisplayName("the done list is what the signed-in person did, newest first, one page at a time with a cursor; a malformed cursor or limit is 400")
    void theDoneListIsPaged() throws Exception {
        long unit = newId();
        long initiator = newId();
        long manager = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(manager, unit, null);
        for (int i = 0; i < 3; i++) {
            ApprovalTaskId task = openTaskId(start(newKey("done"), initiator, manager));
            claimTask.execute(task, TestAccounts.account(manager), OptionalLong.empty());
            decideTasks.execute(TestAccounts.account(manager), List.of(new TaskDecision(task, DecisionOutcome.APPROVE, "ok " + i)), OptionalLong.empty());
        }

        String first = mvc.perform(get(RECORDS).param("limit", "4").with(user(TestAccounts.account(manager).value().toString()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4)).andExpect(jsonPath("$.items[0].action").value("APPROVED"))
                .andExpect(jsonPath("$.items[0].reason").value("ok 2")).andExpect(jsonPath("$.items[0].actorId").value(TestAccounts.account(manager).value().toString()))
                .andExpect(jsonPath("$.items[0].recordId").exists()).andExpect(jsonPath("$.items[0].nodeName").exists())
                .andExpect(jsonPath("$.items[0].round").value(1)).andExpect(jsonPath("$.nextCursor").exists())
                .andReturn().getResponse().getContentAsString();
        String cursor = com.jayway.jsonpath.JsonPath.read(first, "$.nextCursor");

        mvc.perform(get(RECORDS).param("limit", "4").param("cursor", cursor).with(user(TestAccounts.account(manager).value().toString()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].reason").value("ok 0"))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mvc.perform(get(RECORDS).param("cursor", "not-a-cursor").with(user(TestAccounts.account(manager).value().toString()))).andExpect(status().isBadRequest());
        mvc.perform(get(RECORDS).param("limit", "0").with(user(TestAccounts.account(manager).value().toString()))).andExpect(status().isBadRequest());
        mvc.perform(get(RECORDS).with(user(TestAccounts.account(initiator).value().toString()))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("a reassignment to somebody who already has a task on the same node of the approval is refused; the overview is paged and only for the administrator")
    void reassignmentAndOverview() throws Exception {
        long unit = newId();
        long initiator = newId();
        long first = newId();
        long second = newId();
        organisation.unit(unit, null, null).person(initiator, unit, null).person(first, unit, null).person(second, unit, null);
        String key = newKey("cosign");
        tx.execute(s -> approvals.start(startRequest(key, TestAccounts.employee(initiator),
                java.util.Set.of(com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new com.forcitis.one.platform.approval.api.ApprovalField(
                        com.forcitis.one.cost.timesheet.api.TimesheetApprovalField.PROJECT_MANAGER.fieldName(), List.of(TestAccounts.employee(first).toString(), TestAccounts.employee(second).toString()))))));
        ApprovalTaskId task = new ApprovalTaskId(taskService.createTaskQuery().processInstanceBusinessKey("TIMESHEET:" + key)
                .taskCandidateOrAssigned(TestAccounts.account(first).value().toString()).singleResult().getId());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reassignTask.execute(task, TestAccounts.account(ADMINISTRATOR), TestAccounts.employee(second), "swap", OptionalLong.empty()))
                .isInstanceOf(com.forcitis.one.kernel.DomainException.class);

        String overview = "/api/v1/platform/approval-task-overviews";
        mvc.perform(get(overview).param("limit", "1").with(user(TestAccounts.account(ADMINISTRATOR).value().toString()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.nextCursor").exists());
        mvc.perform(get(overview).with(user(TestAccounts.account(first).value().toString()))).andExpect(status().isForbidden());
    }
}
