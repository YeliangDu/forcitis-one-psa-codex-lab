package com.forcitis.one.organization.workforce.infrastructure.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The root of a new concurrent post (#711 DD 4.1): a person has any number of them, each made by the command that starts the post. */
final class ConcurrentAssignmentRoot {
    private ConcurrentAssignmentRoot() {
    }

    static void insert(JdbcClient jdbc, UUID root, UUID employee, Instant at) {
        jdbc.sql("""
                insert into organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind, created_at, updated_at)
                values (:id, :employee, 'CONCURRENT', :at, :at)
                """).param("id", root).param("employee", employee).param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC)).update();
    }
}
