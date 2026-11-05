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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
                 where not exists (select 1 from organization_workforce.employment_relationship_versions ev where ev.employee_id = e.id)
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
                 where not exists (select 1 from organization_workforce.employment_relationship_versions ev where ev.employee_id = e.id)
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
        return jdbc.sql("select count(distinct employee_id) from organization_workforce.employment_relationship_versions").query(Long.class).single();
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
                insert into organization_workforce.employment_relationship_versions (employment_relationship_version_id, employment_relationship_id, employee_id, effective_from_date, effective_to_date, revision_number,
                       employment_status, employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind,
                       change_channel_kind, closed_at, created_at, updated_at)
                values (:id, :relationship, :employee, :from, :to, 0, :status, :phase, false, :feeEarner, null, :startBasis, 'MIGRATION', :closedAt, :at, :at)
                """)
                .param("id", ids.next()).param("relationship", relationshipOf(employee, row.from(), at)).param("employee", employee.value())
                .param("from", row.from()).param("to", row.to(), Types.DATE)
                .param("status", row.employed() ? "EMPLOYED" : "SEPARATED").param("phase", row.employed() ? "REGULAR" : null, Types.VARCHAR)
                .param("feeEarner", row.feeEarner()).param("startBasis", row.employed() ? "UNKNOWN" : null, Types.VARCHAR)
                .param("closedAt", row.to() == null ? null : stamp, Types.TIMESTAMP_WITH_TIMEZONE).param("at", stamp).update();
    }

    @Override
    public void insertAssignmentVersionFromSnapshot(EmployeeId employee, LocalDate from, Instant at) {
        OffsetDateTime stamp = OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
        UUID root = PrimaryAssignmentRoot.of(jdbc, ids, employee.value(), at);
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, employment_relationship_id, employee_id, assignment_id, assignment_kind, effective_from_date,
                       effective_to_date, org_unit_id, position_id, reports_to_employee_id, revision_number, change_channel_kind, created_at, updated_at)
                select :id, :relationship, a.employee_id, :root, 'PRIMARY', :from, null, a.org_unit_id, a.position_id, a.reports_to_employee_id,
                       coalesce((select max(v.revision_number) + 1 from organization_workforce.assignment_versions v
                                  where v.employee_id = a.employee_id and v.effective_from_date = :from), 0),
                       'MIGRATION', :at, :at
                  from organization_workforce.assignments a where a.employee_id = :employee
                """)
                .param("id", ids.next()).param("relationship", relationshipOf(employee, from, at)).param("root", root).param("employee", employee.value())
                .param("from", from).param("at", stamp).update();
    }

    /**
     * The person's one relationship; a snapshot person has none yet, so it is made here with the employer the attribution names on the
     * first day: the person's own legal entity, else the nearest one up the primary org unit's parents (the rule of LegalEntityResolution).
     * Nobody is guessed: the caller skips a person without attribution (see hasEmployerOn) and counts them.
     */
    private UUID relationshipOf(EmployeeId employee, LocalDate firstDay, Instant at) {
        List<UUID> found = jdbc.sql("select employment_relationship_id from organization_workforce.employment_relationships where employee_id = :employee")
                .param("employee", employee.value()).query(UUID.class).list();
        if (!found.isEmpty()) {
            return found.getFirst();
        }
        UUID employer = employerOn(employee.value(), firstDay);
        if (employer == null) {
            throw new IllegalStateException("initial version migration: a person has no legal entity attribution on the first day; attribute them first, no employer is guessed");
        }
        return insertRelationship(employee, employer, at);
    }

    private UUID insertRelationship(EmployeeId employee, UUID employer, Instant at) {
        UUID id = ids.next();
        OffsetDateTime stamp = OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
        jdbc.sql("""
                insert into organization_workforce.employment_relationships (employment_relationship_id, employee_id, employer_legal_entity_id, employer_source_kind, created_at, updated_at)
                values (:id, :employee, :employer, 'MIGRATION_ATTRIBUTION', :at, :at)
                """).param("id", id).param("employee", employee.value()).param("employer", employer).param("at", stamp).update();
        return id;
    }

    @Override
    public boolean hasEmployerOn(EmployeeId employee, LocalDate firstDay) {
        return !jdbc.sql("select 1 from organization_workforce.employment_relationships where employee_id = :employee").param("employee", employee.value()).query(Integer.class).list().isEmpty()
                || employerOn(employee.value(), firstDay) != null;
    }

    private @Nullable UUID employerOn(UUID employee, LocalDate day) {
        UUID own = jdbc.sql("""
                select p.legal_entity_id from organization_workforce.employee_legal_entities p
                  left join lateral (select new_effective_to_date from organization_workforce.legal_entity_attribution_corrections
                                      where employee_id = p.employee_id and period_effective_from_date = p.effective_from_date
                                      order by correction_number desc limit 1) c on true
                 where p.employee_id = :employee and p.superseded_at is null and p.effective_from_date <= :day
                   and (coalesce(c.new_effective_to_date, p.effective_to_date) is null or :day < coalesce(c.new_effective_to_date, p.effective_to_date))
                """).param("employee", employee).param("day", day).query(UUID.class).optional().orElse(null);
        if (own != null) {
            return own;
        }
        UUID unit = jdbc.sql("""
                select org_unit_id from organization_workforce.assignments where employee_id = :employee
                """).param("employee", employee).query(UUID.class).optional().orElse(null);
        Set<UUID> seen = new HashSet<>();
        while (unit != null && seen.add(unit)) {
            UUID employer = jdbc.sql("""
                    select p.legal_entity_id from organization_workforce.org_unit_legal_entities p
                      left join lateral (select new_effective_to_date from organization_workforce.legal_entity_attribution_corrections
                                          where org_unit_id = p.org_unit_id and period_effective_from_date = p.effective_from_date
                                          order by correction_number desc limit 1) c on true
                     where p.org_unit_id = :unit and p.superseded_at is null and p.effective_from_date <= :day
                       and (coalesce(c.new_effective_to_date, p.effective_to_date) is null or :day < coalesce(c.new_effective_to_date, p.effective_to_date))
                    """).param("unit", unit).param("day", day).query(UUID.class).optional().orElse(null);
            if (employer != null) {
                return employer;
            }
            unit = jdbc.sql("""
                    select parent_org_unit_id from organization_workforce.org_unit_versions
                     where org_unit_id = :unit and superseded_at is null and effective_from_date <= :day
                       and (effective_to_date is null or :day < effective_to_date)
                    """).param("unit", unit).param("day", day).query(UUID.class).optional().orElse(null);
        }
        return null;
    }
}
