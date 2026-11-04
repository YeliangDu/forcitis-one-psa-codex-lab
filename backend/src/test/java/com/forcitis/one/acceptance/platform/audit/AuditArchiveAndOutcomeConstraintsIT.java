package com.forcitis.one.acceptance.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.acceptance.P5Names;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * DATA-REPAIR-timesheet-audit PR-3 (TB-06, TB-08; one-data ruling 2026-10-16): black-box checks of the database constraints.
 * Each refused case violates exactly one constraint and the test asserts its name and SQLSTATE; every insert runs inside a
 * savepoint of a transaction that is rolled back (both tables refuse update and delete, so rollback leaves nothing behind).
 * Audit entries carry an explicit created_at inside a pre-built partition (no DEFAULT partition, ADR-0048).
 * Not pinned on purpose (ruled out): paired outcome/method/address, "address implies outcome", first/last same-month CHECK.
 */
class AuditArchiveAndOutcomeConstraintsIT extends AcceptanceSupport {

    static final String CHECK_VIOLATION = "23514";
    static final String UNIQUE_VIOLATION = "23505";
    static final String ACCEPTED = "accepted";
    static final String UK_FIRST_ENTRY_ID = "uk_audit_archive_batches_first_entry_id";
    static final String UK_OBJECT_KEY = "uk_audit_archive_batches_object_key";
    static final String CK_SHA256 = "ck_audit_archive_batches_sha256_hash";
    static final String CK_LOGIN_METHOD = "ck_audit_entries_login_method_outcome";
    static final String VALID_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    static final TestDatabase postgres = TestPostgres.database(AuditArchiveAndOutcomeConstraintsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** SQLSTATE and constraint name of what the database answered; state is "accepted" when the row went in. */
    private record Outcome(String state, String constraint) {
        static Outcome accepted() {
            return new Outcome(ACCEPTED, null);
        }
    }

    private static final String INSERT_BATCH = "insert into audit_archive_batches (id, first_entry_id, first_entry_created_at, "
            + "last_entry_id, last_entry_created_at, entry_count, object_key, sha256_hash, archived_at) values (uuidv7(), ?, ?, ?, ?, 1, ?, ?, ?)";

    private void insertBatch(Connection audit, UUID firstEntryId, UUID lastEntryId, String objectKey, String hash) throws SQLException {
        try (PreparedStatement insert = audit.prepareStatement(INSERT_BATCH)) {
            insert.setObject(1, firstEntryId);
            insert.setTimestamp(2, Timestamp.from(NOW.minus(Duration.ofDays(2))));
            insert.setObject(3, lastEntryId);
            insert.setTimestamp(4, Timestamp.from(NOW.minus(Duration.ofDays(1))));
            insert.setString(5, objectKey);
            insert.setString(6, hash);
            insert.setTimestamp(7, Timestamp.from(NOW));
            insert.executeUpdate();
        }
    }

    private Outcome batch(Connection audit, UUID firstEntryId, UUID lastEntryId, String objectKey, String hash) throws SQLException {
        Savepoint before = audit.setSavepoint();
        try {
            insertBatch(audit, firstEntryId, lastEntryId, objectKey, hash);
            return Outcome.accepted();
        } catch (SQLException refused) {
            return new Outcome(refused.getSQLState(), constraintOf(refused));
        } finally {
            audit.rollback(before);
        }
    }

    private Outcome entry(Connection audit, String outcome, String method) throws SQLException {
        Savepoint before = audit.setSavepoint();
        try (PreparedStatement insert = audit.prepareStatement("insert into audit_entries (id, action, subject_kind, subject_id, outcome_kind, "
                + "login_method_kind, created_at) values (uuidv7(), 'tb08.synthetic', 'USER_ACCOUNT', ?, ?, ?, ?)")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setString(2, outcome);
            insert.setString(3, method);
            insert.setTimestamp(4, Timestamp.from(NOW)); // explicit created_at, inside the pre-built 2026-11 partition
            insert.executeUpdate();
            return Outcome.accepted();
        } catch (SQLException refused) {
            return new Outcome(refused.getSQLState(), constraintOf(refused));
        } finally {
            audit.rollback(before);
        }
    }

    private static String constraintOf(SQLException refused) {
        return refused instanceof PSQLException psql && psql.getServerErrorMessage() != null ? psql.getServerErrorMessage().getConstraint() : null;
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-06：重复 first_entry_id 被 uk_audit_archive_batches_first_entry_id 拒（23505），其余列各不相同；不同 first_entry_id 通过")
    void duplicateFirstEntryIdIsRefused() throws Exception {
        try (Connection audit = schemaConnection(P5Names.AUDIT_SCHEMA)) {
            audit.setAutoCommit(false);
            UUID first = UUID.randomUUID();
            insertBatch(audit, first, UUID.randomUUID(), "archive/tb06/seed-a", VALID_HASH);
            assertThat(batch(audit, first, UUID.randomUUID(), "archive/tb06/other-a", VALID_HASH)).isEqualTo(new Outcome(UNIQUE_VIOLATION, UK_FIRST_ENTRY_ID));
            assertThat(batch(audit, UUID.randomUUID(), UUID.randomUUID(), "archive/tb06/other-b", VALID_HASH)).isEqualTo(Outcome.accepted());
            audit.rollback();
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-06：重复 object_key 被 uk_audit_archive_batches_object_key 拒（23505），其余列各不相同；不同 object_key 通过")
    void duplicateObjectKeyIsRefused() throws Exception {
        try (Connection audit = schemaConnection(P5Names.AUDIT_SCHEMA)) {
            audit.setAutoCommit(false);
            insertBatch(audit, UUID.randomUUID(), UUID.randomUUID(), "archive/tb06/seed-b", VALID_HASH);
            assertThat(batch(audit, UUID.randomUUID(), UUID.randomUUID(), "archive/tb06/seed-b", VALID_HASH)).isEqualTo(new Outcome(UNIQUE_VIOLATION, UK_OBJECT_KEY));
            assertThat(batch(audit, UUID.randomUUID(), UUID.randomUUID(), "archive/tb06/seed-c", VALID_HASH)).isEqualTo(Outcome.accepted());
            audit.rollback();
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-06：sha256_hash 须为 64 位小写十六进制——大写、63 位、65 位、含非十六进制字符、空串被 ck_audit_archive_batches_sha256_hash 拒（23514）；合法值通过")
    void invalidHashIsRefused() throws Exception {
        try (Connection audit = schemaConnection(P5Names.AUDIT_SCHEMA)) {
            audit.setAutoCommit(false);
            Outcome refused = new Outcome(CHECK_VIOLATION, CK_SHA256);
            assertThat(batch(audit, UUID.randomUUID(), UUID.randomUUID(), "archive/tb06/h1", VALID_HASH.toUpperCase())).as("uppercase").isEqualTo(refused);
            assertThat(batch(audit, UUID.randomUUID(), UUID.randomUUID(), "archive/tb06/h2", VALID_HASH.substring(1))).as("63 characters").isEqualTo(refused);
            assertThat(batch(audit, UUID.randomUUID(), UUID.randomUUID(), "archive/tb06/h3", VALID_HASH + "a")).as("65 characters").isEqualTo(refused);
            assertThat(batch(audit, UUID.randomUUID(), UUID.randomUUID(), "archive/tb06/h4", "g" + VALID_HASH.substring(1))).as("non-hex character").isEqualTo(refused);
            assertThat(batch(audit, UUID.randomUUID(), UUID.randomUUID(), "archive/tb06/h5", "")).as("empty").isEqualTo(refused);
            assertThat(batch(audit, UUID.randomUUID(), UUID.randomUUID(), "archive/tb06/h6", VALID_HASH)).as("valid").isEqualTo(Outcome.accepted());
            assertThat(batch(audit, UUID.randomUUID(), UUID.randomUUID(), "archive/tb06/h7", "f".repeat(64))).as("valid, all f").isEqualTo(Outcome.accepted());
            audit.rollback();
        }
    }

    // RULE-486
    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-08：有登录方式而无结果被 ck_audit_entries_login_method_outcome 拒（23514），每种登录方式都一样")
    void methodWithoutOutcomeIsRefused() throws Exception {
        try (Connection audit = schemaConnection(P5Names.AUDIT_SCHEMA)) {
            audit.setAutoCommit(false);
            for (String method : new String[] {"WECOM_WORKBENCH", "WECOM_MINI_PROGRAM", "WECOM_QR_CODE_LOGIN", "SSO", "SIGNED_TICKET", "EMERGENCY"}) {
                assertThat(entry(audit, null, method)).as(method + " without outcome").isEqualTo(new Outcome(CHECK_VIOLATION, CK_LOGIN_METHOD));
            }
            audit.rollback();
        }
    }

    // RULE-486
    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-08：有结果无方式、两者皆空、两者皆有都通过")
    void otherCombinationsOfOutcomeAndMethodAreAccepted() throws Exception {
        try (Connection audit = schemaConnection(P5Names.AUDIT_SCHEMA)) {
            audit.setAutoCommit(false);
            assertThat(entry(audit, "SUCCEEDED", null)).as("outcome only").isEqualTo(Outcome.accepted());
            assertThat(entry(audit, "FAILED", null)).as("failed outcome only").isEqualTo(Outcome.accepted());
            assertThat(entry(audit, null, null)).as("both empty").isEqualTo(Outcome.accepted());
            assertThat(entry(audit, "SUCCEEDED", "SSO")).as("both, succeeded").isEqualTo(Outcome.accepted());
            assertThat(entry(audit, "FAILED", "EMERGENCY")).as("both, failed").isEqualTo(Outcome.accepted());
            audit.rollback();
        }
    }
}
