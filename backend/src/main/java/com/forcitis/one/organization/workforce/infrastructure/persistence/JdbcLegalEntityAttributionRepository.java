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
                .map(row -> LegalEntityAttribution.restore(row[0], subject, periods(subject), row[1], row[2]));
    }

    private void updatePeriodEnd(AttributionSubject subject, AttributionPeriod period) {
        String sql = subject.kind() == AttributionKind.ORG_UNIT
                ? "update organization_workforce.org_unit_legal_entities set effective_to_date = :to, updated_at = now() where org_unit_id = :id and effective_from_date = :from"
                : "update organization_workforce.employee_legal_entities set effective_to_date = :to, updated_at = now() where employee_id = :id and effective_from_date = :from";
        jdbc.sql(sql).param("id", subject.subjectKey()).param("from", Date.valueOf(period.effectiveFrom()))
                .param("to", period.effectiveTo() == null ? null : Date.valueOf(period.effectiveTo()), java.sql.Types.DATE).update();
    }

    private void deletePeriod(AttributionSubject subject, AttributionPeriod period) {
        String sql = subject.kind() == AttributionKind.ORG_UNIT
                ? "delete from organization_workforce.org_unit_legal_entities where org_unit_id = :id and effective_from_date = :from"
                : "delete from organization_workforce.employee_legal_entities where employee_id = :id and effective_from_date = :from";
        jdbc.sql(sql).param("id", subject.subjectKey()).param("from", Date.valueOf(period.effectiveFrom())).update();
    }

    private void writePeriods(LegalEntityAttribution attribution) {
        AttributionSubject subject = attribution.subject();
        java.util.Map<java.time.LocalDate, AttributionPeriod> stored = new java.util.HashMap<>();
        for (AttributionPeriod period : periods(subject)) {
            stored.put(period.effectiveFrom(), period);
        }
        for (AttributionPeriod period : attribution.periods()) {
            AttributionPeriod before = stored.remove(period.effectiveFrom());
            if (before == null) {
                insertPeriod(subject, period);
            } else if (!before.equals(period)) {
                if (!before.legalEntityId().equals(period.legalEntityId())) {
                    deletePeriod(subject, before);
                    insertPeriod(subject, period);
                } else {
                    updatePeriodEnd(subject, period);
                }
            }
        }
        stored.values().forEach(removed -> deletePeriod(subject, removed));
    }

    private void insertPeriod(AttributionSubject subject, AttributionPeriod period) {
        String sql = subject.kind() == AttributionKind.ORG_UNIT
                ? "insert into organization_workforce.org_unit_legal_entities (org_unit_id, legal_entity_id, effective_from_date, effective_to_date) values (:id, :legalEntity, :from, :to)"
                : "insert into organization_workforce.employee_legal_entities (employee_id, legal_entity_id, effective_from_date, effective_to_date) values (:id, :legalEntity, :from, :to)";
        jdbc.sql(sql).param("id", subject.subjectKey()).param("legalEntity", period.legalEntityId().value())
                .param("from", Date.valueOf(period.effectiveFrom()))
                .param("to", period.effectiveTo() == null ? null : Date.valueOf(period.effectiveTo()), java.sql.Types.DATE).update();
    }

    private List<AttributionPeriod> periods(AttributionSubject subject) {
        return jdbc.sql("select legal_entity_id, effective_from_date, effective_to_date from organization_workforce." + table(subject)
                        + " where " + column(subject) + " = :id")
                .param("id", subject.subjectKey())
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
        writePeriods(attribution);
    }

    private static String table(AttributionSubject subject) {
        return subject.kind() == AttributionKind.ORG_UNIT ? "org_unit_legal_entities" : "employee_legal_entities";
    }

    private static String column(AttributionSubject subject) {
        return subject.kind() == AttributionKind.ORG_UNIT ? "org_unit_id" : "employee_id";
    }

    private static DomainException conflict(LegalEntityAttribution attribution) {
        AttributionKind kind = attribution.subject().kind();
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                "the attribution of " + kind + " " + attribution.subject().subjectKey() + " was changed by someone else");
    }
}
