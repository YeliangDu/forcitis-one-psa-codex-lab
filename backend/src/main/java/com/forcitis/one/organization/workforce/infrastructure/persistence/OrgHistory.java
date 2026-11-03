package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.Assignment;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
    private final IdGenerator ids;

    OrgHistory(JdbcClient jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    void recordAssignments(List<Assignment> assignments, LocalDate day, Instant now) {
        if (assignments.isEmpty()) {
            return;
        }
        record Values(OrgUnitId orgUnit, UUID position, UUID reportsTo, Open open) {
        }
        Map<UUID, Values> open = new HashMap<>();
        jdbc.sql("""
                        select employee_id, effective_from_date, revision_number, org_unit_id, position_id, reports_to_employee_id
                          from organization_workforce.assignment_versions
                         where effective_to_date is null and superseded_at is null and assignment_kind = 'PRIMARY' and employee_id in (:ids)
                        """)
                .param("ids", assignments.stream().map(a -> a.employeeId().value()).toList())
                .query((rs, n) -> open.put(rs.getObject("employee_id", UUID.class), new Values(new OrgUnitId(rs.getObject("org_unit_id", UUID.class)),
                        rs.getObject("position_id", UUID.class), rs.getObject("reports_to_employee_id", UUID.class),
                        new Open(rs.getObject("effective_from_date", LocalDate.class), rs.getInt("revision_number"))))).list();
        for (Assignment a : assignments) {
            Values before = open.get(a.employeeId().value());
            UUID position = a.positionId() == null ? null : a.positionId().value();
            UUID reportsTo = a.reportsToEmployeeId() == null ? null : a.reportsToEmployeeId().value();
            if (before != null && before.orgUnit().equals(a.orgUnitId()) && Objects.equals(before.position(), position) && Objects.equals(before.reportsTo(), reportsTo)) {
                continue;
            }
            int revision = revisionFor(before == null ? null : before.open(), day);
            UUID root = PrimaryAssignmentRoot.of(jdbc, ids, a.employeeId().value(), now);
            if (before != null) {
                jdbc.sql("""
                        update organization_workforce.assignment_versions set effective_to_date = :day, closed_at = :closedAt
                         where assignment_id = :root and effective_to_date is null and superseded_at is null
                        """)
                        .param("root", root).param("day", day).param("closedAt", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).update();
            }
            jdbc.sql("""
                            insert into organization_workforce.assignment_versions (assignment_version_id, employee_id, assignment_id, assignment_kind,
                                   effective_from_date, org_unit_id, position_id, reports_to_employee_id, revision_number)
                            values (:versionId, :id, :root, 'PRIMARY', :day, :orgUnit, :position, :reportsTo, :revision)
                            """)
                    .param("versionId", ids.next()).param("id", a.employeeId().value()).param("root", root).param("day", day).param("orgUnit", a.orgUnitId().value())
                    .param("position", position, java.sql.Types.OTHER).param("reportsTo", reportsTo, java.sql.Types.OTHER)
                    .param("revision", revision).update();
        }
    }

    private static int revisionFor(Open open, LocalDate day) {
        return open == null || !open.from().equals(day) ? 0 : open.revision() + 1;
    }
}
