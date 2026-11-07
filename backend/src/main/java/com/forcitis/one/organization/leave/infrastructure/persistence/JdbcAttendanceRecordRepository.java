package com.forcitis.one.organization.leave.infrastructure.persistence;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.leave.api.AttendanceRecordInput;
import com.forcitis.one.organization.leave.domain.AttendanceRecordRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The unique source/key constraint makes replay a no-op without updating audit timestamps. */
@Repository
class JdbcAttendanceRecordRepository implements AttendanceRecordRepository {
    private final JdbcClient jdbc;
    private final IdGenerator ids;

    JdbcAttendanceRecordRepository(JdbcClient jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public void insertIfAbsent(AttendanceRecordInput record, Instant createdAt) {
        jdbc.sql("""
                insert into organization_leave.attendance_records
                    (id, employee_id, record_kind, occurred_at, source_system,
                     external_record_key, created_at, updated_at)
                values (:id, :employeeId, :kind, :occurredAt, :source, :key, :createdAt, :createdAt)
                on conflict (source_system, external_record_key) do nothing
                """)
                .param("id", ids.next())
                .param("employeeId", record.employeeId().value())
                .param("kind", record.recordKind().name())
                .param("occurredAt", record.occurredAt().atOffset(ZoneOffset.UTC))
                .param("source", record.sourceSystem())
                .param("key", record.externalRecordKey())
                .param("createdAt", createdAt.atOffset(ZoneOffset.UTC)).update();
    }
}
