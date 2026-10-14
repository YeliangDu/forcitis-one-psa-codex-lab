package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.organization.workforce.domain.Assignment;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Keeps the version rows in step with the snapshot, in the snapshot's transaction; a second change on the same day replaces that day's version. */
final class OrgHistory {
    private final JdbcClient jdbc;

    OrgHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void recordOrgUnit(OrgUnit u, LocalDate day) {
        record Open(LocalDate from, boolean same) {
        }
        List<Open> open = jdbc.sql("""
                        select effective_from_date,
                               (code is not distinct from :code and name = :name and parent_org_unit_id is not distinct from :parent
                                and status = :status and head_employee_id is not distinct from :head
                                and regional_receivables_manager_id is not distinct from :manager
                                and region_kind is not distinct from :category and is_delivery_unit = :delivery) as same
                          from organization_workforce.org_unit_versions where org_unit_id = :id and effective_to_date is null
                        """)
                .param("id", u.orgUnitId().value()).param("code", u.code()).param("name", u.name())
                .param("parent", u.parentOrgUnitId() == null ? null : u.parentOrgUnitId().value(), java.sql.Types.BIGINT)
                .param("status", u.status().name())
                .param("head", u.headEmployeeId() == null ? null : u.headEmployeeId().value(), java.sql.Types.BIGINT)
                .param("manager", u.regionalReceivablesManagerId() == null ? null : u.regionalReceivablesManagerId().value(), java.sql.Types.BIGINT)
                .param("category", u.regionCategory() == null ? null : u.regionCategory().name(), java.sql.Types.VARCHAR)
                .param("delivery", u.deliveryUnit())
                .query((rs, n) -> new Open(rs.getDate("effective_from_date").toLocalDate(), rs.getBoolean("same"))).list();
        if (!open.isEmpty() && open.getFirst().same()) {
            return;
        }
        if (!open.isEmpty() && open.getFirst().from().equals(day)) {
            jdbc.sql("delete from organization_workforce.org_unit_versions where org_unit_id = :id and effective_from_date = :day")
                    .param("id", u.orgUnitId().value()).param("day", Date.valueOf(day)).update();
        } else if (!open.isEmpty()) {
            jdbc.sql("update organization_workforce.org_unit_versions set effective_to_date = :day where org_unit_id = :id and effective_to_date is null")
                    .param("id", u.orgUnitId().value()).param("day", Date.valueOf(day)).update();
        }
        jdbc.sql("""
                        insert into organization_workforce.org_unit_versions (org_unit_id, effective_from_date, code, name, parent_org_unit_id,
                               status, head_employee_id, regional_receivables_manager_id, region_kind, is_delivery_unit)
                        values (:id, :day, :code, :name, :parent, :status, :head, :manager, :category, :delivery)
                        """)
                .param("id", u.orgUnitId().value()).param("day", Date.valueOf(day)).param("code", u.code()).param("name", u.name())
                .param("parent", u.parentOrgUnitId() == null ? null : u.parentOrgUnitId().value(), java.sql.Types.BIGINT)
                .param("status", u.status().name())
                .param("head", u.headEmployeeId() == null ? null : u.headEmployeeId().value(), java.sql.Types.BIGINT)
                .param("manager", u.regionalReceivablesManagerId() == null ? null : u.regionalReceivablesManagerId().value(), java.sql.Types.BIGINT)
                .param("category", u.regionCategory() == null ? null : u.regionCategory().name(), java.sql.Types.VARCHAR)
                .param("delivery", u.deliveryUnit()).update();
    }

    void recordAssignment(Assignment a, LocalDate day) {
        record Open(LocalDate from, boolean same) {
        }
        List<Open> open = jdbc.sql("""
                        select effective_from_date,
                               (org_unit_id = :orgUnit and position_id is not distinct from :position
                                and reports_to_employee_id is not distinct from :reportsTo) as same
                          from organization_workforce.assignment_versions where employee_id = :id and effective_to_date is null
                        """)
                .param("id", a.employeeId().value()).param("orgUnit", a.orgUnitId().value())
                .param("position", a.positionId() == null ? null : a.positionId().value(), java.sql.Types.BIGINT)
                .param("reportsTo", a.reportsToEmployeeId() == null ? null : a.reportsToEmployeeId().value(), java.sql.Types.BIGINT)
                .query((rs, n) -> new Open(rs.getDate("effective_from_date").toLocalDate(), rs.getBoolean("same"))).list();
        if (!open.isEmpty() && open.getFirst().same()) {
            return;
        }
        if (!open.isEmpty() && open.getFirst().from().equals(day)) {
            jdbc.sql("delete from organization_workforce.assignment_versions where employee_id = :id and effective_from_date = :day")
                    .param("id", a.employeeId().value()).param("day", Date.valueOf(day)).update();
        } else if (!open.isEmpty()) {
            jdbc.sql("update organization_workforce.assignment_versions set effective_to_date = :day where employee_id = :id and effective_to_date is null")
                    .param("id", a.employeeId().value()).param("day", Date.valueOf(day)).update();
        }
        jdbc.sql("""
                        insert into organization_workforce.assignment_versions (employee_id, effective_from_date, org_unit_id, position_id,
                               reports_to_employee_id)
                        values (:id, :day, :orgUnit, :position, :reportsTo)
                        """)
                .param("id", a.employeeId().value()).param("day", Date.valueOf(day)).param("orgUnit", a.orgUnitId().value())
                .param("position", a.positionId() == null ? null : a.positionId().value(), java.sql.Types.BIGINT)
                .param("reportsTo", a.reportsToEmployeeId() == null ? null : a.reportsToEmployeeId().value(), java.sql.Types.BIGINT).update();
    }
}
