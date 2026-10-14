package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLegalEntityResolutionSources implements LegalEntityResolutionSources {
    private final JdbcClient jdbc;

    JdbcLegalEntityResolutionSources(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LegalEntityId> employeeAttribution(UserId employee, LocalDate date) {
        return attribution("employee_legal_entities", "employee_id", employee.value(), date);
    }

    @Override
    public Optional<LegalEntityId> orgUnitAttribution(OrgUnitId orgUnit, LocalDate date) {
        return attribution("org_unit_legal_entities", "org_unit_id", orgUnit.value(), date);
    }

    private Optional<LegalEntityId> attribution(String table, String column, long subjectId, LocalDate date) {
        return jdbc.sql("select legal_entity_id from organization_workforce." + table + " where " + column + " = :id"
                        + " and effective_from_date <= :date and (effective_to_date is null or :date < effective_to_date)")
                .param("id", subjectId).param("date", Date.valueOf(date))
                .query(Long.class).optional().map(LegalEntityId::new);
    }

    @Override
    public Optional<OrgUnitId> assignedUnit(UserId employee, LocalDate date) {
        return jdbc.sql("""
                        select org_unit_id from organization_workforce.assignment_versions
                         where employee_id = :id and effective_from_date <= :date and (effective_to_date is null or :date < effective_to_date)
                        """)
                .param("id", employee.value()).param("date", Date.valueOf(date)).query(Long.class).optional().map(OrgUnitId::new);
    }

    @Override
    public Optional<OrgUnitId> parentUnit(OrgUnitId orgUnit, LocalDate date) {
        return jdbc.sql("""
                        select parent_org_unit_id from organization_workforce.org_unit_versions
                         where org_unit_id = :id and effective_from_date <= :date and (effective_to_date is null or :date < effective_to_date)
                           and parent_org_unit_id is not null
                        """)
                .param("id", orgUnit.value()).param("date", Date.valueOf(date)).query(Long.class).optional().map(OrgUnitId::new);
    }
}
