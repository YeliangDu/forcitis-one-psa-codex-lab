package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.Uuids;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Synthetic people for the HTTP tests: an employed person with a migrated employee version and assignment version, in a unit and a position of their own. */
public final class PersonnelSeedSupport {
    public static final LocalDate SINCE = LocalDate.of(2020, 1, 1);

    public record Person(UUID employee, UUID orgUnit, UUID position) {
    }

    private PersonnelSeedSupport() {
    }

    public static UUID orgUnit(JdbcClient jdbc, Clock clock) {
        UUID id = Uuids.timeOrdered(clock);
        jdbc.sql("insert into organization_workforce.org_units (id, name, status) values (:id, 'Synthetic unit', 'ACTIVE')").param("id", id).update();
        return id;
    }

    public static Person employed(JdbcClient jdbc, Clock clock, String code) {
        UUID employee = Uuids.timeOrdered(clock);
        UUID unit = orgUnit(jdbc, clock);
        UUID position = Uuids.timeOrdered(clock);
        jdbc.sql("insert into organization_workforce.positions (id, code, name, status) values (:id, :code, 'Synthetic position', 'ACTIVE')").param("id", position).param("code", code).update();
        jdbc.sql("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (:id, :code, 'Synthetic person', 'EMPLOYED')")
                .param("id", employee).param("code", code).update();
        UUID relationship = EmploymentRelationshipSeed.relationship(jdbc, employee);
        jdbc.sql("""
                insert into organization_workforce.employment_relationship_versions (employment_relationship_version_id, employment_relationship_id, employee_id, effective_from_date, revision_number, employment_status,
                  employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind, change_channel_kind, created_at, updated_at)
                values (:id, :relationship, :employee, :since, 0, 'EMPLOYED', 'REGULAR', false, false, :since, 'LEGACY_HR_RECORD', 'MIGRATION', now(), now())""")
                .param("id", Uuids.timeOrdered(clock)).param("relationship", relationship).param("employee", employee).param("since", SINCE).update();
        jdbc.sql("""
                insert into organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind, created_at, updated_at)
                values (uuidv7(), :employee, 'PRIMARY', now(), now()) on conflict (employee_id) where assignment_kind = 'PRIMARY' do nothing""")
                .param("employee", employee).update();
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, employment_relationship_id, employee_id, assignment_id, assignment_kind, effective_from_date,
                  org_unit_id, position_id, change_channel_kind, created_at, updated_at)
                select :id, :relationship, :employee, assignment_id, assignment_kind, :since, :unit, :position, 'MIGRATION', now(), now()
                  from organization_workforce.employee_assignments where employee_id = :employee and assignment_kind = 'PRIMARY'""")
                .param("id", Uuids.timeOrdered(clock)).param("relationship", relationship).param("employee", employee).param("since", SINCE).param("unit", unit).param("position", position).update();
        return new Person(employee, unit, position);
    }
}
