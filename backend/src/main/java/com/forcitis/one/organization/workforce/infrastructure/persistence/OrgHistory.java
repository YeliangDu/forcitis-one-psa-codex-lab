package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.Assignment;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Keeps the version rows in step with the snapshot, in the snapshot's transaction. The open versions of all changed rows are read in one
 * query; a row whose values did not change gets nothing. A change closes the open version at the day and appends the next one; a second
 * change on the same day closes that day's version (an empty range) and appends the next revision.
 */
final class OrgHistory {
    private record Open(LocalDate from, int revision) {
    }

    private final JdbcClient jdbc;

    OrgHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void recordOrgUnits(List<OrgUnit> units, LocalDate day) {
        if (units.isEmpty()) {
            return;
        }
        record Values(Long parent, Long head, Long manager, String code, String name, String status, String category, boolean delivery, Open open) {
        }
        Map<Long, Values> open = new HashMap<>();
        jdbc.sql("""
                        select org_unit_id, effective_from_date, revision_number, code, name, parent_org_unit_id, status, head_employee_id,
                               regional_receivables_manager_id, region_kind, is_delivery_unit
                          from organization_workforce.org_unit_versions where effective_to_date is null and org_unit_id in (:ids)
                        """)
                .param("ids", units.stream().map(u -> u.orgUnitId().value()).toList())
                .query((rs, n) -> open.put(rs.getLong("org_unit_id"), new Values((Long) rs.getObject("parent_org_unit_id"), (Long) rs.getObject("head_employee_id"),
                        (Long) rs.getObject("regional_receivables_manager_id"), rs.getString("code"), rs.getString("name"), rs.getString("status"),
                        rs.getString("region_kind"), rs.getBoolean("is_delivery_unit"),
                        new Open(rs.getDate("effective_from_date").toLocalDate(), rs.getInt("revision_number"))))).list();
        for (OrgUnit u : units) {
            Values before = open.get(u.orgUnitId().value());
            Long parent = u.parentOrgUnitId() == null ? null : u.parentOrgUnitId().value();
            Long head = u.headEmployeeId() == null ? null : u.headEmployeeId().value();
            Long manager = u.regionalReceivablesManagerId() == null ? null : u.regionalReceivablesManagerId().value();
            String category = u.regionCategory() == null ? null : u.regionCategory().name();
            if (before != null && Objects.equals(before.parent(), parent) && Objects.equals(before.head(), head) && Objects.equals(before.manager(), manager)
                    && Objects.equals(before.code(), u.code()) && before.name().equals(u.name()) && before.status().equals(u.status().name())
                    && Objects.equals(before.category(), category) && before.delivery() == u.deliveryUnit()) {
                continue;
            }
            int revision = revisionFor(before == null ? null : before.open(), day);
            if (before != null) {
                jdbc.sql("update organization_workforce.org_unit_versions set effective_to_date = :day where org_unit_id = :id and effective_to_date is null")
                        .param("id", u.orgUnitId().value()).param("day", Date.valueOf(day)).update();
            }
            jdbc.sql("""
                            insert into organization_workforce.org_unit_versions (org_unit_id, effective_from_date, code, name, parent_org_unit_id,
                                   status, head_employee_id, regional_receivables_manager_id, region_kind, is_delivery_unit, revision_number)
                            values (:id, :day, :code, :name, :parent, :status, :head, :manager, :category, :delivery, :revision)
                            """)
                    .param("id", u.orgUnitId().value()).param("day", Date.valueOf(day)).param("code", u.code()).param("name", u.name())
                    .param("parent", parent, java.sql.Types.BIGINT).param("status", u.status().name()).param("head", head, java.sql.Types.BIGINT)
                    .param("manager", manager, java.sql.Types.BIGINT).param("category", category, java.sql.Types.VARCHAR)
                    .param("delivery", u.deliveryUnit()).param("revision", revision).update();
        }
    }

    void recordAssignments(List<Assignment> assignments, LocalDate day) {
        if (assignments.isEmpty()) {
            return;
        }
        record Values(OrgUnitId orgUnit, Long position, Long reportsTo, Open open) {
        }
        Map<Long, Values> open = new HashMap<>();
        jdbc.sql("""
                        select employee_id, effective_from_date, revision_number, org_unit_id, position_id, reports_to_employee_id
                          from organization_workforce.assignment_versions where effective_to_date is null and employee_id in (:ids)
                        """)
                .param("ids", assignments.stream().map(a -> a.employeeId().value()).toList())
                .query((rs, n) -> open.put(rs.getLong("employee_id"), new Values(new OrgUnitId(rs.getLong("org_unit_id")), (Long) rs.getObject("position_id"),
                        (Long) rs.getObject("reports_to_employee_id"),
                        new Open(rs.getDate("effective_from_date").toLocalDate(), rs.getInt("revision_number"))))).list();
        for (Assignment a : assignments) {
            Values before = open.get(a.employeeId().value());
            Long position = a.positionId() == null ? null : a.positionId().value();
            Long reportsTo = a.reportsToEmployeeId() == null ? null : a.reportsToEmployeeId().value();
            if (before != null && before.orgUnit().equals(a.orgUnitId()) && Objects.equals(before.position(), position) && Objects.equals(before.reportsTo(), reportsTo)) {
                continue;
            }
            int revision = revisionFor(before == null ? null : before.open(), day);
            if (before != null) {
                jdbc.sql("update organization_workforce.assignment_versions set effective_to_date = :day where employee_id = :id and effective_to_date is null")
                        .param("id", a.employeeId().value()).param("day", Date.valueOf(day)).update();
            }
            jdbc.sql("""
                            insert into organization_workforce.assignment_versions (employee_id, effective_from_date, org_unit_id, position_id,
                                   reports_to_employee_id, revision_number)
                            values (:id, :day, :orgUnit, :position, :reportsTo, :revision)
                            """)
                    .param("id", a.employeeId().value()).param("day", Date.valueOf(day)).param("orgUnit", a.orgUnitId().value())
                    .param("position", position, java.sql.Types.BIGINT).param("reportsTo", reportsTo, java.sql.Types.BIGINT)
                    .param("revision", revision).update();
        }
    }

    private static int revisionFor(Open open, LocalDate day) {
        return open == null || !open.from().equals(day) ? 0 : open.revision() + 1;
    }
}
