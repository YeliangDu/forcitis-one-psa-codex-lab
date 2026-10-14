package com.forcitis.one.platform.approval.infrastructure.persistence;

import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.ApprovalRequest;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.domain.ApprovalRound;
import com.forcitis.one.platform.approval.api.ApprovalStatus;
import com.forcitis.one.platform.approval.domain.StaleAggregateException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Unique constraints and the version / status condition, not application checks, keep starts idempotent and writes from overwriting each other. */
@Repository
class JdbcApprovalRequestRepository implements ApprovalRequestRepository {

    private static final String COLUMNS = "process_key, document_type, document_key, legal_entity_id, initiator_id, current_round,"
            + " current_instance_id, status, result_reason, event_count, version";

    private final JdbcClient jdbc;

    JdbcApprovalRequestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockForStart(ProcessKey processKey, DocumentReference document) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "approval-start:" + processKey.name() + ":" + document.documentType().name() + ":" + document.documentId().value())
                .query().singleValue();
    }

    @Override
    public Optional<ApprovalRequest> find(ProcessKey processKey, DocumentReference document) {
        return jdbc.sql("SELECT " + COLUMNS + """
                  FROM platform_approval.approval_requests
                 WHERE process_key = :processKey AND document_type = :type AND document_key = :id
                   FOR UPDATE
                """)
                .param("processKey", processKey.name()).param("type", document.documentType().name())
                .param("id", document.documentId().value()).query((rs, n) -> read(rs)).optional();
    }

    @Override
    public Optional<ApprovalRequest> findByInstance(ApprovalInstanceId instanceId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                  FROM platform_approval.approval_requests
                 WHERE current_instance_id = :instance
                   FOR UPDATE
                """)
                .param("instance", instanceId.value()).query((rs, n) -> read(rs)).optional();
    }

    @Override
    public void save(ApprovalRequest request) {
        if (request.isNew()) {
            insert(request);
        } else {
            update(request);
        }
        if (request.isNew() || request.round() != request.loadedRound()) {
            jdbc.sql("""
                    INSERT INTO platform_approval.start_rounds (process_key, document_key, round_number, initiator_id, instance_id)
                    VALUES (:processKey, :id, :round, :initiator, :instance)
                    """)
                    .param("processKey", request.processKey().name()).param("id", request.document().documentId().value())
                    .param("round", request.round()).param("initiator", request.initiator().value())
                    .param("instance", request.instanceId().value()).update();
        }
    }

    private void insert(ApprovalRequest request) {
        jdbc.sql("""
                INSERT INTO platform_approval.approval_requests (process_key, document_type, document_key, legal_entity_id, initiator_id,
                       current_round, current_instance_id, status, result_reason, event_count)
                VALUES (:processKey, :type, :id, :legalEntity, :initiator, :round, :instance, :status, :reason, :sequence)
                """)
                .param("processKey", request.processKey().name()).param("type", request.document().documentType().name())
                .param("id", request.document().documentId().value()).param("legalEntity", request.legalEntityId().value())
                .param("initiator", request.initiator().value()).param("round", request.round())
                .param("instance", request.instanceId().value()).param("status", request.status().name())
                .param("reason", request.resultReason()).param("sequence", request.eventSequence()).update();
    }

    private void update(ApprovalRequest request) {
        int updated = jdbc.sql("""
                UPDATE platform_approval.approval_requests
                   SET current_round = :round, current_instance_id = :instance, status = :status, result_reason = :reason,
                       event_count = :sequence, updated_at = now(), version = version + 1
                 WHERE process_key = :processKey AND document_type = :type AND document_key = :id
                   AND version = :version AND status = :previous
                """)
                .param("round", request.round()).param("instance", request.instanceId().value())
                .param("status", request.status().name()).param("reason", request.resultReason())
                .param("sequence", request.eventSequence()).param("processKey", request.processKey().name())
                .param("type", request.document().documentType().name()).param("id", request.document().documentId().value())
                .param("version", request.loadedVersion()).param("previous", request.loadedStatus().name()).update();
        if (updated != 1) {
            throw new StaleAggregateException("Approval request " + request.aggregateId() + " was changed by somebody else");
        }
    }

    @Override
    public List<ApprovalRound> rounds(ProcessKey processKey, DocumentReference document) {
        return jdbc.sql("""
                SELECT round_number, initiator_id, instance_id FROM platform_approval.start_rounds
                 WHERE process_key = :processKey AND document_key = :id ORDER BY round_number
                """)
                .param("processKey", processKey.name()).param("id", document.documentId().value())
                .query((rs, n) -> new ApprovalRound(rs.getInt("round_number"), new UserId(rs.getLong("initiator_id")),
                        new ApprovalInstanceId(rs.getString("instance_id")))).list();
    }

    private static ApprovalRequest read(ResultSet rs) throws SQLException {
        return ApprovalRequest.restore(ProcessKey.valueOf(rs.getString("process_key")),
                new DocumentReference(DocumentType.valueOf(rs.getString("document_type")), new DocumentId(rs.getString("document_key"))),
                new LegalEntityId(rs.getLong("legal_entity_id")), new UserId(rs.getLong("initiator_id")), rs.getInt("current_round"),
                new ApprovalInstanceId(rs.getString("current_instance_id")), ApprovalStatus.valueOf(rs.getString("status")),
                rs.getString("result_reason"), rs.getLong("event_count"), rs.getLong("version"));
    }
}
