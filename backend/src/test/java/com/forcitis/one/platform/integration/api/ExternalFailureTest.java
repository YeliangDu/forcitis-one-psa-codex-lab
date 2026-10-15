package com.forcitis.one.platform.integration.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.UncategorizedSQLException;

class ExternalFailureTest {

    static final String VALUE = "row value 7731";

    @Test
    @DisplayName("ADR37-1 复审 L-3 a failure names its type, the first SQL error number and SQLState and the innermost cause type, never a message")
    void sqlFailure() {
        SQLException sql = new SQLException("conversion failed for " + VALUE, "22018", 245, new ConnectException(VALUE));
        String told = ExternalFailure.of(new UncategorizedSQLException("read", "SELECT " + VALUE, sql)).toString();

        assertThat(told).isEqualTo("UncategorizedSQLException, error 245, SQLState 22018, innermost ConnectException");
    }

    @Test
    @DisplayName("ADR37-1 复审 L-3 a failure without an SQL error says so and still names the innermost cause type")
    void noSqlError() {
        String told = ExternalFailure.of(new DataAccessResourceFailureException(VALUE, new ConnectException(VALUE))).toString();

        assertThat(told).isEqualTo("DataAccessResourceFailureException, no SQL error, innermost ConnectException").doesNotContain(VALUE);
    }
}
