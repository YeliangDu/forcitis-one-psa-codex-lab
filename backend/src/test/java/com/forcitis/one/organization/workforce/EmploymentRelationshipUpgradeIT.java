package com.forcitis.one.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #1372 (ADR-0086): the structural migration gives every person with version or command rows one relationship, or fails loudly and names no employer it would have to guess. */
@Testcontainers
class EmploymentRelationshipUpgradeIT {
    private static final String PERSON = "'019a0000-0000-7000-8000-000000000001'";
    private static final String ENTITY = "'019a0000-0000-7000-8000-0000000000e1'";

    @Container
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql");

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration")
                .target(target).load();
    }

    @Test
    @DisplayName("#1372 a person without attribution stops the migration with a count; once attributed, versions and commands hang on one MIGRATION_ATTRIBUTION relationship")
    void theBackfillRefusesTheUnattributedAndBindsTheRest() throws SQLException {
        flyway("20261104120000").migrate();
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); Statement s = c.createStatement()) {
            s.execute("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (" + PERSON + ", 'E-01', 'name', 'EMPLOYED')");
            s.execute("insert into organization_workforce.legal_entities (id, code, name, status) values (" + ENTITY + ", 'ABC', 'entity', 'ACTIVE')");
            s.execute("insert into organization_workforce.employee_versions (employee_version_id, employee_id, effective_from_date, employment_status, employment_phase_kind,"
                    + " is_on_unpaid_leave, is_fee_earner, employment_start_basis_kind, change_channel_kind, revision_number, created_at, updated_at) values"
                    + " ('019a0000-0000-7000-8000-0000000000d1', " + PERSON + ", date '2026-01-01', 'EMPLOYED', 'REGULAR', false, false, 'UNKNOWN', 'MIGRATION', 0, now(), now())");
            s.execute("insert into organization_workforce.personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at)"
                    + " values ('019a0000-0000-7000-8000-0000000000c1', " + PERSON + ", 'SEPARATE', date '2026-12-01', 'PENDING', now(), '019a0000-0000-7000-8000-0000000000aa', now())");

            assertThatThrownBy(() -> flyway("latest").migrate()).hasMessageContaining("no legal entity attribution");

            s.execute("insert into organization_workforce.legal_entity_attributions (attribution_id, employee_id) values ('019a0000-0000-7000-8000-0000000000a1', " + PERSON + ")");
            s.execute("insert into organization_workforce.employee_legal_entities (employee_legal_entity_id, employee_id, effective_from_date, legal_entity_id) values"
                    + " ('019a0000-0000-7000-8000-0000000000a2', " + PERSON + ", date '2025-01-01', " + ENTITY + ")");

            flyway("latest").migrate();

            try (ResultSet r = s.executeQuery("select (select count(*) from organization_workforce.employment_relationships where employer_source_kind = 'MIGRATION_ATTRIBUTION'"
                    + " and employer_confirmed_at is null and employer_legal_entity_id = " + ENTITY + "),"
                    + " (select count(*) from organization_workforce.employment_relationship_versions v join organization_workforce.employment_relationships e using (employment_relationship_id)),"
                    + " (select count(*) from organization_workforce.personnel_commands c join organization_workforce.employment_relationships e using (employment_relationship_id))")) {
                r.next();
                assertThat(r.getInt(1)).as("one unconfirmed relationship with the attributed employer").isEqualTo(1);
                assertThat(r.getInt(2)).as("the version hangs on it").isEqualTo(1);
                assertThat(r.getInt(3)).as("the command hangs on it").isEqualTo(1);
            }
        }
    }
}
