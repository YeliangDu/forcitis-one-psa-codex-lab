package com.forcitis.one.platform.integration.infrastructure.persistence;

import com.forcitis.one.platform.integration.api.ConfirmationStatus;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.domain.SourceReference;
import com.forcitis.one.platform.integration.domain.SourceReferenceId;
import com.forcitis.one.platform.integration.domain.SourceReferenceQueries;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSourceReferenceQueries implements SourceReferenceQueries {

    private final JdbcClient jdbc;

    JdbcSourceReferenceQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OwnObjectId> findOwnId(ExternalSource system, SourceObjectKind kind, SourceObjectId sourceId) {
        if (kind.hasPeriod()) {
            throw new IllegalArgumentException(kind + " references carry a period: find the source id on a day instead");
        }
        return jdbc.sql("""
                        select own_id from platform_integration.source_references
                         where source_system = :system and source_object_kind = :kind and source_id = :sourceId and confirmation_status = 'CONFIRMED'
                           and effective_from_date is null
                        """)
                .param("system", system.name()).param("kind", kind.name()).param("sourceId", sourceId.value())
                .query(UUID.class).optional().map(OwnObjectId::new);
    }

    @Override
    public Optional<SourceObjectId> findSourceId(ExternalSource system, SourceObjectKind kind, OwnObjectId ownId, LocalDate on) {
        return jdbc.sql("""
                        select source_id from platform_integration.source_references
                         where source_system = :system and source_object_kind = :kind and own_id = :ownId and confirmation_status = 'CONFIRMED'
                           and (effective_from_date is null or effective_from_date <= :on)
                           and (effective_to_date is null or :on < effective_to_date)
                        """)
                .param("system", system.name()).param("kind", kind.name()).param("ownId", ownId.value()).param("on", on)
                .query(Long.class).optional().map(SourceObjectId::new);
    }

    /** The status is written into the statement as a literal so that the planner can match the partial index of the pending rows. */
    @Override
    public List<SourceReference> list(ExternalSource system, SourceObjectKind kind, @Nullable ConfirmationStatus status, @Nullable OwnObjectId ownId,
                                      @Nullable SourceReferenceId after, int limit) {
        StringBuilder sql = new StringBuilder("select " + SourceReferenceRows.COLUMNS + """
                 from platform_integration.source_references
                where source_system = :system and source_object_kind = :kind
                """);
        if (status != null) {
            sql.append(" and confirmation_status = '").append(status.name()).append("'");
        }
        if (ownId != null) {
            sql.append(" and own_id = :ownId");
        }
        if (after != null) {
            sql.append(" and source_reference_id > :after");
        }
        sql.append(" order by source_reference_id limit :limit");
        JdbcClient.StatementSpec statement = jdbc.sql(sql.toString()).param("system", system.name()).param("kind", kind.name()).param("limit", limit);
        if (ownId != null) {
            statement = statement.param("ownId", ownId.value());
        }
        if (after != null) {
            statement = statement.param("after", after.value());
        }
        return statement.query((rs, n) -> SourceReferenceRows.map(rs)).list();
    }
}
