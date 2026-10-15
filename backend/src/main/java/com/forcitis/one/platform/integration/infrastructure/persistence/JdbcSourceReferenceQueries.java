package com.forcitis.one.platform.integration.infrastructure.persistence;

import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.domain.SourceReferenceQueries;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
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
        return jdbc.sql("""
                        select own_id from platform_integration.source_references
                         where source_system = :system and source_object_kind = :kind and source_id = :sourceId and confirmation_status = 'CONFIRMED'
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
}
