package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.ApprovalP4Support;
import com.forcitis.one.acceptance.P4Names;
import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.platform.approval.api.ApprovalProgress;
import com.forcitis.one.platform.approval.api.ApprovalStartRequest;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.RetractionResult;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.jayway.jsonpath.JsonPath;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * DATA-REPAIR-approval AP-01 to AP-05 (data design of PR-6, one-data ruling 2026-10-16): the approval tables carry the rules that the
 * code already keeps. A rejection, return, transfer, reassignment or withdrawal without a reason is refused over the public interface
 * and a row that lacks one is refused by the database; a task's handling fields come in pairs; the three instance ids point at a
 * start round; a handling record's task belongs to the record's request; the start rounds are append-only. The advisory (co-signer) and
 * hook-node rejections are not reachable through the public interface of the production process, so their rule is pinned on the row
 * (a completed task that is REJECTED or RETURNED needs a reason whatever its effect). Rows are written with plain SQL, the only way
 * to bring the database to a state the code would never produce.
 */
class ApprovalRowRulesIT extends ApprovalP4Support {

    static final TestDatabase postgres = TestPostgres.database(ApprovalRowRulesIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String SYNTHETIC_REASON = "synthetic: reason";

    private UUID administrator;
    private SignedIn administratorAccount;

    @BeforeEach
    void aProcessAdministrator() throws Exception {
        administrator = newEmployeeId();
        employee(administrator, unit, null);
        grantByOperations(administrator, "process_administrator");
        administratorAccount = signIn(administrator);
    }

    private interface ThrowingSql {
        void run() throws SQLException;
    }

    /** The database refusal of the statement; fails loudly when the statement went through. */
    private static PSQLException refusedBy(ThrowingSql statement) {
        try {
            statement.run();
        } catch (PSQLException refused) {
            return refused;
        } catch (SQLException other) {
            throw new AssertionError(other);
        }
        throw new AssertionError("the statement was not refused by the database");
    }

    private static String constraintOf(PSQLException refused) {
        return refused.getServerErrorMessage() == null ? null : refused.getServerErrorMessage().getConstraint();
    }

    private void assertRefused(ThrowingSql statement, String sqlState, String constraint) {
        PSQLException refused = refusedBy(statement);
        assertThat(refused.getSQLState()).as(refused.getMessage()).isEqualTo(sqlState);
        if (constraint != null) {
            assertThat(constraintOf(refused)).as(refused.getMessage()).isEqualTo(constraint);
        }
    }

    private MockHttpServletResponse write(UUID task, SignedIn caller, String version, String operation, String body) throws Exception {
        return mvc.perform(post(TASKS + task + ":" + operation).header("If-Match", version).with(csrf()).with(session(caller))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    private String requestStatus(UUID request) throws SQLException {
        return (String) rows("select status from approval_requests where id = ?", request).getFirst().get("status");
    }

    private Map<String, Object> requestRow(UUID request) throws SQLException {
        return rows("select status, result_reason, current_round, current_instance_id from approval_requests where id = ?", request).getFirst();
    }

    private long recordCount(UUID request) throws SQLException {
        return (Long) rows("select count(*) as n from approval_task_records where approval_request_id = ?", request).getFirst().get("n");
    }

    private void assertReasonRequired(MockHttpServletResponse refused) throws Exception {
        assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(422);
        assertThat((String) JsonPath.read(refused.getContentAsString(), "$.errorCode")).isEqualTo("KERNEL_REASON_REQUIRED");
    }

    private ApprovalProgress progressOf(DocumentReference document) {
        return approvals.progress(TimesheetApprovalProcess.KEY, document).orElseThrow();
    }

    private void startRound(DocumentReference document, int round) {
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "acceptance.timesheet-submission")) {
            approvals.start(new ApprovalStartRequest(TimesheetApprovalProcess.KEY, document, legalEntity, employeeIdOf(initiator), new RoundNumber(round),
                    UUID.randomUUID(), Set.of(P4Names.PROJECT_HOURS_FLAG), List.of(projectManagers(projectManager)), new ContentHash("synthetic-content-" + round),
                    new ApprovalSummary("Synthetic timesheet round " + round)));
        }
    }

    /** A started approval: the request and its one open task; claimed() also has the CLAIMED handling record to copy. */
    private record Started(DocumentReference document, UUID request, UUID task) {
    }

    private Started claimed() throws Exception {
        DocumentReference document = startProjectHours(projectManager);
        UUID task = openTaskIds(document).getFirst();
        actOk(task, projectManagerAccount, "claim", null);
        return new Started(document, requestIdOf(document), task);
    }

    private Started pending() throws Exception {
        DocumentReference document = startProjectHours(projectManager);
        return new Started(document, requestIdOf(document), openTaskIds(document).getFirst());
    }

    // 参数顺序：动作、记录标识、请求标识、原因、任务；转交与改派按 ck_approval_task_records_assignee 带处理人，其余动作不带，使 record_reason 约束可单独触发
    private static final String COPY_CLAIMED_RECORD = "with p(action) as (select cast(? as varchar)) insert into approval_task_records (record_id, approval_request_id,"
            + " record_number, round_number, task_id, instance_id, node_key, node_name, action_kind, actor_id, decided_at, record_reason, assignee_id)"
            + " select ?, ?, 90, round_number, task_id, instance_id, node_key, node_name, p.action, actor_id, decided_at, ?,"
            + " case when p.action in ('TRANSFERRED', 'REASSIGNED') then actor_id end"
            + " from approval_task_records cross join p where task_id = ? and action_kind = 'CLAIMED'";

    // ---- AP-01 ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DATA-REPAIR-approval AP-01：驳回无原因（缺失、空串、空白）被拒 422 KERNEL_REASON_REQUIRED，任务与请求不变；有原因通过")
    void rejectionWithoutAReasonIsRefused() throws Exception {
        Started c = pending();
        String version = versionOf(c.task(), projectManagerAccount);

        for (String body : List.of("{\"outcome\":\"REJECT\"}", "{\"outcome\":\"REJECT\",\"reason\":null}", "{\"outcome\":\"REJECT\",\"reason\":\"\"}",
                "{\"outcome\":\"REJECT\",\"reason\":\"   \"}")) {
            assertReasonRequired(write(c.task(), projectManagerAccount, version, "decide", body));
        }
        assertThat(requestStatus(c.request())).isEqualTo("RUNNING");
        assertThat(openTaskIds(c.document())).containsExactly(c.task());

        MockHttpServletResponse accepted = write(c.task(), projectManagerAccount, version, "decide", "{\"outcome\":\"REJECT\",\"reason\":\"synthetic: hours do not add up\"}");
        assertThat(accepted.getStatus()).as(accepted.getContentAsString()).isEqualTo(200);
        Map<String, Object> ended = requestRow(c.request());
        assertThat(ended.get("status")).isIn("REJECTED", "RETURNED");
        assertThat(ended.get("result_reason")).isEqualTo("synthetic: hours do not add up");
        assertThat(rows("select action_kind, record_reason from approval_task_records where task_id = ? and action_kind in ('REJECTED', 'RETURNED')", c.task()))
                .singleElement().satisfies(row -> assertThat(row.get("record_reason")).isEqualTo("synthetic: hours do not add up"));
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-01：退回发起人无原因被拒 422；有原因通过，本轮结束并写原因")
    void returnWithoutAReasonIsRefused() throws Exception {
        Started c = pending();
        String version = versionOf(c.task(), projectManagerAccount);

        for (String body : List.of("{}", "{\"reason\":\"\"}", "{\"reason\":\" \"}")) {
            assertReasonRequired(write(c.task(), projectManagerAccount, version, "return", body));
        }
        assertThat(requestStatus(c.request())).isEqualTo("RUNNING");

        MockHttpServletResponse accepted = write(c.task(), projectManagerAccount, version, "return", "{\"reason\":\"synthetic: attachment missing\"}");
        assertThat(accepted.getStatus()).as(accepted.getContentAsString()).isEqualTo(200);
        assertThat(requestRow(c.request())).containsEntry("status", "RETURNED").containsEntry("result_reason", "synthetic: attachment missing");
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-01：转交无原因被拒 422，办理记录不增加；有原因通过")
    void transferWithoutAReasonIsRefused() throws Exception {
        Started c = claimed();
        String version = versionOf(c.task(), projectManagerAccount);
        long before = recordCount(c.request());

        for (String reason : List.of("null", "\"\"", "\" \"")) {
            MockHttpServletResponse refused = write(c.task(), projectManagerAccount, version, "transfer",
                    "{\"assigneeId\":\"" + text(orgUnitHead) + "\",\"reason\":" + reason + "}");
            assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(422);
        }
        assertThat(recordCount(c.request())).isEqualTo(before);

        MockHttpServletResponse accepted = write(c.task(), projectManagerAccount, version, "transfer",
                "{\"assigneeId\":\"" + text(orgUnitHead) + "\",\"reason\":\"synthetic: on leave\"}");
        assertThat(accepted.getStatus()).as(accepted.getContentAsString()).isEqualTo(204);
        assertThat(rows("select record_reason from approval_task_records where task_id = ? and action_kind = 'TRANSFERRED'", c.task()))
                .singleElement().satisfies(row -> assertThat(row.get("record_reason")).isEqualTo("synthetic: on leave"));
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-01：改派无原因被拒 422，办理记录不增加；有原因通过")
    void reassignmentWithoutAReasonIsRefused() throws Exception {
        Started c = claimed();
        String version = versionOf(c.task(), projectManagerAccount);
        long before = recordCount(c.request());

        for (String reason : List.of("null", "\"\"", "\" \"")) {
            MockHttpServletResponse refused = write(c.task(), administratorAccount, version, "reassign",
                    "{\"assigneeId\":\"" + text(orgUnitHead) + "\",\"reason\":" + reason + "}");
            assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(422);
        }
        assertThat(recordCount(c.request())).isEqualTo(before);

        MockHttpServletResponse accepted = write(c.task(), administratorAccount, version, "reassign",
                "{\"assigneeId\":\"" + text(orgUnitHead) + "\",\"reason\":\"synthetic: handover\"}");
        assertThat(accepted.getStatus()).as(accepted.getContentAsString()).isEqualTo(204);
        assertThat(rows("select record_reason from approval_task_records where task_id = ? and action_kind = 'REASSIGNED'", c.task()))
                .singleElement().satisfies(row -> assertThat(row.get("record_reason")).isEqualTo("synthetic: handover"));
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-01：撤回、撤销无原因被拒，请求仍运行中；有原因时本轮结束并写原因，原单随后可再发起（RETURNED、CANCELLED 各一）")
    void withdrawAndCancelNeedAReasonAndTheDocumentMayStartAgain() throws Exception {
        for (boolean cancel : new boolean[] {false, true}) {
            DocumentReference document = startProjectHours(projectManager);
            UUID request = requestIdOf(document);
            actInSession(initiatorAccount);
            UserId actor = new UserId(initiatorAccount.userId());

            for (String reason : new String[] {null, "", "  "}) {
                RetractionResult refused = cancel ? approvals.cancel(TimesheetApprovalProcess.KEY, document, actor, reason)
                        : approvals.withdraw(TimesheetApprovalProcess.KEY, document, actor, reason);
                assertThat(refused).as("a missing reason is an answer, not an exception").isEqualTo(RetractionResult.REASON_REQUIRED);
            }
            assertThat(requestStatus(request)).isEqualTo("RUNNING");

            RetractionResult done = cancel ? approvals.cancel(TimesheetApprovalProcess.KEY, document, actor, "synthetic: wrong hours")
                    : approvals.withdraw(TimesheetApprovalProcess.KEY, document, actor, "synthetic: wrong hours");
            assertThat(done).isEqualTo(RetractionResult.DONE);
            assertThat(requestRow(request)).containsEntry("status", cancel ? "CANCELLED" : "RETURNED").containsEntry("result_reason", "synthetic: wrong hours");

            startRound(document, 2);
            assertThat(requestRow(request)).containsEntry("status", "RUNNING").containsEntry("result_reason", null).containsEntry("current_round", 2);
            assertThat(progressOf(document).status()).isEqualTo(ApprovalStatus.RUNNING);
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-01：驳回后原单可再发起（状态回到运行中、原因清空、轮次加一）")
    void aRejectedDocumentMayStartAgain() throws Exception {
        Started c = pending();
        MockHttpServletResponse rejected = write(c.task(), projectManagerAccount, versionOf(c.task(), projectManagerAccount), "decide",
                "{\"outcome\":\"REJECT\",\"reason\":\"synthetic: no\"}");
        assertThat(rejected.getStatus()).as(rejected.getContentAsString()).isEqualTo(200);
        assertThat(requestRow(c.request()).get("result_reason")).isEqualTo("synthetic: no");

        startRound(c.document(), 2);

        assertThat(requestRow(c.request())).containsEntry("status", "RUNNING").containsEntry("result_reason", null).containsEntry("current_round", 2);
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-01：库层——审批请求为 REJECTED、RETURNED、CANCELLED 而无原因被拒（ck_approval_requests_result_reason）")
    void aFinishedRequestWithoutAReasonIsRefusedByTheDatabase() throws Exception {
        for (String status : List.of("REJECTED", "RETURNED", "CANCELLED")) {
            UUID request = pending().request();
            assertRefused(() -> execute("update approval_requests set status = ?, result_reason = null where id = ?", status, request), "23514",
                    "ck_approval_requests_result_reason");
            assertThat(requestStatus(request)).isEqualTo("RUNNING");
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-01：库层——运行中或已通过的请求带原因被拒；空串原因被拒；结束且带原因通过")
    void aRunningRequestWithAReasonAndAnEmptyReasonAreRefusedByTheDatabase() throws Exception {
        UUID request = pending().request();
        assertRefused(() -> execute("update approval_requests set result_reason = 'synthetic: stray' where id = ?", request), "23514", "ck_approval_requests_result_reason");
        assertRefused(() -> execute("update approval_requests set status = 'APPROVED', result_reason = 'synthetic: stray' where id = ?", request), "23514",
                "ck_approval_requests_result_reason");
        assertRefused(() -> execute("update approval_requests set status = 'CANCELLED', result_reason = '' where id = ?", request), "23514", "ck_approval_requests_result_reason");

        execute("update approval_requests set status = 'CANCELLED', result_reason = 'synthetic: ended' where id = ?", request);
        assertThat(requestStatus(request)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-01：库层——办理记录 REJECTED、RETURNED、TRANSFERRED、REASSIGNED 无原因或空串原因被拒（ck_approval_task_records_record_reason）；通过可不填，空串仍被拒")
    void aHandlingRecordNeedsItsReasonInTheDatabase() throws Exception {
        Started c = claimed();
        for (String action : List.of("REJECTED", "RETURNED", "TRANSFERRED", "REASSIGNED")) {
            assertRefused(() -> execute(COPY_CLAIMED_RECORD, action, UUID.randomUUID(), c.request(), null, c.task()), "23514", "ck_approval_task_records_record_reason");
            assertRefused(() -> execute(COPY_CLAIMED_RECORD, action, UUID.randomUUID(), c.request(), "", c.task()), "23514", "ck_approval_task_records_record_reason");
        }
        assertRefused(() -> execute(COPY_CLAIMED_RECORD, "RELEASED", UUID.randomUUID(), c.request(), "", c.task()), "23514", "ck_approval_task_records_record_reason");

        execute(COPY_CLAIMED_RECORD, "APPROVED", UUID.randomUUID(), c.request(), null, c.task());
        assertThat(rows("select 1 from approval_task_records where task_id = ? and action_kind = 'APPROVED'", c.task())).hasSize(1);
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-01：库层——办结为 REJECTED 或 RETURNED 的任务无原因被拒，加签人（ADVISORY）与钩子节点的驳回同样（ck_approval_tasks_result_reason）；空串被拒；通过可不填")
    void aRejectedOrReturnedTaskNeedsItsReasonInTheDatabase() throws Exception {
        UUID task = pending().task();
        for (String outcome : List.of("REJECTED", "RETURNED")) {
            for (String effect : List.of("BINDING", "ADVISORY")) {
                assertRefused(() -> execute("update approval_tasks set status = 'COMPLETED', completed_at = ?, outcome_kind = ?, effect_kind = ?, result_reason = null"
                        + " where task_id = ?", Timestamp.from(NOW), outcome, effect, task), "23514", "ck_approval_tasks_result_reason");
            }
        }
        assertRefused(() -> execute("update approval_tasks set status = 'COMPLETED', completed_at = ?, outcome_kind = 'REJECTED', effect_kind = 'ADVISORY',"
                + " result_reason = '' where task_id = ?", Timestamp.from(NOW), task), "23514", "ck_approval_tasks_result_reason");
        assertRefused(() -> execute("update approval_tasks set status = 'COMPLETED', completed_at = ?, outcome_kind = 'APPROVED', effect_kind = 'BINDING',"
                + " result_reason = '' where task_id = ?", Timestamp.from(NOW), task), "23514", "ck_approval_tasks_result_reason");

        execute("update approval_tasks set status = 'COMPLETED', completed_at = ?, outcome_kind = 'REJECTED', effect_kind = 'ADVISORY', result_reason = ?"
                + " where task_id = ?", Timestamp.from(NOW), SYNTHETIC_REASON, task);
        UUID other = pending().task();
        execute("update approval_tasks set status = 'COMPLETED', completed_at = ?, outcome_kind = 'APPROVED', effect_kind = 'BINDING' where task_id = ?",
                Timestamp.from(NOW), other);
    }

    // ---- AP-02 ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DATA-REPAIR-approval AP-02：办理链认领、释放、再认领、通过照常——释放后处理人与认领时刻为空，办结后结果、效力、办结时刻成对")
    void theHandlingChainStillWorks() throws Exception {
        DocumentReference document = startProjectHours(projectManager);
        UUID task = openTaskIds(document).getFirst();

        actOk(task, projectManagerAccount, "claim", null);
        actOk(task, projectManagerAccount, "release", null);
        assertThat(rows("select status, assignee_id, claimed_at from approval_tasks where task_id = ?", task).getFirst())
                .containsEntry("status", "PENDING").containsEntry("assignee_id", null).containsEntry("claimed_at", null);
        actOk(task, projectManagerAccount, "claim", null);
        actOk(task, projectManagerAccount, "decide", "{\"outcome\":\"APPROVE\"}");

        Map<String, Object> done = rows("select status, outcome_kind, effect_kind, completed_at, result_reason from approval_tasks where task_id = ?", task).getFirst();
        assertThat(done).containsEntry("status", "COMPLETED").containsEntry("outcome_kind", "APPROVED").containsEntry("effect_kind", "BINDING")
                .containsEntry("result_reason", null);
        assertThat(done.get("completed_at")).isNotNull();
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-02：待认领的任务有处理人或认领时刻被拒（ck_approval_tasks_pending_unassigned）")
    void aPendingTaskHasNoHandler() throws Exception {
        UUID task = pending().task();
        assertRefused(() -> execute("update approval_tasks set assignee_id = ? where task_id = ?", initiatorAccount.userId(), task), "23514",
                "ck_approval_tasks_pending_unassigned");
        assertRefused(() -> execute("update approval_tasks set claimed_at = ? where task_id = ?", Timestamp.from(NOW), task), "23514",
                "ck_approval_tasks_pending_unassigned");
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-02：办理结果与办理效力不成对被拒（只有一个）")
    void outcomeAndEffectComeInPairs() throws Exception {
        UUID task = pending().task();
        assertRefused(() -> execute("update approval_tasks set status = 'COMPLETED', completed_at = ?, outcome_kind = 'APPROVED' where task_id = ?",
                Timestamp.from(NOW), task), "23514", null);
        assertRefused(() -> execute("update approval_tasks set status = 'COMPLETED', completed_at = ?, effect_kind = 'BINDING' where task_id = ?",
                Timestamp.from(NOW), task), "23514", null);
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-02：系统自动通过的任务其结果只能是通过（ck_approval_tasks_automatic）")
    void anAutomaticPassIsAnApproval() throws Exception {
        UUID task = pending().task();
        assertRefused(() -> execute("update approval_tasks set status = 'COMPLETED', completed_at = ?, outcome_kind = 'REJECTED', effect_kind = 'AUTOMATIC',"
                + " result_reason = 'synthetic: x' where task_id = ?", Timestamp.from(NOW), task), "23514", "ck_approval_tasks_automatic");
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-02：通过时内容摘要只随正式通过出现（ck_approval_tasks_approved_hash）")
    void anApprovedHashBelongsToABindingApproval() throws Exception {
        UUID task = pending().task();
        assertRefused(() -> execute("update approval_tasks set status = 'COMPLETED', completed_at = ?, outcome_kind = 'REJECTED', effect_kind = 'BINDING',"
                + " result_reason = 'synthetic: x', approved_hash = 'synthetic-hash' where task_id = ?", Timestamp.from(NOW), task), "23514", "ck_approval_tasks_approved_hash");
        assertRefused(() -> execute("update approval_tasks set status = 'COMPLETED', completed_at = ?, outcome_kind = 'APPROVED', effect_kind = 'ADVISORY',"
                + " approved_hash = 'synthetic-hash' where task_id = ?", Timestamp.from(NOW), task), "23514", "ck_approval_tasks_approved_hash");
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-02：超时时刻须有截止时刻、升级时刻须有超时时刻（ck_approval_tasks_overdue、ck_approval_tasks_escalated）")
    void overdueNeedsADeadlineAndEscalationNeedsOverdue() throws Exception {
        UUID task = pending().task();
        execute("update approval_tasks set due_at = null, remind_at = null, escalate_at = null where task_id = ?", task);
        assertRefused(() -> execute("update approval_tasks set overdue_at = ? where task_id = ?", Timestamp.from(NOW), task), "23514", "ck_approval_tasks_overdue");
        assertRefused(() -> execute("update approval_tasks set escalated_at = ? where task_id = ?", Timestamp.from(NOW), task), "23514", "ck_approval_tasks_escalated");
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-02：未办结（待认领、已认领）的任务带办结字段被拒（ck_approval_tasks_not_completed）")
    void anOpenTaskCarriesNoCompletionFields() throws Exception {
        UUID task = pending().task();
        assertRefused(() -> execute("update approval_tasks set completed_at = ? where task_id = ?", Timestamp.from(NOW), task), "23514", "ck_approval_tasks_not_completed");
        assertRefused(() -> execute("update approval_tasks set result_reason = 'synthetic: early' where task_id = ?", task), "23514", "ck_approval_tasks_not_completed");
        assertRefused(() -> execute("update approval_tasks set outcome_kind = 'APPROVED', effect_kind = 'BINDING' where task_id = ?", task), "23514",
                "ck_approval_tasks_not_completed");
    }

    // ---- AP-03 ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DATA-REPAIR-approval AP-03：任务的实例 ID 指向不存在的发起轮次被拒（fk_approval_tasks_instance_id）")
    void aTaskNeedsARealInstance() throws Exception {
        Started c = pending();
        assertRefused(() -> execute("insert into approval_tasks (task_id, instance_id, approval_request_id, node_key, status, node_name) values (?, ?, ?, 'synthetic', 'PENDING', 'synthetic')",
                UUID.randomUUID(), UUID.randomUUID(), c.request()), "23503", "fk_approval_tasks_instance_id");
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-03：办理记录的实例 ID 指向不存在的发起轮次被拒（fk_approval_task_records_instance_id）")
    void aRecordNeedsARealInstance() throws Exception {
        Started c = claimed();
        assertRefused(() -> execute("insert into approval_task_records (record_id, approval_request_id, record_number, round_number, task_id, instance_id, node_key,"
                + " node_name, action_kind, actor_id, decided_at) select ?, approval_request_id, 90, round_number, task_id, ?, node_key, node_name, action_kind,"
                + " actor_id, decided_at from approval_task_records where task_id = ? and action_kind = 'CLAIMED'", UUID.randomUUID(), UUID.randomUUID(), c.task()),
                "23503", "fk_approval_task_records_instance_id");
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-03：审批请求的当前实例 ID 指向不存在的发起轮次被拒（fk_approval_requests_current_instance_id，提交时检查）")
    void aRequestNeedsARealCurrentInstance() throws Exception {
        UUID request = pending().request();
        Object before = requestRow(request).get("current_instance_id");

        assertRefused(() -> execute("update approval_requests set current_instance_id = ? where id = ?", UUID.randomUUID(), request), "23503",
                "fk_approval_requests_current_instance_id");
        assertThat(requestRow(request).get("current_instance_id")).isEqualTo(before);
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-03：当前轮次小于 1 被拒（ck_approval_requests_current_round）")
    void theCurrentRoundIsAtLeastOne() throws Exception {
        UUID request = pending().request();
        assertRefused(() -> execute("update approval_requests set current_round = 0 where id = ?", request), "23514", "ck_approval_requests_current_round");
    }

    // ---- AP-04 ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DATA-REPAIR-approval AP-04：办理记录的任务与审批请求不同属被拒——任务属请求甲、记录挂请求乙（fk_approval_task_records_task_id）")
    void aRecordsTaskBelongsToTheRecordsRequest() throws Exception {
        Started a = claimed();
        UUID otherRequest = pending().request();

        assertRefused(() -> execute("insert into approval_task_records (record_id, approval_request_id, record_number, round_number, task_id, instance_id, node_key,"
                + " node_name, action_kind, actor_id, decided_at) select ?, ?, 90, round_number, task_id, instance_id, node_key, node_name, action_kind, actor_id,"
                + " decided_at from approval_task_records where task_id = ? and action_kind = 'CLAIMED'", UUID.randomUUID(), otherRequest, a.task()),
                "23503", "fk_approval_task_records_task_id");
        assertThat(recordCount(otherRequest)).isZero();

        execute("insert into approval_task_records (record_id, approval_request_id, record_number, round_number, task_id, instance_id, node_key,"
                + " node_name, action_kind, actor_id, decided_at) select ?, approval_request_id, 90, round_number, task_id, instance_id, node_key, node_name,"
                + " 'RELEASED', actor_id, decided_at from approval_task_records where task_id = ? and action_kind = 'CLAIMED'", UUID.randomUUID(), a.task());
        assertThat(recordCount(a.request())).isEqualTo(2);
    }

    // ---- AP-05 ------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DATA-REPAIR-approval AP-05：发起轮次只追加——UPDATE、DELETE 被拒（23001），行不变；新的发起照常写入")
    void startRoundsCannotBeChanged() throws Exception {
        Started c = pending();
        Map<String, Object> round = rows("select id, initiator_id, instance_id from start_rounds where approval_request_id = ?", c.request()).getFirst();

        assertRefused(() -> execute("update start_rounds set initiator_id = ? where id = ?", UUID.randomUUID(), round.get("id")), "23001", null);
        assertRefused(() -> execute("update start_rounds set updated_at = now() where id = ?", round.get("id")), "23001", null);
        assertRefused(() -> execute("delete from start_rounds where id = ?", round.get("id")), "23001", null);

        Map<String, Object> after = rows("select id, initiator_id, instance_id from start_rounds where approval_request_id = ?", c.request()).getFirst();
        assertThat(after).isEqualTo(round);
        UUID another = requestIdOf(startProjectHours(projectManager));
        assertThat(rows("select 1 from start_rounds where approval_request_id = ?", another)).hasSize(1);
    }

    @Test
    @DisplayName("DATA-REPAIR-approval AP-05：发起轮次只追加——TRUNCATE 被拒（0A000，外键引用先拒绝），行仍在；目录里有 BEFORE TRUNCATE 触发器")
    void startRoundsCannotBeTruncated() throws Exception {
        UUID request = pending().request();

        PSQLException refused = refusedBy(() -> execute("truncate table start_rounds"));
        assertThat(refused.getSQLState()).as(refused.getMessage()).isEqualTo("0A000");

        assertThat(rows("select 1 from start_rounds where approval_request_id = ?", request)).hasSize(1);
        assertThat(rows("""
                select t.tgname, p.proname
                from pg_trigger t
                join pg_proc p on p.oid = t.tgfoid
                where t.tgrelid = 'platform_approval.start_rounds'::regclass
                  and not t.tgisinternal
                  and (t.tgtype & 1) = 0 and (t.tgtype & 2) = 2 and (t.tgtype & 32) = 32
                """)).as("a BEFORE TRUNCATE statement trigger on start_rounds")
                .singleElement()
                .satisfies(trigger -> {
                    assertThat(trigger.get("tgname")).isEqualTo("start_rounds_reject_truncate");
                    assertThat(trigger.get("proname")).isEqualTo("reject_start_round_change");
                });
    }
}
