package com.forcitis.one.platform.integration.infrastructure.persistence;

import com.forcitis.one.platform.integration.api.ConfirmationStatus;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.domain.ListedSourceReference;
import com.forcitis.one.platform.integration.domain.SourceReferencePosition;
import com.forcitis.one.platform.integration.domain.SourceReferenceQueries;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSourceReferenceQueries implements SourceReferenceQueries {

    /** Bind parameters per statement, well below the 65535 PostgreSQL takes. */
    static final int BATCH = 1000;

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
    public Map<SourceObjectId, OwnObjectId> findOwnIds(ExternalSource system, SourceObjectKind kind, Collection<SourceObjectId> sourceIds) {
        if (kind.hasPeriod()) {
            throw new IllegalArgumentException(kind + " references carry a period: find the source id on a day instead");
        }
        List<Long> all = sourceIds.stream().map(SourceObjectId::value).distinct().toList();
        Map<SourceObjectId, OwnObjectId> found = new HashMap<>();
        for (int from = 0; from < all.size(); from += BATCH) {
            List<Long> chunk = new ArrayList<>(all.subList(from, Math.min(all.size(), from + BATCH)));
            jdbc.sql("""
                            select source_id, own_id from platform_integration.source_references
                             where source_system = :system and source_object_kind = :kind and source_id in (:sourceIds)
                               and confirmation_status = 'CONFIRMED' and effective_from_date is null
                            """)
                    .param("system", system.name()).param("kind", kind.name()).param("sourceIds", chunk)
                    .query((rs, n) -> Map.entry(new SourceObjectId(rs.getLong("source_id")), new OwnObjectId(rs.getObject("own_id", UUID.class))))
                    .list().forEach(row -> found.put(row.getKey(), row.getValue()));
        }
        return found;
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
    public List<ListedSourceReference> list(ExternalSource system, SourceObjectKind kind, @Nullable ConfirmationStatus status, @Nullable OwnObjectId ownId,
                                      @Nullable SourceReferencePosition after, int limit) {
        StringBuilder sql = new StringBuilder(SourceReferenceRows.SELECT + """
                where source_system = :system and source_object_kind = :kind
                """);
        if (status != null) {
            sql.append(" and confirmation_status = :status");
        }
        if (ownId != null) {
            sql.append(" and own_id = :ownId");
        }
        if (after != null) {
            sql.append(" and (created_at, source_reference_id) > (:afterAt, :afterId)");
        }
        sql.append(" order by created_at, source_reference_id limit :limit");
        JdbcClient.StatementSpec statement = jdbc.sql(sql.toString()).param("system", system.name()).param("kind", kind.name()).param("limit", limit);
        if (status != null) {
            statement = statement.param("status", status.name());
        }
        if (ownId != null) {
            statement = statement.param("ownId", ownId.value());
        }
        if (after != null) {
            statement = statement.param("afterAt", OffsetDateTime.ofInstant(after.createdAt(), ZoneOffset.UTC)).param("afterId", after.id().value());
        }
        return statement.query((rs, n) -> new ListedSourceReference(SourceReferenceRows.map(rs), rs.getObject("created_at", OffsetDateTime.class).toInstant())).list();
    }
}
