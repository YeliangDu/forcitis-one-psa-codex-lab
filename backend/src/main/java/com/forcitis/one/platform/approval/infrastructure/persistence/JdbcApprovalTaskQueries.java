package com.forcitis.one.platform.approval.infrastructure.persistence;

import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.NodeName;
import com.forcitis.one.platform.approval.api.TaskRecordAction;
import com.forcitis.one.platform.approval.api.TaskRecordId;
import com.forcitis.one.platform.approval.domain.TaskHandling;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.TaskEffect;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.domain.ApprovalTaskQueries;
import com.forcitis.one.platform.approval.api.ApprovalTaskStatus;
import com.forcitis.one.platform.approval.domain.TaskRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcApprovalTaskQueries implements ApprovalTaskQueries {

    private static final String RECORD = """
            SELECT t.task_id, t.instance_id, r.process_key, r.document_id, r.document_type, t.node_key, r.initiator_id, t.status,
                   t.assignee_id, t.created_at, t.due_at, t.completed_at, t.outcome_kind, t.effect_kind, t.result_reason, r.summary_text, r.version,
                   COALESCE((SELECT s.round_number FROM platform_approval.start_rounds s WHERE s.instance_id = t.instance_id), 0) AS round_number
              FROM platform_approval.approval_tasks t
              JOIN platform_approval.approval_requests r ON r.id = t.approval_request_id
            """;

    private static final String HANDLING = """
            SELECT r.record_id, q.process_key, q.document_type, q.document_id, r.round_number, r.task_id, r.instance_id, r.node_key,
                   r.node_name, r.action_kind, r.actor_id, r.on_behalf_of_id, r.assignee_id, r.record_reason, r.decided_at
              FROM platform_approval.approval_task_records r
              JOIN platform_approval.approval_requests q ON q.id = r.approval_request_id
            """;

    private final JdbcClient jdbc;

    JdbcApprovalTaskQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TaskRecord> find(ApprovalTaskId taskId) {
        return jdbc.sql(RECORD + " WHERE t.task_id = :id").param("id", taskId.value()).query((rs, n) -> read(rs)).optional();
    }

    @Override
    public Optional<ApprovalInstanceId> instanceOf(ApprovalTaskId taskId) {
        return jdbc.sql("SELECT instance_id FROM platform_approval.approval_tasks WHERE task_id = :id").param("id", taskId.value())
                .query((rs, n) -> new ApprovalInstanceId(rs.getObject("instance_id", UUID.class))).optional();
    }

    @Override
    public List<TaskHandling> handledBy(UserId user, Optional<Instant> after, Optional<UUID> afterId, int limit) {
        return jdbc.sql(HANDLING + """
                 WHERE r.actor_id = :user
                   AND r.action_kind <> 'PASSED_AUTOMATICALLY'
                   AND (CAST(:after AS timestamptz) IS NULL OR (r.decided_at, r.record_id) < (CAST(:after AS timestamptz), CAST(:afterId AS uuid)))
                 ORDER BY r.decided_at DESC, r.record_id DESC
                 LIMIT :limit
                """)
                .param("user", user.value()).param("after", after.map(JdbcApprovalTaskQueries::at).orElse(null))
                .param("afterId", afterId.orElse(null)).param("limit", limit).query((rs, n) -> readHandling(rs)).list();
    }

    private static TaskHandling readHandling(ResultSet rs) throws SQLException {
        Optional<UserId> onBehalf = Optional.ofNullable(rs.getObject("on_behalf_of_id", UUID.class)).map(UserId::new);
        Optional<UserId> assigneeId = Optional.ofNullable(rs.getObject("assignee_id", UUID.class)).map(UserId::new);
        return new TaskHandling(new TaskRecordId(rs.getObject("record_id", UUID.class)), ProcessKey.of(rs.getString("process_key")),
                new DocumentReference(DocumentTypeCode.of(rs.getString("document_type")), new DocumentId(rs.getObject("document_id", UUID.class))),
                rs.getInt("round_number"), new ApprovalTaskId(rs.getObject("task_id", UUID.class)),
                new ApprovalInstanceId(rs.getObject("instance_id", UUID.class)),
                new NodeKey(rs.getString("node_key")), new NodeName(rs.getString("node_name")),
                TaskRecordAction.valueOf(rs.getString("action_kind")), new UserId(rs.getObject("actor_id", UUID.class)), onBehalf, assigneeId,
                Optional.ofNullable(rs.getString("record_reason")).map(Reason::new), JdbcApprovalRequestRepository.instant(rs, "decided_at"));
    }

    @Override
    public List<TaskRecord> openTasks(Optional<Instant> overdueAt, Optional<Instant> after, Optional<ApprovalTaskId> afterId, int limit) {
        return jdbc.sql(RECORD + """
                 WHERE t.status IN ('PENDING', 'CLAIMED') AND (CAST(:overdueAt AS timestamptz) IS NULL OR t.due_at < CAST(:overdueAt AS timestamptz))
                   AND (CAST(:after AS timestamptz) IS NULL OR (t.created_at, t.task_id) > (CAST(:after AS timestamptz), CAST(:afterId AS uuid)))
                 ORDER BY t.created_at, t.task_id
                 LIMIT :limit
                """)
                .param("overdueAt", overdueAt.map(JdbcApprovalTaskQueries::at).orElse(null))
                .param("after", after.map(JdbcApprovalTaskQueries::at).orElse(null)).param("afterId", afterId.map(ApprovalTaskId::value).orElse(null))
                .param("limit", limit).query((rs, n) -> read(rs)).list();
    }

    @Override
    public Map<ApprovalTaskId, Instant> dueAtOf(Collection<ApprovalTaskId> taskIds) {
        Map<ApprovalTaskId, Instant> due = new HashMap<>();
        if (taskIds.isEmpty()) {
            return due;
        }
        jdbc.sql("SELECT task_id, due_at FROM platform_approval.approval_tasks WHERE due_at IS NOT NULL AND task_id IN (:ids)")
                .param("ids", taskIds.stream().map(ApprovalTaskId::value).toList())
                .query((rs, n) -> due.put(new ApprovalTaskId(rs.getObject("task_id", UUID.class)), JdbcApprovalRequestRepository.instant(rs, "due_at")))
                .list();
        return due;
    }

    @Override
    public List<TaskHandling> historyOf(ProcessKey processKey, DocumentId documentId) {
        return jdbc.sql(HANDLING + """
                 WHERE q.process_key = :processKey AND q.document_id = :documentId
                 ORDER BY r.record_number
                """)
                .param("processKey", processKey.value()).param("documentId", documentId.value()).query((rs, n) -> readHandling(rs)).list();
    }

    @Override
    public List<ApprovalTaskId> findDue(Instant now) {
        return jdbc.sql("""
                SELECT due.task_id FROM (
                    SELECT task_id, remind_at FROM platform_approval.approval_tasks
                     WHERE status IN ('PENDING', 'CLAIMED') AND overdue_at IS NULL AND remind_at <= :now
                    UNION ALL
                    SELECT task_id, remind_at FROM platform_approval.approval_tasks
                     WHERE status IN ('PENDING', 'CLAIMED') AND overdue_at IS NOT NULL AND escalated_at IS NULL AND escalate_at <= :now
                ) due
                 ORDER BY due.remind_at
                """)
                .param("now", at(now)).query((rs, n) -> new ApprovalTaskId(rs.getObject("task_id", UUID.class))).list();
    }

    private static TaskRecord read(ResultSet rs) throws SQLException {
        Optional<UserId> assigneeId = Optional.ofNullable(rs.getObject("assignee_id", UUID.class)).map(UserId::new);
        String outcome = rs.getString("outcome_kind");
        String effect = rs.getString("effect_kind");
        String reason = rs.getString("result_reason");
        return new TaskRecord(new ApprovalTaskId(rs.getObject("task_id", UUID.class)), new ApprovalInstanceId(rs.getObject("instance_id", UUID.class)),
                ProcessKey.of(rs.getString("process_key")), new DocumentReference(DocumentTypeCode.of(rs.getString("document_type")),
                new DocumentId(rs.getObject("document_id", UUID.class))), new NodeKey(rs.getString("node_key")), new EmployeeId(rs.getObject("initiator_id", UUID.class)),
                ApprovalTaskStatus.valueOf(rs.getString("status")), assigneeId, JdbcApprovalRequestRepository.instant(rs, "created_at"),
                Optional.ofNullable(JdbcApprovalRequestRepository.instant(rs, "due_at")),
                Optional.ofNullable(JdbcApprovalRequestRepository.instant(rs, "completed_at")),
                Optional.ofNullable(outcome).map(TaskOutcome::valueOf), Optional.ofNullable(effect).map(TaskEffect::valueOf),
                Optional.ofNullable(reason).map(Reason::new), new ApprovalSummary(rs.getString("summary_text")), rs.getLong("version"),
                rs.getInt("round_number"));
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
