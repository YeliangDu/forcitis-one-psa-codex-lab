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

/**
 * #1372 review: the SQL backfill picks the employer LegalEntityResolution would (its Java twin is checked in InitialVersionEmployerResolutionIT):
 * inherited through a parent unit, an end date moved by a correction, a person's own attribution first, a person nobody attributes refused with a count.
 */
@Testcontainers
class EmploymentRelationshipBackfillResolutionIT {
    private static final String E1 = "'019a0000-0000-7000-8000-0000000000e1'";
    private static final String E2 = "'019a0000-0000-7000-8000-0000000000e2'";
    private static final String PARENT = "'019a0000-0000-7000-8000-0000000000b1'";
    private static final String CHILD = "'019a0000-0000-7000-8000-0000000000b2'";
    private static final String LONE = "'019a0000-0000-7000-8000-0000000000b3'";
    private static final String INHERITED = "'019a0000-0000-7000-8000-000000000011'";
    private static final String OWN = "'019a0000-0000-7000-8000-000000000012'";
    private static final String NOBODY = "'019a0000-0000-7000-8000-000000000013'";

    @Container
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql");

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration").target(target).load();
    }

    private static String unit(String id, String parent) {
        return "insert into organization_workforce.org_units (id, name, status, parent_org_unit_id) values (" + id + ", 'u', 'ACTIVE', " + parent + ")";
    }

    private static String unitVersion(String n, String unit, String parent) {
        return "insert into organization_workforce.org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, name, parent_org_unit_id, status, is_delivery_unit)"
                + " values ('019a0000-0000-7000-8000-0000000001" + n + "', " + unit + ", date '2025-01-01', 'u', " + parent + ", 'ACTIVE', false)";
    }

    private static String person(String id, String n, String unit) {
        return "insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values (" + id + ", 'E-" + n + "', 'name', 'EMPLOYED');"
                + "insert into organization_workforce.employee_assignments (assignment_id, employee_id, assignment_kind, created_at, updated_at) values ('019a0000-0000-7000-8000-0000000002" + n + "', " + id + ", 'PRIMARY', now(), now());"
                + "insert into organization_workforce.assignment_versions (assignment_version_id, assignment_id, assignment_kind, employee_id, effective_from_date, org_unit_id, revision_number, created_at, updated_at)"
                + " values ('019a0000-0000-7000-8000-0000000003" + n + "', '019a0000-0000-7000-8000-0000000002" + n + "', 'PRIMARY', " + id + ", date '2026-02-01', " + unit + ", 0, now(), now())";
    }

    private static String unitAttribution(String n, String unit, String entity) {
        return "insert into organization_workforce.legal_entity_attributions (attribution_id, org_unit_id) values ('019a0000-0000-7000-8000-0000000004" + n + "', " + unit + ");"
                + "insert into organization_workforce.org_unit_legal_entities (org_unit_legal_entity_id, org_unit_id, effective_from_date, effective_to_date, closed_at, legal_entity_id)"
                + " values ('019a0000-0000-7000-8000-0000000005" + n + "', " + unit + ", date '2025-01-01', date '2026-01-01', now(), " + entity + ")";
    }

    @Test
    @DisplayName("#1372 评审必改6：回填的用人单位与 LegalEntityResolution 一致——经父部门继承、更正后的终止日、员工自己的归属优先；无归属者带计数拒绝")
    void theBackfillEmployerIsTheResolvedOne() throws SQLException {
        flyway("20261104120000").migrate();
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); Statement s = c.createStatement()) {
            s.execute("insert into organization_workforce.legal_entities (id, code, name, status) values (" + E1 + ", 'QAA', 'one', 'ACTIVE'), (" + E2 + ", 'QAB', 'two', 'ACTIVE')");
            s.execute(unit(PARENT, "null"));
            s.execute(unit(CHILD, PARENT));
            s.execute(unit(LONE, "null"));
            s.execute(unitVersion("01", PARENT, "null"));
            s.execute(unitVersion("02", CHILD, PARENT));
            s.execute(unitVersion("03", LONE, "null"));
            // The parent's attribution was written to end before the first day, then corrected to end after it; the lone unit's was not.
            s.execute(unitAttribution("01", PARENT, E1));
            s.execute("insert into organization_workforce.legal_entity_attribution_corrections (correction_id, org_unit_id, period_effective_from_date, previous_effective_to_date,"
                    + " new_effective_to_date, correction_reason, corrected_by_user_id) values ('019a0000-0000-7000-8000-0000000006c1', " + PARENT
                    + ", date '2025-01-01', date '2026-01-01', date '2026-03-01', 'end date was wrong', '019a0000-0000-7000-8000-0000000007aa')");
            s.execute(unitAttribution("03", LONE, E2));
            for (String[] p : new String[][] {{INHERITED, "11", CHILD}, {OWN, "12", CHILD}, {NOBODY, "13", LONE}}) {
                for (String sql : person(p[0], p[1], p[2]).split(";")) {
                    s.execute(sql);
                }
                s.execute("insert into organization_workforce.employee_versions (employee_version_id, employee_id, effective_from_date, employment_status, employment_phase_kind,"
                        + " is_on_unpaid_leave, is_fee_earner, employment_start_basis_kind, change_channel_kind, revision_number, created_at, updated_at) values"
                        + " ('019a0000-0000-7000-8000-0000000008" + p[1] + "', " + p[0] + ", date '2026-02-01', 'EMPLOYED', 'REGULAR', false, false, 'UNKNOWN', 'MIGRATION', 0, now(), now())");
            }
            s.execute("insert into organization_workforce.legal_entity_attributions (attribution_id, employee_id) values ('019a0000-0000-7000-8000-0000000004a2', " + OWN + ")");
            s.execute("insert into organization_workforce.employee_legal_entities (employee_legal_entity_id, employee_id, effective_from_date, legal_entity_id) values"
                    + " ('019a0000-0000-7000-8000-0000000005a2', " + OWN + ", date '2025-01-01', " + E2 + ")");

            assertThatThrownBy(() -> flyway("latest").migrate()).hasMessageContaining("1 employee(s) have no legal entity attribution");

            s.execute("insert into organization_workforce.legal_entity_attributions (attribution_id, employee_id) values ('019a0000-0000-7000-8000-0000000004a3', " + NOBODY + ")");
            s.execute("insert into organization_workforce.employee_legal_entities (employee_legal_entity_id, employee_id, effective_from_date, legal_entity_id) values"
                    + " ('019a0000-0000-7000-8000-0000000005a3', " + NOBODY + ", date '2025-01-01', " + E2 + ")");
            flyway("latest").migrate();

            try (ResultSet r = s.executeQuery("select employee_id::text, employer_legal_entity_id::text from organization_workforce.employment_relationships order by employee_id")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getString(2)).as("inherited from the parent, end date as corrected").isEqualTo("019a0000-0000-7000-8000-0000000000e1");
                assertThat(r.next()).isTrue();
                assertThat(r.getString(2)).as("the person's own attribution first").isEqualTo("019a0000-0000-7000-8000-0000000000e2");
                assertThat(r.next()).isTrue();
                assertThat(r.getString(2)).isEqualTo("019a0000-0000-7000-8000-0000000000e2");
                assertThat(r.next()).isFalse();
            }
        }
    }
}
