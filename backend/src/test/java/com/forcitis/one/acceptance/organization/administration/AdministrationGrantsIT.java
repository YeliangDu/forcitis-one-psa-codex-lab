package com.forcitis.one.acceptance.organization.administration;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Card A, authorization (DDL draft R6): the runtime account's grants on the four tables, taken from the real database setup script
 * (one_db_roles_schemas.sql: roles, schemas, default grants, and its after-migration section) on a database the application's migrations ran in under
 * the migration account. The grants are read from the privilege functions, and a few statements are really run as the runtime account.
 */
@Testcontainers
class AdministrationGrantsIT {

    static final String DATABASE = "forcitis_one_dev";
    static final String MIGRATOR = "one_dev_migrator";
    static final String RUNTIME = "one_dev_app";
    static final String PERMISSION_DENIED = "42501";
    static final String SCHEMA = "organization_administration";
    static final List<String> PRIVILEGES = List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER");

    @Container
    static PostgreSQLContainer postgres = TestPostgres.container().withDatabaseName(DATABASE);

    static final String MIGRATOR_PASSWORD = UUID.randomUUID().toString();
    static final String RUNTIME_PASSWORD = UUID.randomUUID().toString();

    @BeforeAll
    static void deployWithSeparatedRoles() throws Exception {
        String script = Files.readString(Path.of("../docs/ops/one-db-roles-schemas.sql"));
        int afterMigration = script.indexOf("-- §4b");
        assertThat(afterMigration).as("the after-migration section of the setup script").isPositive();
        try (Connection c = superuser(); Statement s = c.createStatement()) {
            s.execute(script.substring(0, afterMigration));
            s.execute("GRANT CREATE ON DATABASE " + DATABASE + " TO one_dev_owner");
            s.execute("ALTER ROLE " + MIGRATOR + " PASSWORD '" + MIGRATOR_PASSWORD + "'");
            s.execute("ALTER ROLE " + RUNTIME + " PASSWORD '" + RUNTIME_PASSWORD + "'");
        }
        Flyway.configure().dataSource(postgres.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD).locations("classpath:db/migration")
                .defaultSchema("one_migration").schemas(flywaySchemas()).load().migrate();
        try (Connection c = superuser(); Statement s = c.createStatement()) {
            s.execute(script.substring(afterMigration, script.indexOf("-- §5 撤销")));
        }
    }

