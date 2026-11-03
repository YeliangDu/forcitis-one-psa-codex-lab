package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.CountersignRequestKind;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAttributionCountersignRepository implements AttributionCountersignRepository {
    private static final String COLUMNS = """
            select attribution_countersign_id, personnel_command_id, employee_id, drafted_by, request_kind, request_sequence_number, legal_entity_id, requested_at,
                   requested_by, due_on_date, request_reason, decision_kind, decided_at, decided_by, decision_reason, escalation_level_number, version
              from organization_workforce.attribution_countersigns
            """;

    private final JdbcClient jdbc;

    JdbcAttributionCountersignRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AttributionCountersign> find(AttributionCountersignId id) {
        return jdbc.sql(COLUMNS + " where attribution_countersign_id = :id").param("id", id.value()).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public List<AttributionCountersign> ofCommand(PersonnelCommandId command) {
        return jdbc.sql(COLUMNS + " where personnel_command_id = :command order by request_kind, request_sequence_number, legal_entity_id")
                .param("command", command.value()).query((rs, n) -> row(rs)).list();
    }

    private static AttributionCountersign row(ResultSet rs) throws SQLException {
        String decision = rs.getString("decision_kind");
        OffsetDateTime decidedAt = rs.getObject("decided_at", OffsetDateTime.class);
        UUID decidedBy = rs.getObject("decided_by", UUID.class);
        return AttributionCountersign.restore(new AttributionCountersignId(rs.getObject("attribution_countersign_id", UUID.class)),
                new PersonnelCommandId(rs.getObject("personnel_command_id", UUID.class)), new EmployeeId(rs.getObject("employee_id", UUID.class)),
                new UserId(rs.getObject("drafted_by", UUID.class)), CountersignRequestKind.valueOf(rs.getString("request_kind")), rs.getInt("request_sequence_number"),
                new LegalEntityId(rs.getObject("legal_entity_id", UUID.class)), rs.getObject("requested_at", OffsetDateTime.class).toInstant(),
                new UserId(rs.getObject("requested_by", UUID.class)), rs.getObject("due_on_date", LocalDate.class), rs.getInt("escalation_level_number"),
                rs.getString("request_reason"), rs.getLong("version"), decision,
                decidedAt == null ? null : decidedAt.toInstant(), decidedBy == null ? null : new UserId(decidedBy), rs.getString("decision_reason"));
    }

    @Override
    public void insert(AttributionCountersign r) {
        jdbc.sql("""
                insert into organization_workforce.attribution_countersigns (attribution_countersign_id, personnel_command_id, employee_id, drafted_by, request_kind,
                       request_sequence_number, legal_entity_id, requested_at, requested_by, due_on_date, request_reason, escalation_level_number, created_at, updated_at, version)
                values (:id, :command, :employee, :draftedBy, :kind, :sequence, :legalEntity, :requestedAt, :requestedBy, :dueOn, :requestReason, :level, :requestedAt, :requestedAt, :version)
                """)
                .param("id", r.id().value()).param("command", r.commandId().value()).param("employee", r.employeeId().value()).param("draftedBy", r.draftedBy().value())
                .param("kind", r.requestKind().name()).param("sequence", r.requestSequenceNumber()).param("legalEntity", r.legalEntityId().value())
                .param("requestedAt", at(r.requestedAt())).param("requestedBy", r.requestedBy().value()).param("dueOn", r.dueOn()).param("requestReason", r.requestReason(), Types.VARCHAR)
                .param("level", r.escalationLevel()).param("version", r.version()).update();
    }

    @Override
    public void saveDecision(AttributionCountersign r, AuditEntryId decisionAudit, Instant at, long loadedVersion) {
        int updated = jdbc.sql("""
                update organization_workforce.attribution_countersigns set decision_kind = :decision, decided_at = :decidedAt, decided_by = :decidedBy,
                       decision_reason = :reason, decision_audit_entry_id = :audit, updated_at = :now, version = :version
                 where attribution_countersign_id = :id and version = :loaded and decision_kind is null
                """)
                .param("decision", r.status().name()).param("decidedAt", at(r.decidedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("decidedBy", r.decidedBy() == null ? null : r.decidedBy().value(), Types.OTHER).param("reason", r.decisionReason(), Types.VARCHAR)
                .param("audit", decisionAudit.value()).param("now", at(at)).param("version", loadedVersion + 1).param("id", r.id().value()).param("loaded", loadedVersion).update();
        if (updated != 1) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE, ErrorKind.CONFLICT, "the countersign request was changed by someone else");
        }
    }

    @Override
    public boolean isFinanceHead(LegalEntityId legalEntity, EmployeeId employee, LocalDate day) {
        return jdbc.sql("""
                select exists (select 1 from organization_workforce.legal_entity_versions
                                where legal_entity_id = :entity and finance_head_employee_id = :employee and effective_from_date <= :day
                                  and (effective_to_date is null or :day < effective_to_date))
                """).param("entity", legalEntity.value()).param("employee", employee.value()).param("day", day).query(Boolean.class).single();
    }

    private static @Nullable OffsetDateTime at(@Nullable Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
