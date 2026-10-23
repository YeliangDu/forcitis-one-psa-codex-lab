package com.forcitis.one.acceptance.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.acceptance.P5Names;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R-1b P5 ADR-0033 section 7 item 2 and item 6 (card acceptance item 1, D-5, D-7): an audit entry names its object in two columns, a
 * kind and the object's own id, not in a "type:id" text; the kinds without an object leave the id empty and only they do; the archive
 * batch carries the moments of its first and last entry. Read from the catalogue of the migrated database; the checks that insert use a
 * transaction that is rolled back (the table refuses update and delete, so a rollback is how the test leaves nothing behind).
 */
@Testcontainers
class AuditSubjectColumnsIT extends AcceptanceSupport {

    static final String CHECK_VIOLATION = "23514";
    static final String FOREIGN_KEY_VIOLATION = "23503";
    static final String NOT_NULL_VIOLATION = "23502";
    static final String ACCEPTED = "accepted";
    static final String NEW_IX_SUBJECT = "ix_audit_entries_subject_kind_subject_id_created_at";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    private String scalar(Connection catalogue, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement select = catalogue.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                select.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String columnType(Connection catalogue, String table, String column) throws SQLException {
        return scalar(catalogue, "select data_type from information_schema.columns where table_schema = ? and table_name = ? and column_name = ?",
                P5Names.AUDIT_SCHEMA, table, column);
    }

    private String nullable(Connection catalogue, String table, String column) throws SQLException {
        return scalar(catalogue, "select is_nullable from information_schema.columns where table_schema = ? and table_name = ? and column_name = ?",
                P5Names.AUDIT_SCHEMA, table, column);
    }

    private String comment(Connection catalogue, String table, String column) throws SQLException {
        return scalar(catalogue, """
                select col_description(c.oid, a.attnum) from pg_class c join pg_namespace n on n.oid = c.relnamespace
                  join pg_attribute a on a.attrelid = c.oid
                 where n.nspname = ? and c.relname = ? and a.attname = ?""", P5Names.AUDIT_SCHEMA, table, column);
    }

    @Test
    @DisplayName("R-1b P5 ADR-0033 第 7 项第 2 条：audit_entries 无 subject_key；subject_kind 为 text 非空带外键（ADR-0069 第 14 条取代 CHECK），subject_id 为 uuid；两列注释含 P4 同一句的「被记录或被投递的业务对象」，subject_id 另写明为空的含义")
    void theSubjectIsTwoColumns() throws Exception {
        try (Connection catalogue = schemaConnection("public")) {
            assertThat(columnType(catalogue, P5Names.AUDIT_ENTRIES, P5Names.SUBJECT_KEY_OLD)).as("the text key is gone").isNull();
            assertThat(columnType(catalogue, P5Names.AUDIT_ENTRIES, P5Names.SUBJECT_KIND)).isEqualTo("text");
            assertThat(nullable(catalogue, P5Names.AUDIT_ENTRIES, P5Names.SUBJECT_KIND)).isEqualTo("NO");
            assertThat(columnType(catalogue, P5Names.AUDIT_ENTRIES, P5Names.SUBJECT_ID)).isEqualTo("uuid");
            assertThat(nullable(catalogue, P5Names.AUDIT_ENTRIES, P5Names.SUBJECT_ID)).as("empty for the kinds without an object (D-5)").isEqualTo("YES");
            assertThat(scalar(catalogue, """
                    select pg_get_constraintdef(k.oid) from pg_constraint k
                     where k.conrelid = 'platform_audit.audit_entries'::regclass and k.conname = ?""", P5Names.FK_SUBJECT_KIND))
                    .as(P5Names.FK_SUBJECT_KIND).isNotNull().contains(P5Names.SUBJECT_KIND).contains("audit_subject_kinds");
            assertThat(comment(catalogue, P5Names.AUDIT_ENTRIES, P5Names.SUBJECT_KIND)).contains(P5Names.SUBJECT_COLUMN_COMMENT);
            assertThat(comment(catalogue, P5Names.AUDIT_ENTRIES, P5Names.SUBJECT_ID)).contains(P5Names.SUBJECT_COLUMN_COMMENT)
                    .contains(P5Names.EMPTY_ID_COMMENT);
        }
    }

    @Test
    @DisplayName("ADR-0033 第 7 项第 2 条 / ADR-0048（RULING-DR-PR1-1）：旧索引 audit_entries_subject_idx 不存在，新索引 ix_audit_entries_subject_kind_subject_id_created_at 列为 (subject_kind, subject_id, created_at DESC, id DESC)")
    void theSubjectIndexIsRenamedAndGetsNewColumns() throws Exception {
        try (Connection catalogue = schemaConnection("public")) {
            assertThat(scalar(catalogue, "select indexdef from pg_indexes where schemaname = ? and indexname = ?", P5Names.AUDIT_SCHEMA, P5Names.IX_SUBJECT))
                    .as("the old name " + P5Names.IX_SUBJECT + " is gone").isNull();
            String definition = scalar(catalogue, "select indexdef from pg_indexes where schemaname = ? and indexname = ?", P5Names.AUDIT_SCHEMA, NEW_IX_SUBJECT);
            assertThat(definition).as(NEW_IX_SUBJECT).isNotNull()
                    .contains("(" + P5Names.SUBJECT_KIND + ", " + P5Names.SUBJECT_ID + ", created_at DESC, id DESC)").doesNotContain(P5Names.SUBJECT_KEY_OLD);
        }
    }

    private String insert(Connection audit, String kind, UUID subjectId) throws SQLException {
        Savepoint before = audit.setSavepoint();
        try (PreparedStatement insert = audit.prepareStatement("insert into audit_entries (id, action, " + P5Names.SUBJECT_KIND + ", " + P5Names.SUBJECT_ID
                + ", created_at) values (uuidv7(), 'p5.synthetic', ?, ?, ?)")) {
            insert.setString(1, kind);
            insert.setObject(2, subjectId);
            insert.setTimestamp(3, Timestamp.from(NOW)); // created_at has no default (ADR-0048); NOW falls in a pre-built partition
            insert.executeUpdate();
            audit.rollback(before);
            return ACCEPTED;
        } catch (SQLException refused) {
            audit.rollback(before);
            return refused.getSQLState();
        }
    }

    @Test
    @DisplayName("R-1b P5 RULING-P5-D5：subject_id 为空当且仅当类别是 REQUEST / OPERATION / LOGIN_ATTEMPT；类别只取登记表中的值：其他值带 ID 时被外键拒绝（23503），不带 ID 时 CHECK 先于外键拒绝（23514）；登记表的种子集合与 KINDS_* 一致，has_object 与 ck_audit_entries_subject_id 一致；旧的类别 CHECK 已不存在")
    void theIdIsEmptyExactlyForTheKindsWithoutAnObject() throws Exception {
        List<String> wrong = new ArrayList<>();
        try (Connection audit = schemaConnection(P5Names.AUDIT_SCHEMA)) {
            audit.setAutoCommit(false);
            try (var kinds = audit.prepareStatement("select code, has_object from platform_audit.audit_subject_kinds where audit_subject_kind_id::text like '019a0000-0000-7000-8000-000000000%'");
                    var rows = kinds.executeQuery()) {
                List<String> withObject = new ArrayList<>();
                List<String> withoutObject = new ArrayList<>();
                while (rows.next()) {
                    (rows.getBoolean("has_object") ? withObject : withoutObject).add(rows.getString("code"));
                }
                assertThat(withObject).as("registry kinds with an object").containsExactlyInAnyOrderElementsOf(P5Names.KINDS_WITH_OBJECT);
                assertThat(withoutObject).as("registry kinds without an object, the same three the ck_audit_entries_subject_id lists").containsExactlyInAnyOrderElementsOf(P5Names.KINDS_WITHOUT_OBJECT);
            }
            try (var gone = audit.prepareStatement("select count(*) from pg_constraint where conrelid = 'platform_audit.audit_entries'::regclass and conname = ?")) {
                gone.setString(1, P5Names.CK_SUBJECT_KIND);
                try (var found = gone.executeQuery()) {
                    found.next();
                    assertThat(found.getLong(1)).as("the old kind CHECK is gone, the foreign key replaced it").isZero();
                }
            }
            for (String kind : P5Names.KINDS_WITH_OBJECT) {
                expect(wrong, kind + " with an id", insert(audit, kind, UUID.randomUUID()), ACCEPTED);
                expect(wrong, kind + " without an id", insert(audit, kind, null), CHECK_VIOLATION);
            }
            for (String kind : P5Names.KINDS_WITHOUT_OBJECT) {
                expect(wrong, kind + " without an id", insert(audit, kind, null), ACCEPTED);
                expect(wrong, kind + " with an id", insert(audit, kind, UUID.randomUUID()), CHECK_VIOLATION);
            }
            expect(wrong, "an unknown kind", insert(audit, "BOGUS", UUID.randomUUID()), FOREIGN_KEY_VIOLATION);
            // subject_id is empty here, so ck_audit_entries_subject_id (a CHECK) is evaluated before the foreign key and refuses with 23514
            expect(wrong, "an unknown kind without an id", insert(audit, "BOGUS", null), CHECK_VIOLATION);
            expect(wrong, "a kind spelt as the old text prefix", insert(audit, "user-account", UUID.randomUUID()), FOREIGN_KEY_VIOLATION);
            audit.rollback();
        }

        assertThat(wrong).isEmpty();
    }

    private static void expect(List<String> wrong, String what, String actual, String expected) {
        if (!expected.equals(actual)) {
            wrong.add(what + ": expected " + expected + " but got " + actual);
        }
    }

    @Test
    @DisplayName("R-1b P5 ADR-0033 第 7 项第 6 条 / RULING-P5-D7：audit_archive_batches 新增 first_entry_created_at、last_entry_created_at（timestamptz、非空），水位是时刻加 ID")
    void archiveBatchesCarryTheMomentsOfTheirEntries() throws Exception {
        try (Connection catalogue = schemaConnection("public")) {
            for (String column : List.of(P5Names.FIRST_ENTRY_CREATED_AT, P5Names.LAST_ENTRY_CREATED_AT)) {
                assertThat(columnType(catalogue, P5Names.ARCHIVE_BATCHES, column)).as(column).isEqualTo("timestamp with time zone");
                assertThat(nullable(catalogue, P5Names.ARCHIVE_BATCHES, column)).as("nullable " + column).isEqualTo("NO");
                assertThat(comment(catalogue, P5Names.ARCHIVE_BATCHES, column)).as("comment on " + column).isNotBlank();
            }
            for (String kept : List.of("first_entry_id", "last_entry_id", "entry_count")) {
                assertThat(columnType(catalogue, P5Names.ARCHIVE_BATCHES, kept)).as("kept " + kept).isNotNull();
            }
        }
    }
}
