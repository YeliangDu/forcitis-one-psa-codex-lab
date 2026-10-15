package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.domain.AttributionPeriod;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttributionQueries;
import java.util.List;
import java.util.UUID;
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
        return jdbc.sql(EffectiveAttributionPeriods.select(subject.kind()) + " order by p.effective_from_date")
                .param("id", subject.subjectKey())
                .query((rs, n) -> new AttributionPeriod(new LegalEntityId(rs.getObject("legal_entity_id", UUID.class)),
                        rs.getDate("effective_from_date").toLocalDate(),
                        rs.getDate("effective_to_date") == null ? null : rs.getDate("effective_to_date").toLocalDate())).list();
    }
}
