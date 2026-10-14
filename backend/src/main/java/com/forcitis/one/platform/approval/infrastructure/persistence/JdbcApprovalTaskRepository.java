package com.forcitis.one.platform.approval.infrastructure.persistence;

import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.ApprovalTask;
import com.forcitis.one.platform.approval.domain.ApprovalTaskRepository;
import com.forcitis.one.platform.approval.domain.ApprovalTaskStatus;
import com.forcitis.one.platform.approval.domain.StaleAggregateException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcApprovalTaskRepository implements ApprovalTaskRepository {

    private static final String COLUMNS = "task_id, instance_id, process_key, document_type, document_key, legal_entity_id, node_key,"
            + " status, assignee_id, due_at, escalate_at, overdue_at, escalated_at, approved_hash, event_count, version";

    private final JdbcClient jdbc;

    JdbcApprovalTaskRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ApprovalTask> find(ApprovalTaskId taskId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM platform_approval.approval_tasks WHERE task_id = :id FOR UPDATE")
                .param("id", taskId.value()).query((rs, n) -> read(rs)).optional();
    }

    @Override
    public Optional<ApprovalInstanceId> instanceOf(ApprovalTaskId taskId) {
        return jdbc.sql("SELECT instance_id FROM platform_approval.approval_tasks WHERE task_id = :id")
                .param("id", taskId.value()).query((rs, n) -> new ApprovalInstanceId(rs.getString("instance_id"))).optional();
    }

    @Override
    public boolean hasApprovedSameContent(ApprovalInstanceId instanceId, String nodeKey, UserId user, String contentDigest) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM platform_approval.approval_tasks
                                WHERE instance_id = :instance AND node_key = :node AND assignee_id = :user
                                  AND status = 'COMPLETED' AND approved_hash = :digest)
                """)
                .param("instance", instanceId.value()).param("node", nodeKey).param("user", user.value())
                .param("digest", contentDigest).query(Boolean.class).single();
    }

    @Override
    public List<ApprovalTask> findOpenByInstance(ApprovalInstanceId instanceId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                  FROM platform_approval.approval_tasks
                 WHERE instance_id = :instance AND status IN ('PENDING', 'CLAIMED')
                 ORDER BY task_id
                   FOR UPDATE
                """)
                .param("instance", instanceId.value()).query((rs, n) -> read(rs)).list();
    }

    @Override
    public List<ApprovalTaskId> findDue(Instant now) {
        return jdbc.sql("""
                SELECT task_id FROM platform_approval.approval_tasks
                 WHERE status IN ('PENDING', 'CLAIMED')
                   AND ((overdue_at IS NULL AND due_at <= :now) OR (overdue_at IS NOT NULL AND escalated_at IS NULL AND escalate_at <= :now))
                 ORDER BY due_at
                """)
                .param("now", at(now)).query((rs, n) -> new ApprovalTaskId(rs.getString("task_id"))).list();
    }

    @Override
    public void save(ApprovalTask task) {
        if (task.isNew()) {
            jdbc.sql("""
                    INSERT INTO platform_approval.approval_tasks (task_id, instance_id, process_key, document_type, document_key,
                           legal_entity_id, node_key, status, assignee_id, due_at, escalate_at, overdue_at, escalated_at, approved_hash, event_count)
                    VALUES (:id, :instance, :processKey, :type, :documentId, :legalEntity, :node, :status, :assignee, :due, :escalate,
                            :overdue, :escalated, :digest, :sequence)
                    """)
                    .param("id", task.taskId().value()).param("instance", task.instanceId().value())
                    .param("processKey", task.processKey().name()).param("type", task.document().documentType().name())
                    .param("documentId", task.document().documentId().value()).param("legalEntity", task.legalEntityId().value())
                    .param("node", task.nodeKey()).param("status", task.status().name())
                    .param("assignee", task.assignee().map(UserId::value).orElse(null))
                    .param("due", task.dueAt().map(JdbcApprovalTaskRepository::at).orElse(null))
                    .param("escalate", task.escalateAt().map(JdbcApprovalTaskRepository::at).orElse(null))
                    .param("overdue", task.overdueAt().map(JdbcApprovalTaskRepository::at).orElse(null))
                    .param("escalated", task.escalatedAt().map(JdbcApprovalTaskRepository::at).orElse(null))
                    .param("digest", task.approvedDigest().orElse(null)).param("sequence", task.eventSequence()).update();
            return;
        }
        int updated = jdbc.sql("""
                UPDATE platform_approval.approval_tasks
                   SET status = :status, assignee_id = :assignee, overdue_at = :overdue, escalated_at = :escalated, approved_hash = :digest,
                       event_count = :sequence, updated_at = now(), version = version + 1
                 WHERE task_id = :id AND version = :version AND status = :previous
                """)
                .param("status", task.status().name()).param("assignee", task.assignee().map(UserId::value).orElse(null))
                .param("overdue", task.overdueAt().map(JdbcApprovalTaskRepository::at).orElse(null))
                .param("escalated", task.escalatedAt().map(JdbcApprovalTaskRepository::at).orElse(null))
                .param("digest", task.approvedDigest().orElse(null)).param("sequence", task.eventSequence()).param("id", task.taskId().value())
                .param("version", task.loadedVersion()).param("previous", task.loadedStatus().name()).update();
        if (updated != 1) {
            throw new StaleAggregateException("Approval task " + task.taskId().value() + " was changed by somebody else");
        }
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static ApprovalTask read(ResultSet rs) throws SQLException {
        long assignee = rs.getLong("assignee_id");
        UserId assigneeId = rs.wasNull() ? null : new UserId(assignee);
        return ApprovalTask.restore(new ApprovalTaskId(rs.getString("task_id")), new ApprovalInstanceId(rs.getString("instance_id")),
                ProcessKey.valueOf(rs.getString("process_key")),
                new DocumentReference(DocumentType.valueOf(rs.getString("document_type")), new DocumentId(rs.getString("document_key"))),
                new LegalEntityId(rs.getLong("legal_entity_id")), rs.getString("node_key"),
                ApprovalTaskStatus.valueOf(rs.getString("status")), assigneeId,
                instant(rs, "due_at"), instant(rs, "escalate_at"), instant(rs, "overdue_at"), instant(rs, "escalated_at"),
                rs.getString("approved_hash"), rs.getLong("event_count"), rs.getLong("version"));
    }
}
