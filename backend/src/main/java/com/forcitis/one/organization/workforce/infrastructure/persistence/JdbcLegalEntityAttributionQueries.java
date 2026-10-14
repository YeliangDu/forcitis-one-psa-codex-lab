package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.domain.AttributionPeriod;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttributionQueries;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLegalEntityAttributionQueries implements LegalEntityAttributionQueries {
    private final JdbcClient jdbc;

    JdbcLegalEntityAttributionQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public java.util.Optional<Long> versionOf(AttributionSubject subject) {
        return jdbc.sql("select version from organization_workforce.legal_entity_attributions where subject_kind = :kind and subject_id = :id")
                .param("kind", subject.kind().name()).param("id", subject.subjectKey()).query(Long.class).optional();
    }

    @Override
    public List<AttributionPeriod> periodsOf(AttributionSubject subject) {
        String table = subject.kind() == com.forcitis.one.organization.workforce.api.AttributionKind.ORG_UNIT ? "org_unit_legal_entities" : "employee_legal_entities";
        String column = subject.kind() == com.forcitis.one.organization.workforce.api.AttributionKind.ORG_UNIT ? "org_unit_id" : "employee_id";
        return jdbc.sql("select legal_entity_id, effective_from_date, effective_to_date from organization_workforce." + table + " where " + column
                        + " = :key order by effective_from_date")
                .param("key", subject.subjectKey())
                .query((rs, n) -> new AttributionPeriod(new LegalEntityId(rs.getLong("legal_entity_id")),
                        rs.getDate("effective_from_date").toLocalDate(),
                        rs.getDate("effective_to_date") == null ? null : rs.getDate("effective_to_date").toLocalDate())).list();
    }
}
