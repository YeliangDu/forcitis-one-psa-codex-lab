package com.forcitis.one.platform.numbering.infrastructure.persistence;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.platform.numbering.api.LegacyNumberEntry;
import com.forcitis.one.platform.numbering.domain.LegacyNumberRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLegacyNumberRepository implements LegacyNumberRepository {

    private static final RowMapper<LegacyNumberEntry> MAPPER = (rs, n) -> new LegacyNumberEntry(
            new DocumentReference(DocumentType.valueOf(rs.getString("document_type")), new DocumentId(Long.toString(rs.getLong("document_id")))),
            rs.getString("legacy_number"), legalEntity(rs), rs.getString("import_batch"));

    private static Optional<LegalEntityId> legalEntity(java.sql.ResultSet rs) throws java.sql.SQLException {
        long value = rs.getLong("legal_entity_id");
        return rs.wasNull() ? Optional.empty() : Optional.of(new LegalEntityId(value));
    }

    private final JdbcClient jdbc;

    JdbcLegacyNumberRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
                         order by id
                        """)
                .param("type", document.documentType().name())
                .param("document", Long.parseLong(document.documentId().value()))
                .query(MAPPER)
                .list();
    }

    @Override
    public boolean insertIfAbsent(LegacyNumberEntry entry) {
        return jdbc.sql("""
                        insert into platform_numbering.legacy_numbers
                               (document_type, legacy_number, document_id, legal_entity_id, import_batch)
                        values (:type, :legacy, :document, :le, :batch)
                        on conflict (document_type, legacy_number) do nothing
                        """)
                .param("type", entry.document().documentType().name())
                .param("legacy", entry.legacyNumber())
                .param("document", Long.parseLong(entry.document().documentId().value()))
                .param("le", entry.legalEntity().map(LegalEntityId::value).orElse(null))
                .param("batch", entry.importBatch())
                .update() == 1;
    }
}
