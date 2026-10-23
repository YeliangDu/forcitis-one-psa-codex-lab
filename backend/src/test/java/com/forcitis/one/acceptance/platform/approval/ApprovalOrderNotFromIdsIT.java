package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.ApprovalP4Support;
import com.forcitis.one.acceptance.P4Names;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.kernel.DocumentReference;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R-1b P4 ADR-0033 section 1 items 9 and 10 (CS-2.21), acceptance items 4, 5 and 6: nothing about the approval depends on how its ids
 * sort. The id generator is replaced by one whose ids fall, so a handling written later has the smaller id, and the clock stands
 * still, so several handlings share one moment: the history still reads in the order things were done (by the handling number
 * of the request), the tasks of a request are restored by creation time, and the request gets its id when it is made, not in the
 * repository.
 */
@Testcontainers
class ApprovalOrderNotFromIdsIT extends ApprovalP4Support {

    @TestConfiguration
    static class FallingIds {
        @Bean
        @Primary
        DescendingIds descendingIds() {
            return new DescendingIds();
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    @Autowired
    DescendingIds ids;

    @Autowired
    ApprovalRequestRepository requests;

    @Autowired
    TransactionTemplate transaction;

    private void claimReleaseClaimAndApprove(UUID task) throws Exception {
        actOk(task, projectManagerAccount, "claim", null);
        actOk(task, projectManagerAccount, "release", null);
        actOk(task, projectManagerAccount, "claim", null);
        actOk(task, projectManagerAccount, "decide", "{\"outcome\":\"APPROVE\",\"reason\":\"synthetic\"}");
    }

    @Test
    @DisplayName("R-1b P4 办理记录序号：同一审批请求的办理记录序号从 1 起连续，历史按序号排列——递减 ID、固定 Clock（同一时刻四条）下历史顺序仍等于办理顺序")
    void historyFollowsTheRecordNumberNotTheRecordId() throws Exception {
        var document = startProjectHours(projectManager);
        UUID task = openTaskIds(document).getFirst();

        claimReleaseClaimAndApprove(task);

        List<Map<String, Object>> records = rows("select record_id, " + P4Names.RECORD_NUMBER + ", decided_at, action_kind from approval_task_records where "
                + P4Names.APPROVAL_REQUEST_ID + " = ? order by " + P4Names.RECORD_NUMBER, requestIdOf(document));
        assertThat(records).extracting(row -> row.get(P4Names.RECORD_NUMBER)).as("record numbers").containsExactly(1, 2, 3, 4);
        assertThat(records).extracting(row -> row.get("action_kind")).containsExactly("CLAIMED", "RELEASED", "CLAIMED", "APPROVED");
        assertThat(records.stream().map(row -> ((Timestamp) row.get("decided_at")).toInstant()).distinct().count())
                .as("all four were done at the one moment of the fixed clock").isEqualTo(1);
        List<UUID> recordIds = records.stream().map(row -> (UUID) row.get("record_id")).toList();
        assertThat(recordIds).as("the premise: the record ids fall while the handlings rise").isSortedAccordingTo((a, b) -> b.toString().compareTo(a.toString()));

        assertThat(historyActions(task, projectManagerAccount)).as("the history as the task detail shows it")
                .containsExactly("CLAIMED", "RELEASED", "CLAIMED", "APPROVED");
        assertThat(historyRecordIds(task, projectManagerAccount)).as("the same records in the order of their numbers").containsExactlyElementsOf(recordIds);
    }

    @Test
    @DisplayName("R-1b P4 办理记录序号：序号按审批请求分别从 1 起；同一请求重复序号被唯一约束 uk_approval_task_records_record_number 拒绝")
    void numbersRestartPerRequestAndNeverRepeat() throws Exception {
        var first = startProjectHours(projectManager);
        var second = startProjectHours(projectManager);
        actOk(openTaskIds(first).getFirst(), projectManagerAccount, "claim", null);
        actOk(openTaskIds(second).getFirst(), projectManagerAccount, "claim", null);

        for (var document : List.of(first, second)) {
            assertThat(rows("select " + P4Names.RECORD_NUMBER + " as n from approval_task_records where " + P4Names.APPROVAL_REQUEST_ID + " = ?",
                    requestIdOf(document))).extracting(row -> row.get("n")).containsExactly(1);
        }

        String insertDuplicate = "insert into approval_task_records (record_id, " + P4Names.APPROVAL_REQUEST_ID + ", " + P4Names.RECORD_NUMBER
                + ", round_number, task_id, instance_id, node_key, node_name, action_kind, actor_id, decided_at) select ?, " + P4Names.APPROVAL_REQUEST_ID
                + ", " + P4Names.RECORD_NUMBER + ", round_number, task_id, instance_id, node_key, node_name, action_kind, actor_id, decided_at"
                + " from approval_task_records where " + P4Names.APPROVAL_REQUEST_ID + " = ?";
        PSQLException refused = org.junit.jupiter.api.Assertions.assertThrows(PSQLException.class,
                () -> execute(insertDuplicate, UUID.randomUUID(), requestIdOf(first)));
        assertThat(refused.getSQLState()).isEqualTo("23505");
        assertThat(refused.getServerErrorMessage().getConstraint()).isEqualTo(P4Names.UK_RECORD_NUMBER);
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 第 1 项第 10 条：审批请求恢复时任务按创建时刻排列，不按任务 ID（递减 ID；先创建的任务 ID 更大，其创建时刻更早）")
    void tasksAreRestoredByCreationTime() throws Exception {
        var document = startProjectHours(projectManager, secondProjectManager);
        List<UUID> tasks = new ArrayList<>(openTaskIds(document));
        assertThat(tasks).hasSize(2);
        UUID larger = tasks.stream().max((a, b) -> a.toString().compareTo(b.toString())).orElseThrow();
        UUID smaller = tasks.stream().min((a, b) -> a.toString().compareTo(b.toString())).orElseThrow();
        Instant earlier = NOW.minusSeconds(600);
        execute("update approval_tasks set created_at = ? where task_id = ?", Timestamp.from(earlier), larger);
        execute("update approval_tasks set created_at = ? where task_id = ?", Timestamp.from(NOW), smaller);

        ApprovalRequest restored = restore(document);

        assertThat(restored.tasks()).extracting(task -> String.valueOf(task.taskId().value()))
                .as("sorting by task id would give %s first", smaller).containsExactly(larger.toString(), smaller.toString());
        assertThat(restored.tasks().getFirst().createdAt()).isEqualTo(earlier);
    }

    private ApprovalRequest restore(DocumentReference document) {
        return transaction.execute(status -> requests.find(TimesheetApprovalProcess.KEY, document).orElseThrow());
    }

    @Test
    @DisplayName("R-1b P4 ADR-0033 第 1 项第 9 条：审批请求的 ID 在构造时取得，仓储不生成 ID——请求 ID 由注入的 IdGenerator 在领域或应用层产出，三张子表的 approval_request_id 等于它")
    void theRequestIdIsMadeBeforeTheRepositorySeesIt() throws Exception {
        var document = startProjectHours(projectManager);
        UUID request = requestIdOf(document);
        UUID task = openTaskIds(document).getFirst();
        actOk(task, projectManagerAccount, "claim", null);

        DescendingIds.Made made = ids.madeAs(request);

        assertThat(made).as("the request id came from the injected generator").isNotNull();
        assertThat(made.callers()).as("who asked the generator for the request id").noneMatch(caller -> caller.contains(".infrastructure.persistence."))
                .anyMatch(caller -> caller.startsWith("com.forcitis.one.platform.approval.domain.") || caller.startsWith("com.forcitis.one.platform.approval.application."));
        for (String table : List.of("start_rounds", "approval_tasks", "approval_task_records")) {
            assertThat(rows("select distinct " + P4Names.APPROVAL_REQUEST_ID + " as id from " + table + " where " + P4Names.APPROVAL_REQUEST_ID + " = ?", request))
                    .as("rows of %s that hang on the request", table).hasSize(1);
            assertThat(rows("select count(*) as n from " + table + " t where t." + P4Names.APPROVAL_REQUEST_ID + " <> ? and t." + P4Names.APPROVAL_REQUEST_ID
                    + " in (select id from approval_requests where " + P4Names.DOCUMENT_ID + " = ?)", request, uuidOf(document)).getFirst().get("n")).isEqualTo(0L);
        }
    }
}
