package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.domain.LegalEntityQueries;
import com.forcitis.one.organization.workforce.domain.LegalEntityStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLegalEntityQueries implements LegalEntityQueries {
    private static final String SELECT = """
            select id, code, name, status, version
              from organization_workforce.legal_entities
            """;

    private final JdbcClient jdbc;

    JdbcLegalEntityQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LegalEntityRecord> findAll() {
        return jdbc.sql(SELECT + """
                 order by code
                """)
                .query((rs, n) -> row(rs)).list();
    }

    @Override
    public Optional<LegalEntityRecord> find(LegalEntityId legalEntityId) {
        return jdbc.sql(SELECT + """
                 where id = :id
                """)
                .param("id", legalEntityId.value()).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public Optional<LegalEntityRecord> findSharedLocked(LegalEntityId legalEntityId) {
        return jdbc.sql(SELECT + """
                 where id = :id
                   for share
                """)
                .param("id", legalEntityId.value()).query((rs, n) -> row(rs)).optional();
    }

    private static LegalEntityRecord row(ResultSet rs) throws SQLException {
        return new LegalEntityRecord(new LegalEntityId(rs.getObject("id", UUID.class)), new LegalEntityCode(rs.getString("code")), rs.getString("name"),
                LegalEntityStatus.valueOf(rs.getString("status")), rs.getLong("version"));
    }
}
