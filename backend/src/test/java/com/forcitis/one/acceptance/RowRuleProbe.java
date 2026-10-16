package com.forcitis.one.acceptance;

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
import org.postgresql.util.PSQLException;

/**
 * Black-box probe of row rules on the migrated database (the application's own migrations, no Spring context). One transaction that is
 * never committed; every probe statement runs under a savepoint that is rolled back, so each statement stands alone and a refused one
 * does not poison the next. A statement that must be refused is checked for SQLSTATE and constraint name; one that must go through is
 * only executed.
 */
public final class RowRuleProbe implements AutoCloseable {

    public static final String CHECK_VIOLATION = "23514";

    /** TestPostgres hands out one database per class and purpose; the class's tests share it and each rolls back. */
    private static final java.util.Map<Class<?>, TestDatabase> DATABASES = new java.util.concurrent.ConcurrentHashMap<>();

    private final Connection connection;

    public RowRuleProbe(Class<?> testClass) throws SQLException {
        TestDatabase database = DATABASES.computeIfAbsent(testClass, c -> TestPostgres.database(c, TestPostgres.MIGRATED));
        connection = DriverManager.getConnection(database.url(), database.username(), database.password());
        connection.setAutoCommit(false);
    }

    /** The statement runs and is undone. Fails loudly when the database refuses it. */
    public void accepted(String sql, Object... parameters) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            run(sql, parameters);
        } finally {
            connection.rollback(savepoint);
        }
    }

    /** The statement runs and is kept for the rest of the test (setup rows). */
    public void setup(String sql, Object... parameters) throws SQLException {
        run(sql, parameters);
    }

    /** The statement must be refused with this SQLSTATE and constraint name. */
    public void refused(String sqlState, String constraint, String sql, Object... parameters) throws SQLException {
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
        assertThat(refusal.getSQLState()).as(refusal.getMessage()).isEqualTo(sqlState);
        String actual = refusal.getServerErrorMessage() == null ? null : refusal.getServerErrorMessage().getConstraint();
        assertThat(actual).as(refusal.getMessage()).isEqualTo(constraint);
    }

    public void checkRefused(String constraint, String sql, Object... parameters) throws SQLException {
        refused(CHECK_VIOLATION, constraint, sql, parameters);
    }

    public List<Object> column(String sql, Object... parameters) throws SQLException {
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

    public long count(String sql, Object... parameters) throws SQLException {
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
