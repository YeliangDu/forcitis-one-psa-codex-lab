package com.forcitis.one.platform.numbering.infrastructure.persistence;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.platform.numbering.api.DocumentId;
import com.forcitis.one.platform.numbering.api.DocumentType;
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
            DocumentType.valueOf(rs.getString("document_type")), rs.getString("legacy_number"), new DocumentId(rs.getLong("document_id")),
            new LegalEntityId(rs.getLong("legal_entity_id")), rs.getString("import_batch"));

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
    public List<LegacyNumberEntry> findByDocument(DocumentType type, DocumentId documentId) {
        return jdbc.sql("""
                        select document_type, legacy_number, document_id, legal_entity_id, import_batch
                          from platform_numbering.legacy_numbers
                         where document_type = :type and document_id = :document
                         order by id
                        """)
                .param("type", type.name())
                .param("document", documentId.value())
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
                .param("type", entry.documentType().name())
                .param("legacy", entry.legacyNumber())
                .param("document", entry.documentId().value())
                .param("le", entry.legalEntity().value())
                .param("batch", entry.importBatch())
                .update() == 1;
    }
}