    private static String[] flywaySchemas() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = AdministrationGrantsIT.class.getResourceAsStream("/application.properties")) {
            properties.load(in);
        }
        return properties.getProperty("spring.flyway.schemas").strip().split(",");
    }

    private static Connection superuser() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static Connection runtimeAccount() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), RUNTIME, RUNTIME_PASSWORD);
    }

    private static Set<String> runtimePrivilegesOn(String table) throws SQLException {
        Set<String> held = new TreeSet<>();
        try (Connection c = superuser(); PreparedStatement s = c.prepareStatement("select has_table_privilege(?::text, ?::text, ?::text)")) {
            for (String privilege : PRIVILEGES) {
                s.setString(1, RUNTIME);
                s.setString(2, SCHEMA + "." + table);
                s.setString(3, privilege);
                try (ResultSet rs = s.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    if (rs.getBoolean(1)) {
                        held.add(privilege);
                    }
                }
            }
        }
        return held;
    }

    private static void denied(String statement) throws SQLException {
        try (Connection c = runtimeAccount(); Statement s = c.createStatement()) {
            SQLException refusal = null;
            try {
                s.execute(statement);
            } catch (SQLException e) {
                refusal = e;
            }
            assertThat((Throwable) refusal).as("the runtime account is refused: " + statement).isNotNull();
            assertThat(refusal.getSQLState()).as(refusal.getMessage()).isEqualTo(PERMISSION_DENIED);
        }
    }

    @Test
    @DisplayName("ADM-A-62 给定部署后的运行账号，当查 config_sets 的权限，则恰有 SELECT 与 UPDATE（无 INSERT、DELETE、TRUNCATE）")
    void configSetsAreReadAndLockedOnly() throws SQLException {
        assertThat(runtimePrivilegesOn("config_sets")).containsExactly("SELECT", "UPDATE");
    }

    @Test
    @DisplayName("ADM-A-63 给定部署后的运行账号，当查 config_set_versions 的权限，则恰有 SELECT、INSERT、UPDATE（无 DELETE、TRUNCATE）")
    void versionsAreDraftedAndChangedButNotDeleted() throws SQLException {
        assertThat(runtimePrivilegesOn("config_set_versions")).containsExactly("INSERT", "SELECT", "UPDATE");
    }

    @Test
    @DisplayName("ADM-A-64 给定部署后的运行账号，当查条目表与 state_transitions 的权限，则都恰有 SELECT 与 INSERT")
    void itemsAndTransitionsAreAppendOnlyForTheRuntimeAccount() throws SQLException {
        assertThat(runtimePrivilegesOn("employee_activity_type_items")).containsExactly("INSERT", "SELECT");
        assertThat(runtimePrivilegesOn("state_transitions")).containsExactly("INSERT", "SELECT");
    }

    @Test
    @DisplayName("ADM-A-65 给定以运行账号连接，当对四张表执行被回收权限的语句，则被 42501 拒绝；读与被保留的写（转换追加）可执行")
    void revokedStatementsAreDeniedToTheRuntimeAccount() throws SQLException {
        String sets = SCHEMA + ".config_sets";
        String versions = SCHEMA + ".config_set_versions";
        String items = SCHEMA + ".employee_activity_type_items";
        String transitions = SCHEMA + ".state_transitions";

        denied("insert into " + sets + " (config_set_id, config_set_code, owner_role_code, created_at, updated_at, version)"
                + " values (gen_random_uuid(), 'REMINDER_RULES', 'probe_owner_role', now(), now(), 0)");
        denied("delete from " + sets + " where false");
        denied("truncate table " + sets);
        denied("delete from " + versions + " where false");
        denied("truncate table " + versions);
        denied("update " + items + " set is_enabled = false where false");
        denied("delete from " + items + " where false");
        denied("truncate table " + items);
        denied("update " + transitions + " set actor_type = 'SYSTEM' where false");
        denied("delete from " + transitions + " where false");
        denied("truncate table " + transitions);

        try (Connection c = runtimeAccount(); Statement s = c.createStatement()) {
            c.setAutoCommit(false);
            s.execute("select count(*) from " + sets);
            s.execute("update " + sets + " set version = version where false");
            s.execute("update " + versions + " set version = version where false");
            s.execute("insert into " + transitions + " (id, aggregate_type_code, aggregate_id, transition_sequence_number, from_status, to_status,"
                    + " occurred_on_date, recorded_at, legal_entity_id, actor_type, actor_id, cause_kind, command_id, event_id, correlation_id,"
                    + " reverses_transition_id, created_at, updated_at) values (gen_random_uuid(), 'ConfigSetVersion', gen_random_uuid(), 1, null,"
                    + " 'PENDING', DATE '2026-11-02', TIMESTAMPTZ '2026-11-02 02:00:00+00', null, 'USER', gen_random_uuid(), 'COMMAND', gen_random_uuid(),"
                    + " null, gen_random_uuid(), null, TIMESTAMPTZ '2026-11-02 02:00:00+00', TIMESTAMPTZ '2026-11-02 02:00:00+00')");
            c.rollback();
        }
    }

    @Test
    @DisplayName("ADM-A-66 给定部署后的运行账号，当查 organization_administration 的 schema 权限，则有 USAGE、无 CREATE")
    void theRuntimeAccountUsesButDoesNotCreateInTheSchema() throws SQLException {
        try (Connection c = superuser(); PreparedStatement s = c.prepareStatement("select has_schema_privilege(?::text, ?::text, ?::text)")) {
            for (String[] expected : new String[][] {{"USAGE", "true"}, {"CREATE", "false"}}) {
                s.setString(1, RUNTIME);
                s.setString(2, SCHEMA);
                s.setString(3, expected[0]);
                try (ResultSet rs = s.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(String.valueOf(rs.getBoolean(1))).as(expected[0]).isEqualTo(expected[1]);
                }
            }
        }
    }
}
