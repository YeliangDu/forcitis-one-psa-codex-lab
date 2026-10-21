package com.forcitis.one.platform.approval;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static com.forcitis.one.platform.approval.OrdinarySessions.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.ListInbox;
import com.forcitis.one.platform.approval.application.MergedInbox;
import com.forcitis.one.platform.identity.api.Permission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class MergedInboxIT extends ApprovalIntegrationSupport {

    @org.junit.jupiter.api.BeforeEach
    void candidateGroupNodes() {
        deployVariant("groupnode");
    }

    @Autowired
    ListInbox listInbox;

    @Autowired
    ClaimTask claimTask;

    static RequestPostProcessor reader(EmployeeId id) {
        return user(TestAccounts.account(id).value().toString()).authorities(com.forcitis.one.platform.approval.OrdinarySessions.with(Permission.Codes.PLATFORM_APPROVAL_TASK_READ));
    }

    ApprovalInstanceId newTaskFor(long initiator, String key, EmployeeId... managers) {
        return tx.execute(s -> approvals.start(startRequest(key, TestAccounts.employee(initiator), Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), java.util.Arrays.stream(managers).map(EmployeeId::toString).toList())))));
    }

    @Test
    @DisplayName("C1a-14 inbox counts contain only the approval tasks of the same user")
    void ownTaskCountsAreConsistent() throws Exception {
        long me = newId();
        long colleague = newId();
        long initiator = newId();
        EmployeeId myself = TestAccounts.employee(me);
        organisation.person(myself, 1, null);
        EmployeeId myColleague = TestAccounts.employee(colleague);
        organisation.person(myColleague, 1, null);
        organisation.person(initiator, 1, null);
        newTaskFor(initiator, newKey("inbox"), myself, myColleague);
        newTaskFor(initiator, newKey("inbox"), myself);

        MergedInbox inbox = listInbox.execute(TestAccounts.account(myself), com.forcitis.one.platform.approval.application.ListPage.first());
        assertThat(inbox.newCount()).isEqualTo(2);
        assertThat(inbox.totalCount()).isEqualTo(2);
        assertThat(inbox.items()).hasSize(2);

        mvc.perform(get("/api/v1/platform/approval-tasks").with(reader(myself))).andExpect(status().isOk())
                .andExpect(jsonPath("$.newCount").value(2))
                .andExpect(jsonPath("$.totalCount").value(2));
        MergedInbox colleagueInbox = listInbox.execute(TestAccounts.account(myColleague), com.forcitis.one.platform.approval.application.ListPage.first());
        assertThat(colleagueInbox.newCount()).isEqualTo(1);
        assertThat(colleagueInbox.totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("C8 the approval tasks show without an old-system reference")
    void ownTasksNeedNoOldSystemReference() {
        long me = newId();
        long initiator = newId();
        organisation.person(me, 1, null).person(initiator, 1, null);
        newTaskFor(initiator, newKey("inbox"), TestAccounts.employee(me));

        MergedInbox inbox = listInbox.execute(TestAccounts.account(me), com.forcitis.one.platform.approval.application.ListPage.first());
        assertThat(inbox.newCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("C1a-14 the group's tasks: both candidates see an unclaimed task; after a claim only the claimer keeps it")
    void groupTasksFollowTheClaim() {
        long first = newId();
        long second = newId();
        long initiator = newId();
        organisation.person(first, 1, null).person(second, 1, null).person(initiator, 1, null);
        ApprovalInstanceId instance = newTaskFor(initiator, newKey("inbox"), TestAccounts.employee(first), TestAccounts.employee(second));

        assertThat(listInbox.execute(TestAccounts.account(first), com.forcitis.one.platform.approval.application.ListPage.first()).newCount()).isEqualTo(1);
        assertThat(listInbox.execute(TestAccounts.account(second), com.forcitis.one.platform.approval.application.ListPage.first()).newCount()).isEqualTo(1);
        assertThat(listInbox.execute(TestAccounts.account(first), com.forcitis.one.platform.approval.application.ListPage.first()).items().getFirst().claimed()).isFalse();

        claimTask.execute(openTaskId(instance), TestAccounts.account(first), java.util.OptionalLong.empty());

        assertThat(listInbox.execute(TestAccounts.account(first), com.forcitis.one.platform.approval.application.ListPage.first()).newCount()).isEqualTo(1);
        assertThat(listInbox.execute(TestAccounts.account(first), com.forcitis.one.platform.approval.application.ListPage.first()).items().getFirst().claimed()).isTrue();
        assertThat(listInbox.execute(TestAccounts.account(second), com.forcitis.one.platform.approval.application.ListPage.first()).newCount()).isZero();
        assertThat(listInbox.execute(TestAccounts.account(initiator), com.forcitis.one.platform.approval.application.ListPage.first()).newCount()).isZero();
    }

    @Test
    @DisplayName("the inbox is paged by cursor: the counts are of the whole inbox, nextCursor leads to the rest, a bad cursor is 400")
    void theInboxIsPaged() throws Exception {
        long me = newId();
        long initiator = newId();
        organisation.person(me, 1, null).person(initiator, 1, null);
        for (int i = 0; i < 3; i++) {
            newTaskFor(initiator, newKey("paged"), TestAccounts.employee(me));
        }

        String first = mvc.perform(get("/api/v1/platform/approval-tasks").param("limit", "2").with(reader(TestAccounts.employee(me)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.newCount").value(3))
                .andExpect(jsonPath("$.totalCount").value(3)).andExpect(jsonPath("$.noticeCount").value(0))
                .andExpect(jsonPath("$.nextCursor").exists()).andReturn().getResponse().getContentAsString();
        String cursor = com.jayway.jsonpath.JsonPath.read(first, "$.nextCursor");
        String second = mvc.perform(get("/api/v1/platform/approval-tasks").param("limit", "2").param("cursor", cursor).with(reader(TestAccounts.employee(me))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.nextCursor").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        List<String> firstIds = com.jayway.jsonpath.JsonPath.read(first, "$.items[*].taskId");
        List<String> secondIds = com.jayway.jsonpath.JsonPath.read(second, "$.items[*].taskId");
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
        mvc.perform(get("/api/v1/platform/approval-tasks").param("cursor", "garbage").with(reader(TestAccounts.employee(me)))).andExpect(status().isBadRequest());
    }
}
