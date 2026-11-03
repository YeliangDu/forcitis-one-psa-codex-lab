package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.AttributionKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLegalEntityResolutionSources implements LegalEntityResolutionSources {
    private final JdbcClient jdbc;

    JdbcLegalEntityResolutionSources(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LegalEntityId> employeeAttribution(EmployeeId employee, LocalDate date) {
        return attribution(AttributionKind.EMPLOYEE, employee.value(), date);
    }

    @Override
    public Optional<LegalEntityId> orgUnitAttribution(OrgUnitId orgUnit, LocalDate date) {
        return attribution(AttributionKind.ORG_UNIT, orgUnit.value(), date);
    }

    private Optional<LegalEntityId> attribution(AttributionKind kind, UUID subjectId, LocalDate date) {
        return jdbc.sql("""
                select legal_entity_id from (
                """ + EffectiveAttributionPeriods.select(kind) + """
                ) effective
                 where effective_from_date <= :date and (effective_to_date is null or :date < effective_to_date)
                """)
                .param("id", subjectId).param("date", date)
                .query(UUID.class).optional().map(LegalEntityId::new);
    }

    @Override
    public Optional<OrgUnitId> assignedUnit(EmployeeId employee, LocalDate date) {
        return jdbc.sql("""
                        select org_unit_id from organization_workforce.assignment_versions
                         where employee_id = :id and superseded_at is null and effective_from_date <= :date
                           and (effective_to_date is null or :date < effective_to_date)
                        """)
                .param("id", employee.value()).param("date", date).query(UUID.class).optional().map(OrgUnitId::new);
    }

    @Override
    public Optional<OrgUnitId> parentUnit(OrgUnitId orgUnit, LocalDate date) {
        return jdbc.sql("""
                        select parent_org_unit_id from organization_workforce.org_unit_versions
                         where org_unit_id = :id and superseded_at is null and effective_from_date <= :date and (effective_to_date is null or :date < effective_to_date)
                           and parent_org_unit_id is not null
                        """)
                .param("id", orgUnit.value()).param("date", date).query(UUID.class).optional().map(OrgUnitId::new);
    }
}
