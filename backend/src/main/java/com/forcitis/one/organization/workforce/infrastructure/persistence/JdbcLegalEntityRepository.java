package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.domain.LegalEntity;
import com.forcitis.one.organization.workforce.domain.LegalEntityRepository;
import com.forcitis.one.organization.workforce.domain.LegalEntityStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLegalEntityRepository implements LegalEntityRepository {

    private static final String COLUMNS = "id, code, name, status";

    private final JdbcClient jdbc;

    JdbcLegalEntityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LegalEntity> findAll() {
        return jdbc.sql("select " + COLUMNS + " from organization_workforce.legal_entities order by code")
                .query((rs, n) -> row(rs)).list();
    }

    @Override
    public Optional<LegalEntity> find(LegalEntityId legalEntityId) {
        return jdbc.sql("select " + COLUMNS + " from organization_workforce.legal_entities where id = :id")
                .param("id", legalEntityId.value()).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public Optional<LegalEntity> insert(LegalEntityCode code, String name) {
        return jdbc.sql("""
                        insert into organization_workforce.legal_entities (code, name, status)
                        values (:code, :name, 'ACTIVE')
                        on conflict (code) do nothing
                        returning """ + " " + COLUMNS)
                .param("code", code.value()).param("name", name.trim()).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public void update(LegalEntity legalEntity) {
        jdbc.sql("""
                        update organization_workforce.legal_entities
                           set name = :name, status = :status, updated_at = now()
                         where id = :id
                        """)
                .param("name", legalEntity.name()).param("status", legalEntity.status().name())
                .param("id", legalEntity.legalEntityId().value()).update();
    }

    private static LegalEntity row(ResultSet rs) throws SQLException {
        return new LegalEntity(new LegalEntityId(rs.getLong("id")), new LegalEntityCode(rs.getString("code")), rs.getString("name"),
                LegalEntityStatus.valueOf(rs.getString("status")));
    }
}
