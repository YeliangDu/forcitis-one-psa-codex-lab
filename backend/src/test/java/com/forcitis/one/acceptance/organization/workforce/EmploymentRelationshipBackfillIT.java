package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * #1371 acceptance part, done-when 3 of docs/architecture/proposals/employment-relationship.md (section 7), rows only, no reject-list count
 * (lead ruling item 5). The database is migrated to the last migration of main, rows are written in today's shape, then the rest is
 * migrated: every attributed employee has exactly one relationship, and no version, command or assignment lacks one. Synthetic data only.
 */
@Testcontainers
class EmploymentRelationshipBackfillIT {

    /** The last migration of main when the card was written: the new migrations of the implementation come after it. */
    private static final String BEFORE = "20261105100000";

    @Container
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql");

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration")
                .target(target).load();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void run(Connection db, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.execute();
        }
    }

    private static long count(Connection db, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    @Test
    @DisplayName("#1371 完成标准 3（设计 7）：迁移后每个有归属的员工恰有一段关系，版本、命令、任职都带关系编号")
    void everyAttributedEmployeeGetsOneRelationship() throws Exception {
        flyway(BEFORE).migrate();
        UUID entity = UUID.randomUUID();
        UUID unit = UUID.randomUUID();
        UUID[] people = {UUID.randomUUID(), UUID.randomUUID()};
        LocalDate since = LocalDate.parse("2026-01-01");
        try (Connection db = connect()) {
            run(db, "insert into organization_workforce.legal_entities (id, code, name, status) values (?, 'SYNTH', 'Synthetic entity', 'ACTIVE')", entity);
            run(db, "insert into organization_workforce.org_units (id, name, status) values (?, 'Synthetic unit', 'ACTIVE')", unit);
            for (UUID person : people) {
                UUID root = UUID.randomUUID();
                run(db, "insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (?, ?, 'Synthetic', 'EMPLOYED')",
                        person, "S" + person.toString().substring(0, 8));
                run(db, "insert into organization_workforce.legal_entity_attributions (attribution_id, employee_id) values (?, ?)", UUID.randomUUID(), person);
                run(db, "insert into organization_workforce.employee_legal_entities (employee_legal_entity_id, employee_id, effective_from_date, legal_entity_id) values (?, ?, ?, ?)",
                        UUID.randomUUID(), person, since, entity);
                run(db, """
                        insert into organization_workforce.employee_versions (employee_version_id, employee_id, effective_from_date, revision_number, employment_status,
                          employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind, change_channel_kind, created_at, updated_at)
                        values (?, ?, ?, 0, 'EMPLOYED', 'REGULAR', false, false, ?, 'LEGACY_HR_RECORD', 'MIGRATION', now(), now())""", UUID.randomUUID(), person, since, since);
                run(db, "insert into organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind, created_at, updated_at) values (?, ?, 'PRIMARY', now(), now())", root, person);
                run(db, """
                        insert into organization_workforce.assignment_versions (assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date,
                          revision_number, org_unit_id, change_channel_kind, created_at, updated_at) values (?, ?, ?, 'PRIMARY', ?, 0, ?, 'MIGRATION', now(), now())""", UUID.randomUUID(), person, root, since, unit);
                run(db, """
                        insert into organization_workforce.personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at)
                        values (?, ?, 'REGULARIZE', ?, 'PENDING', now(), ?, now())""", UUID.randomUUID(), person, since.plusDays(400), UUID.randomUUID());
            }

            flyway("latest").migrate();

            for (UUID person : people) {
                assertThat(count(db, "select count(*) from organization_workforce.employment_relationships where employee_id = ?", person))
                        .as("design 7: one relationship per employee").isEqualTo(1);
                assertThat(count(db, "select count(*) from organization_workforce.employment_relationships where employee_id = ? and employer_legal_entity_id = ?"
                        + " and employer_source_kind = 'MIGRATION_ATTRIBUTION'", person, entity)).as("design 7: employer is the attribution entity, awaiting HR confirmation").isEqualTo(1);
            }
            for (String table : new String[] {"employment_relationship_versions", "assignment_versions"}) {
                assertThat(count(db, "select count(*) from organization_workforce." + table + " where employment_relationship_id is null"))
                        .as("design 7 and 4.1: no row of " + table + " lacks a relationship").isZero();
                assertThat(count(db, "select count(*) from organization_workforce." + table + " where employee_id = any(?)", (Object) db.createArrayOf("uuid", people)))
                        .as("the seeded rows of " + table + " are still there").isPositive();
            }
            assertThat(count(db, "select count(*) from organization_workforce.personnel_commands where employee_id = any(?) and employment_relationship_id is null",
                    (Object) db.createArrayOf("uuid", people))).as("design 7: existing commands gain the id by join on employee").isZero();
        }
    }
}
