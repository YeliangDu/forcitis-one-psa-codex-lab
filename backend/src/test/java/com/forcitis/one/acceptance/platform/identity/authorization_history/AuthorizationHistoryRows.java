package com.forcitis.one.acceptance.platform.identity.authorization_history;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.postgresql.util.PSQLException;

/**
 * ADR-0034-IMPL card 7.1: rows of platform_identity.role_versions and account_role_grants on the database the application's own migrations
 * build (no Spring context). One transaction per test that is never committed; a probe runs under a savepoint that is rolled back, so a
 * refused statement does not poison the next. Times are literals, never the database clock.
 */
final class AuthorizationHistoryRows implements AutoCloseable {

    static final String RESTRICT_VIOLATION = "23001";
    static final String NOT_NULL_VIOLATION = "23502";
    static final String CHECK_VIOLATION = "23514";
    static final String EXCLUSION_VIOLATION = "23P01";

    static final String T0 = "2026-11-02T02:00:00Z";
    static final String T1 = "2026-11-03T02:00:00Z";
    static final String T2 = "2026-11-04T02:00:00Z";
    static final String T3 = "2026-11-05T02:00:00Z";

    static final String VERSION_INSERT = """
            insert into platform_identity.role_versions (role_version_id, role_id, version_number, effective_from_at, effective_to_at, role_name,
                   role_status, access_scope_kind, permission_codes, change_channel_kind, recorded_by, change_reason, authorization_request_id,
                   declaration_revision_number, version_audit_entry_id, closed_by, closure_reason, closure_audit_entry_id, created_at, updated_at)
            values (?, ?, ?, ?::timestamptz, ?::timestamptz, ?, 'ENABLED', 'SELF', ?::text[], 'SEED', null, 'acceptance fixture', null,
                    1, ?, null, null, null, ?::timestamptz, ?::timestamptz)""";

    static final String GRANT_INSERT = """
            insert into platform_identity.account_role_grants (account_role_grant_id, user_id, role_id, effective_from_at, effective_to_at,
                   granted_by, grant_channel_kind, grant_authorization_request_id, migration_batch_id, grant_audit_entry_id, revoked_by,
                   revocation_cause_kind, revocation_authorization_request_id, revocation_separation_command_id, revocation_audit_entry_id, created_at, updated_at)
            values (?, ?, ?, ?::timestamptz, ?::timestamptz, null, 'DEFAULT_ROLE', null, null, ?, null, ?, null, null, ?, ?::timestamptz, ?::timestamptz)""";

    private static final Map<Class<?>, TestDatabase> DATABASES = new ConcurrentHashMap<>();

    private final Connection connection;
    private Boolean rolesHaveName;

    AuthorizationHistoryRows(Class<?> testClass) throws SQLException {
        TestDatabase database = DATABASES.computeIfAbsent(testClass, c -> TestPostgres.database(c, TestPostgres.MIGRATED));
        connection = DriverManager.getConnection(database.url(), database.username(), database.password());
        connection.setAutoCommit(false);
    }

    Connection connection() {
        return connection;
    }

    /** A role row; the name column exists until PR-2 of the card moves it into role_versions. */
    UUID role() throws SQLException {
        UUID id = UUID.randomUUID();
        String code = "ah_" + id.toString().substring(0, 8);
        if (rolesHaveName == null) {
            rolesHaveName = count("select count(*) from information_schema.columns where table_schema = 'platform_identity'"
                    + " and table_name = 'roles' and column_name = 'name'") == 1;
        }
        if (rolesHaveName) {
            setup("insert into platform_identity.roles (role_id, code, name) values (?, ?, ?)", id, code, "acceptance " + id);
        } else {
            setup("insert into platform_identity.roles (role_id, code) values (?, ?)", id, code);
        }
        return id;
    }

    UUID account() throws SQLException {
        UUID id = UUID.randomUUID();
        setup("insert into platform_identity.user_accounts (user_id, employee_id) values (?, ?)", id, UUID.randomUUID());
        return id;
    }

    /** A current (open) SEED version of the role with a normalised permission set. */
    UUID openVersion(UUID role, int number, String from) throws SQLException {
        UUID id = UUID.randomUUID();
        setup(VERSION_INSERT, id, role, number, from, null, uniqueName(), "{platform.identity:role:read}", UUID.randomUUID(), from, from);
        return id;
    }

