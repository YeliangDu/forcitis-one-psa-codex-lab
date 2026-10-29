package com.forcitis.one.platform.integration.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.IntegrationError;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.domain.SourceReference;
import com.forcitis.one.platform.integration.domain.SourceReferenceId;
import com.forcitis.one.platform.integration.domain.SourceReferenceRepository;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSourceReferenceRepository implements SourceReferenceRepository {

    private static final String EXCLUSION_VIOLATION = "23P01";
    private static final String UNIQUE_VIOLATION = "23505";

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcSourceReferenceRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void insert(SourceReference unsaved) {
        insertStatement(unsaved, "");
    }

    @Override
    public boolean insertIfAbsent(SourceReference allocated) {
        return insertStatement(allocated, " on conflict do nothing") == 1;
    }

    private int insertStatement(SourceReference reference, String onConflict) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return jdbc.sql("""
                        insert into platform_integration.source_references
                               (source_reference_id, source_system, source_object_kind, source_id, own_id, confirmation_status, effective_from_date,
                                effective_to_date, created_by, created_at, updated_at, version, event_count)
                        values (:id, :system, :kind, :sourceId, :ownId, :status, :from, :to, :createdBy, :now, :now, :version, :eventSequence)
                        """ + onConflict)
                .param("id", reference.sourceReferenceId().value())
                .param("system", reference.system().name())
                .param("kind", reference.kind().name())
                .param("sourceId", reference.sourceId().value())
                .param("ownId", reference.ownId().value())
                .param("status", reference.status().name())
                .param("from", reference.effectiveFrom(), Types.DATE)
                .param("to", reference.effectiveTo(), Types.DATE)
                .param("createdBy", reference.registeredBy() == null ? null : reference.registeredBy().value(), Types.OTHER)
                .param("now", now)
                .param("version", reference.versionAfterSave())
                .param("eventSequence", reference.eventSequenceAfterSave())
                .update();
    }

    @Override
    public Optional<SourceReference> findForUpdate(SourceReferenceId id) {
        return jdbc.sql(SourceReferenceRows.SELECT + """
                 where source_reference_id = :id
                   for update
                """)
                .param("id", id.value()).query((rs, n) -> SourceReferenceRows.map(rs)).optional();
    }

    @Override
    public Optional<SourceReference> findOpenConfirmedForUpdate(ExternalSource system, SourceObjectKind kind, OwnObjectId ownId) {
        return jdbc.sql(SourceReferenceRows.SELECT + """
                        where source_system = :system and source_object_kind = :kind and own_id = :ownId
                          and confirmation_status = 'CONFIRMED' and effective_to_date is null
                          for update
                        """)
                .param("system", system.name()).param("kind", kind.name()).param("ownId", ownId.value()).query((rs, n) -> SourceReferenceRows.map(rs)).optional();
    }

    @Override
    public void save(SourceReference reference) {
        int updated;
        try {
            updated = jdbc.sql("""
                            update platform_integration.source_references
                               set confirmation_status = :status, effective_to_date = :to, decided_by = :decidedBy, decided_at = :decidedAt,
                                   version = :next, event_count = :eventSequence, updated_at = :updatedAt
                             where source_reference_id = :id and version = :version and confirmation_status = :loadedStatus
                            """)
                    .param("status", reference.status().name())
                    .param("to", reference.effectiveTo(), Types.DATE)
                    .param("decidedBy", reference.decidedBy() == null ? null : reference.decidedBy().value(), Types.OTHER)
                    .param("decidedAt", reference.decidedAt() == null ? null : OffsetDateTime.ofInstant(reference.decidedAt(), ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                    .param("next", reference.versionAfterSave())
                    .param("eventSequence", reference.eventSequenceAfterSave())
                    .param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                    .param("id", reference.sourceReferenceId().value())
                    .param("version", reference.version())
                    .param("loadedStatus", reference.loadedStatus().name())
                    .update();
        } catch (DataIntegrityViolationException refused) {
            if (isOverlap(refused)) {
                throw new DomainException(IntegrationError.PLATFORM_INTEGRATION_SOURCE_REFERENCE_CONFLICT, ErrorKind.CONFLICT,
                        "a confirmed reference already holds this source id or this own id for that period");
            }
            throw refused;
        }
        if (updated != 1) {
            throw new DomainException(IntegrationError.PLATFORM_INTEGRATION_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                    "source reference " + reference.sourceReferenceId() + " was changed by someone else");
        }
    }

    private static boolean isOverlap(DataIntegrityViolationException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && (EXCLUSION_VIOLATION.equals(sql.getSQLState()) || UNIQUE_VIOLATION.equals(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }
}
