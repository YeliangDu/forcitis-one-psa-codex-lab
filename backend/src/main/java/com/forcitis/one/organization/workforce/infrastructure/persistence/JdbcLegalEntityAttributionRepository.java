package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.AttributionPeriod;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttribution;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttributionRepository;
import java.sql.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLegalEntityAttributionRepository implements LegalEntityAttributionRepository {
    private final JdbcClient jdbc;

    JdbcLegalEntityAttributionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LegalEntityAttribution> find(AttributionSubject subject) {
        return jdbc.sql("""
                        select attribution_id, version, event_count from organization_workforce.legal_entity_attributions
                         where subject_kind = :kind and subject_id = :id
                        """)
                .param("kind", subject.kind().name()).param("id", subject.subjectKey())
                .query((rs, n) -> new long[] {rs.getLong("attribution_id"), rs.getLong("version"), rs.getLong("event_count")}).optional()
                .map(row -> LegalEntityAttribution.restore(row[0], subject, periods(row[0]), row[1], row[2]));
    }

    private List<AttributionPeriod> periods(long attributionId) {
        return jdbc.sql("""
                        select legal_entity_id, effective_from_date, effective_to_date
                          from organization_workforce.legal_entity_attribution_periods where attribution_id = :id order by effective_from_date
                        """)
                .param("id", attributionId)
                .query((rs, n) -> new AttributionPeriod(new LegalEntityId(rs.getLong("legal_entity_id")),
                        rs.getDate("effective_from_date").toLocalDate(),
                        rs.getDate("effective_to_date") == null ? null : rs.getDate("effective_to_date").toLocalDate())).list();
    }

    @Override
    public void save(LegalEntityAttribution attribution) {
        long id;
        if (attribution.storedRow() == null) {
            try {
                id = jdbc.sql("""
                                insert into organization_workforce.legal_entity_attributions (subject_kind, subject_id, version, event_count)
                                values (:kind, :subject, :version, :eventCount) returning attribution_id
                                """)
                        .param("kind", attribution.subject().kind().name()).param("subject", attribution.subject().subjectKey())
                        .param("version", attribution.versionAfterSave()).param("eventCount", attribution.eventSequenceAfterSave())
                        .query(Long.class).single();
            } catch (DuplicateKeyException saved) {
                throw conflict(attribution);
            }
        } else {
            id = attribution.storedRow();
            int updated = jdbc.sql("""
                            update organization_workforce.legal_entity_attributions
                               set version = :next, event_count = :eventCount, updated_at = now()
                             where attribution_id = :id and version = :version
                            """)
                    .param("next", attribution.versionAfterSave()).param("eventCount", attribution.eventSequenceAfterSave())
                    .param("id", id).param("version", attribution.version()).update();
            if (updated != 1) {
                throw conflict(attribution);
            }
        }
        List<AttributionPeriod> stored = periods(id);
        for (AttributionPeriod period : attribution.periods()) {
            AttributionPeriod existing = stored.stream().filter(p -> p.effectiveFrom().equals(period.effectiveFrom())).findFirst().orElse(null);
            if (existing == null) {
                insertPeriod(id, period);
            } else if (!existing.equals(period)) {
                updatePeriodEnd(id, period);
            }
        }
    }

    private void insertPeriod(long attributionId, AttributionPeriod period) {
        jdbc.sql("""
                        insert into organization_workforce.legal_entity_attribution_periods
                               (attribution_id, legal_entity_id, effective_from_date, effective_to_date)
                        values (:id, :legalEntity, :from, :to)
                        """)
                .param("id", attributionId).param("legalEntity", period.legalEntityId().value())
                .param("from", Date.valueOf(period.effectiveFrom()))
                .param("to", period.effectiveTo() == null ? null : Date.valueOf(period.effectiveTo()), java.sql.Types.DATE).update();
    }

    private void updatePeriodEnd(long attributionId, AttributionPeriod period) {
        jdbc.sql("""
                        update organization_workforce.legal_entity_attribution_periods set effective_to_date = :to, updated_at = now()
                         where attribution_id = :id and effective_from_date = :from
                        """)
                .param("id", attributionId).param("from", Date.valueOf(period.effectiveFrom())).param("to", Date.valueOf(period.effectiveTo())).update();
    }

    private static DomainException conflict(LegalEntityAttribution attribution) {
        AttributionKind kind = attribution.subject().kind();
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                "the attribution of " + kind + " " + attribution.subject().subjectKey() + " was changed by someone else");
    }
}
