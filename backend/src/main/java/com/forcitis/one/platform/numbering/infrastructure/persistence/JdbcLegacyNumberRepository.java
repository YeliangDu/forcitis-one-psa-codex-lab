package com.forcitis.one.platform.numbering.infrastructure.persistence;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.MigrationBatchId;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import com.forcitis.one.platform.numbering.domain.LegacyNumberRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLegacyNumberRepository implements LegacyNumberRepository {

    private static final RowMapper<LegacyNumberEntry> MAPPER = (rs, n) -> new LegacyNumberEntry(
            new DocumentReference(DocumentType.valueOf(rs.getString("document_type")), new DocumentId(rs.getObject("document_id", UUID.class).toString())),
            ExternalSource.valueOf(rs.getString("source_system")), rs.getString("legacy_number"), legalEntity(rs), new MigrationBatchId(rs.getObject("migration_batch_id", UUID.class)));

    private static Optional<LegalEntityId> legalEntity(ResultSet rs) throws SQLException {
        UUID value = rs.getObject("legal_entity_id", UUID.class);
        return value == null ? Optional.empty() : Optional.of(new LegalEntityId(value));
    }

    private final JdbcClient jdbc;
    private final IdGenerator ids;
    private final Clock clock;

    JdbcLegacyNumberRepository(JdbcClient jdbc, IdGenerator ids, Clock clock) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public List<LegacyNumberEntry> findByPrefix(ExternalSource sourceSystem, DocumentType type, String prefix, String after, int limit) {
        return jdbc.sql("""
                        select source_system, document_type, legacy_number, document_id, legal_entity_id, migration_batch_id
                          from platform_numbering.legacy_numbers
                         where source_system = :system and document_type = :type and starts_with(legacy_number, :prefix)
                           and (cast(:after as text) is null or legacy_number > :after)
                         order by legacy_number limit :limit
                        """)
                .param("system", sourceSystem.name()).param("type", type.name()).param("prefix", prefix).param("after", after, Types.VARCHAR).param("limit", limit)
                .query(MAPPER).list();
    }

    @Override
    public Optional<LegacyNumberEntry> find(ExternalSource sourceSystem, DocumentType type, String legacyNumber) {
        return jdbc.sql("""
                        select source_system, document_type, legacy_number, document_id, legal_entity_id, migration_batch_id
                          from platform_numbering.legacy_numbers
                         where source_system = :system and document_type = :type and legacy_number = :legacy
                        """)
                .param("system", sourceSystem.name())
                .param("type", type.name())
                .param("legacy", legacyNumber)
                .query(MAPPER)
                .optional();
    }

    @Override
    public List<LegacyNumberEntry> findByDocument(DocumentReference document) {
        return jdbc.sql("""
                        select source_system, document_type, legacy_number, document_id, legal_entity_id, migration_batch_id
                          from platform_numbering.legacy_numbers
                         where document_type = :type and document_id = :document
                         order by legacy_number
                        """)
                .param("type", document.documentType().name())
                .param("document", UUID.fromString(document.documentId().value()))
                .query(MAPPER)
                .list();
    }

    @Override
    public boolean insertIfAbsent(LegacyNumberEntry entry) {
        return jdbc.sql("""
                        insert into platform_numbering.legacy_numbers
                               (id, source_system, document_type, legacy_number, document_id, legal_entity_id, migration_batch_id, created_at, updated_at)
                        values (:id, :system, :type, :legacy, :document, :le, :batch, :now, :now)
                        on conflict (source_system, document_type, legacy_number) do nothing
                        """)
                .param("id", ids.next())
                .param("system", entry.sourceSystem().name())
                .param("type", entry.document().documentType().name())
                .param("legacy", entry.legacyNumber())
                .param("document", UUID.fromString(entry.document().documentId().value()))
                .param("le", entry.legalEntity().map(LegalEntityId::value).orElse(null))
                .param("batch", entry.migrationBatchId().value())
                .param("now", Timestamp.from(clock.instant()))
                .update() == 1;
    }
}
