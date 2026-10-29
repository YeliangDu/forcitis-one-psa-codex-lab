package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.domain.AttributionPeriod;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttributionQueries;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
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
        return jdbc.sql("""
                select version from organization_workforce.legal_entity_attributions where
                """
                        + EffectiveAttributionPeriods.subjectColumn(subject.kind()) + " = :id")
                .param("id", subject.subjectId()).query(Long.class).optional();
    }

    @Override
    public List<AttributionPeriod> periodsOf(AttributionSubject subject) {
        return jdbc.sql(EffectiveAttributionPeriods.select(subject.kind()) + " order by p.effective_from_date")
                .param("id", subject.subjectId())
                .query((rs, n) -> new AttributionPeriod(new LegalEntityId(rs.getObject("legal_entity_id", UUID.class)),
                        rs.getObject("effective_from_date", LocalDate.class),
                        rs.getObject("effective_to_date", LocalDate.class))).list();
    }
}
