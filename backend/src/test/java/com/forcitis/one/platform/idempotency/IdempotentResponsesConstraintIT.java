package com.forcitis.one.platform.idempotency;

import com.forcitis.one.BusinessDay;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.IdempotentResponses;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Each named CHECK of platform_idempotency.idempotent_responses refuses a row that breaks it and lets a lawful row through. */
class IdempotentResponsesConstraintIT {
    static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    static final String INSERT = """
            insert into platform_idempotency.idempotent_responses
                (idempotent_response_id, user_id, idempotency_key, request_hash, response_number, response_name, response_text, response_header_text, expires_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    static Connection connection;

    @BeforeAll
    static void connect() throws SQLException {
        TestDatabase database = TestPostgres.database(IdempotentResponsesConstraintIT.class, TestPostgres.MIGRATED);
        connection = DriverManager.getConnection(database.url(), database.username(), database.password());
    }

    @AfterAll
    static void disconnect() throws SQLException {
        connection.close();
    }

    private static void insert(String key, String hash, Integer number, String name, String text, String headers) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, UUID.randomUUID());
            statement.setString(3, key);
            statement.setString(4, hash);
            statement.setObject(5, number);
            statement.setString(6, name);
            statement.setString(7, text);
            statement.setString(8, headers);
            statement.setTimestamp(9, Timestamp.from(BusinessDay.instant().plusSeconds(86400)));
            statement.executeUpdate();
        }
    }

    private static void assertRefusedBy(String constraint, org.assertj.core.api.ThrowableAssert.ThrowingCallable insert) {
        assertThatThrownBy(insert).isInstanceOf(SQLException.class).hasMessageContaining(constraint);
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-03: idempotency_key of 1 to 128 characters passes, an empty one and one of 129 are refused")
    void idempotencyKeyLength() throws SQLException {
        assertThatCode(() -> insert("k", HASH, null, null, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> insert("k".repeat(128), HASH, null, null, null, null)).doesNotThrowAnyException();
        assertRefusedBy("ck_idempotent_responses_idempotency_key", () -> insert("", HASH, null, null, null, null));
        assertRefusedBy("ck_idempotent_responses_idempotency_key", () -> insert("k".repeat(129), HASH, null, null, null, null));
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-03: request_hash of 64 lowercase hex characters passes, uppercase, short and non-hex are refused")
    void requestHashFormat() throws SQLException {
        assertThatCode(() -> insert("hash-ok", HASH, null, null, null, null)).doesNotThrowAnyException();
        assertRefusedBy("ck_idempotent_responses_request_hash", () -> insert("hash-upper", HASH.toUpperCase(), null, null, null, null));
        assertRefusedBy("ck_idempotent_responses_request_hash", () -> insert("hash-short", HASH.substring(1), null, null, null, null));
        assertRefusedBy("ck_idempotent_responses_request_hash", () -> insert("hash-nonhex", "g" + HASH.substring(1), null, null, null, null));
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-03: response_number 100 and 599 pass, 99 and 600 are refused")
    void responseNumberRange() throws SQLException {
        assertThatCode(() -> insert("num-100", HASH, 100, "application/json", "{}", "{}")).doesNotThrowAnyException();
        assertThatCode(() -> insert("num-599", HASH, 599, "application/json", "{}", "{}")).doesNotThrowAnyException();
        assertRefusedBy("ck_idempotent_responses_response_number", () -> insert("num-99", HASH, 99, "application/json", "{}", "{}"));
        assertRefusedBy("ck_idempotent_responses_response_number", () -> insert("num-600", HASH, 600, "application/json", "{}", "{}"));
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-03: a row in progress has all four response columns empty, a finished row may carry them; a half-empty in-progress row is refused")
    void inProgressRowHasNoResponse() throws SQLException {
        assertThatCode(() -> insert("pair-progress", HASH, null, null, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> insert("pair-done", HASH, 201, "application/json", "{}", "{}")).doesNotThrowAnyException();
        assertThatCode(() -> insert("pair-done-bare", HASH, 204, null, null, null)).doesNotThrowAnyException();
        assertRefusedBy("ck_idempotent_responses_in_progress", () -> insert("pair-name", HASH, null, "application/json", null, null));
        assertRefusedBy("ck_idempotent_responses_in_progress", () -> insert("pair-text", HASH, null, null, "{}", null));
        assertRefusedBy("ck_idempotent_responses_in_progress", () -> insert("pair-headers", HASH, null, null, null, "{}"));
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-02: response_text of 32768 characters passes, 32769 is refused; the limit is the constant the filter uses")
    void responseTextLength() throws SQLException {
        assertThatCode(() -> insert("text-limit", HASH, 200, "text/plain", "x".repeat(IdempotentResponses.MAX_RESPONSE_LENGTH), "{}")).doesNotThrowAnyException();
        assertRefusedBy("ck_idempotent_responses_response_text",
                () -> insert("text-over", HASH, 200, "text/plain", "x".repeat(IdempotentResponses.MAX_RESPONSE_LENGTH + 1), "{}"));
    }
}
