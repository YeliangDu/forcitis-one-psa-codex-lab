package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.workforce.domain.InitialVersionMigrationRepository;
import com.forcitis.one.organization.workforce.domain.InitialVersionPlan;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcInitialVersionMigrationRepository implements InitialVersionMigrationRepository {
    private final JdbcClient jdbc;
    private final IdGenerator ids;
    private final BusinessZone zone;

    JdbcInitialVersionMigrationRepository(JdbcClient jdbc, IdGenerator ids, BusinessZone zone) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.zone = zone;
    }

    @Override
    public List<InitialVersionPlan.SnapshotPerson> lockEmployeesWithoutVersions() {
        // Commands lock the employee row without changing it, so a row that waited for the lock is not re-checked: lock first, then look again in a new statement.
        jdbc.sql("""
                select e.id from organization_workforce.employees e
                 where not exists (select 1 from organization_workforce.employee_versions ev where ev.employee_id = e.id)
                 order by e.created_at, e.id
                   for update of e
                """).query(UUID.class).list();
        return jdbc.sql("""
                select e.id, e.employment_status, e.is_fee_earner, e.separated_at,
                       (select min(v.effective_from_date) from organization_workforce.assignment_versions v
                         where v.employee_id = e.id and v.assignment_kind = 'PRIMARY' and v.superseded_at is null) as first_day,
                       (exists (select 1 from organization_workforce.assignments a where a.employee_id = e.id)
                        and not exists (select 1 from organization_workforce.assignment_versions v
                                         where v.employee_id = e.id and v.assignment_kind = 'PRIMARY' and v.superseded_at is null)) as missing_assignment_version
                  from organization_workforce.employees e
                 where not exists (select 1 from organization_workforce.employee_versions ev where ev.employee_id = e.id)
                 order by e.created_at, e.id
                """).query((rs, n) -> {
            OffsetDateTime separatedAt = rs.getObject("separated_at", OffsetDateTime.class);
            return new InitialVersionPlan.SnapshotPerson(rs.getObject("id", UUID.class), "EMPLOYED".equals(rs.getString("employment_status")),
                    rs.getBoolean("is_fee_earner"), separatedAt == null ? null : zone.dateOf(separatedAt.toInstant()),
                    rs.getObject("first_day", LocalDate.class), rs.getBoolean("missing_assignment_version"));
        }).list();
    }

    @Override
    public long countEmployeesWithVersions() {
        return jdbc.sql("select count(distinct employee_id) from organization_workforce.employee_versions").query(Long.class).single();
    }

    @Override
    public long countAssignmentConflicts(LocalDate today) {
        return jdbc.sql("""
                select count(distinct a.employee_id)
                  from organization_workforce.assignments a
                  join organization_workforce.assignment_versions v on v.employee_id = a.employee_id and v.assignment_kind = 'PRIMARY' and v.superseded_at is null
                   and v.effective_from_date <= :today and (v.effective_to_date is null or v.effective_to_date > :today)
                 where a.org_unit_id <> v.org_unit_id or a.position_id is distinct from v.position_id
                    or a.reports_to_employee_id is distinct from v.reports_to_employee_id
                """).param("today", today).query(Long.class).single();
    }

    @Override
    public void insertEmployeeVersion(EmployeeId employee, InitialVersionPlan.Row row, Instant at) {
        OffsetDateTime stamp = OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
        jdbc.sql("""
                insert into organization_workforce.employee_versions (employee_version_id, employee_id, effective_from_date, effective_to_date, revision_number,
                       employment_status, employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind,
                       change_channel_kind, closed_at, created_at, updated_at)
                values (:id, :employee, :from, :to, 0, :status, :phase, false, :feeEarner, null, :startBasis, 'MIGRATION', :closedAt, :at, :at)
                """)
                .param("id", ids.next()).param("employee", employee.value()).param("from", row.from()).param("to", row.to(), Types.DATE)
                .param("status", row.employed() ? "EMPLOYED" : "SEPARATED").param("phase", row.employed() ? "REGULAR" : null, Types.VARCHAR)
                .param("feeEarner", row.feeEarner()).param("startBasis", row.employed() ? "UNKNOWN" : null, Types.VARCHAR)
                .param("closedAt", row.to() == null ? null : stamp, Types.TIMESTAMP_WITH_TIMEZONE).param("at", stamp).update();
    }

    @Override
    public void insertAssignmentVersionFromSnapshot(EmployeeId employee, LocalDate from, Instant at) {
        OffsetDateTime stamp = OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
        UUID root = PrimaryAssignmentRoot.of(jdbc, ids, employee.value(), at);
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date,
                       effective_to_date, org_unit_id, position_id, reports_to_employee_id, revision_number, change_channel_kind, created_at, updated_at)
                select :id, a.employee_id, :root, 'PRIMARY', :from, null, a.org_unit_id, a.position_id, a.reports_to_employee_id,
                       coalesce((select max(v.revision_number) + 1 from organization_workforce.assignment_versions v
                                  where v.employee_id = a.employee_id and v.effective_from_date = :from), 0),
                       'MIGRATION', :at, :at
                  from organization_workforce.assignments a where a.employee_id = :employee
                """)
                .param("id", ids.next()).param("root", root).param("employee", employee.value()).param("from", from).param("at", stamp).update();
    }
}
