package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.AttributionId;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.api.AttributionSubject;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.AttributionCorrection;
import com.forcitis.one.organization.workforce.domain.AttributionPeriod;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttribution;
import com.forcitis.one.organization.workforce.domain.LegalEntityAttributionRepository;
import java.sql.Types;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLegalEntityAttributionRepository implements LegalEntityAttributionRepository {
    private final JdbcClient jdbc;
    private final IdGenerator ids;
    private final Clock clock;

    JdbcLegalEntityAttributionRepository(JdbcClient jdbc, IdGenerator ids, Clock clock) {
        this.clock = clock;
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public Optional<LegalEntityAttribution> find(AttributionSubject subject) {
        return jdbc.sql("""
                select attribution_id, version, event_count from organization_workforce.legal_entity_attributions where
                """ + EffectiveAttributionPeriods.subjectColumn(subject.kind()) + """
                 = :id
                """)
                .param("id", subject.subjectId())
                .query((rs, n) -> LegalEntityAttribution.restore(new AttributionId(rs.getObject("attribution_id", UUID.class)), subject, periods(subject),
                        rs.getLong("version"), rs.getLong("event_count"))).optional();
    }

    private void updatePeriodEnd(AttributionSubject subject, AttributionPeriod period) {
        String sql = subject.kind() == AttributionKind.ORG_UNIT
                ? """
                  update organization_workforce.org_unit_legal_entities set effective_to_date = :to, closed_at = :updatedAt, updated_at = :updatedAt
                   where org_unit_id = :id and effective_from_date = :from and superseded_at is null
                  """
                : """
                  update organization_workforce.employee_legal_entities set effective_to_date = :to, closed_at = :updatedAt, updated_at = :updatedAt
                   where employee_id = :id and effective_from_date = :from and superseded_at is null
                  """;
        jdbc.sql(sql).param("id", subject.subjectId()).param("from", period.effectiveFrom())
                .param("to", period.effectiveTo(), java.sql.Types.DATE)
                .param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)).update();
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
                    throw new IllegalStateException("a period never changes its legal entity: it is closed and a new one is appended");
                }
                updatePeriodEnd(subject, period);
            }
        }
        if (!stored.isEmpty()) {
            throw new IllegalStateException("a period is never removed: " + stored.keySet());
        }
    }

    private void insertPeriod(AttributionSubject subject, AttributionPeriod period) {
        String sql = subject.kind() == AttributionKind.ORG_UNIT
                ? """
                  insert into organization_workforce.org_unit_legal_entities (org_unit_legal_entity_id, org_unit_id, legal_entity_id, effective_from_date, effective_to_date, closed_at)
                  values (:rowId, :id, :legalEntity, :from, :to, :closedAt)
                  """
                : """
                  insert into organization_workforce.employee_legal_entities (employee_legal_entity_id, employee_id, legal_entity_id, effective_from_date, effective_to_date, closed_at)
                  values (:rowId, :id, :legalEntity, :from, :to, :closedAt)
                  """;
        jdbc.sql(sql).param("rowId", ids.next()).param("id", subject.subjectId()).param("legalEntity", period.legalEntityId().value())
                .param("from", period.effectiveFrom())
                .param("to", period.effectiveTo(), java.sql.Types.DATE)
                .param("closedAt", period.effectiveTo() == null ? null : OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE).update();
    }

    private List<AttributionPeriod> periods(AttributionSubject subject) {
        return jdbc.sql(EffectiveAttributionPeriods.select(subject.kind()))
                .param("id", subject.subjectId())
                .query((rs, n) -> new AttributionPeriod(new LegalEntityId(rs.getObject("legal_entity_id", UUID.class)),
                        rs.getObject("effective_from_date", LocalDate.class),
                        rs.getObject("effective_to_date", LocalDate.class))).list();
    }

    @Override
    public void save(LegalEntityAttribution attribution) {
        if (attribution.version() == 0) {
            try {
                jdbc.sql("""
                                insert into organization_workforce.legal_entity_attributions (attribution_id, org_unit_id, employee_id, version, event_count)
                                values (:id, :orgUnit, :employee, :version, :eventCount)
                                """)
                        .param("id", attribution.id().value())
                        .param("orgUnit", orgUnitOf(attribution.subject()), Types.OTHER).param("employee", employeeOf(attribution.subject()), Types.OTHER)
                        .param("version", attribution.versionAfterSave()).param("eventCount", attribution.eventSequenceAfterSave()).update();
            } catch (DuplicateKeyException saved) {
                throw conflict(attribution);
            }
        } else {
            int updated = jdbc.sql("""
                            update organization_workforce.legal_entity_attributions
                               set version = :next, event_count = :eventCount, updated_at = :updatedAt
                             where attribution_id = :id and version = :version
                            """)
                    .param("next", attribution.versionAfterSave()).param("eventCount", attribution.eventSequenceAfterSave())
                    .param("id", attribution.id().value()).param("version", attribution.version())
                    .param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)).update();
            if (updated != 1) {
                throw conflict(attribution);
            }
        }
        insertCorrections(attribution);
        writePeriods(attribution);
    }

    private void insertCorrections(LegalEntityAttribution attribution) {
        for (AttributionCorrection correction : attribution.pendingCorrections()) {
            jdbc.sql("""
                    insert into organization_workforce.legal_entity_attribution_corrections (correction_id, org_unit_id, employee_id,
                           period_effective_from_date, previous_effective_to_date, new_effective_to_date, correction_reason, corrected_by_user_id)
                    values (:id, :orgUnit, :employee, :from, :previous, :next, :reason, :by)
                    """)
                    .param("id", ids.next())
                    .param("orgUnit", orgUnitOf(attribution.subject()), Types.OTHER).param("employee", employeeOf(attribution.subject()), Types.OTHER)
                    .param("from", correction.periodFrom()).param("previous", correction.previousEffectiveTo())
                    .param("next", correction.newEffectiveTo()).param("reason", correction.reason().value())
                    .param("by", correction.correctedBy().value()).update();
        }
    }

    private static UUID orgUnitOf(AttributionSubject subject) {
        return subject.orgUnit() == null ? null : subject.orgUnit().value();
    }

    private static UUID employeeOf(AttributionSubject subject) {
        return subject.employee() == null ? null : subject.employee().value();
    }

    private static DomainException conflict(LegalEntityAttribution attribution) {
        AttributionKind kind = attribution.subject().kind();
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                "the attribution of " + kind + " " + attribution.subject().subjectId() + " was changed by someone else");
    }
}
