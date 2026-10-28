package com.forcitis.one.acceptance.platform.identity.role_versioning;

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
 * ADR-0034-IMPL card 7.2: rows of platform_identity.roles on the database the application's own migrations build (no Spring context, so
 * no start-up reconciliation has run). One transaction per test that is never committed; a probe runs under a savepoint that is rolled
 * back, so a refused statement does not poison the next.
 */
final class MigratedRoleRows implements AutoCloseable {

    static final String RESTRICT_VIOLATION = "23001";

    private static final Map<Class<?>, TestDatabase> DATABASES = new ConcurrentHashMap<>();

    private final Connection connection;

    MigratedRoleRows(Class<?> testClass) throws SQLException {
        TestDatabase database = DATABASES.computeIfAbsent(testClass, c -> TestPostgres.database(c, TestPostgres.MIGRATED));
        connection = DriverManager.getConnection(database.url(), database.username(), database.password());
        connection.setAutoCommit(false);
    }

    /**
     * A CUSTOM, non-administrative role row. Before card 7.2 the row also needs a name (the column leaves roles in PR-2); the test then
     * still runs, so that it fails on the behaviour it is about and not on the fixture.
     */
    UUID customRole() throws SQLException {
        UUID id = UUID.randomUUID();
        String code = "rv_" + id.toString().substring(0, 8);
        boolean hasName = count("select count(*) from information_schema.columns where table_schema = 'platform_identity'"
                + " and table_name = 'roles' and column_name = 'name'") == 1;
        if (hasName) {
            run("insert into platform_identity.roles (role_id, code, name) values (?, ?, ?)", id, code, "role versioning " + id);
        } else {
            run("insert into platform_identity.roles (role_id, code) values (?, ?)", id, code);
        }
        return id;
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

    /** Refused by a RAISE in the named function with restrict_violation (23001), not by a constraint. */
    void refusedByTrigger(String function, String sql, Object... parameters) throws SQLException {
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
        assertThat(refusal.getSQLState()).as(refusal.getMessage()).isEqualTo(RESTRICT_VIOLATION);
        assertThat(refusal.getServerErrorMessage()).isNotNull();
        assertThat(refusal.getServerErrorMessage().getConstraint()).as("a trigger refusal names no constraint: " + refusal.getMessage()).isNull();
        assertThat(refusal.getServerErrorMessage().getWhere()).as(refusal.getMessage()).contains("PL/pgSQL function platform_identity." + function + "()");
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

    void run(String sql, Object... parameters) throws SQLException {
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
