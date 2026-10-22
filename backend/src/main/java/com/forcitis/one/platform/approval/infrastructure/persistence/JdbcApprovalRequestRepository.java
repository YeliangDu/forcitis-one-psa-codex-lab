package com.forcitis.one.platform.approval.infrastructure.persistence;

import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.KernelError;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Reason;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalRequestId;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.api.ApprovalSummary;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.api.NodeName;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.TaskEffect;
import com.forcitis.one.platform.approval.api.TaskOutcome;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.domain.ApprovalRound;
import com.forcitis.one.platform.approval.domain.ApprovalTask;
import com.forcitis.one.platform.approval.api.ApprovalTaskStatus;
import com.forcitis.one.platform.approval.domain.DeadlinePlan;
import com.forcitis.one.platform.approval.domain.TaskHandling;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The version and status conditions of the update, not application checks, keep concurrent writes from overwriting each other. */
@Repository
class JdbcApprovalRequestRepository implements ApprovalRequestRepository {

    private static final String REQUEST_COLUMNS = "id, process_key, document_type, document_id, legal_entity_id, initiator_id, current_round,"
            + " current_instance_id, status, result_reason, summary_text, event_count, version";
    private static final String TASK_COLUMNS = "task_id, instance_id, node_key, node_name, status, assignee_id, created_at, due_at, remind_at,"
            + " escalate_at, claimed_at, completed_at, overdue_at, escalated_at, outcome_kind, effect_kind, result_reason, approved_hash";

    private final JdbcClient jdbc;
    private final Clock clock;
    private final IdGenerator ids;

