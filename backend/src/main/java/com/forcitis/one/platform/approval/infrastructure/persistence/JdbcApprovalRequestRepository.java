package com.forcitis.one.platform.approval.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRecord;
import com.forcitis.one.platform.approval.domain.ApprovalRound;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import com.forcitis.one.platform.approval.domain.ApprovalStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * approval_requests (one row per document, unique on process key + business key) and start_rounds (one row per start,
 * unique on process key + business key + round). The unique constraints, not application checks, are what make starts
 * idempotent and round numbers collision-free under concurrency (RULE-264/266/278).
 */
@Repository
class JdbcApprovalRequestRepository implements ApprovalRequestRepository {

    private static final String COLUMNS = "process_key, business_key, initiator_id, current_round, current_instance_id, status";

    private final JdbcClient jdbc;

    JdbcApprovalRequestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ApprovalRequestRecord lockOrCreate(ProcessKey processKey, String businessKey, UserId initiator) {
        int inserted = jdbc.sql("""
                INSERT INTO platform_approval.approval_requests (process_key, business_key, initiator_id, current_round, status)
                VALUES (:processKey, :key, :initiator, 0, 'RUNNING')
                ON CONFLICT (process_key, business_key) DO NOTHING
                """)
                .param("processKey", processKey.name()).param("key", businessKey).param("initiator", initiator.value()).update();
        ApprovalRequestRecord row = jdbc.sql("SELECT " + COLUMNS + """
                  FROM platform_approval.approval_requests
                 WHERE process_key = :processKey AND business_key = :key
                   FOR UPDATE
                """)
                .param("processKey", processKey.name()).param("key", businessKey)
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
                INSERT INTO platform_approval.start_rounds (process_key, business_key, round_number, initiator_id, instance_id)
                VALUES (:processKey, :key, :round, :initiator, :instance)
                """)
                .param("processKey", processKey.name()).param("key", businessKey).param("round", round)
                .param("initiator", initiator.value()).param("instance", instanceId.value()).update();
        jdbc.sql("""
                UPDATE platform_approval.approval_requests
                   SET current_round = :round, current_instance_id = :instance, status = 'RUNNING', result_reason = NULL,
                       updated_at = now(), version = version + 1
                 WHERE process_key = :processKey AND business_key = :key
                """)
                .param("processKey", processKey.name()).param("key", businessKey).param("round", round)
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

    @Override
    public List<ApprovalRound> rounds(ProcessKey processKey, String businessKey) {
        return jdbc.sql("""
                SELECT round_number, initiator_id, instance_id FROM platform_approval.start_rounds
                 WHERE process_key = :processKey AND business_key = :key ORDER BY round_number
                """)
                .param("processKey", processKey.name()).param("key", businessKey)
                .query((rs, n) -> new ApprovalRound(rs.getInt("round_number"), new UserId(rs.getLong("initiator_id")),
                        new ApprovalInstanceId(rs.getString("instance_id")))).list();
    }

    private static ApprovalRequestRecord read(ResultSet rs, boolean created) throws SQLException {
        String instance = rs.getString("current_instance_id");
        return new ApprovalRequestRecord(ProcessKey.valueOf(rs.getString("process_key")), rs.getString("business_key"),
                new UserId(rs.getLong("initiator_id")), rs.getInt("current_round"),
                instance == null ? null : new ApprovalInstanceId(instance), ApprovalStatus.valueOf(rs.getString("status")), created);
    }
}
