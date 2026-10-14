package com.forcitis.one.platform.approval;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.application.ClaimTask;
import com.forcitis.one.platform.approval.application.ListInbox;
import com.forcitis.one.platform.approval.application.MergedInbox;
import com.forcitis.one.platform.identity.api.Permission;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    static RequestPostProcessor reader(long id) {
        return user(String.valueOf(id)).authorities(() -> Permission.Codes.PLATFORM_APPROVAL_TASK_READ);
    }

    void legacyRow(long user, String task, String instance, String node, String when, int result) {
        jdbc.update("insert into legacy_fixture.todo_rows (assignee_user_id, task_id, process_instance_id, task_def_key, task_name,"
                + " process_title, created_at, result) values (?, ?, ?, ?, ?, ?, ?, ?)", user, task, instance, node, "Old node " + node,
                "Old title " + instance, LocalDateTime.ofInstant(Instant.parse(when), ZoneOffset.UTC), result);
    }

    ApprovalInstanceId newTaskFor(long initiator, String key, long... managers) {
        return tx.execute(s -> approvals.start(startRequest(key, new UserId(initiator), Set.of(TimesheetRoutingFlag.PROJECT_HOURS.flagName()),
                List.of(new ApprovalField(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), java.util.Arrays.stream(managers).mapToObj(String::valueOf).toList())))));
    }

    @Test
    @DisplayName("C1a-14 old and new counts together: 2 new tasks + 3 old to-dos (5 row tasks of 2 nodes merged) = 5 for the same user")
    void oldAndNewCountsAreConsistent() throws Exception {
        long me = newId();
        long colleague = newId();
        long initiator = newId();
        organisation.person(me, 1, null).person(colleague, 1, null).person(initiator, 1, null);
        newTaskFor(initiator, newKey("inbox"), me, colleague);
        newTaskFor(initiator, newKey("inbox"), me);
        legacyRow(me, "r1", "P1-" + me, "N1", "2026-10-01T01:00:00Z", 1);
        legacyRow(me, "r2", "P1-" + me, "N1", "2026-10-01T02:00:00Z", 1);
        legacyRow(me, "r3", "P1-" + me, "N1", "2026-10-01T03:00:00Z", 1);
        legacyRow(me, "r4", "P1-" + me, "N2", "2026-10-02T01:00:00Z", 1);
        legacyRow(me, "r5", "P2-" + me, "N1", "2026-10-03T01:00:00Z", 1);
        legacyRow(me, "done", "P3-" + me, "N1", "2026-10-03T01:00:00Z", 2);
        legacyRow(colleague, "c1", "P9-" + colleague, "N1", "2026-10-03T01:00:00Z", 1);

        MergedInbox inbox = listInbox.execute(new UserId(me));
        assertThat(inbox.newCount()).isEqualTo(2);
        assertThat(inbox.legacyCount()).isEqualTo(3);
        assertThat(inbox.totalCount()).isEqualTo(5);
        assertThat(inbox.legacyAvailable()).isTrue();
        assertThat(inbox.items()).hasSize(5);
        assertThat(inbox.items().stream().filter(i -> i.source().name().equals("LEGACY")).map(i -> i.taskId()).toList())
                .containsExactlyInAnyOrder("r3", "r4", "r5");

        mvc.perform(get("/api/v1/platform/approval-tasks").with(reader(me))).andExpect(status().isOk())
                .andExpect(jsonPath("$.newCount").value(2)).andExpect(jsonPath("$.legacyCount").value(3))
                .andExpect(jsonPath("$.totalCount").value(5)).andExpect(jsonPath("$.legacyAvailable").value(true));
        MergedInbox colleagueInbox = listInbox.execute(new UserId(colleague));
        assertThat(colleagueInbox.newCount()).isEqualTo(1);
        assertThat(colleagueInbox.legacyCount()).isEqualTo(1);
        assertThat(colleagueInbox.totalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("RULE-261/C1a-14 every old to-do carries the old system's detail link; new tasks carry none")
    void legacyItemsLinkBackToTheOldSystem() {
        long me = newId();
        organisation.person(me, 1, null);
        legacyRow(me, "t-1", "P-" + me, "N1", "2026-10-01T01:00:00Z", 1);
        MergedInbox inbox = listInbox.execute(new UserId(me));
        assertThat(inbox.items()).singleElement().satisfies(item -> {
            assertThat(item.source().name()).isEqualTo("LEGACY");
            assertThat(item.detailUrl()).contains("https://legacy.example.test/bpm/detail?instance=P-" + me + "&task=t-1");
            assertThat(item.title()).isEqualTo("Old title P-" + me);
            assertThat(item.nodeName()).contains("Old node N1");
            assertThat(item.createdAt()).contains(Instant.parse("2026-10-01T01:00:00Z"));
        });
    }

    @Test
    @DisplayName("REIMAGINED_ARCHITECTURE 3.6 no cache: rows added or finished in the old system show up on the very next call")
    void theOldViewIsReadLiveEveryTime() {
        long me = newId();
        organisation.person(me, 1, null);
        assertThat(listInbox.execute(new UserId(me)).legacyCount()).isZero();
        legacyRow(me, "live-1", "P-" + me, "N1", "2026-10-01T01:00:00Z", 1);
        assertThat(listInbox.execute(new UserId(me)).legacyCount()).isEqualTo(1);
        legacyRow(me, "live-2", "Q-" + me, "N1", "2026-10-02T01:00:00Z", 1);
        assertThat(listInbox.execute(new UserId(me)).legacyCount()).isEqualTo(2);
        jdbc.update("update legacy_fixture.todo_rows set result = 2 where task_id = 'live-1'");
        assertThat(listInbox.execute(new UserId(me)).legacyCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("C1a-14 the group's tasks: both candidates see an unclaimed task; after a claim only the claimer keeps it")
    void groupTasksFollowTheClaim() {
        long first = newId();
        long second = newId();
        long initiator = newId();
        organisation.person(first, 1, null).person(second, 1, null).person(initiator, 1, null);
        ApprovalInstanceId instance = newTaskFor(initiator, newKey("inbox"), first, second);

        assertThat(listInbox.execute(new UserId(first)).newCount()).isEqualTo(1);
        assertThat(listInbox.execute(new UserId(second)).newCount()).isEqualTo(1);
        assertThat(listInbox.execute(new UserId(first)).items().getFirst().claimed()).isFalse();

        claimTask.execute(openTaskId(instance), new UserId(first));

        assertThat(listInbox.execute(new UserId(first)).newCount()).isEqualTo(1);
        assertThat(listInbox.execute(new UserId(first)).items().getFirst().claimed()).isTrue();
        assertThat(listInbox.execute(new UserId(second)).newCount()).isZero();
        assertThat(listInbox.execute(new UserId(initiator)).newCount()).isZero();
    }
}