    JdbcApprovalRequestRepository(JdbcClient jdbc, Clock clock, IdGenerator ids) {
        this.ids = ids;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Override
    public void lockForStart(ProcessKey processKey, DocumentReference document) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "approval-start:" + processKey.name() + ":" + document.documentType().value() + ":" + document.documentId())
                .query().singleValue();
    }

    @Override
    public Optional<ApprovalRequest> find(ProcessKey processKey, DocumentReference document) {
        return select("WHERE process_key = :processKey AND document_type = :type AND document_id = :id", "")
                .param("processKey", processKey.name()).param("type", document.documentType().value())
                .param("id", document.documentId().value()).query((rs, n) -> readRow(rs)).optional().map(this::restore);
    }

    @Override
    public Optional<ApprovalRequest> findForUpdate(ProcessKey processKey, DocumentReference document) {
        return select("WHERE process_key = :processKey AND document_type = :type AND document_id = :id", " FOR UPDATE")
                .param("processKey", processKey.name()).param("type", document.documentType().value())
                .param("id", document.documentId().value()).query((rs, n) -> readRow(rs)).optional().map(this::restore);
    }

    @Override
    public Optional<ApprovalRequest> findByInstanceForUpdate(ApprovalInstanceId instanceId) {
        return select("WHERE current_instance_id = :instance", " FOR UPDATE")
                .param("instance", instanceId.value()).query((rs, n) -> readRow(rs)).optional().map(this::restore);
    }

    @Override
    public Optional<ApprovalRequest> findByTaskForUpdate(ApprovalTaskId taskId) {
        return select("WHERE id = (SELECT approval_request_id FROM platform_approval.approval_tasks WHERE task_id = :task)", " FOR UPDATE")
                .param("task", taskId.value()).query((rs, n) -> readRow(rs)).optional().map(this::restore);
    }

    private JdbcClient.StatementSpec select(String where, String lock) {
        return jdbc.sql("SELECT " + REQUEST_COLUMNS + " FROM platform_approval.approval_requests " + where + lock);
    }

    @Override
    public void save(ApprovalRequest request) {
        if (request.isNew()) {
            insert(request);
        } else {
            update(request);
        }
        if (request.startsRound()) {
            jdbc.sql("""
                    INSERT INTO platform_approval.start_rounds (id, approval_request_id, round_number, initiator_id, instance_id,
                           trigger_event_id)
                    VALUES (:startRoundId, :requestId, :round, :initiator, :instance, :trigger)
                    """)
                    .param("startRoundId", ids.next()).param("requestId", request.id().value())
                    .param("round", request.round()).param("initiator", request.initiator().value())
                    .param("instance", request.instanceId().value()).param("trigger", request.trigger().orElseThrow()).update();
        }
        for (ApprovalTask task : request.tasks()) {
            if (task.isNew()) {
                insert(request, task);
            } else if (task.isChanged()) {
                update(task);
            }
        }
        List<TaskHandling> handlings = request.drainHandlings();
        if (!handlings.isEmpty()) {
            int number = lastRecordNumber(request);
            for (TaskHandling handling : handlings) {
                insert(request, handling, ++number);
            }
        }
    }

    private int lastRecordNumber(ApprovalRequest request) {
        return jdbc.sql("SELECT COALESCE(MAX(record_number), 0) FROM platform_approval.approval_task_records WHERE approval_request_id = :requestId")
                .param("requestId", request.id().value()).query(Integer.class).single();
    }

    private void insert(ApprovalRequest request, TaskHandling handling, int number) {
        try {
            insertRecord(request, handling, number);
        } catch (DuplicateKeyException numbered) {
            throw concurrentChange(request);
        }
    }

    private void insertRecord(ApprovalRequest request, TaskHandling handling, int number) {
        jdbc.sql("""
                INSERT INTO platform_approval.approval_task_records (record_id, approval_request_id, record_number, round_number, task_id,
                       instance_id, node_key, node_name, action_kind, actor_id, on_behalf_of_id, assignee_id, record_reason, decided_at)
                VALUES (:id, :requestId, :number, :round, :task, :instance, :node, :nodeName, :action, :actor, :onBehalfOf, :assignee,
                        :reason, :decidedAt)
                """)
                .param("id", handling.recordId().value()).param("requestId", request.id().value()).param("number", number)
                .param("round", handling.round())
                .param("task", handling.taskId().value()).param("instance", handling.instanceId().value())
                .param("node", handling.nodeKey().value()).param("nodeName", handling.nodeName().value())
                .param("action", handling.action().name()).param("actor", handling.actor().value())
                .param("onBehalfOf", handling.onBehalfOf().map(UserId::value).orElse(null))
                .param("assignee", handling.assignee().map(UserId::value).orElse(null))
                .param("reason", handling.reason().map(Reason::value).orElse(null)).param("decidedAt", at(handling.decidedAt())).update();
    }

    private void insert(ApprovalRequest request) {
        try {
            jdbc.sql("""
                    INSERT INTO platform_approval.approval_requests (id, process_key, document_type, document_id, legal_entity_id, initiator_id,
                           current_round, current_instance_id, status, result_reason, summary_text, event_count, version)
                    VALUES (:requestId, :processKey, :type, :id, :legalEntity, :initiator, :round, :instance, :status, :reason, :summary, :sequence, :next)
                    """)
                    .param("requestId", request.id().value()).param("processKey", request.processKey().name()).param("type", request.document().documentType().value())
                    .param("id", request.document().documentId().value()).param("legalEntity", request.legalEntityId().value())
                    .param("initiator", request.initiator().value()).param("round", request.round())
                    .param("instance", request.instanceId().value()).param("status", request.status().name())
                    .param("reason", request.resultReason().map(Reason::value).orElse(null))
                    .param("summary", request.summary().value()).param("sequence", request.eventSequenceAfterSave())
                    .param("next", request.versionAfterSave()).update();
        } catch (DuplicateKeyException started) {
            throw concurrentChange(request);
        }
    }

    private void update(ApprovalRequest request) {
        int updated = jdbc.sql("""
                UPDATE platform_approval.approval_requests
                   SET current_round = :round, current_instance_id = :instance, status = :status, result_reason = :reason,
                       summary_text = :summary, event_count = :sequence, updated_at = :updatedAt, version = :next
                 WHERE id = :requestId AND version = :version AND status = :previous
                """)
                .param("round", request.round()).param("instance", request.instanceId().value())
                .param("status", request.status().name()).param("reason", request.resultReason().map(Reason::value).orElse(null))
                .param("summary", request.summary().value()).param("sequence", request.eventSequenceAfterSave())
                .param("next", request.versionAfterSave()).param("requestId", request.id().value())
                .param("version", request.version()).param("previous", request.loadedStatus().name()).param("updatedAt", Timestamp.from(clock.instant()))
                .update();
        if (updated != 1) {
            throw concurrentChange(request);
        }
    }

    private void insert(ApprovalRequest request, ApprovalTask task) {
        jdbc.sql("""
                INSERT INTO platform_approval.approval_tasks (task_id, instance_id, approval_request_id, node_key, node_name, status, assignee_id,
                       created_at, due_at, remind_at, escalate_at, claimed_at, completed_at, overdue_at, escalated_at, outcome_kind,
                       effect_kind, result_reason, approved_hash)
                VALUES (:id, :instance, :requestId, :node, :nodeName, :status, :assignee, :created, :due, :remind, :escalate, :claimed,
                        :completed, :overdue, :escalated, :outcome, :effect, :reason, :hash)
                """)
                .param("id", task.taskId().value()).param("instance", task.instanceId().value())
                .param("requestId", request.id().value())
                .param("node", task.nodeKey().value()).param("nodeName", task.nodeName().value()).param("status", task.status().name())
                .param("assignee", task.assignee().map(UserId::value).orElse(null)).param("created", at(task.createdAt()))
                .param("due", task.deadline().map(plan -> at(plan.dueAt())).orElse(null))
                .param("remind", task.deadline().map(plan -> at(plan.remindAt())).orElse(null))
                .param("escalate", task.deadline().map(plan -> at(plan.escalateAt())).orElse(null))
                .param("claimed", task.claimedAt().map(JdbcApprovalRequestRepository::at).orElse(null))
                .param("completed", task.completedAt().map(JdbcApprovalRequestRepository::at).orElse(null))
                .param("overdue", task.overdueAt().map(JdbcApprovalRequestRepository::at).orElse(null))
                .param("escalated", task.escalatedAt().map(JdbcApprovalRequestRepository::at).orElse(null))
                .param("outcome", task.outcome().map(TaskOutcome::name).orElse(null))
                .param("effect", task.effect().map(TaskEffect::name).orElse(null))
                .param("reason", task.resultReason().map(Reason::value).orElse(null))
                .param("hash", task.approvedHash().map(ContentHash::value).orElse(null)).update();
    }

    private void update(ApprovalTask task) {
        int updated = jdbc.sql("""
                UPDATE platform_approval.approval_tasks
                   SET status = :status, assignee_id = :assignee, claimed_at = :claimed, completed_at = :completed, overdue_at = :overdue,
                       escalated_at = :escalated, outcome_kind = :outcome, effect_kind = :effect, result_reason = :reason,
                       approved_hash = :hash, updated_at = :updatedAt
                 WHERE task_id = :id
                """)
                .param("status", task.status().name()).param("assignee", task.assignee().map(UserId::value).orElse(null))
                .param("claimed", task.claimedAt().map(JdbcApprovalRequestRepository::at).orElse(null))
                .param("completed", task.completedAt().map(JdbcApprovalRequestRepository::at).orElse(null))
                .param("overdue", task.overdueAt().map(JdbcApprovalRequestRepository::at).orElse(null))
                .param("escalated", task.escalatedAt().map(JdbcApprovalRequestRepository::at).orElse(null))
                .param("outcome", task.outcome().map(TaskOutcome::name).orElse(null))
                .param("effect", task.effect().map(TaskEffect::name).orElse(null))
                .param("reason", task.resultReason().map(Reason::value).orElse(null))
                .param("hash", task.approvedHash().map(ContentHash::value).orElse(null)).param("id", task.taskId().value()).param("updatedAt", Timestamp.from(clock.instant()))
                .update();
        if (updated != 1) {
            throw KernelError.KERNEL_CONCURRENT_CHANGE.refusal("Task " + task.taskId().value() + " vanished while it was saved");
        }
    }

    @Override
    public List<ApprovalRound> rounds(ProcessKey processKey, DocumentReference document) {
        return jdbc.sql("""
                SELECT s.round_number, s.initiator_id, s.instance_id FROM platform_approval.start_rounds s
                  JOIN platform_approval.approval_requests q ON q.id = s.approval_request_id
                 WHERE q.process_key = :processKey AND q.document_id = :id ORDER BY s.round_number
                """)
                .param("processKey", processKey.name()).param("id", document.documentId().value())
                .query((rs, n) -> new ApprovalRound(rs.getInt("round_number"), new EmployeeId(rs.getObject("initiator_id", UUID.class)),
                        new ApprovalInstanceId(rs.getObject("instance_id", UUID.class)))).list();
    }

    private record Row(ApprovalRequestId id, ProcessKey processKey, DocumentReference document, LegalEntityId legalEntityId, EmployeeId initiator, int round,
                       ApprovalInstanceId instance, ApprovalStatus status, Reason reason, ApprovalSummary summary, long version,
                       long eventCount) {
    }

    private static Row readRow(ResultSet rs) throws SQLException {
        String reason = rs.getString("result_reason");
        return new Row(new ApprovalRequestId(rs.getObject("id", UUID.class)), ProcessKey.valueOf(rs.getString("process_key")),
                new DocumentReference(DocumentTypeCode.of(rs.getString("document_type")), new DocumentId(rs.getObject("document_id", UUID.class))),
                new LegalEntityId(rs.getObject("legal_entity_id", UUID.class)), new EmployeeId(rs.getObject("initiator_id", UUID.class)),
                rs.getInt("current_round"), new ApprovalInstanceId(rs.getObject("current_instance_id", UUID.class)),
                ApprovalStatus.valueOf(rs.getString("status")), reason == null ? null : new Reason(reason),
                new ApprovalSummary(rs.getString("summary_text")), rs.getLong("version"), rs.getLong("event_count"));
    }

    private ApprovalRequest restore(Row row) {
        List<ApprovalTask> tasks = jdbc.sql("SELECT " + TASK_COLUMNS + """
                  FROM platform_approval.approval_tasks
                 WHERE approval_request_id = :requestId AND (instance_id = :instance OR status IN ('PENDING', 'CLAIMED'))
                 ORDER BY created_at, task_id
                """)
                .param("requestId", row.id().value()).param("instance", row.instance().value()).query((task, n) -> readTask(task)).list();
        return ApprovalRequest.restore(row.id(), ids, row.processKey(), row.document(), row.legalEntityId(), row.initiator(), row.round(), row.instance(),
                row.status(), row.reason(), row.summary(), tasks, row.version(), row.eventCount());
    }

    private static ApprovalTask readTask(ResultSet rs) throws SQLException {
        UUID assignee = rs.getObject("assignee_id", UUID.class);
        UserId assigneeId = assignee == null ? null : new UserId(assignee);
        Instant due = instant(rs, "due_at");
        DeadlinePlan plan = due == null ? null : new DeadlinePlan(due, instant(rs, "remind_at"), instant(rs, "escalate_at"));
        String outcome = rs.getString("outcome_kind");
        String effect = rs.getString("effect_kind");
        String reason = rs.getString("result_reason");
        String hash = rs.getString("approved_hash");
        return ApprovalTask.restore(new ApprovalTaskId(rs.getObject("task_id", UUID.class)),
                new ApprovalInstanceId(rs.getObject("instance_id", UUID.class)), new NodeKey(rs.getString("node_key")), new NodeName(rs.getString("node_name")), ApprovalTaskStatus.valueOf(rs.getString("status")), assigneeId,
                instant(rs, "created_at"), plan, instant(rs, "claimed_at"), instant(rs, "completed_at"), instant(rs, "overdue_at"),
                instant(rs, "escalated_at"), outcome == null ? null : TaskOutcome.valueOf(outcome),
                effect == null ? null : TaskEffect.valueOf(effect), reason == null ? null : new Reason(reason),
                hash == null ? null : new ContentHash(hash));
    }

    private static com.forcitis.one.kernel.DomainException concurrentChange(ApprovalRequest request) {
        return KernelError.KERNEL_CONCURRENT_CHANGE.refusal("Approval request " + request.id().value()
                + " was changed by somebody else");
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
