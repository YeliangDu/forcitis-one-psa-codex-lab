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
    public List<AttributionPeriod> periodsOf(AttributionSubject subject) {
        return jdbc.sql("""
                        select p.legal_entity_id, p.effective_from_date, p.effective_to_date
                          from organization_workforce.legal_entity_attributions a
                          join organization_workforce.legal_entity_attribution_periods p on p.attribution_id = a.attribution_id
                         where a.subject_kind = :kind and a.subject_id = :key
                         order by p.effective_from_date
                        """)
                .param("kind", subject.kind().name()).param("key", subject.subjectKey())
                .query((rs, n) -> new AttributionPeriod(new LegalEntityId(rs.getLong("legal_entity_id")),
                        rs.getDate("effective_from_date").toLocalDate(),
                        rs.getDate("effective_to_date") == null ? null : rs.getDate("effective_to_date").toLocalDate())).list();
    }
}
