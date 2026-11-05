package com.forcitis.one.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #1310: the standard-hours migration's guards, exercised through SQL without application validation. */
@Testcontainers
class StandardHoursMigrationIT {

    @Container
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql");

    static final String RESTRICT_VIOLATION = "23001";
    static final String CHECK_VIOLATION = "23514";
    static final String UNIQUE_VIOLATION = "23505";
    static final String EXCLUSION_VIOLATION = "23P01";
    static final UUID CONFIRMER = UUID.randomUUID();
    static final UUID VOIDER = UUID.randomUUID();
    static final OffsetDateTime CONFIRMED_AT = OffsetDateTime.parse("2026-01-02T02:00:00Z");
    static final OffsetDateTime VOIDED_AT = CONFIRMED_AT.plusDays(1);

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).locations("classpath:db/migration").load().migrate();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void run(Connection db, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.execute();
        }
    }

    private static String text(Connection db, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    private static LocalDate today(Connection db) throws SQLException {
        return LocalDate.parse(text(db, "select (transaction_timestamp() at time zone 'Asia/Shanghai')::date::text"));
    }

    /** A refused statement must not abort the transaction that owns a version and its overrides. */
    private static void refused(Connection db, String state, String rule, Throwing statement) throws SQLException {
        Savepoint before = db.setSavepoint();
        SQLException refusal;
        try {
            refusal = catchThrowableOfType(SQLException.class, statement::run);
        } finally {
            db.rollback(before);
            db.releaseSavepoint(before);
        }
        assertThat((Throwable) refusal).as("the database refuses: " + rule).isNotNull();
        assertThat(refusal.getSQLState()).as("SQLSTATE for " + rule + ": " + refusal.getMessage()).isEqualTo(state);
        assertThat(refusal.getMessage()).as("the refusal names the rule: " + rule).contains(rule);
    }

    interface Throwing {
        void run() throws SQLException;
    }

    private static UUID version(Connection db, UUID entity, int number, LocalDate from) throws SQLException {
        return versionOf(db, entity == null ? "DEFAULT" : "LEGAL_ENTITY", entity, number, "IN_EFFECT", from, null, 480, 480, "synthetic-basis");
    }

    private static UUID versionOf(Connection db, String scope, UUID entity, int number, String status, LocalDate from, LocalDate to,
                                  int workdayMinutes, int adjustedMinutes, String basis) throws SQLException {
        UUID id = UUID.randomUUID();
        run(db, """
                insert into organization_attendance.standard_hours_versions (id, scope_kind, legal_entity_id, version_number, status,
                       effective_from_date, effective_to_date, workday_minutes_count, adjusted_workday_minutes_count, basis_ref_text,
                       confirmed_by, confirmed_at, decision_audit_entry_id, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, scope, entity, number, status, from, to, workdayMinutes, adjustedMinutes, basis,
                CONFIRMER, CONFIRMED_AT, UUID.randomUUID(), CONFIRMED_AT, CONFIRMED_AT);
        return id;
    }

    private static UUID override(Connection db, UUID version, LocalDate date, int minutes, String reason) throws SQLException {
        UUID id = UUID.randomUUID();
        run(db, """
                insert into organization_attendance.standard_hours_date_overrides (id, standard_hours_version_id, work_date, minutes_count,
                       reason_text, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?)
                """, id, version, date, minutes, reason, CONFIRMED_AT, CONFIRMED_AT);
        return id;
    }

    private static void voidVersion(Connection db, UUID version, String reason) throws SQLException {
        run(db, """
                update organization_attendance.standard_hours_versions set status = 'VOIDED', voided_by = ?, voided_at = ?, void_reason_text = ? where id = ?
                """, VOIDER, VOIDED_AT, reason, version);
    }

    @Test
    @DisplayName("#1310 排他：同一法人或默认范围的有效版本不能重叠，不同范围可以共用日期")
    void inEffectPeriodsDoNotOverlapWithinEitherKindOfScope() throws Exception {
        try (Connection db = connect()) {
            db.setAutoCommit(false);
            LocalDate from = today(db);
            UUID entity = UUID.randomUUID();
            UUID first = version(db, entity, 1, from);
            UUID other = version(db, UUID.randomUUID(), 1, from);
            UUID fallback = version(db, null, 1, from);
            assertThat(text(db, "select count(*) from organization_attendance.standard_hours_versions where id in (?, ?, ?) and status = 'IN_EFFECT' and effective_from_date = ?",
                    first, other, fallback, from)).as("two legal entities and DEFAULT may share the same open period").isEqualTo("3");
            refused(db, CHECK_VIOLATION, "must be later than the latest version", () -> version(db, entity, 2, from));
            refused(db, CHECK_VIOLATION, "must be later than the latest version", () -> version(db, null, 2, from));

            // As in WorkCalendarVersionsMigrationIT, isolate the exclusion constraints from automatic predecessor closing.
            run(db, "alter table organization_attendance.standard_hours_versions disable trigger tg_standard_hours_versions_insert");
            try {
                refused(db, EXCLUSION_VIOLATION, "ex_standard_hours_versions_overlap_legal_entity", () -> version(db, entity, 2, from.plusDays(1)));
                refused(db, EXCLUSION_VIOLATION, "ex_standard_hours_versions_overlap_default", () -> version(db, null, 2, from.plusDays(1)));
            } finally {
                run(db, "alter table organization_attendance.standard_hours_versions enable trigger tg_standard_hours_versions_insert");
            }
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1310 版本号：每个范围从 1 起逐个加 1，作废的版本仍占号")
    void versionNumbersAdvanceByOnePerScopeAndAreNotRecycled() throws Exception {
        try (Connection db = connect()) {
            db.setAutoCommit(false);
            LocalDate from = today(db).plusDays(10);
            for (UUID entity : new UUID[] {UUID.randomUUID(), null}) {
                refused(db, CHECK_VIOLATION, "version_number must be 1", () -> version(db, entity, 2, from));
                version(db, entity, 1, from);
                refused(db, CHECK_VIOLATION, "version_number must be 2", () -> version(db, entity, 1, from.plusDays(1)));
                refused(db, CHECK_VIOLATION, "version_number must be 2", () -> version(db, entity, 3, from.plusDays(1)));
                UUID second = version(db, entity, 2, from.plusDays(1));
                voidVersion(db, second, "synthetic-cancellation");
                refused(db, CHECK_VIOLATION, "version_number must be 3", () -> version(db, entity, 2, from.plusDays(2)));
                refused(db, CHECK_VIOLATION, "version_number must be 3", () -> version(db, entity, 4, from.plusDays(2)));
                UUID third = version(db, entity, 3, from.plusDays(2));
                assertThat(text(db, "select version_number::text from organization_attendance.standard_hours_versions where id = ? and status = 'VOIDED'", second))
                        .as("a voided version retains number 2 in scope " + entity).isEqualTo("2");
                assertThat(text(db, "select version_number::text from organization_attendance.standard_hours_versions where id = ?", third))
                        .as("the next published version uses number 3 in scope " + entity).isEqualTo("3");
            }
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1310 生效日：任何版本都不早于库的上海事务日期（含首版与只剩已作废版本的范围），后续版本须晚于本范围最新有效版本")
    void successorDatesAdvanceAndDoNotActOnThePast() throws Exception {
        try (Connection db = connect()) {
            db.setAutoCommit(false);
            LocalDate today = today(db);
            UUID entity = UUID.randomUUID();
            refused(db, CHECK_VIOLATION, "earlier than today", () -> version(db, entity, 1, today.minusDays(1)));
            refused(db, CHECK_VIOLATION, "earlier than today", () -> version(db, null, 1, today.minusDays(10)));
            UUID first = version(db, entity, 1, today);
            assertThat(text(db, "select effective_from_date::text from organization_attendance.standard_hours_versions where id = ?", first))
                    .as("the first version may start on the database's Shanghai date").isEqualTo(today.toString());
            refused(db, CHECK_VIOLATION, "must be later than the latest version", () -> version(db, entity, 2, today.minusDays(1)));
            refused(db, CHECK_VIOLATION, "must be later than the latest version", () -> version(db, entity, 2, today));
            version(db, entity, 2, today.plusDays(1));
            UUID third = version(db, entity, 3, today.plusDays(10));
            refused(db, CHECK_VIOLATION, "must be later than the latest version", () -> version(db, entity, 4, today.plusDays(9)));
            voidVersion(db, third, "synthetic-cancellation");
            refused(db, CHECK_VIOLATION, "must be later than the latest version", () -> version(db, entity, 4, today));
            UUID fourth = version(db, entity, 4, today.plusDays(9));
            assertThat(text(db, "select version_number::text from organization_attendance.standard_hours_versions where id = ?", fourth))
                    .as("a voided version no longer blocks an earlier date but keeps its number 3").isEqualTo("4");

            UUID allVoided = UUID.randomUUID();
            voidVersion(db, version(db, allVoided, 1, today.plusDays(5)), "synthetic-cancellation");
            refused(db, CHECK_VIOLATION, "earlier than today", () -> version(db, allVoided, 2, today.minusDays(1)));
            UUID startingToday = version(db, allVoided, 2, today);
            assertThat(text(db, "select status || ' ' || effective_from_date::text from organization_attendance.standard_hours_versions where id = ?", startingToday))
                    .as("a scope whose versions are all voided accepts a version effective on the database's Shanghai date").isEqualTo("IN_EFFECT " + today);
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1310 发布：新版本只能有效且开放，发布后上一开放版本在新版本起始日关闭")
    void aNewVersionIsInEffectAndOpenAndClosesItsPredecessor() throws Exception {
        try (Connection db = connect()) {
            db.setAutoCommit(false);
            LocalDate today = today(db);
            UUID entity = UUID.randomUUID();
            refused(db, CHECK_VIOLATION, "a new standard-hours version is IN_EFFECT and open", () ->
                    versionOf(db, "LEGAL_ENTITY", entity, 1, "VOIDED", today, null, 480, 480, "synthetic-basis"));
            refused(db, CHECK_VIOLATION, "a new standard-hours version is IN_EFFECT and open", () ->
                    versionOf(db, "LEGAL_ENTITY", entity, 1, "IN_EFFECT", today, today.plusDays(1), 480, 480, "synthetic-basis"));
            UUID first = version(db, entity, 1, today);
            assertThat(text(db, "select status from organization_attendance.standard_hours_versions where id = ?", first))
                    .as("a newly inserted version is IN_EFFECT").isEqualTo("IN_EFFECT");
            assertThat(text(db, "select effective_to_date::text from organization_attendance.standard_hours_versions where id = ?", first))
                    .as("a newly inserted version is open").isNull();
            UUID other = version(db, UUID.randomUUID(), 1, today);
            UUID second = version(db, entity, 2, today.plusDays(10));
            assertThat(text(db, "select effective_to_date::text from organization_attendance.standard_hours_versions where id = ?", first))
                    .as("the predecessor ends on the successor's start, exclusively").isEqualTo(today.plusDays(10).toString());
            assertThat(text(db, "select effective_to_date::text from organization_attendance.standard_hours_versions where id = ? and status = 'IN_EFFECT'", second))
                    .as("the successor stays IN_EFFECT and open").isNull();
            assertThat(text(db, "select effective_to_date::text from organization_attendance.standard_hours_versions where id = ?", other))
                    .as("publishing a successor does not close another scope").isNull();
            UUID third = version(db, entity, 3, today.plusDays(20));
            assertThat(text(db, "select effective_to_date::text from organization_attendance.standard_hours_versions where id = ?", second))
                    .as("a further successor closes the open predecessor").isEqualTo(today.plusDays(20).toString());
            assertThat(text(db, "select effective_to_date::text from organization_attendance.standard_hours_versions where id = ?", first))
                    .as("an already closed predecessor keeps its boundary").isEqualTo(today.plusDays(10).toString());
            assertThat(text(db, "select effective_to_date::text from organization_attendance.standard_hours_versions where id = ?", third))
                    .as("only the latest version remains open").isNull();
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1310 冻结：版本不能直接改，版本和日期覆盖都不能删除或清空")
    void versionsRejectOtherUpdatesAndBothTablesRejectDeleteAndTruncate() throws Exception {
        try (Connection db = connect()) {
            db.setAutoCommit(false);
            LocalDate from = today(db).plusDays(10);
            UUID entity = UUID.randomUUID();
            UUID first = version(db, entity, 1, from);
            UUID day = override(db, first, from, 240, "synthetic-override");
            for (String change : List.of("id = decision_audit_entry_id", "scope_kind = 'DEFAULT'", "legal_entity_id = null", "version_number = 2",
                    "effective_from_date = effective_from_date + 1", "effective_to_date = effective_from_date + 1", "workday_minutes_count = 481",
                    "adjusted_workday_minutes_count = 481", "basis_ref_text = 'changed'", "confirmed_by = decision_audit_entry_id",
                    "confirmed_at = confirmed_at + interval '1 second'", "decision_audit_entry_id = confirmed_by",
                    "created_at = created_at + interval '1 second'", "updated_at = updated_at + interval '1 second'",
                    "voided_by = confirmed_by", "voided_at = confirmed_at", "void_reason_text = 'changed'", "status = status",
                    "status = 'VOIDED', workday_minutes_count = 481")) {
                refused(db, RESTRICT_VIOLATION, "only changes by being closed by its successor or voided once", () ->
                        run(db, "update organization_attendance.standard_hours_versions set " + change + " where id = ?", first));
            }
            version(db, entity, 2, from.plusDays(10));
            refused(db, RESTRICT_VIOLATION, "only changes by being closed by its successor or voided once", () ->
                    run(db, "update organization_attendance.standard_hours_versions set effective_to_date = null where id = ?", first));
            refused(db, RESTRICT_VIOLATION, "append-only", () -> run(db, "delete from organization_attendance.standard_hours_versions where id = ?", first));
            refused(db, RESTRICT_VIOLATION, "append-only", () -> run(db, "truncate table organization_attendance.standard_hours_versions cascade"));
            refused(db, RESTRICT_VIOLATION, "append-only", () -> run(db, "delete from organization_attendance.standard_hours_date_overrides where id = ?", day));
            refused(db, RESTRICT_VIOLATION, "append-only", () -> run(db, "truncate table organization_attendance.standard_hours_date_overrides"));
            assertThat(text(db, "select count(*) from organization_attendance.standard_hours_date_overrides where id = ?", day))
                    .as("the refused delete and truncate preserve the override").isEqualTo("1");
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1310 作废：仅未来最新版本可作废，三项作废信息须齐全，作废后重开上一版本")
    void onlyTheFutureLatestVersionCanBeVoidedWithAllEvidenceAndItsPredecessorReopens() throws Exception {
        try (Connection db = connect()) {
            db.setAutoCommit(false);
            LocalDate today = today(db);
            UUID entity = UUID.randomUUID();
            UUID past = version(db, entity, 1, today);
            refused(db, CHECK_VIOLATION, "a version in force or in the past cannot be voided", () -> voidVersion(db, past, "synthetic-cancellation"));
            UUID future = version(db, entity, 2, today.plusDays(10));
            UUID latest = version(db, entity, 3, today.plusDays(20));
            refused(db, CHECK_VIOLATION, "a version in force or in the past cannot be voided", () -> voidVersion(db, past, "synthetic-closed-past"));
            refused(db, CHECK_VIOLATION, "only the latest version of a scope can be voided", () -> voidVersion(db, future, "synthetic-cancellation"));
            for (int mask = 0; mask < 7; mask++) {
                UUID by = (mask & 1) == 0 ? null : VOIDER;
                OffsetDateTime at = (mask & 2) == 0 ? null : VOIDED_AT;
                String reason = (mask & 4) == 0 ? null : "synthetic-cancellation";
                refused(db, CHECK_VIOLATION, "ck_standard_hours_versions_void_columns", () -> run(db, """
                        update organization_attendance.standard_hours_versions set status = 'VOIDED', voided_by = ?, voided_at = ?, void_reason_text = ? where id = ?
                        """, by, at, reason, latest));
            }
            assertThat(text(db, "select effective_to_date::text from organization_attendance.standard_hours_versions where id = ?", future))
                    .as("failed void attempts leave the predecessor closed").isEqualTo(today.plusDays(20).toString());
            UUID other = version(db, UUID.randomUUID(), 1, today.plusDays(30));
            voidVersion(db, latest, "synthetic-cancellation");
            assertThat(text(db, "select status from organization_attendance.standard_hours_versions where id = ?", latest))
                    .as("the future latest version becomes VOIDED even if another scope has a later version").isEqualTo("VOIDED");
            assertThat(text(db, "select voided_by = ? and voided_at = ? and void_reason_text = ? from organization_attendance.standard_hours_versions where id = ?",
                    VOIDER, VOIDED_AT, "synthetic-cancellation", latest)).as("all three void evidence columns are stored").isEqualTo("t");
            assertThat(text(db, "select effective_to_date::text from organization_attendance.standard_hours_versions where id = ?", future))
                    .as("voiding the successor reopens its predecessor").isNull();
            assertThat(text(db, "select effective_to_date::text from organization_attendance.standard_hours_versions where id = ?", past))
                    .as("voiding does not reopen older closed versions").isEqualTo(today.plusDays(10).toString());
            assertThat(text(db, "select status from organization_attendance.standard_hours_versions where id = ?", other))
                    .as("voiding does not change another scope").isEqualTo("IN_EFFECT");
            refused(db, RESTRICT_VIOLATION, "only changes by being closed by its successor or voided once", () -> voidVersion(db, latest, "again"));
            refused(db, RESTRICT_VIOLATION, "only changes by being closed by its successor or voided once", () ->
                    run(db, "update organization_attendance.standard_hours_versions set status = 'IN_EFFECT', voided_by = null, voided_at = null, void_reason_text = null where id = ?", latest));
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1310 日期覆盖：只在创建版本的事务内写入，不早于版本起始日，每日一行，分钟和原因有界且只追加")
    void overridesBelongToTheCreatingTransactionAndHaveValidImmutableValues() throws Exception {
        try (Connection db = connect()) {
            db.setAutoCommit(false);
            LocalDate from = today(db).plusDays(10);
            UUID entity = UUID.randomUUID();
            UUID first = version(db, entity, 1, from);
            UUID day = override(db, first, from, 1, "x");
            UUID maximum = override(db, first, from.plusDays(1), 1440, "x".repeat(200));
            assertThat(text(db, "select minutes_count::text from organization_attendance.standard_hours_date_overrides where id = ?", day))
                    .as("an override on the start date accepts one minute and a one-character reason").isEqualTo("1");
            assertThat(text(db, "select minutes_count = 1440 and char_length(reason_text) = 200 from organization_attendance.standard_hours_date_overrides where id = ?", maximum))
                    .as("the override accepts both upper bounds").isEqualTo("t");
            refused(db, CHECK_VIOLATION, "is earlier than the effective_from_date", () -> override(db, first, from.minusDays(1), 240, "synthetic-override"));
            refused(db, UNIQUE_VIOLATION, "uk_standard_hours_date_overrides_version_work_date", () -> override(db, first, from, 240, "synthetic-duplicate"));
            for (int minutes : new int[] {0, 1441}) {
                refused(db, CHECK_VIOLATION, "ck_standard_hours_date_overrides_minutes_count", () -> override(db, first, from.plusDays(2), minutes, "synthetic-override"));
            }
            for (String reason : List.of("", " ", " padded", "padded ", "x".repeat(201))) {
                refused(db, CHECK_VIOLATION, "ck_standard_hours_date_overrides_reason_text", () -> override(db, first, from.plusDays(2), 240, reason));
            }
            for (String change : List.of("id = standard_hours_version_id", "standard_hours_version_id = id", "work_date = work_date + 1",
                    "minutes_count = 241", "reason_text = 'changed'", "created_at = created_at + interval '1 second'", "updated_at = updated_at + interval '1 second'")) {
                refused(db, RESTRICT_VIOLATION, "append-only", () -> run(db, "update organization_attendance.standard_hours_date_overrides set " + change + " where id = ?", day));
            }
            refused(db, RESTRICT_VIOLATION, "append-only", () -> run(db, "delete from organization_attendance.standard_hours_date_overrides where id = ?", day));
            db.commit();
            refused(db, RESTRICT_VIOLATION, "already committed", () -> override(db, first, from.plusDays(2), 240, "synthetic-late-override"));
            UUID second = version(db, entity, 2, from.plusDays(10));
            override(db, second, from.plusDays(20), 240, "synthetic-successor-override");
            refused(db, RESTRICT_VIOLATION, "already committed", () -> override(db, first, from.plusDays(2), 240, "synthetic-after-close"));
            voidVersion(db, second, "synthetic-cancellation");
            refused(db, RESTRICT_VIOLATION, "already committed", () -> override(db, first, from.plusDays(2), 240, "synthetic-after-reopen"));
            UUID third = version(db, entity, 3, from.plusDays(20));
            override(db, third, from.plusDays(20), 240, "synthetic-next-override");
            assertThat(text(db, "select count(*) from organization_attendance.standard_hours_date_overrides where standard_hours_version_id = ?", first))
                    .as("commit, close and reopen never allow new overrides into the old version").isEqualTo("2");
            assertThat(text(db, "select count(*) from organization_attendance.standard_hours_date_overrides where standard_hours_version_id = ?", third))
                    .as("a later version may have an override on a date already covered by another version").isEqualTo("1");
            db.rollback();
        }
    }

    @Test
    @DisplayName("#1310 CHECK：范围与法人成对，两种分钟数 1 到 1440，依据 1 到 200 字，作废原因 1 到 500 字且去首尾空格，截止日晚于起始日")
    void checkConstraintsBoundScopeMinutesBasisVoidReasonAndEffectiveRange() throws Exception {
        try (Connection db = connect()) {
            db.setAutoCommit(false);
            LocalDate from = today(db).plusDays(10);
            refused(db, CHECK_VIOLATION, "ck_standard_hours_versions_scope_matches_legal_entity", () ->
                    versionOf(db, "DEFAULT", UUID.randomUUID(), 1, "IN_EFFECT", from, null, 480, 480, "synthetic-basis"));
            refused(db, CHECK_VIOLATION, "ck_standard_hours_versions_scope_matches_legal_entity", () ->
                    versionOf(db, "LEGAL_ENTITY", null, 1, "IN_EFFECT", from, null, 480, 480, "synthetic-basis"));
            for (int minutes : new int[] {0, 1441}) {
                refused(db, CHECK_VIOLATION, "ck_standard_hours_versions_workday_minutes_count", () ->
                        versionOf(db, "LEGAL_ENTITY", UUID.randomUUID(), 1, "IN_EFFECT", from, null, minutes, 480, "synthetic-basis"));
                refused(db, CHECK_VIOLATION, "ck_standard_hours_versions_adjusted_workday_minutes_count", () ->
                        versionOf(db, "LEGAL_ENTITY", UUID.randomUUID(), 1, "IN_EFFECT", from, null, 480, minutes, "synthetic-basis"));
            }
            for (String basis : List.of("", " ", " padded", "padded ", "x".repeat(201))) {
                refused(db, CHECK_VIOLATION, "ck_standard_hours_versions_basis_ref_text", () ->
                        versionOf(db, "LEGAL_ENTITY", UUID.randomUUID(), 1, "IN_EFFECT", from, null, 480, 480, basis));
            }
            UUID entity = UUID.randomUUID();
            UUID minimum = versionOf(db, "LEGAL_ENTITY", entity, 1, "IN_EFFECT", from, null, 1, 1440, "x");
            for (String reason : List.of("", " ", " padded", "padded ", "x".repeat(501))) {
                refused(db, CHECK_VIOLATION, "ck_standard_hours_versions_void_columns", () -> voidVersion(db, minimum, reason));
            }
            voidVersion(db, minimum, "x");
            UUID maximum = versionOf(db, "LEGAL_ENTITY", entity, 2, "IN_EFFECT", from.plusDays(1), null, 1440, 1, "x".repeat(200));
            voidVersion(db, maximum, "x".repeat(500));
            assertThat(text(db, "select workday_minutes_count = 1 and adjusted_workday_minutes_count = 1440 and char_length(basis_ref_text) = 1 and char_length(void_reason_text) = 1 from organization_attendance.standard_hours_versions where id = ?", minimum))
                    .as("version minute and text lower bounds are accepted").isEqualTo("t");
            assertThat(text(db, "select workday_minutes_count = 1440 and adjusted_workday_minutes_count = 1 and char_length(basis_ref_text) = 200 and char_length(void_reason_text) = 500 from organization_attendance.standard_hours_versions where id = ?", maximum))
                    .as("both minute columns and text upper bounds are accepted").isEqualTo("t");
            // The normal insert guard requires an open version; isolate the range CHECK as in the model's exclusion test.
            run(db, "alter table organization_attendance.standard_hours_versions disable trigger tg_standard_hours_versions_insert");
            try {
                for (LocalDate to : List.of(from, from.minusDays(1))) {
                    refused(db, CHECK_VIOLATION, "ck_standard_hours_versions_effective_range", () ->
                            versionOf(db, "LEGAL_ENTITY", UUID.randomUUID(), 1, "IN_EFFECT", from, to, 480, 480, "synthetic-basis"));
                }
                UUID closed = versionOf(db, "LEGAL_ENTITY", UUID.randomUUID(), 1, "IN_EFFECT", from, from.plusDays(1), 480, 480, "synthetic-basis");
                assertThat(text(db, "select effective_to_date::text from organization_attendance.standard_hours_versions where id = ?", closed))
                        .as("the range CHECK accepts an end strictly later than its start").isEqualTo(from.plusDays(1).toString());
            } finally {
                run(db, "alter table organization_attendance.standard_hours_versions enable trigger tg_standard_hours_versions_insert");
            }
            db.rollback();
        }
    }
}
