package com.forcitis.one.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #934 D2 (ADR-0068 section 2.5): the migration that drops the old sync tables refuses while a HELD run still holds separation rows. */
@Testcontainers
class DropSyncMigrationIT {
    @Container
    PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql");

    private static final String BEFORE = "20261030180000";
    private static final String RUN = "019a0000-0000-7000-8000-0000000000a1";
    private static final String EMPLOYEE = "019a0000-0000-7000-8000-000000000001";

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration")
                .target(target).load();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private void seedRun(Statement s, String status, boolean withHeldRow) throws SQLException {
        s.execute("insert into organization_workforce.sync_runs (id, trigger_kind, status, started_at) values ('" + RUN + "', 'SCHEDULED', '" + status + "', now())");
        if (withHeldRow) {
            s.execute("insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values ('" + EMPLOYEE + "', 'E-01', 'name', 'EMPLOYED')");
            s.execute("insert into organization_workforce.sync_run_held_separations (sync_run_held_separation_id, sync_run_id, employee_id)"
                    + " values ('019a0000-0000-7000-8000-0000000000b1', '" + RUN + "', '" + EMPLOYEE + "')");
        }
    }

    private boolean syncRunsExist(Statement s) throws SQLException {
        try (var r = s.executeQuery("select to_regclass('organization_workforce.sync_runs') is not null")) {
            r.next();
            return r.getBoolean(1);
        }
    }

    @Test
    @DisplayName("#934 a HELD sync run that still holds a separation row stops the migration and nothing is dropped")
    void heldRowStopsTheDrop() throws SQLException {
        flyway(BEFORE).migrate();
        try (Connection c = connection(); Statement s = c.createStatement()) {
            seedRun(s, "HELD", true);

            assertThatThrownBy(() -> flyway("latest").migrate()).isInstanceOf(FlywayException.class).hasMessageContaining("cannot drop the old sync tables");

            assertThat(syncRunsExist(s)).isTrue();
        }
    }

    @Test
    @DisplayName("#934 a HELD run that holds no separation row, and a released run with a row left over, do not stop the drop")
    void nothingHeldDrops() throws SQLException {
        flyway(BEFORE).migrate();
        try (Connection c = connection(); Statement s = c.createStatement()) {
            seedRun(s, "RELEASED", true);
            s.execute("insert into organization_workforce.sync_runs (id, trigger_kind, status, started_at) values ('019a0000-0000-7000-8000-0000000000a2', 'SCHEDULED', 'HELD', now())");

            flyway("latest").migrate();

            assertThat(syncRunsExist(s)).isFalse();
        }
    }
}
