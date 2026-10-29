package com.forcitis.one.platform.numbering.infrastructure.persistence;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.platform.numbering.domain.DocumentTypeEntry;
import com.forcitis.one.platform.numbering.domain.DocumentTypeRegistry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcDocumentTypeRegistry implements DocumentTypeRegistry {

    static final String COLUMNS = """
            t.document_type_id, t.code, t.number_code, t.owner_bc_code, t.is_finance_confirmation_required, t.effective_from_date, t.retired_date
            """;

    static final RowMapper<DocumentTypeEntry> MAPPER = (rs, n) -> new DocumentTypeEntry(rs.getObject("document_type_id", UUID.class),
            new DocumentTypeCode(rs.getString("code")), Optional.ofNullable(rs.getString("number_code")), rs.getString("owner_bc_code"),
            rs.getBoolean("is_finance_confirmation_required"), rs.getObject("effective_from_date", java.time.LocalDate.class),
            Optional.ofNullable(rs.getObject("retired_date", java.time.LocalDate.class)));

    private final JdbcClient jdbc;

    JdbcDocumentTypeRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DocumentTypeEntry> find(DocumentTypeCode code) {
        return jdbc.sql("""
                select
                """ + COLUMNS + """
                         from platform_numbering.document_types t where t.code = :code
                        """)
                .param("code", code.value()).query(MAPPER).optional();
    }
}
