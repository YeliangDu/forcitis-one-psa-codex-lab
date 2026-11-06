package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.organization.workforce.domain.SeparationBasis;
import com.forcitis.one.organization.workforce.domain.SeparationBasisQueries;
import com.forcitis.one.organization.workforce.domain.SeparationInitiator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSeparationBasisQueries implements SeparationBasisQueries {
    private static final String SELECT = """
            select separation_basis_key, display_name, default_initiator_kind, is_employer_initiated, is_system_only, is_active
              from organization_workforce.separation_bases
            """;

    private final JdbcClient jdbc;

    JdbcSeparationBasisQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SeparationBasis> find(String key) {
        return jdbc.sql(SELECT + " where separation_basis_key = :key").param("key", key).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public List<SeparationBasis> findAll() {
        return jdbc.sql(SELECT + " order by separation_basis_key").query((rs, n) -> row(rs)).list();
    }

    private static SeparationBasis row(ResultSet rs) throws SQLException {
        return new SeparationBasis(rs.getString("separation_basis_key"), rs.getString("display_name"), SeparationInitiator.valueOf(rs.getString("default_initiator_kind")),
                rs.getBoolean("is_employer_initiated"), rs.getBoolean("is_system_only"), rs.getBoolean("is_active"));
    }
}
