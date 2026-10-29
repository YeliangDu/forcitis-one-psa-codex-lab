package com.forcitis.one.platform.numbering.infrastructure.persistence;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.MigrationBatchId;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import com.forcitis.one.platform.numbering.domain.LegacyNumberRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLegacyNumberRepository implements LegacyNumberRepository {

    private static final RowMapper<LegacyNumberEntry> MAPPER = (rs, n) -> new LegacyNumberEntry(
            new DocumentReference(DocumentTypeCode.of(rs.getString("code")), new DocumentId(rs.getObject("document_id", UUID.class))),
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
    public List<LegacyNumberEntry> findByPrefix(ExternalSource sourceSystem, DocumentTypeCode type, String prefix, String after, int limit) {
        return jdbc.sql("""
                        select l.source_system, t.code, l.legacy_number, l.document_id, l.legal_entity_id, l.migration_batch_id
                          from platform_numbering.legacy_numbers l join platform_numbering.document_types t on t.document_type_id = l.document_type_id
                         where l.source_system = :system and t.code = :type and starts_with(l.legacy_number, :prefix)
                           and (cast(:after as text) is null or l.legacy_number > :after)
                         order by l.legacy_number limit :limit
                        """)
                .param("system", sourceSystem.name()).param("type", type.value()).param("prefix", prefix).param("after", after, Types.VARCHAR).param("limit", limit)
                .query(MAPPER).list();
    }

    @Override
    public Optional<LegacyNumberEntry> find(ExternalSource sourceSystem, DocumentTypeCode type, String legacyNumber) {
        return jdbc.sql("""
                        select l.source_system, t.code, l.legacy_number, l.document_id, l.legal_entity_id, l.migration_batch_id
                          from platform_numbering.legacy_numbers l join platform_numbering.document_types t on t.document_type_id = l.document_type_id
                         where l.source_system = :system and t.code = :type and l.legacy_number = :legacy
                        """)
                .param("system", sourceSystem.name())
                .param("type", type.value())
                .param("legacy", legacyNumber)
                .query(MAPPER)
                .optional();
    }

    @Override
    public List<LegacyNumberEntry> findByDocument(DocumentReference document) {
        return jdbc.sql("""
                        select l.source_system, t.code, l.legacy_number, l.document_id, l.legal_entity_id, l.migration_batch_id
                          from platform_numbering.legacy_numbers l join platform_numbering.document_types t on t.document_type_id = l.document_type_id
                         where t.code = :type and l.document_id = :document
                         order by l.legacy_number
                        """)
                .param("type", document.documentType().value())
                .param("document", document.documentId().value())
                .query(MAPPER)
                .list();
    }

    @Override
    public boolean insertIfAbsent(LegacyNumberEntry entry) {
        return jdbc.sql("""
                        insert into platform_numbering.legacy_numbers
                               (id, source_system, document_type_id, legacy_number, document_id, legal_entity_id, migration_batch_id, created_at, updated_at)
                        values (:id, :system, (select document_type_id from platform_numbering.document_types where code = :type), :legacy, :document, :le, :batch, :now, :now)
                        on conflict (source_system, document_type_id, legacy_number) do nothing
                        """)
                .param("id", ids.next())
                .param("system", entry.sourceSystem().name())
                .param("type", entry.document().documentType().value())
                .param("legacy", entry.legacyNumber())
                .param("document", entry.document().documentId().value())
                .param("le", entry.legalEntity().map(LegalEntityId::value).orElse(null))
                .param("batch", entry.migrationBatchId().value())
                .param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update() == 1;
    }
}
