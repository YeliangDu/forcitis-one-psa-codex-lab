package com.forcitis.one.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;

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

/** IMP-1030-FC-A (#892): the common-set migration back-fills closed_at on periods closed before it, so ck_employee_legal_entities_closed holds for existing rows. */
@Testcontainers
class EmployeeLegalEntityCommonSetMigrationIT {
    @Container
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql");

    private static Flyway flyway(String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration")
                .target(target).load();
    }

    @Test
    @DisplayName("IMP-1030-FC-A a period closed before the migration gets closed_at = updated_at, an open one stays open")
    void closedRowsAreBackFilled() throws SQLException {
        flyway("20261030150000").migrate();
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); Statement s = c.createStatement()) {
            s.execute("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values ('019a0000-0000-7000-8000-000000000001', 'E-01', 'name', 'EMPLOYED')");
            s.execute("insert into organization_workforce.legal_entities (id, code, name, status) values ('019a0000-0000-7000-8000-0000000000e1', 'ABC', 'entity', 'ACTIVE')");
            s.execute("insert into organization_workforce.legal_entity_attributions (attribution_id, employee_id) values ('019a0000-0000-7000-8000-0000000000a1', '019a0000-0000-7000-8000-000000000001')");
            s.execute("insert into organization_workforce.employee_legal_entities (employee_legal_entity_id, employee_id, effective_from_date, effective_to_date, legal_entity_id, updated_at) values"
                    + " ('019a0000-0000-7000-8000-0000000000d1', '019a0000-0000-7000-8000-000000000001', date '2026-01-01', date '2026-02-01', '019a0000-0000-7000-8000-0000000000e1', timestamptz '2026-02-01 08:00+08'),"
                    + " ('019a0000-0000-7000-8000-0000000000d2', '019a0000-0000-7000-8000-000000000001', date '2026-02-01', null, '019a0000-0000-7000-8000-0000000000e1', now())");

            flyway("latest").migrate();

            try (ResultSet r = s.executeQuery("select count(*) filter (where effective_to_date is not null and closed_at = updated_at),"
                    + " count(*) filter (where effective_to_date is null and closed_at is null) from organization_workforce.employee_legal_entities")) {
                r.next();
                assertThat(r.getInt(1)).isEqualTo(1);
                assertThat(r.getInt(2)).isEqualTo(1);
            }
        }
    }
}
