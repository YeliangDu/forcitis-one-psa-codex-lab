package com.forcitis.one.organization.attendance.infrastructure.persistence;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.domain.DateOverride;
import com.forcitis.one.organization.attendance.domain.StandardHours;
import com.forcitis.one.organization.attendance.domain.StandardHoursRepository;
import com.forcitis.one.organization.attendance.domain.StandardHoursScopeTail;
import com.forcitis.one.organization.attendance.domain.StandardHoursVersionRecord;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcStandardHoursRepository implements StandardHoursRepository {
    private static final String EXCLUSION_VIOLATION = "23P01";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHECK_VIOLATION = "23514";

    private final JdbcClient jdbc;
    private final Clock clock;
    private final IdGenerator ids;

    JdbcStandardHoursRepository(JdbcClient jdbc, Clock clock, IdGenerator ids) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public StandardHoursScopeTail tail(@Nullable LegalEntityId legalEntityId) {
        return jdbc.sql("""
                select coalesce(max(version_number), 0) as last_number,
                       max(effective_from_date) filter (where status = 'IN_EFFECT') as latest_from
                  from organization_attendance.standard_hours_versions
                 where legal_entity_id is not distinct from :legalEntityId
                """)
                .param("legalEntityId", legalEntityId == null ? null : legalEntityId.value(), Types.OTHER)
                .query((rs, n) -> new StandardHoursScopeTail(rs.getInt("last_number"), rs.getObject("latest_from", LocalDate.class))).single();
    }

    @Override
    public Optional<StandardHours> find(UUID id) {
        return jdbc.sql("select " + StandardHoursRows.COLUMNS + ", decision_audit_entry_id from organization_attendance.standard_hours_versions where id = :id")
                .param("id", id)
                .query((rs, n) -> {
                    StandardHoursVersionRecord stored = StandardHoursRows.map(rs, StandardHoursRows.overrides(jdbc, id));
                    return StandardHours.restore(stored, new AuditEntryId(rs.getObject("decision_audit_entry_id", UUID.class)));
                }).optional();
    }

    /** Two publishes of one scope that read the same tail meet here: the database lets the first commit win and refuses the other, with an exclusion or unique violation while both are in flight, or with the ordering trigger once the winner has committed. */
    @Override
    public void insert(StandardHours version) {
        try {
            insertRows(version);
        } catch (DataIntegrityViolationException lost) {
            if (!lostRaceToAnotherPublish(lost)) {
                throw lost;
            }
            throw new DomainException(AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_OVERLAP, ErrorKind.CONFLICT,
                    "another publication of the scope was stored first: retry with the current latest version");
        }
    }

    private static boolean lostRaceToAnotherPublish(DataIntegrityViolationException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && (EXCLUSION_VIOLATION.equals(sql.getSQLState()) || UNIQUE_VIOLATION.equals(sql.getSQLState())
                    || CHECK_VIOLATION.equals(sql.getSQLState()) && isOrderingRefusal(sql.getMessage()))) {
                return true;
            }
        }
        return false;
    }

    /** Only the two refusals of guard_standard_hours_version_insert that a stale tail causes; any other check violation is a defect and stays a 500. */
    private static boolean isOrderingRefusal(@Nullable String message) {
        return message != null && (message.contains("version_number must be") || message.contains("must be later than the latest version"));
    }

    private void insertRows(StandardHours version) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        UUID legalEntity = version.legalEntityId() == null ? null : version.legalEntityId().value();
        jdbc.sql("""
                insert into organization_attendance.standard_hours_versions (id, scope_kind, legal_entity_id, version_number, status, effective_from_date,
                    workday_minutes_count, adjusted_workday_minutes_count, basis_ref_text, confirmed_by, confirmed_at, decision_audit_entry_id,
                    created_at, updated_at)
                values (:id, :scopeKind, :legalEntityId, :versionNumber, :status, :effectiveFromDate, :workday, :adjusted, :basis, :confirmedBy,
                    :confirmedAt, :auditEntry, :now, :now)
                """)
                .param("id", version.id()).param("status", version.status().name()).param("scopeKind", legalEntity == null ? "DEFAULT" : "LEGAL_ENTITY")
                .param("legalEntityId", legalEntity, Types.OTHER).param("versionNumber", version.versionNumber())
                .param("effectiveFromDate", version.effectiveFromDate()).param("workday", version.workdayMinutes())
                .param("adjusted", version.adjustedWorkdayMinutes()).param("basis", version.basisRef())
                .param("confirmedBy", version.confirmedBy().value())
                .param("confirmedAt", OffsetDateTime.ofInstant(version.confirmedAt(), ZoneOffset.UTC))
                .param("auditEntry", version.decisionAuditEntryId().value()).param("now", now).update();
        for (DateOverride override : version.dateOverrides()) {
            jdbc.sql("""
                    insert into organization_attendance.standard_hours_date_overrides (id, standard_hours_version_id, work_date, minutes_count,
                        reason_text, created_at, updated_at)
                    values (:id, :version, :workDate, :minutes, :reason, :now, :now)
                    """)
                    .param("id", ids.next()).param("version", version.id()).param("workDate", override.workDate())
                    .param("minutes", override.minutes()).param("reason", override.reason()).param("now", now).update();
        }
    }

    @Override
    public void saveVoided(StandardHours version) {
        // updated_at stays as it was: the database refuses a voiding that changes anything beyond the four void columns.
        jdbc.sql("""
                update organization_attendance.standard_hours_versions
                   set status = :status, voided_by = :by, voided_at = :at, void_reason_text = :reason
                 where id = :id and status = 'IN_EFFECT'
                """)
                .param("id", version.id()).param("status", version.status().name()).param("by", Objects.requireNonNull(version.voidedBy()).value())
                .param("at", OffsetDateTime.ofInstant(Objects.requireNonNull(version.voidedAt()), ZoneOffset.UTC))
                .param("reason", version.voidReason()).update();
    }
}
