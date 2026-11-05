package com.forcitis.one.acceptance.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.forcitis.one.TestPostgres;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * #1436 acceptance part (PY3 bonus input port), done-when 1, schema lines: the table {@code organization_payroll.bonus_inputs} exactly as signed in
 * the PY3 data-design record of organization-payroll (#1428): columns and types, the named constraints, the partial unique key, the change trigger
 * (insert only as ACTIVE, the one legal update is the void, no delete, no truncate), the index and the comments. Rows are written directly, as a writer
 * that bypassed the application would, so only the database is under test. A missing table fails every test with a message naming it.
 */
@Testcontainers
class BonusInputsSchemaIT {

    @Container
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql");

    private static final String TABLE = "organization_payroll.bonus_inputs";
    private static final String CHECK = "23514";
    private static final String UNIQUE = "23505";
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-05T02:00:00Z");
    private static final LocalDate MONTH = LocalDate.of(2026, 10, 1);
    private static final UUID ENTERER = UUID.fromString("019a0000-0000-7000-8000-0000000000b1");
    private static final UUID VOIDER = UUID.fromString("019a0000-0000-7000-8000-0000000000b2");

    private Connection db;

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }

    @BeforeEach
    void openAndRequireTheTable() throws SQLException {
        db = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        db.setAutoCommit(false);
        assertThat(count("select count(*) from information_schema.tables where table_schema = 'organization_payroll' and table_name = 'bonus_inputs'"))
                .as("#1436 done-when 1 and the PY3 record: the table " + TABLE + " exists").isEqualTo(1L);
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.rollback();
        db.close();
    }

    interface Sql {
        void run() throws SQLException;
    }

    private long count(String sql, Object... values) throws SQLException {
        return (long) one(sql, values);
    }

    private Object one(String sql, Object... values) throws SQLException {
        try (var statement = db.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getObject(1);
            }
        }
    }

    private void exec(String sql, Object... values) throws SQLException {
        try (var statement = db.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.execute();
        }
    }

    private void insert(Map<String, Object> row) throws SQLException {
        String columns = String.join(", ", row.keySet());
        String marks = String.join(", ", row.keySet().stream().map(c -> "?").toList());
        exec("insert into " + TABLE + " (" + columns + ") values (" + marks + ")", row.values().toArray());
    }

    /** The statement is refused and leaves nothing behind; the savepoint keeps the surrounding transaction usable. */
    private SQLException refused(Sql action) throws SQLException {
        var savepoint = db.setSavepoint();
        SQLException refusal;
        try {
            refusal = catchThrowableOfType(SQLException.class, action::run);
        } finally {
            db.rollback(savepoint);
            db.releaseSavepoint(savepoint);
        }
        assertThat((Throwable) refusal).as("the database refuses the statement").isNotNull();
        return refusal;
    }

    private void refusedBy(String state, String rule, Sql action) throws SQLException {
        SQLException refusal = refused(action);
        assertThat(refusal.getSQLState()).as(refusal.getMessage()).isEqualTo(state);
        assertThat(refusal.getMessage()).as("the refusal names " + rule).contains(rule);
    }

    private static Map<String, Object> input(UUID relationship, LocalDate month, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("employment_relationship_id", relationship);
        row.put("paying_legal_entity_id", UUID.randomUUID());
        row.put("payroll_month_start_date", month);
        row.put("bonus_amount", new BigDecimal("1000.00"));
        row.put("bonus_currency", "CNY");
        row.put("source_kind", "MANUAL_APPROVED");
        row.put("reason_key", reason);
        row.put("approval_instance_id", UUID.randomUUID());
        row.put("entered_by_user_id", ENTERER);
        row.put("status", "ACTIVE");
        row.put("created_at", AT);
        row.put("updated_at", AT);
        return row;
    }

    private UUID active(UUID relationship, LocalDate month, String reason) throws SQLException {
        Map<String, Object> row = input(relationship, month, reason);
        insert(row);
        return (UUID) row.get("id");
    }

    private void voidIt(UUID id) throws SQLException {
        exec("update " + TABLE + " set status = 'VOIDED', voided_by_user_id = ?, voided_at = ?, updated_at = ? where id = ?", VOIDER, AT.plusHours(1), AT.plusHours(1), id);
    }

    private String statusOf(UUID id) throws SQLException {
        return (String) one("select status from " + TABLE + " where id = ?", id);
    }

    // ---- columns ----

    @Test
    @DisplayName("#1436 完成标准 1：bonus_inputs 的列、类型与可空性如已签字的数据设计（PY3 记录 Columns and constraints）")
    void theColumnsAreAsSigned() throws SQLException {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("id", "uuid NO");
        expected.put("employment_relationship_id", "uuid NO");
        expected.put("paying_legal_entity_id", "uuid NO");
        expected.put("payroll_month_start_date", "date NO");
        expected.put("bonus_amount", "numeric(19,2) NO");
        expected.put("bonus_currency", "text NO");
        expected.put("source_kind", "text NO");
        expected.put("reason_key", "text NO");
        expected.put("approval_instance_id", "uuid NO");
        expected.put("entered_by_user_id", "uuid NO");
        expected.put("status", "text NO");
        expected.put("voided_by_user_id", "uuid YES");
        expected.put("voided_at", "timestamp with time zone YES");
        expected.put("created_at", "timestamp with time zone NO");
        expected.put("updated_at", "timestamp with time zone NO");

        Map<String, String> actual = new LinkedHashMap<>();
        try (var statement = db.prepareStatement("select a.attname, format_type(a.atttypid, a.atttypmod), a.attnotnull from pg_attribute a "
                + "where a.attrelid = 'organization_payroll.bonus_inputs'::regclass and a.attnum > 0 and not a.attisdropped order by a.attnum");
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                actual.put(rows.getString(1), rows.getString(2) + (rows.getBoolean(3) ? " NO" : " YES"));
            }
        }
        assertThat(actual).as("PY3 record: exactly these columns, no version column (one legal update, the void)").containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    @DisplayName("#1436 完成标准 1：主键 pk_bonus_inputs，对其他上下文的 id 不建外键（B4）")
    void theKeyIsThePrimaryKeyAndNothingReferencesAnotherContext() throws SQLException {
        assertThat(count("select count(*) from pg_constraint where conrelid = 'organization_payroll.bonus_inputs'::regclass and contype = 'p' and conname = 'pk_bonus_inputs'"))
                .as("PY3 record: primary key pk_bonus_inputs").isEqualTo(1L);
        assertThat(count("select count(*) from pg_constraint where conrelid = 'organization_payroll.bonus_inputs'::regclass and contype = 'f'"))
                .as("PY3 record B4: cross-BC ids carry no foreign key").isZero();
    }

    // ---- constraints ----

    @Test
    @DisplayName("#1436 完成标准 1：一行有效输入被接受；金额必须为正，ck_bonus_inputs_bonus_amount 拒绝 0 与负数")
    void aPositiveAmountIsAcceptedAndZeroOrNegativeIsRefused() throws SQLException {
        UUID relationship = UUID.randomUUID();
        UUID ok = active(relationship, MONTH, "YEAR_END");
        assertThat(statusOf(ok)).as("PY3 record: a valid ACTIVE row is accepted").isEqualTo("ACTIVE");

        for (String amount : List.of("0.00", "-1.00")) {
            Map<String, Object> bad = input(UUID.randomUUID(), MONTH, "YEAR_END");
            bad.put("bonus_amount", new BigDecimal(amount));
            refusedBy(CHECK, "ck_bonus_inputs_bonus_amount", () -> insert(bad));
        }
    }

    @Test
    @DisplayName("#1436 完成标准 1：币种只能是 CNY（C-4），ck_bonus_inputs_bonus_currency")
    void onlyCnyIsAccepted() throws SQLException {
        Map<String, Object> bad = input(UUID.randomUUID(), MONTH, "YEAR_END");
        bad.put("bonus_currency", "USD");
        refusedBy(CHECK, "ck_bonus_inputs_bonus_currency", () -> insert(bad));
    }

    @Test
    @DisplayName("#1436 完成标准 1：薪酬月份列只能是月初一日，ck_bonus_inputs_payroll_month_start_date")
    void theMonthColumnHoldsTheFirstDay() throws SQLException {
        Map<String, Object> bad = input(UUID.randomUUID(), MONTH.plusDays(14), "YEAR_END");
        refusedBy(CHECK, "ck_bonus_inputs_payroll_month_start_date", () -> insert(bad));
    }

    @Test
    @DisplayName("#1436 完成标准 1：来源种类目前只有 MANUAL_APPROVED，ck_bonus_inputs_source_kind")
    void onlyTheManualApprovedSourceIsAccepted() throws SQLException {
        Map<String, Object> bad = input(UUID.randomUUID(), MONTH, "YEAR_END");
        bad.put("source_kind", "PERFORMANCE");
        refusedBy(CHECK, "ck_bonus_inputs_source_kind", () -> insert(bad));
    }

    @Test
    @DisplayName("#1436 完成标准 1：原因键格式 ^[A-Z][A-Z0-9_]{1,39}$，ck_bonus_inputs_reason_key")
    void theReasonKeyHasTheKeyFormat() throws SQLException {
        for (String reason : List.of("year_end", "1YEAR", "A", "A".repeat(41), "YEAR END", "")) {
            Map<String, Object> bad = input(UUID.randomUUID(), MONTH, reason);
            refusedBy(CHECK, "ck_bonus_inputs_reason_key", () -> insert(bad));
        }
        active(UUID.randomUUID(), MONTH, "AB");
        active(UUID.randomUUID(), MONTH, "A" + "9".repeat(39));
    }

    @Test
    @DisplayName("#1436 完成标准 1：状态只有 ACTIVE 与 VOIDED，ck_bonus_inputs_status")
    void theStatusIsActiveOrVoided() throws SQLException {
        Map<String, Object> bad = input(UUID.randomUUID(), MONTH, "YEAR_END");
        bad.put("status", "PENDING");
        // a before-insert trigger (insert only as ACTIVE) may answer before the CHECK does, so the refusal and the empty table are asserted, not the rule's name
        refused(() -> insert(bad));
        assertThat(count("select count(*) from " + TABLE + " where id = ?", bad.get("id"))).as("PY3 record: no row with a status outside ACTIVE and VOIDED").isZero();
    }

    @Test
    @DisplayName("#1436 完成标准 1：作废列成对出现，ck_bonus_inputs_void_columns（状态、作废时间、作废人同有同无）")
    void theVoidColumnsGoTogether() throws SQLException {
        Map<String, Object> voidedWithoutPair = input(UUID.randomUUID(), MONTH, "YEAR_END");
        voidedWithoutPair.put("status", "VOIDED");
        refused(() -> insert(voidedWithoutPair));  // the insert trigger (only ACTIVE) may answer before the CHECK

        Map<String, Object> activeWithTime = input(UUID.randomUUID(), MONTH, "YEAR_END");
        activeWithTime.put("voided_at", AT);
        refusedBy(CHECK, "ck_bonus_inputs_void_columns", () -> insert(activeWithTime));

        Map<String, Object> activeWithUser = input(UUID.randomUUID(), MONTH, "YEAR_END");
        activeWithUser.put("voided_by_user_id", VOIDER);
        refusedBy(CHECK, "ck_bonus_inputs_void_columns", () -> insert(activeWithUser));

        Map<String, Object> timeWithoutUser = input(UUID.randomUUID(), MONTH, "YEAR_END");
        timeWithoutUser.put("status", "VOIDED");
        timeWithoutUser.put("voided_at", AT);
        refused(() -> insert(timeWithoutUser));  // the insert trigger (only ACTIVE) may answer before the CHECK
    }

    @Test
    @DisplayName("#1436 完成标准 1：一个审批只能对应一行，uk_bonus_inputs_approval_instance_id（重放 ApprovalCompleted 只找到一行）")
    void oneApprovalOneRow() throws SQLException {
        Map<String, Object> first = input(UUID.randomUUID(), MONTH, "YEAR_END");
        insert(first);
        Map<String, Object> replay = input(UUID.randomUUID(), MONTH.plusMonths(1), "PROJECT");
        replay.put("approval_instance_id", first.get("approval_instance_id"));

        refusedBy(UNIQUE, "uk_bonus_inputs_approval_instance_id", () -> insert(replay));
    }

    @Test
    @DisplayName("#1436 完成标准 1：同一关系、月份、原因的有效输入只有一行，同月不同原因可并存，作废后可补录（uk_bonus_inputs_relationship_month_reason）")
    void oneActiveInputPerRelationshipMonthAndReason() throws SQLException {
        UUID relationship = UUID.randomUUID();
        UUID first = active(relationship, MONTH, "YEAR_END");

        refusedBy(UNIQUE, "uk_bonus_inputs_relationship_month_reason", () -> insert(input(relationship, MONTH, "YEAR_END")));
        active(relationship, MONTH, "PROJECT");
        active(relationship, MONTH.plusMonths(1), "YEAR_END");
        active(UUID.randomUUID(), MONTH, "YEAR_END");

        voidIt(first);
        active(relationship, MONTH, "YEAR_END");
        assertThat(count("select count(*) from " + TABLE + " where employment_relationship_id = ? and payroll_month_start_date = ? and reason_key = 'YEAR_END'", relationship, MONTH))
                .as("PY3 record: a voided input frees the key for a corrected one; the voided row stays").isEqualTo(2L);
    }

    // ---- the change trigger ----

    @Test
    @DisplayName("#1436 完成标准 1：tg_bonus_inputs_change 只允许以 ACTIVE 插入，不允许直接插入已作废行")
    void aRowIsInsertedOnlyAsActive() throws SQLException {
        Map<String, Object> voided = input(UUID.randomUUID(), MONTH, "YEAR_END");
        voided.put("status", "VOIDED");
        voided.put("voided_by_user_id", VOIDER);
        voided.put("voided_at", AT);

        refused(() -> insert(voided));
        assertThat(count("select count(*) from " + TABLE + " where id = ?", voided.get("id"))).as("PY3 record: nothing is left behind").isZero();
    }

    @Test
    @DisplayName("#1436 完成标准 1：唯一合法更新是 ACTIVE 到 VOIDED 并写入作废人与时间；其余列不可改")
    void theOnlyLegalUpdateIsTheVoid() throws SQLException {
        UUID relationship = UUID.randomUUID();
        UUID id = active(relationship, MONTH, "YEAR_END");

        voidIt(id);
        assertThat(statusOf(id)).as("PY3 record: ACTIVE to VOIDED is the one legal update").isEqualTo("VOIDED");
        assertThat(one("select voided_by_user_id from " + TABLE + " where id = ?", id)).as("PY3 record: the void pair is written").isEqualTo(VOIDER);
    }

    @Test
    @DisplayName("#1436 完成标准 1：已入库的行不可被改写（金额、原因、月份、关系、审批、录入人、币种、来源、法人）")
    void aRowIsNeverEdited() throws SQLException {
        UUID id = active(UUID.randomUUID(), MONTH, "YEAR_END");
        Map<String, Object> edits = new LinkedHashMap<>();
        edits.put("bonus_amount", new BigDecimal("2000.00"));
        edits.put("reason_key", "OTHER");
        edits.put("payroll_month_start_date", MONTH.plusMonths(1));
        edits.put("employment_relationship_id", UUID.randomUUID());
        edits.put("paying_legal_entity_id", UUID.randomUUID());
        edits.put("approval_instance_id", UUID.randomUUID());
        edits.put("entered_by_user_id", VOIDER);

        for (var edit : edits.entrySet()) {
            refused(() -> exec("update " + TABLE + " set " + edit.getKey() + " = ? where id = ?", edit.getValue(), id));
        }
        assertThat(one("select bonus_amount from " + TABLE + " where id = ?", id)).as("PY3 record: the amount is unchanged after the refused edits")
                .isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("#1436 完成标准 1：作废同时改其他列被拒绝；作废只设状态、作废人、作废时间与更新时间")
    void aVoidThatEditsAnotherColumnIsRefused() throws SQLException {
        UUID id = active(UUID.randomUUID(), MONTH, "YEAR_END");

        refused(() -> exec("update " + TABLE + " set status = 'VOIDED', voided_by_user_id = ?, voided_at = ?, updated_at = ?, bonus_amount = 1.00 where id = ?", VOIDER, AT, AT, id));
        assertThat(statusOf(id)).as("PY3 record: the refused void leaves the row ACTIVE").isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("#1436 完成标准 1：已作废的行不能恢复为 ACTIVE，也不能再次作废或改动")
    void aVoidedRowStaysVoided() throws SQLException {
        UUID id = active(UUID.randomUUID(), MONTH, "YEAR_END");
        voidIt(id);

        refused(() -> exec("update " + TABLE + " set status = 'ACTIVE', voided_by_user_id = null, voided_at = null where id = ?", id));
        refused(() -> exec("update " + TABLE + " set voided_by_user_id = ?, voided_at = ? where id = ?", ENTERER, AT.plusDays(1), id));
        assertThat(statusOf(id)).as("PY3 record: VOIDED is final").isEqualTo("VOIDED");
        assertThat(one("select voided_by_user_id from " + TABLE + " where id = ?", id)).as("PY3 record: the void pair is written once").isEqualTo(VOIDER);
    }

    @Test
    @DisplayName("#1436 完成标准 1：行不能被删除，也不能被清空表（tg_bonus_inputs_change 与 tg_bonus_inputs_truncate）")
    void noDeleteAndNoTruncate() throws SQLException {
        UUID id = active(UUID.randomUUID(), MONTH, "YEAR_END");

        refused(() -> exec("delete from " + TABLE + " where id = ?", id));
        refused(() -> exec("truncate " + TABLE));
        assertThat(count("select count(*) from " + TABLE + " where id = ?", id)).as("PY3 record: the row survives delete and truncate").isEqualTo(1L);
        assertThat(count("select count(*) from pg_trigger where tgrelid = 'organization_payroll.bonus_inputs'::regclass and tgname in ('tg_bonus_inputs_change', 'tg_bonus_inputs_truncate')"))
                .as("PY3 record: both triggers exist under their signed names").isEqualTo(2L);
    }

    // ---- index and comments ----

    @Test
    @DisplayName("#1436 完成标准 1：索引 ix_bonus_inputs_entity_month 覆盖（paying_legal_entity_id, payroll_month_start_date）")
    void theEntityMonthIndexExists() throws SQLException {
        List<String> columns = new ArrayList<>();
        try (var statement = db.prepareStatement("select a.attname from pg_index i join pg_class c on c.oid = i.indexrelid join pg_attribute a on a.attrelid = i.indrelid "
                + "and a.attnum = any(i.indkey) where c.relname = 'ix_bonus_inputs_entity_month' and i.indrelid = 'organization_payroll.bonus_inputs'::regclass "
                + "order by array_position(i.indkey::int[], a.attnum::int)");
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                columns.add(rows.getString(1));
            }
        }
        assertThat(columns).as("PY3 record query path 1: the settlement reads inputs by entity and month").containsExactly("paying_legal_entity_id", "payroll_month_start_date");
    }

    @Test
    @DisplayName("#1436 完成标准 1：表和每一列都有注释，可空列写明「为空表示」（C1）")
    void theTableAndEveryColumnHaveAComment() throws SQLException {
        assertThat(one("select obj_description('organization_payroll.bonus_inputs'::regclass, 'pg_class')")).as("PY3 record C1: the table has a comment").isNotNull();
        List<String> uncommented = new ArrayList<>();
        List<String> nullableWithoutMeaning = new ArrayList<>();
        try (var statement = db.prepareStatement("select a.attname, col_description(a.attrelid, a.attnum), a.attnotnull from pg_attribute a "
                + "where a.attrelid = 'organization_payroll.bonus_inputs'::regclass and a.attnum > 0 and not a.attisdropped");
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String comment = rows.getString(2);
                if (comment == null || comment.isBlank()) {
                    uncommented.add(rows.getString(1));
                } else if (!rows.getBoolean(3) && !comment.contains("为空表示")) {
                    nullableWithoutMeaning.add(rows.getString(1));
                }
            }
        }
        assertThat(uncommented).as("PY3 record C1: every column has a comment").isEmpty();
        assertThat(nullableWithoutMeaning).as("PY3 record C1: a nullable column states 为空表示").isEmpty();
    }
}
