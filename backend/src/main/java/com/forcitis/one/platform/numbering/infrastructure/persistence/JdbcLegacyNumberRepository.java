package com.forcitis.one.platform.numbering.infrastructure.persistence;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import com.forcitis.one.platform.numbering.domain.LegacyNumberRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
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
            rs.getString("legacy_number"), legalEntity(rs), rs.getString("import_batch"));

    private static Optional<LegalEntityId> legalEntity(ResultSet rs) throws SQLException {
        UUID value = rs.getObject("legal_entity_id", UUID.class);
        return value == null ? Optional.empty() : Optional.of(new LegalEntityId(value));
    }

    private final JdbcClient jdbc;
    private final IdGenerator ids;

    JdbcLegacyNumberRepository(JdbcClient jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public List<LegacyNumberEntry> findByPrefix(DocumentType type, String prefix, String after, int limit) {
        return jdbc.sql("""
                        select document_type, legacy_number, document_id, legal_entity_id, import_batch
                          from platform_numbering.legacy_numbers
                         where document_type = :type and starts_with(legacy_number, :prefix)
                           and (cast(:after as text) is null or legacy_number > :after)
                         order by legacy_number limit :limit
                        """)
                .param("type", type.name()).param("prefix", prefix).param("after", after, java.sql.Types.VARCHAR).param("limit", limit)
                .query(MAPPER).list();
    }

    @Override
    public Optional<LegacyNumberEntry> find(DocumentType type, String legacyNumber) {
        return jdbc.sql("""
                        select document_type, legacy_number, document_id, legal_entity_id, import_batch
                          from platform_numbering.legacy_numbers
                         where document_type = :type and legacy_number = :legacy
                        """)
                .param("type", type.name())
                .param("legacy", legacyNumber)
                .query(MAPPER)
                .optional();
    }

    @Override
    public List<LegacyNumberEntry> findByDocument(DocumentReference document) {
        return jdbc.sql("""
                        select document_type, legacy_number, document_id, legal_entity_id, import_batch
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
                               (id, document_type, legacy_number, document_id, legal_entity_id, import_batch)
                        values (:id, :type, :legacy, :document, :le, :batch)
                        on conflict (document_type, legacy_number) do nothing
                        """)
                .param("id", ids.next())
                .param("type", entry.document().documentType().name())
                .param("legacy", entry.legacyNumber())
                .param("document", UUID.fromString(entry.document().documentId().value()))
                .param("le", entry.legalEntity().map(LegalEntityId::value).orElse(null))
                .param("batch", entry.importBatch())
                .update() == 1;
    }
}
