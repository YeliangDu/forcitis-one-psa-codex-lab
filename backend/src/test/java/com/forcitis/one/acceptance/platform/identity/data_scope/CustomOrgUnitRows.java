package com.forcitis.one.acceptance.platform.identity.data_scope;

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
 * F-1 card section 7.1, first block (#728): rows of platform_identity.role_version_custom_org_units and
 * authorization_request_custom_org_units, and of their parents role_versions and authorization_requests, on the database the
 * application's own migrations build (no Spring context). One transaction per test that is never committed; a probe runs under a
 * savepoint that is rolled back, so a refused statement does not poison the next. Times are literals, never the database clock.
 * Same method as AuthorizationHistoryRows of ADR-0034-IMPL PR-1.
 */
final class CustomOrgUnitRows implements AutoCloseable {

    static final String RESTRICT_VIOLATION = "23001";
    static final String UNIQUE_VIOLATION = "23505";

    static final String GUARD = "guard_custom_org_unit_rows";
    static final String ROLE_VERSION_UNITS = "platform_identity.role_version_custom_org_units";
    static final String REQUEST_UNITS = "platform_identity.authorization_request_custom_org_units";

    static final String T0 = "2026-11-02T02:00:00Z";
    static final String T1 = "2026-11-03T02:00:00Z";
    static final String T2 = "2026-11-04T02:00:00Z";

    static final String VERSION_INSERT = """
            insert into platform_identity.role_versions (role_version_id, role_id, version_number, effective_from_at, effective_to_at, role_name,
                   role_status, access_scope_kind, permission_codes, change_channel_kind, recorded_by, change_reason, authorization_request_id,
                   declaration_revision_number, version_audit_entry_id, closed_by, closure_reason, closure_audit_entry_id, created_at, updated_at)
            values (?, ?, ?, ?::timestamptz, ?::timestamptz, ?, 'ENABLED', ?, '{platform.identity:role:read}'::text[], 'SEED', null, 'acceptance fixture', null,
                    1, ?, null, null, null, ?::timestamptz, ?::timestamptz)""";

    static final String REQUEST_INSERT = """
            insert into platform_identity.authorization_requests (request_id, request_kind, requested_by, requested_at, subject_user_id, role_id, role_codes,
                   new_role_code, role_name, is_enabled, access_scope_kind, permission_codes, request_status, decided_by, decided_at, created_at, updated_at)
            values (?, ?, ?, ?::timestamptz, null, null, '{}'::text[], null, ?, true, ?, '{}'::text[], ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz)""";

    static final String ROLE_VERSION_UNIT_INSERT = """
            insert into platform_identity.role_version_custom_org_units (role_version_custom_org_unit_id, role_version_id, org_unit_id,
                   is_descendants_included, created_at, updated_at) values (?, ?, ?, ?, ?::timestamptz, ?::timestamptz)""";

    static final String REQUEST_UNIT_INSERT = """
            insert into platform_identity.authorization_request_custom_org_units (authorization_request_custom_org_unit_id, request_id, org_unit_id,
                   created_at, updated_at) values (?, ?, ?, ?::timestamptz, ?::timestamptz)""";

    private static final Map<Class<?>, TestDatabase> DATABASES = new ConcurrentHashMap<>();

    private final Connection connection;

    CustomOrgUnitRows(Class<?> testClass) throws SQLException {
        TestDatabase database = DATABASES.computeIfAbsent(testClass, c -> TestPostgres.database(c, TestPostgres.MIGRATED));
        connection = DriverManager.getConnection(database.url(), database.username(), database.password());
        connection.setAutoCommit(false);
    }

    UUID role() throws SQLException {
        UUID id = UUID.randomUUID();
        setup("insert into platform_identity.roles (role_id, code) values (?, ?)", id, "cou_" + id.toString().substring(0, 8));
        return id;
    }

    UUID account() throws SQLException {
        UUID id = UUID.randomUUID();
        setup("insert into platform_identity.user_accounts (user_id, employee_id) values (?, ?)", id, UUID.randomUUID());
        return id;
    }

    /** A current (open) version of the role with the given scope kind. */
    UUID openVersion(UUID role, int number, String scopeKind, String from) throws SQLException {
        UUID id = UUID.randomUUID();
        setup(VERSION_INSERT, id, role, number, from, null, uniqueName(), scopeKind, UUID.randomUUID(), from, from);
        return id;
    }

    /** A version that a later one replaced: closed, closure columns empty (card 7.1 of ADR-0034-IMPL, ck_role_versions_closure). */
    UUID replacedVersion(UUID role, int number, String scopeKind, String from, String to) throws SQLException {
        UUID id = UUID.randomUUID();
        setup(VERSION_INSERT, id, role, number, from, to, uniqueName(), scopeKind, UUID.randomUUID(), from, to);
        return id;
    }

    /** An authorization request of the kind, in the status and with the scope kind (null for none); a decided one is decided by someone else. */
    UUID request(String kind, String status, String scopeKind) throws SQLException {
        UUID id = UUID.randomUUID();
        boolean decided = !"PENDING".equals(status);
        setup(REQUEST_INSERT, id, kind, UUID.randomUUID(), T0, uniqueName(), scopeKind, status, decided ? UUID.randomUUID() : null, decided ? T1 : null, T0, T0);
        return id;
    }

    /** A pending CREATE_ROLE request for a CUSTOM_ORG_UNITS role: the one a request unit may hang on. */
    UUID openRequest() throws SQLException {
        return request("CREATE_ROLE", "PENDING", "CUSTOM_ORG_UNITS");
    }

    UUID roleVersionUnit(UUID version, UUID orgUnit, boolean descendants) throws SQLException {
        UUID id = UUID.randomUUID();
        setup(ROLE_VERSION_UNIT_INSERT, id, version, orgUnit, descendants, T0, T0);
        return id;
    }

    UUID requestUnit(UUID request, UUID orgUnit) throws SQLException {
        UUID id = UUID.randomUUID();
        setup(REQUEST_UNIT_INSERT, id, request, orgUnit, T0, T0);
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
