package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.IdGenerator;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The one PRIMARY root of an employee's assignments (#711 DD 4.1): every writer of assignment versions finds it, or creates it when the employee has none. */
final class PrimaryAssignmentRoot {
    private PrimaryAssignmentRoot() {
    }

    static UUID of(JdbcClient jdbc, IdGenerator ids, UUID employee, Instant at) {
        OffsetDateTime stamp = OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
        jdbc.sql("""
                insert into organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind, created_at, updated_at)
                values (:id, :employee, 'PRIMARY', :at, :at)
                on conflict (employee_id) where assignment_kind = 'PRIMARY' do nothing
                """).param("id", ids.next()).param("employee", employee).param("at", stamp).update();
        return jdbc.sql("select assignment_id from organization_workforce.employee_assignments where employee_id = :employee and assignment_kind = 'PRIMARY'")
                .param("employee", employee).query(UUID.class).single();
    }
}