    /** A version that a later one replaced: closed, closure columns empty (card 7.1 ck_role_versions_closure). */
    UUID replacedVersion(UUID role, int number, String from, String to) throws SQLException {
        UUID id = UUID.randomUUID();
        setup(VERSION_INSERT, id, role, number, from, to, uniqueName(), "{platform.identity:role:read}", UUID.randomUUID(), from, to);
        return id;
    }

    UUID openGrant(UUID account, UUID role, String from) throws SQLException {
        UUID id = UUID.randomUUID();
        setup(GRANT_INSERT, id, account, role, from, null, UUID.randomUUID(), null, null, from, from);
        return id;
    }

    /** A grant taken back by an operations command: OPERATIONS needs neither a revoker nor a sync run. */
    UUID revokedGrant(UUID account, UUID role, String from, String to) throws SQLException {
        UUID id = UUID.randomUUID();
        setup(GRANT_INSERT, id, account, role, from, to, UUID.randomUUID(), "OPERATIONS", UUID.randomUUID(), from, to);
        return id;
    }

    static String uniqueName() {
        return "acceptance role " + UUID.randomUUID();
    }

    void setup(String sql, Object... parameters) throws SQLException {
        run(sql, parameters);
    }

    /** The statement runs and is undone; fails loudly when the database refuses it. */
    void accepted(String sql, Object... parameters) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            run(sql, parameters);
        } finally {
            connection.rollback(savepoint);
        }
    }

    /** The statement must be refused; the refusal is handed back for the caller to check SQLSTATE and source. */
    PSQLException refused(String sql, Object... parameters) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        PSQLException refusal = null;
        try {
            run(sql, parameters);
        } catch (PSQLException e) {
            refusal = e;
        } finally {
            connection.rollback(savepoint);
        }
        if (refusal == null) {
            throw new AssertionError("the statement was not refused by the database: " + sql);
        }
        return refusal;
    }

    /** Refused by a RAISE in the named card 7.1 function with restrict_violation, not by a foreign key (which also answers 23001). */
    void refusedByTrigger(String function, String sql, Object... parameters) throws SQLException {
        PSQLException refusal = refused(sql, parameters);
        assertThat(refusal.getSQLState()).as(refusal.getMessage()).isEqualTo(RESTRICT_VIOLATION);
        assertThat(refusal.getServerErrorMessage()).isNotNull();
        assertThat(refusal.getServerErrorMessage().getConstraint()).as("a trigger refusal names no constraint: " + refusal.getMessage()).isNull();
        assertThat(refusal.getServerErrorMessage().getWhere()).as(refusal.getMessage()).contains("PL/pgSQL function platform_identity." + function + "()");
    }

    void refusedByConstraint(String sqlState, String constraint, String sql, Object... parameters) throws SQLException {
        PSQLException refusal = refused(sql, parameters);
        assertThat(refusal.getSQLState()).as(refusal.getMessage()).isEqualTo(sqlState);
        assertThat(refusal.getServerErrorMessage()).isNotNull();
        assertThat(refusal.getServerErrorMessage().getConstraint()).as(refusal.getMessage()).isEqualTo(constraint);
    }

    void refusedAsNull(String column, String sql, Object... parameters) throws SQLException {
        PSQLException refusal = refused(sql, parameters);
        assertThat(refusal.getSQLState()).as(refusal.getMessage()).isEqualTo(NOT_NULL_VIOLATION);
        assertThat(refusal.getServerErrorMessage().getColumn()).as(refusal.getMessage()).isEqualTo(column);
    }

    List<Object> column(String sql, Object... parameters) throws SQLException {
        List<Object> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    values.add(rs.getObject(1));
                }
            }
        }
        return values;
    }

    long count(String sql, Object... parameters) throws SQLException {
        return ((Number) column(sql, parameters).getFirst()).longValue();
    }

    private void run(String sql, Object[] parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.execute();
        }
    }

    private static void bind(PreparedStatement statement, Object[] parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
    }

    @Override
    public void close() throws SQLException {
        connection.rollback();
        connection.close();
    }
}
