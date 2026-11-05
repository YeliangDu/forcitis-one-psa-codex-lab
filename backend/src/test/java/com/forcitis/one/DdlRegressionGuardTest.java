package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DATA_DESIGN_CHECKLIST D2 and D18 (NAMING_STANDARD section 2.4a rules 4, 6, 7): the database the migrations produce has no foreign key across schemas,
 * no PostgreSQL enum type, no jsonb column without a type check, and no soft-delete flag column. Read from the catalog, not from SQL text.
 */
class DdlRegressionGuardTest {
    static PostgreSQLContainer postgres;

    @BeforeAll
    static void migrate() throws Exception {
        postgres = TestPostgres.container();
        postgres.start();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration")
                .defaultSchema("one_migration").createSchemas(true).load().migrate();
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    static Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    static final String SYSTEM = "n.nspname NOT IN ('pg_catalog', 'information_schema', 'pg_toast') AND n.nspname NOT LIKE 'pg_temp%'";

    static List<String> column(Connection c, String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    /** One line per violation, across the schemas that are not PostgreSQL's own. */
    static List<String> violations(Connection c) throws SQLException {
        List<String> problems = new ArrayList<>();
        column(c, "SELECT 'foreign key ' || con.conname || ' on ' || n.nspname || '.' || t.relname || ' points into schema ' || fn.nspname FROM pg_constraint con "
                + "JOIN pg_class t ON t.oid = con.conrelid JOIN pg_namespace n ON n.oid = t.relnamespace JOIN pg_class f ON f.oid = con.confrelid JOIN pg_namespace fn ON fn.oid = f.relnamespace "
                + "WHERE con.contype = 'f' AND n.oid <> fn.oid AND " + SYSTEM).forEach(problems::add);
        column(c, "SELECT 'enum type ' || n.nspname || '.' || ty.typname FROM pg_type ty JOIN pg_namespace n ON n.oid = ty.typnamespace WHERE ty.typtype = 'e' AND " + SYSTEM).forEach(problems::add);
        column(c, "SELECT 'jsonb column ' || n.nspname || '.' || t.relname || '.' || a.attname || ' has no jsonb_typeof check' FROM pg_attribute a JOIN pg_class t ON t.oid = a.attrelid "
                + "JOIN pg_namespace n ON n.oid = t.relnamespace WHERE a.atttypid = 'jsonb'::regtype AND a.attnum > 0 AND NOT a.attisdropped AND t.relkind IN ('r', 'p') AND " + SYSTEM
                + " AND NOT EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conrelid = t.oid AND k.contype = 'c' AND pg_get_constraintdef(k.oid) LIKE '%jsonb_typeof(' || a.attname || ')%')").forEach(problems::add);
        column(c, "SELECT 'soft-delete column ' || n.nspname || '.' || t.relname || '.' || a.attname FROM pg_attribute a JOIN pg_class t ON t.oid = a.attrelid JOIN pg_namespace n ON n.oid = t.relnamespace "
                + "WHERE a.attnum > 0 AND NOT a.attisdropped AND t.relkind IN ('r', 'p') AND a.attname ~ '^(is_deleted|deleted|deleted_at|delete_flag|del_flag|is_del)$' AND " + SYSTEM).forEach(problems::add);
        return problems;
    }

    @Test
    @DisplayName("D2 / D18 the migrated database has no cross-schema foreign key, enum type, untyped jsonb column or soft-delete column")
    void theSchemaHasNoBannedDdl() throws SQLException {
        try (Connection c = connect()) {
            assertThat(violations(c)).as(GuardMessage.of("D18", "the migrated schema has a cross-schema foreign key, a PostgreSQL enum type, a jsonb column without a jsonb_typeof check, or a soft-delete flag column",
                    "refer across schemas by id only, use text with a CHECK instead of an enum, add CHECK (jsonb_typeof(col) = 'object') for jsonb, and delete rows (or version them) instead of flagging them")).isEmpty();
        }
    }

    @Test
    @DisplayName("injected: each banned shape is caught in a scratch schema, a clean table is not")
    void bannedShapesAreCaught() throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA scratch_a; CREATE SCHEMA scratch_b; CREATE TABLE scratch_a.parent (id uuid PRIMARY KEY); "
                    + "CREATE TABLE scratch_b.child (id uuid PRIMARY KEY, parent_id uuid REFERENCES scratch_a.parent (id), is_deleted boolean, doc jsonb, ok jsonb CHECK (jsonb_typeof(ok) = 'object')); "
                    + "CREATE TYPE scratch_b.colour AS ENUM ('red'); CREATE TABLE scratch_b.clean (id uuid PRIMARY KEY, name text)");
            List<String> problems = violations(c);
            s.execute("DROP SCHEMA scratch_a CASCADE; DROP SCHEMA scratch_b CASCADE");
            assertThat(problems).hasSize(4);
            assertThat(String.join("\n", problems)).contains("points into schema scratch_a").contains("enum type scratch_b.colour").contains("scratch_b.child.doc has no jsonb_typeof check")
                    .contains("soft-delete column scratch_b.child.is_deleted").doesNotContain("scratch_b.clean").doesNotContain("child.ok");
        }
    }
}
