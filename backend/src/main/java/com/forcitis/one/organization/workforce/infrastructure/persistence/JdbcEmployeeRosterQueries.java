package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.domain.AssignmentKind;
import com.forcitis.one.organization.workforce.domain.EmployeeRosterQueries;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEmployeeRosterQueries implements EmployeeRosterQueries {
    /**
     * As in OrgSnapshotRows, only employees without any versions may still be read from the snapshot during migration. A separation
     * ends every post on the separation day, so a separated person belongs to the units of their last posts (the day before).
     */
    private static final String CURRENT = """
            with employee_rows as (
                select e.id, e.employee_code, e.display_name,
                       case when v.versioned then ev.employment_status else e.employment_status end as employment_status,
                       ev.employment_start_date as hire_date,
                       case when ev.employment_status = 'SEPARATED' then ev.effective_from_date - 1 else :day end as posts_day
                  from organization_workforce.employees e
                  cross join lateral (select exists (select 1 from organization_workforce.employment_relationship_versions x where x.employee_id = e.id) as versioned) v
                  left join organization_workforce.employment_relationship_versions ev on v.versioned and ev.employee_id = e.id and ev.superseded_at is null
                       and ev.effective_from_date <= :day and (ev.effective_to_date is null or :day < ev.effective_to_date)
                 where not v.versioned or ev.employment_relationship_version_id is not null
            ), assignment_rows as (
                select av.employee_id, av.assignment_kind, av.org_unit_id, av.position_id, av.reports_to_employee_id
                  from organization_workforce.assignment_versions av
                  join employee_rows e on e.id = av.employee_id
                 where av.superseded_at is null
                   and ((av.effective_from_date <= :day and (av.effective_to_date is null or :day < av.effective_to_date))
                        or (av.effective_from_date <= e.posts_day and (av.effective_to_date is null or e.posts_day < av.effective_to_date)))
                union all
                select a.employee_id, 'PRIMARY', a.org_unit_id, a.position_id, a.reports_to_employee_id
                  from organization_workforce.assignments a
                 where not exists (select 1 from organization_workforce.employment_relationship_versions x where x.employee_id = a.employee_id)
                   and not exists (select 1 from organization_workforce.assignment_versions x where x.employee_id = a.employee_id)
            )
            """;

    private static final String VISIBLE = """
            , visible_rows as (
                select e.id, e.employee_code, e.display_name, e.employment_status, e.hire_date
                  from employee_rows e
                 where (cast(:status as text) is null or e.employment_status = :status)
                   and ((:everything and cast(:orgUnit as uuid) is null) or exists (
                       select 1 from assignment_rows a where a.employee_id = e.id
                         and (:everything or a.org_unit_id = any(:units))
                         and (cast(:orgUnit as uuid) is null or a.org_unit_id = :orgUnit)))
            )
            select id, employee_code, display_name, employment_status, hire_date from visible_rows
            """;

    private final JdbcClient jdbc;
    private final Clock clock;
    private final BusinessZone zone;

    JdbcEmployeeRosterQueries(JdbcClient jdbc, Clock clock, BusinessZone zone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public List<Row> list(Visible visible, @Nullable OrgUnitId orgUnit, @Nullable EmploymentStatus status, @Nullable EmployeeId after, int limit) {
        LocalDate day = zone.today(clock);
        var rows = visibleQuery(jdbc.sql(CURRENT + VISIBLE + """
                 where cast(:after as uuid) is null or employee_code > (select employee_code from visible_rows where id = :after)
                 order by employee_code limit :limit
                """), visible, orgUnit, status, day)
                .param("after", after == null ? null : after.value(), Types.OTHER).param("limit", limit)
                .query((rs, n) -> row(rs)).list();
        if (rows.isEmpty() && after != null && visibleQuery(jdbc.sql(CURRENT + VISIBLE + " where id = :id"), visible, orgUnit, status, day)
                .param("id", after.value()).query((rs, n) -> 1).list().isEmpty()) {
            // A cursor row that left the caller's result set between two pages must not look like the end of the list.
            throw new InvalidCursorException("the cursor row is no longer in the result");
        }
        return withAssignments(rows, day);
    }

    @Override
    public Optional<Row> find(Visible visible, EmployeeId employee) {
        LocalDate day = zone.today(clock);
        var rows = visibleQuery(jdbc.sql(CURRENT + VISIBLE + " where id = :id"), visible, null, null, day)
                .param("id", employee.value()).query((rs, n) -> row(rs)).list();
        return withAssignments(rows, day).stream().findFirst();
    }

    @Override
    public boolean exists(EmployeeId employee) {
        return jdbc.sql(CURRENT + "select exists(select 1 from employee_rows where id = :id)")
                .param("day", zone.today(clock)).param("id", employee.value()).query(Boolean.class).single();
    }

    private static JdbcClient.StatementSpec visibleQuery(JdbcClient.StatementSpec query, Visible visible, @Nullable OrgUnitId orgUnit,
                                                         @Nullable EmploymentStatus status, LocalDate day) {
        return query.param("day", day).param("everything", visible.everything())
                .param("units", visible.orgUnits().stream().map(OrgUnitId::value).toArray(UUID[]::new))
                .param("orgUnit", orgUnit == null ? null : orgUnit.value(), Types.OTHER)
                .param("status", status == null ? null : status.name(), Types.VARCHAR);
    }

    /** Fetch all assignments only for the employees already selected by the scoped SQL page. */
    private List<Row> withAssignments(List<Row> rows, LocalDate day) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<EmployeeId, List<AssignmentRow>> assignments = new HashMap<>();
        jdbc.sql(CURRENT + """
                select a.employee_id, a.assignment_kind, a.org_unit_id, coalesce(uv.name, u.name) as org_unit_name,
                       a.position_id, coalesce(pv.name, p.name) as position_name,
                       case when a.assignment_kind = 'PRIMARY' then a.reports_to_employee_id end as reports_to_employee_id,
                       case when a.assignment_kind = 'PRIMARY' then manager.display_name end as reports_to_display_name
                  from assignment_rows a
                  join organization_workforce.org_units u on u.id = a.org_unit_id
                  left join organization_workforce.org_unit_versions uv on uv.org_unit_id = u.id and uv.superseded_at is null
                       and uv.effective_from_date <= :day and (uv.effective_to_date is null or :day < uv.effective_to_date)
                  left join organization_workforce.positions p on p.id = a.position_id
                  left join organization_workforce.position_versions pv on pv.position_id = p.id and pv.superseded_at is null
                       and pv.effective_from_date <= :day and (pv.effective_to_date is null or :day < pv.effective_to_date)
                  left join organization_workforce.employees manager on manager.id = a.reports_to_employee_id
                 where a.employee_id in (:ids)
                 order by a.assignment_kind desc, u.code, p.code
                """).param("day", day).param("ids", rows.stream().map(row -> row.employeeId().value()).toList())
                .query((rs, n) -> {
                    EmployeeId employee = new EmployeeId(rs.getObject("employee_id", UUID.class));
                    assignments.computeIfAbsent(employee, ignored -> new ArrayList<>()).add(assignment(rs));
                    return employee;
                }).list();
        return rows.stream().map(row -> new Row(row.employeeId(), row.employeeCode(), row.displayName(), row.employmentStatus(), row.hireDate(),
                assignments.getOrDefault(row.employeeId(), List.of()))).toList();
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(new EmployeeId(rs.getObject("id", UUID.class)), rs.getString("employee_code"), rs.getString("display_name"),
                EmploymentStatus.valueOf(rs.getString("employment_status")), rs.getObject("hire_date", LocalDate.class), List.of());
    }

    private static AssignmentRow assignment(ResultSet rs) throws SQLException {
        UUID position = rs.getObject("position_id", UUID.class);
        UUID manager = rs.getObject("reports_to_employee_id", UUID.class);
        return new AssignmentRow(AssignmentKind.valueOf(rs.getString("assignment_kind")), new OrgUnitId(rs.getObject("org_unit_id", UUID.class)),
                rs.getString("org_unit_name"), position == null ? null : new PositionId(position), rs.getString("position_name"),
                manager == null ? null : new EmployeeId(manager), rs.getString("reports_to_display_name"));
    }
}
