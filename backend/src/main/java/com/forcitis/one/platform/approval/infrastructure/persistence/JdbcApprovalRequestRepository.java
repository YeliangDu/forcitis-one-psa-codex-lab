package com.forcitis.one.platform.approval.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRecord;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.domain.ApprovalStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * approval_requests (one row per document, unique on process type + business key) and start_rounds (one row per start,
 * unique on process type + business key + round). The unique constraints, not application checks, are what make starts
 * idempotent and round numbers collision-free under concurrency (RULE-264/266/278).
 */
@Repository
class JdbcApprovalRequestRepository implements ApprovalRequestRepository {

    private static final String COLUMNS = "process_type, business_key, initiator_id, current_round, current_instance_id, status";

    private final JdbcClient jdbc;

    JdbcApprovalRequestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ApprovalRequestRecord lockOrCreate(ProcessKey processKey, String businessKey, UserId initiator) {
        int inserted = jdbc.sql("""
                INSERT INTO platform_approval.approval_requests (process_type, business_key, initiator_id, current_round, status)
                VALUES (:type, :key, :initiator, 0, 'RUNNING')
                ON CONFLICT (process_type, business_key) DO NOTHING
                """)
                .param("type", processKey.name()).param("key", businessKey).param("initiator", initiator.value()).update();
        ApprovalRequestRecord row = jdbc.sql("SELECT " + COLUMNS + """
                  FROM platform_approval.approval_requests
                 WHERE process_type = :type AND business_key = :key
                   FOR UPDATE
                """)
                .param("type", processKey.name()).param("key", businessKey)
                .query((rs, n) -> read(rs, inserted == 1)).single();
        return row;
    }

    @Override
    public Optional<ApprovalRequestRecord> lockByInstance(ApprovalInstanceId instanceId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                  FROM platform_approval.approval_requests
                 WHERE current_instance_id = :instance
                   FOR UPDATE
                """)
                .param("instance", instanceId.value()).query((rs, n) -> read(rs, false)).optional();
    }

    @Override
    public void beginRound(ProcessKey processKey, String businessKey, int round, UserId initiator, ApprovalInstanceId instanceId) {
        jdbc.sql("""
                INSERT INTO platform_approval.start_rounds (process_type, business_key, round_number, initiator_id, instance_id)
                VALUES (:type, :key, :round, :initiator, :instance)
                """)
                .param("type", processKey.name()).param("key", businessKey).param("round", round)
                .param("initiator", initiator.value()).param("instance", instanceId.value()).update();
        jdbc.sql("""
                UPDATE platform_approval.approval_requests
                   SET current_round = :round, current_instance_id = :instance, status = 'RUNNING', result_reason = NULL,
                       updated_at = now(), version = version + 1
                 WHERE process_type = :type AND business_key = :key
                """)
                .param("type", processKey.name()).param("key", businessKey).param("round", round)
                .param("instance", instanceId.value()).update();
    }

    @Override
    public void updateStatus(ApprovalInstanceId instanceId, ApprovalStatus status, String reason) {
        jdbc.sql("""
                UPDATE platform_approval.approval_requests
                   SET status = :status, result_reason = :reason, updated_at = now(), version = version + 1
                 WHERE current_instance_id = :instance
                """)
                .param("status", status.name()).param("reason", reason).param("instance", instanceId.value()).update();
    }

    private static ApprovalRequestRecord read(ResultSet rs, boolean created) throws SQLException {
        String instance = rs.getString("current_instance_id");
        return new ApprovalRequestRecord(ProcessKey.valueOf(rs.getString("process_type")), rs.getString("business_key"),
                new UserId(rs.getLong("initiator_id")), rs.getInt("current_round"),
                instance == null ? null : new ApprovalInstanceId(instance), ApprovalStatus.valueOf(rs.getString("status")), created);
    }
}
