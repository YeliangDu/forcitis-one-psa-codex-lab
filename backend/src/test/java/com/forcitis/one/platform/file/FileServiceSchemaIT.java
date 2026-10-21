package com.forcitis.one.platform.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The database half of ADR-0060, 0061 and 0070 as signed by one-data (unit 3): what the triggers and constraints refuse on their own. */
@DisplayName("platform_file schema: immutability, transitions, four eyes")
class FileServiceSchemaIT {
    static final TestDatabase DATABASE = TestPostgres.database(FileServiceSchemaIT.class, TestPostgres.MIGRATED);
    static final String TRIGGER = "23001";
    static final String CHECK = "23514";
    static final String FOREIGN_KEY = "23503";
    static final String HASH = "a".repeat(64);
    static final UUID USER_A = UUID.fromString("01900000-0000-7000-8000-00000000000a");
    static final UUID USER_B = UUID.fromString("01900000-0000-7000-8000-00000000000b");

    Connection c;

    @BeforeEach
    void open() throws SQLException {
        c = DriverManager.getConnection(DATABASE.url(), DATABASE.username(), DATABASE.password());
        c.setAutoCommit(false);
    }

    @AfterEach
    void close() throws SQLException {
        c.rollback();
        c.close();
    }

    private void sql(String statement) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("SAVEPOINT step");
            s.execute(statement);
            s.execute("RELEASE SAVEPOINT step");
        } catch (SQLException e) {
            try (Statement s = c.createStatement()) {
                s.execute("ROLLBACK TO SAVEPOINT step");
            }
            throw e;
        }
    }

    private void refused(String statement, String state) {
        assertThatThrownBy(() -> sql(statement)).isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).as(e.getMessage()).isEqualTo(state));
    }

    private static UUID id() {
        return UUID.randomUUID();
    }

    private UUID storedFile(String status) throws SQLException {
        UUID file = id();
        String key = status.equals("QUARANTINED") ? "dev/quarantine/" + file : "dev/files/2026/10/" + file;
        String threat = status.equals("QUARANTINED") ? "'Eicar-Signature'" : "null";
        sql("""
                insert into platform_file.stored_files (stored_file_id, object_key, content_hash, byte_count, media_type_code, uploader_kind,
                    uploader_user_id, file_status, has_macros, scan_engine_version_code, scan_definitions_version_code, threat_name,
                    is_released_by_exception, stored_at, created_at, updated_at)
                values ('%s', '%s', '%s', 7, 'application/pdf', 'USER', '%s', '%s', false, 'ClamAV 1.4.1', '27435', %s, false, now(), now(), now())
                """.formatted(file, key, HASH, USER_A, status, threat));
        return file;
    }

    @Test
    @DisplayName("a stored file keeps its object key and content; its status moves only AVAILABLE -> EXPIRED_UNBOUND; it is never deleted")
    void storedFilesAreImmutable() throws SQLException {
        UUID file = storedFile("AVAILABLE");
        refused("update platform_file.stored_files set content_hash = '" + "b".repeat(64) + "' where stored_file_id = '" + file + "'", TRIGGER);
        refused("update platform_file.stored_files set object_key = 'dev/files/2026/11/" + file + "' where stored_file_id = '" + file + "'", TRIGGER);
        refused("update platform_file.stored_files set uploader_user_id = '" + USER_B + "' where stored_file_id = '" + file + "'", TRIGGER);
        refused("delete from platform_file.stored_files where stored_file_id = '" + file + "'", TRIGGER);
        sql("update platform_file.stored_files set file_status = 'EXPIRED_UNBOUND' where stored_file_id = '" + file + "'");
        refused("update platform_file.stored_files set file_status = 'AVAILABLE' where stored_file_id = '" + file + "'", TRIGGER);
        UUID quarantined = storedFile("QUARANTINED");
        refused("update platform_file.stored_files set file_status = 'AVAILABLE' where stored_file_id = '" + quarantined + "'", TRIGGER);
        refused("truncate table platform_file.stored_files cascade", TRIGGER);
    }

    @Test
    @DisplayName("the object key ends with the file's own id, a quarantined file sits under quarantine with a threat name, one uploader column")
    void storedFileShapeIsChecked() throws SQLException {
        String columns = "(stored_file_id, object_key, content_hash, byte_count, media_type_code, uploader_kind, uploader_user_id, uploader_link_id, file_status,"
                + " has_macros, scan_engine_version_code, scan_definitions_version_code, threat_name, is_released_by_exception, stored_at, created_at, updated_at)";
        UUID file = id();
        refused("insert into platform_file.stored_files " + columns + " values ('" + file + "', 'dev/files/2026/10/" + id() + "', '" + HASH
                + "', 7, 'application/pdf', 'USER', '" + USER_A + "', null, 'AVAILABLE', false, 'e', 'd', null, false, now(), now(), now())", CHECK);
        refused("insert into platform_file.stored_files " + columns + " values ('" + file + "', 'dev/files/2026/10/" + file + "', '" + HASH
                + "', 7, 'application/pdf', 'USER', '" + USER_A + "', '" + id() + "', 'AVAILABLE', false, 'e', 'd', null, false, now(), now(), now())", CHECK);
        refused("insert into platform_file.stored_files " + columns + " values ('" + file + "', 'dev/files/2026/10/" + file + "', '" + HASH
                + "', 7, 'application/pdf', 'USER', '" + USER_A + "', null, 'QUARANTINED', false, 'e', 'd', 'X', false, now(), now(), now())", CHECK);
        refused("insert into platform_file.stored_files " + columns + " values ('" + file + "', 'dev/staging/user/" + file + "', '" + HASH
                + "', 7, 'application/pdf', 'USER', '" + USER_A + "', null, 'AVAILABLE', false, 'e', 'd', null, false, now(), now(), now())", CHECK);
    }

    @Test
    @DisplayName("an original name is de-identified once, to the fixed placeholder only")
    void fileNamesAreDeidentifiedOnce() throws SQLException {
        UUID file = storedFile("AVAILABLE");
        sql("insert into platform_file.stored_file_names (stored_file_id, original_name, is_deidentified, created_at, updated_at) values ('" + file
                + "', '合同.pdf', false, now(), now())");
        refused("update platform_file.stored_file_names set original_name = '别的.pdf' where stored_file_id = '" + file + "'", TRIGGER);
        refused("update platform_file.stored_file_names set original_name = 'x', is_deidentified = true, deidentified_at = now() where stored_file_id = '" + file + "'", TRIGGER);
        refused("insert into platform_file.stored_file_names (stored_file_id, original_name, is_deidentified, created_at, updated_at) values ('" + storedFile("AVAILABLE")
                + "', 'x', true, now(), now())", CHECK);
        sql("update platform_file.stored_file_names set original_name = '[已去标识]', is_deidentified = true, deidentified_at = now() where stored_file_id = '" + file + "'");
        refused("update platform_file.stored_file_names set deidentified_at = now() where stored_file_id = '" + file + "'", TRIGGER);
        refused("delete from platform_file.stored_file_names where stored_file_id = '" + file + "'", TRIGGER);
    }

    @Test
    @DisplayName("links are append-only and take no SPI until U-14 is decided")
    void bindingsAreAppendOnly() throws SQLException {
        UUID file = storedFile("AVAILABLE");
        String insert = "insert into platform_file.file_bindings (file_binding_id, stored_file_id, document_type, document_id, access_level_code,"
                + " personal_information_mark_code, bound_by_kind, bound_by_user_id, bound_at, created_at, updated_at) values ('%s', '" + file
                + "', 'SEAL_REQUEST', '" + id() + "', 'INTERNAL', '%s', 'USER', '" + USER_A + "', now(), now(), now())";
        refused(insert.formatted(id(), "SPI"), CHECK);
        UUID binding = id();
        sql(insert.formatted(binding, "PI"));
        refused("update platform_file.file_bindings set access_level_code = 'PUBLIC' where file_binding_id = '" + binding + "'", TRIGGER);
        refused("delete from platform_file.file_bindings where file_binding_id = '" + binding + "'", TRIGGER);
    }

    private UUID categoryVersion(UUID author) throws SQLException {
        UUID category = id();
        UUID version = id();
        sql("insert into platform_file.preservation_categories (preservation_category_id, category_code, category_name, created_at, updated_at) values ('"
                + category + "', 'CONTRACT_" + category.toString().substring(0, 8).toUpperCase().replaceAll("[^A-Z0-9]", "X") + "', '合同', now(), now())");
        sql("insert into platform_file.preservation_category_versions (preservation_category_version_id, preservation_category_id, version_number,"
                + " default_years_count, start_basis_text, effective_from_date, decision_ref_key, confirmation_status, author_id, authored_at, created_at, updated_at)"
                + " values ('" + version + "', '" + category + "', 1, 10, '合同履行完毕', date '2026-10-21', 'ADR-0061', 'PENDING', '" + author + "', now(), now(), now())");
        return version;
    }

    @Test
    @DisplayName("a category version is inserted pending and confirmed once by someone other than its author, its content never changes")
    void categoryVersionsNeedFourEyes() throws SQLException {
        UUID version = categoryVersion(USER_A);
        refused("update platform_file.preservation_category_versions set default_years_count = 20 where preservation_category_version_id = '" + version + "'", TRIGGER);
        refused("update platform_file.preservation_category_versions set confirmation_status = 'CONFIRMED', confirmed_by = '" + USER_A
                + "', confirmed_at = now() where preservation_category_version_id = '" + version + "'", CHECK);
        sql("update platform_file.preservation_category_versions set confirmation_status = 'CONFIRMED', confirmed_by = '" + USER_B
                + "', confirmed_at = now() where preservation_category_version_id = '" + version + "'");
        refused("update platform_file.preservation_category_versions set confirmed_by = '" + USER_A + "' where preservation_category_version_id = '" + version + "'", TRIGGER);
        UUID category = id();
        sql("insert into platform_file.preservation_categories (preservation_category_id, category_code, category_name, created_at, updated_at) values ('"
                + category + "', 'DELIVERABLE', '交付文件', now(), now())");
        refused("insert into platform_file.preservation_category_versions (preservation_category_version_id, preservation_category_id, version_number,"
                + " start_basis_text, effective_from_date, decision_ref_key, confirmation_status, author_id, authored_at, confirmed_by, confirmed_at, created_at, updated_at)"
                + " values ('" + id() + "', '" + category + "', 1, '项目结项', date '2026-10-21', 'ADR-0061', 'CONFIRMED', '" + USER_A + "', now(), '" + USER_B
                + "', now(), now(), now())", CHECK);
    }

    @Test
    @DisplayName("a file's preservation rows number 1, 2, ... with one category and anchor; the view shows the newest")
    void preservationRowsAreSequential() throws SQLException {
        UUID file = storedFile("AVAILABLE");
        UUID version = categoryVersion(USER_A);
        UUID anchor = id();
        String insert = "insert into platform_file.file_preservations (file_preservation_id, stored_file_id, preservation_sequence_number,"
                + " preservation_category_version_id, anchor_document_type, anchor_document_id, start_date, recorded_at, created_at, updated_at)"
                + " values ('%s', '" + file + "', %d, '" + version + "', 'CONTRACT', '%s', %s, now(), now(), now())";
        refused(insert.formatted(id(), 2, anchor, "null"), CHECK);
        sql(insert.formatted(id(), 1, anchor, "null"));
        refused(insert.formatted(id(), 2, id(), "date '2026-12-31'"), CHECK);
        UUID second = id();
        sql(insert.formatted(second, 2, anchor, "date '2026-12-31'"));
        try (Statement s = c.createStatement(); var rows = s.executeQuery("select file_preservation_id from platform_file.current_file_preservations where stored_file_id = '" + file + "'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getObject(1, UUID.class)).isEqualTo(second);
            assertThat(rows.next()).isFalse();
        }
        refused("update platform_file.file_preservations set start_date = null where file_preservation_id = '" + second + "'", TRIGGER);
    }

    @Test
    @DisplayName("a false positive needs a second person, and only a confirmed judgment puts a hash on the exception list")
    void falsePositivesNeedTwoPeople() throws SQLException {
        UUID file = storedFile("QUARANTINED");
        UUID judgment = id();
        sql("insert into platform_file.false_positive_judgments (false_positive_judgment_id, stored_file_id, content_hash, judgment_status, judgment_reason,"
                + " judged_by, judged_at, created_at, updated_at) values ('" + judgment + "', '" + file + "', '" + HASH + "', 'PENDING_REVIEW', '样本核对为误报', '"
                + USER_A + "', now(), now(), now())");
        refused("insert into platform_file.scan_exceptions (scan_exception_id, content_hash, false_positive_judgment_id, judgment_status, added_at, created_at, updated_at)"
                + " values ('" + id() + "', '" + HASH + "', '" + judgment + "', 'CONFIRMED', now(), now(), now())", FOREIGN_KEY);
        refused("update platform_file.false_positive_judgments set judgment_status = 'CONFIRMED', review_reason = '同意', reviewed_by = '" + USER_A
                + "', reviewed_at = now() where false_positive_judgment_id = '" + judgment + "'", CHECK);
        sql("update platform_file.false_positive_judgments set judgment_status = 'CONFIRMED', review_reason = '同意', reviewed_by = '" + USER_B
                + "', reviewed_at = now() where false_positive_judgment_id = '" + judgment + "'");
        refused("update platform_file.false_positive_judgments set judgment_status = 'REJECTED' where false_positive_judgment_id = '" + judgment + "'", TRIGGER);
        assertThatCode(() -> sql("insert into platform_file.scan_exceptions (scan_exception_id, content_hash, false_positive_judgment_id, judgment_status, added_at,"
                + " created_at, updated_at) values ('" + id() + "', '" + HASH + "', '" + judgment + "', 'CONFIRMED', now(), now(), now())")).doesNotThrowAnyException();
        refused("insert into platform_file.scan_exception_revocations (scan_exception_revocation_id, content_hash, revocation_reason, revoked_by, confirmed_by,"
                + " revoked_at, created_at, updated_at) values ('" + id() + "', '" + HASH + "', '误判', '" + USER_A + "', '" + USER_A + "', now(), now(), now())", CHECK);
        refused("delete from platform_file.scan_exceptions where content_hash = '" + HASH + "'", TRIGGER);
    }

    @Test
    @DisplayName("a quarantine read is approved by a second person, expires, and is read once")
    void quarantineReadsAreApprovedAndExpire() throws SQLException {
        UUID file = storedFile("QUARANTINED");
        UUID request = id();
        sql("insert into platform_file.quarantine_read_requests (quarantine_read_request_id, stored_file_id, request_status, request_reason, requested_by,"
                + " requested_at, created_at, updated_at) values ('" + request + "', '" + file + "', 'PENDING', '取证', '" + USER_A + "', now(), now(), now())");
        refused("update platform_file.quarantine_read_requests set request_status = 'READ', read_at = now() where quarantine_read_request_id = '" + request + "'", TRIGGER);
        refused("update platform_file.quarantine_read_requests set request_status = 'APPROVED', decided_by = '" + USER_A + "', decided_at = now(),"
                + " approval_expires_at = now() + interval '1 hour' where quarantine_read_request_id = '" + request + "'", CHECK);
        sql("update platform_file.quarantine_read_requests set request_status = 'APPROVED', decided_by = '" + USER_B + "', decided_at = now(),"
                + " approval_expires_at = now() + interval '1 hour' where quarantine_read_request_id = '" + request + "'");
        refused("update platform_file.quarantine_read_requests set request_status = 'READ', read_at = now() + interval '2 hours' where quarantine_read_request_id = '"
                + request + "'", CHECK);
        sql("update platform_file.quarantine_read_requests set request_status = 'READ', read_at = now() where quarantine_read_request_id = '" + request + "'");
        refused("update platform_file.quarantine_read_requests set request_status = 'APPROVED', read_at = null where quarantine_read_request_id = '" + request + "'", TRIGGER);
    }
}
