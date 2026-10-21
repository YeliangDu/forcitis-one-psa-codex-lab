package com.forcitis.one.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * IMP-1021-PAR-02 (one-data review WORK-CALENDAR-DDL-2026-10-21, errata 1 to 9): what the database itself refuses in the versioned work
 * calendar. Fixed dates in 2026: 10-03 Saturday, 10-04 Sunday, 10-05 Monday, 10-10 Saturday.
 */
@Testcontainers
class WorkCalendarVersionsMigrationIT {

    @Container
    static PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql");

    static final String RESTRICT_VIOLATION = "23001";
    static final String CHECK_VIOLATION = "23514";
    static final String UNIQUE_VIOLATION = "23505";
    static final String EXCLUSION_VIOLATION = "23P01";
    static final String INSUFFICIENT_PRIVILEGE = "42501";
    static final UUID AUTHOR = UUID.randomUUID();
    static final UUID CONFIRMER = UUID.randomUUID();
    static final OffsetDateTime AUTHORED_AT = OffsetDateTime.parse("2026-01-02T02:00:00Z");
    static final OffsetDateTime CONFIRMED_AT = OffsetDateTime.parse("2026-01-03T02:00:00Z");
    static final LocalDate SATURDAY = LocalDate.of(2026, 10, 3);
    static final LocalDate SUNDAY = LocalDate.of(2026, 10, 4);
    static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    static final String NOTICE_URL = "https://www.gov.cn/zhengce/notice";

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

    /** The statement is refused with this SQLSTATE and the message names the rule. */
    private static void refused(String state, String rule, Throwing statement) {
        SQLException refusal = catchThrowableOfType(SQLException.class, statement::run);
        assertThat((Throwable) refusal).as("the database refuses: " + rule).isNotNull();
        assertThat(refusal.getSQLState()).as(refusal.getMessage()).isEqualTo(state);
        assertThat(refusal.getMessage()).contains(rule);
    }

    interface Throwing {
        void run() throws SQLException;
    }

    private static UUID calendar(Connection db) throws SQLException {
        UUID id = UUID.randomUUID();
        run(db, "insert into organization_attendance.work_calendars (id, calendar_scope_kind, legal_entity_id, created_at, updated_at) values (?, 'LEGAL_ENTITY', ?, ?, ?)",
                id, UUID.randomUUID(), AUTHORED_AT, AUTHORED_AT);
        return id;
    }

    private static UUID version(Connection db, UUID calendar, int number, String sourceKind, LocalDate from) throws SQLException {
        UUID id = UUID.randomUUID();
        boolean migration = sourceKind.equals("MIGRATION");
        run(db, """
                insert into organization_attendance.work_calendar_versions (id, work_calendar_id, year_number, version_number, confirmation_status, source_kind, effective_from_date,
                       authored_by, authored_at, created_at, updated_at)
                values (?, ?, 2026, ?, 'PENDING_CONFIRMATION', ?, ?, ?, ?, ?, ?)
                """, id, calendar, number, sourceKind, from, migration ? null : AUTHOR, migration ? null : AUTHORED_AT, AUTHORED_AT, AUTHORED_AT);
        return id;
    }

    private static void day(Connection db, UUID version, LocalDate date, String kind, String basis, String note, UUID approvedBy) throws SQLException {
        run(db, """
                insert into organization_attendance.work_calendar_version_days (id, work_calendar_version_id, year_number, calendar_date, day_kind, basis_kind, basis_note, approved_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), version, date.getYear(), date, kind, basis, note, approvedBy, AUTHORED_AT, AUTHORED_AT);
    }

    private static void confirm(Connection db, UUID version, UUID confirmedBy, OffsetDateTime at, String register) throws SQLException {
        run(db, """
                update organization_attendance.work_calendar_versions set confirmation_status = 'CONFIRMED', confirmed_by = ?, confirmed_at = ?, decision_audit_entry_id = ?,
                       official_notice_number = '2025-放假通知-1', official_notice_url = ?, company_leave_register_kind = ?, company_leave_register_key = ?
                 where id = ?
                """, confirmedBy, at, UUID.randomUUID(), NOTICE_URL, register, register.equals("REGISTERED") ? "company-leave-2026" : null, version);
    }

    private static void confirmWith(Connection db, UUID version, String number, String url, String register, String registerKey) throws SQLException {
        run(db, """
                update organization_attendance.work_calendar_versions set confirmation_status = 'CONFIRMED', confirmed_by = ?, confirmed_at = ?, decision_audit_entry_id = ?,
                       official_notice_number = ?, official_notice_url = ?, company_leave_register_kind = ?, company_leave_register_key = ?
                 where id = ?
                """, CONFIRMER, CONFIRMED_AT, UUID.randomUUID(), number, url, register, registerKey, version);
    }

    private static UUID confirmedFirstVersion(Connection db, UUID calendar, LocalDate from) throws SQLException {
        UUID first = version(db, calendar, 1, "AUTHORED", from);
        day(db, first, SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
        confirm(db, first, CONFIRMER, CONFIRMED_AT, "NONE");
        return first;
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

    @Test
    @DisplayName("根表：默认日历至多一份、每个法人至多一份，范围与法人成对，只追加")
    void theRootTableKeepsOneCalendarPerScopeAndIsAppendOnly() throws Exception {
        try (Connection db = connect()) {
            UUID entity = UUID.randomUUID();
            String insert = "insert into organization_attendance.work_calendars (id, calendar_scope_kind, legal_entity_id, created_at, updated_at) values (?, ?, ?, ?, ?)";
            UUID first = UUID.randomUUID();
            run(db, insert, first, "DEFAULT", null, AUTHORED_AT, AUTHORED_AT);
            run(db, insert, UUID.randomUUID(), "LEGAL_ENTITY", entity, AUTHORED_AT, AUTHORED_AT);

            refused(UNIQUE_VIOLATION, "uk_work_calendars_calendar_scope_kind", () -> run(db, insert, UUID.randomUUID(), "DEFAULT", null, AUTHORED_AT, AUTHORED_AT));
            refused(UNIQUE_VIOLATION, "uk_work_calendars_legal_entity_id", () -> run(db, insert, UUID.randomUUID(), "LEGAL_ENTITY", entity, AUTHORED_AT, AUTHORED_AT));
            refused(CHECK_VIOLATION, "ck_work_calendars_scope_matches_legal_entity", () -> run(db, insert, UUID.randomUUID(), "DEFAULT", UUID.randomUUID(), AUTHORED_AT, AUTHORED_AT));
            refused(CHECK_VIOLATION, "ck_work_calendars_scope_matches_legal_entity", () -> run(db, insert, UUID.randomUUID(), "LEGAL_ENTITY", null, AUTHORED_AT, AUTHORED_AT));
            refused(RESTRICT_VIOLATION, "append-only", () -> run(db, "update organization_attendance.work_calendars set created_at = ? where id = ?", CONFIRMED_AT, first));
            refused(RESTRICT_VIOLATION, "append-only", () -> run(db, "delete from organization_attendance.work_calendars where id = ?", first));
            refused(RESTRICT_VIOLATION, "append-only", () -> run(db, "truncate table organization_attendance.work_calendars cascade"));
        }
    }

    @Test
    @DisplayName("版本只能以待确认进入、版本号逐个加 1、同一日历同一年至多一个待确认版本")
    void aVersionStartsPendingAndNumbersGoUpByOne() throws Exception {
        try (Connection db = connect()) {
            UUID calendar = calendar(db);
            refused(CHECK_VIOLATION, "version_number must be 1", () -> version(db, calendar, 2, "AUTHORED", LocalDate.of(2026, 1, 1)));
            refused(CHECK_VIOLATION, "starts as PENDING_CONFIRMATION", () -> run(db, """
                    insert into organization_attendance.work_calendar_versions (id, work_calendar_id, year_number, version_number, confirmation_status, source_kind, effective_from_date,
                           authored_by, authored_at, confirmed_by, confirmed_at, decision_audit_entry_id, official_notice_number, official_notice_url, company_leave_register_kind,
                           created_at, updated_at)
                    values (?, ?, 2026, 1, 'CONFIRMED', 'AUTHORED', '2026-01-01', ?, ?, ?, ?, ?, 'n', ?, 'NONE', ?, ?)
                    """, UUID.randomUUID(), calendar, AUTHOR, AUTHORED_AT, CONFIRMER, CONFIRMED_AT, UUID.randomUUID(), NOTICE_URL, AUTHORED_AT, AUTHORED_AT));
            UUID first = version(db, calendar, 1, "AUTHORED", LocalDate.of(2026, 1, 1));
            refused(UNIQUE_VIOLATION, "uk_work_calendar_versions_pending", () -> version(db, calendar, 2, "AUTHORED", LocalDate.of(2026, 6, 1)));
            confirm(db, first, CONFIRMER, CONFIRMED_AT, "NONE");
            version(db, calendar, 2, "AUTHORED", LocalDate.of(2026, 6, 1));
        }
    }

    @Test
    @DisplayName("四眼：确认人不得是作者；确认要有审计记录和官方通知，加休登记（可为没有）；通知文号与链接有格式")
    void confirmationNeedsAnotherPersonAndTheEvidence() throws Exception {
        try (Connection db = connect()) {
            UUID version = version(db, calendar(db), 1, "AUTHORED", LocalDate.of(2026, 1, 1));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_confirmer_is_not_author", () -> confirm(db, version, AUTHOR, CONFIRMED_AT, "NONE"));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_confirmed_has_evidence", () -> run(db, """
                    update organization_attendance.work_calendar_versions set confirmation_status = 'CONFIRMED', confirmed_by = ?, confirmed_at = ?, decision_audit_entry_id = ? where id = ?
                    """, CONFIRMER, CONFIRMED_AT, UUID.randomUUID(), version));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_confirmation_columns", () -> run(db, """
                    update organization_attendance.work_calendar_versions set confirmation_status = 'CONFIRMED', confirmed_by = ?, confirmed_at = ?, official_notice_number = 'n',
                           official_notice_url = ?, company_leave_register_kind = 'NONE' where id = ?
                    """, CONFIRMER, CONFIRMED_AT, NOTICE_URL, version));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_official_notice_url", () -> confirmWith(db, version, "n", "http://example.com/a", "NONE", null));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_official_notice_number", () -> confirmWith(db, version, " ", NOTICE_URL, "NONE", null));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_company_leave_register_key", () -> confirmWith(db, version, "n", NOTICE_URL, "REGISTERED", null));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_company_leave_register_key", () -> confirmWith(db, version, "n", NOTICE_URL, "NONE", "a-key"));
            refused(RESTRICT_VIOLATION, "can only be confirmed once", () -> run(db,
                    "update organization_attendance.work_calendar_versions set official_notice_url = ? where id = ?", NOTICE_URL, version));
            confirm(db, version, CONFIRMER, CONFIRMED_AT, "NONE");
            assertThat(text(db, "select confirmation_status from organization_attendance.work_calendar_versions where id = ?", version)).isEqualTo("CONFIRMED");
        }
    }

    @Test
    @DisplayName("确认后冻结：不能改、不能删、不能清空；日期只能加进待确认的版本")
    void aConfirmedVersionIsFrozenAndTakesNoMoreDays() throws Exception {
        try (Connection db = connect()) {
            UUID version = confirmedFirstVersion(db, calendar(db), LocalDate.of(2026, 1, 1));
            refused(RESTRICT_VIOLATION, "can only be confirmed once", () -> run(db, "update organization_attendance.work_calendar_versions set effective_to_date = '2026-12-31' where id = ?", version));
            refused(RESTRICT_VIOLATION, "can only be confirmed once", () -> run(db, "update organization_attendance.work_calendar_versions set official_notice_number = 'x' where id = ?", version));
            refused(RESTRICT_VIOLATION, "append-only", () -> run(db, "delete from organization_attendance.work_calendar_versions where id = ?", version));
            refused(RESTRICT_VIOLATION, "append-only", () -> run(db, "truncate table organization_attendance.work_calendar_versions cascade"));
            refused(CHECK_VIOLATION, "waits for confirmation", () -> day(db, version, SUNDAY, "HOLIDAY", "OFFICIAL", null, null));
            refused(RESTRICT_VIOLATION, "append-only", () -> run(db, "delete from organization_attendance.work_calendar_version_days where work_calendar_version_id = ?", version));
            refused(RESTRICT_VIOLATION, "append-only", () -> run(db, "update organization_attendance.work_calendar_version_days set day_kind = 'HOLIDAY' where work_calendar_version_id = ?", version));
            refused(RESTRICT_VIOLATION, "append-only", () -> run(db, "truncate table organization_attendance.work_calendar_version_days"));
        }
    }

    @Test
    @DisplayName("日期行：年份一致、调休必在周末、依据按天、取消调休和手工更正要说明，手工更正要批准人且不是作者")
    void aDayCarriesItsBasisAndStaysInItsYearAndWeekend() throws Exception {
        try (Connection db = connect()) {
            UUID version = version(db, calendar(db), 1, "AUTHORED", LocalDate.of(2026, 1, 1));
            refused(CHECK_VIOLATION, "ck_work_calendar_version_days_calendar_date_in_year", () -> run(db, """
                    insert into organization_attendance.work_calendar_version_days (id, work_calendar_version_id, year_number, calendar_date, day_kind, basis_kind, created_at, updated_at)
                    values (?, ?, 2026, '2027-01-02', 'HOLIDAY', 'OFFICIAL', ?, ?)
                    """, UUID.randomUUID(), version, AUTHORED_AT, AUTHORED_AT));
            refused(CHECK_VIOLATION, "ck_work_calendar_version_days_make_up_weekend", () -> day(db, version, MONDAY, "MAKE_UP_WORKDAY", "OFFICIAL", null, null));
            refused(CHECK_VIOLATION, "ck_work_calendar_version_days_make_up_weekend", () -> day(db, version, MONDAY, "MAKE_UP_WORKDAY_CANCELLED", "COMPANY", "cancelled", null));
            refused(CHECK_VIOLATION, "ck_work_calendar_version_days_day_kind", () -> day(db, version, SATURDAY, "WORKDAY", "OFFICIAL", null, null));
            refused(CHECK_VIOLATION, "ck_work_calendar_version_days_basis_kind", () -> day(db, version, SATURDAY, "HOLIDAY", "UNKNOWN", null, null));
            refused(CHECK_VIOLATION, "ck_work_calendar_version_days_cancelled_basis", () -> day(db, version, SATURDAY, "MAKE_UP_WORKDAY_CANCELLED", "OFFICIAL", "note", null));
            refused(CHECK_VIOLATION, "ck_work_calendar_version_days_basis_note", () -> day(db, version, SATURDAY, "MAKE_UP_WORKDAY_CANCELLED", "COMPANY", null, null));
            refused(CHECK_VIOLATION, "ck_work_calendar_version_days_basis_note", () -> day(db, version, SATURDAY, "HOLIDAY", "MANUAL", null, CONFIRMER));
            refused(CHECK_VIOLATION, "ck_work_calendar_version_days_approved_by", () -> day(db, version, SATURDAY, "HOLIDAY", "MANUAL", "why", null));
            refused(CHECK_VIOLATION, "ck_work_calendar_version_days_approved_by", () -> day(db, version, SATURDAY, "HOLIDAY", "OFFICIAL", null, CONFIRMER));
            refused(CHECK_VIOLATION, "approver of a manual day cannot be the author", () -> day(db, version, SATURDAY, "HOLIDAY", "MANUAL", "why", AUTHOR));
            day(db, version, SATURDAY, "HOLIDAY", "MANUAL", "corrected by HR", CONFIRMER);
            day(db, version, SUNDAY, "MAKE_UP_WORKDAY_CANCELLED", "COMPANY", "cancels the make-up day of notice 2025-1", null);
            refused(UNIQUE_VIOLATION, "uk_work_calendar_version_days_version_calendar_date", () -> day(db, version, SATURDAY, "HOLIDAY", "OFFICIAL", null, null));
        }
    }

    @Test
    @DisplayName("公司加休或取消调休的日期出现时，确认必须登记为已登记；官方日期不要求")
    void companyLeaveNeedsARegisteredEntryAtConfirmation() throws Exception {
        try (Connection db = connect()) {
            UUID company = version(db, calendar(db), 1, "AUTHORED", LocalDate.of(2026, 1, 1));
            day(db, company, SATURDAY, "HOLIDAY", "COMPANY", null, null);
            refused(CHECK_VIOLATION, "needs company_leave_register_kind REGISTERED", () -> confirm(db, company, CONFIRMER, CONFIRMED_AT, "NONE"));
            confirm(db, company, CONFIRMER, CONFIRMED_AT, "REGISTERED");

            UUID cancelled = version(db, calendar(db), 1, "AUTHORED", LocalDate.of(2026, 1, 1));
            day(db, cancelled, SUNDAY, "MAKE_UP_WORKDAY_CANCELLED", "COMPANY", "cancels the make-up day", null);
            refused(CHECK_VIOLATION, "needs company_leave_register_kind REGISTERED", () -> confirm(db, cancelled, CONFIRMER, CONFIRMED_AT, "NONE"));

            UUID official = version(db, calendar(db), 1, "AUTHORED", LocalDate.of(2026, 1, 1));
            day(db, official, SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
            confirm(db, official, CONFIRMER, CONFIRMED_AT, "NONE");
        }
    }

    @Test
    @DisplayName("不追溯：第 2 版起生效日不得早于确认日，生效日之前的例外日必须与上一版本一致；确认新版本关闭上一版本；首版不受限")
    void aNewVersionDoesNotActOnThePastAndClosesThePreviousOne() throws Exception {
        try (Connection db = connect()) {
            UUID calendar = calendar(db);
            UUID first = confirmedFirstVersion(db, calendar, LocalDate.of(2026, 1, 1));
            assertThat(text(db, "select effective_to_date::text from organization_attendance.work_calendar_versions where id = ?", first)).isNull();

            UUID early = version(db, calendar, 2, "AUTHORED", LocalDate.of(2026, 6, 1));
            day(db, early, SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
            OffsetDateTime afterTheEffectiveDay = OffsetDateTime.parse("2026-06-10T04:00:00Z");
            refused(CHECK_VIOLATION, "does not act on the past", () -> confirm(db, early, CONFIRMER, afterTheEffectiveDay, "NONE"));
        }
        try (Connection db = connect()) {
            UUID calendar = calendar(db);
            UUID first = confirmedFirstVersion(db, calendar, LocalDate.of(2026, 1, 1));
            UUID rewritesThePast = version(db, calendar, 2, "AUTHORED", LocalDate.of(2026, 11, 1));
            day(db, rewritesThePast, SUNDAY, "HOLIDAY", "OFFICIAL", null, null);
            refused(CHECK_VIOLATION, "differ from the previous version", () -> confirm(db, rewritesThePast, CONFIRMER, CONFIRMED_AT, "NONE"));
        }
        try (Connection db = connect()) {
            UUID calendar = calendar(db);
            UUID first = confirmedFirstVersion(db, calendar, LocalDate.of(2026, 1, 1));
            UUID second = version(db, calendar, 2, "AUTHORED", LocalDate.of(2026, 11, 1));
            day(db, second, SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
            day(db, second, LocalDate.of(2026, 11, 7), "HOLIDAY", "OFFICIAL", null, null);
            confirm(db, second, CONFIRMER, CONFIRMED_AT, "NONE");
            assertThat(text(db, "select effective_to_date::text from organization_attendance.work_calendar_versions where id = ?", first))
                    .as("the previous version ends where the new one starts").isEqualTo("2026-11-01");
            assertThat(text(db, "select effective_to_date::text from organization_attendance.work_calendar_versions where id = ?", second)).isNull();
            refused(RESTRICT_VIOLATION, "can only be confirmed once", () -> run(db,
                    "update organization_attendance.work_calendar_versions set effective_to_date = '2026-12-01' where id = ?", first));

            UUID startsBeforeTheOpenOne = version(db, calendar, 3, "AUTHORED", LocalDate.of(2025, 12, 1));
            day(db, startsBeforeTheOpenOne, SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
            refused(CHECK_VIOLATION, "does not act on the past", () -> confirm(db, startsBeforeTheOpenOne, CONFIRMER, CONFIRMED_AT, "NONE"));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_effective_range", () -> confirm(db, startsBeforeTheOpenOne, CONFIRMER, OffsetDateTime.parse("2025-11-20T04:00:00Z"), "NONE"));
        }
        try (Connection db = connect()) {
            UUID calendar = calendar(db);
            UUID first = version(db, calendar, 1, "AUTHORED", LocalDate.of(2020, 1, 1));
            confirm(db, first, CONFIRMER, OffsetDateTime.parse("2026-06-10T04:00:00Z"), "NONE");
            assertThat(text(db, "select confirmation_status from organization_attendance.work_calendar_versions where id = ?", first))
                    .as("the first version may start in the past").isEqualTo("CONFIRMED");
        }
    }

    @Test
    @DisplayName("排他约束是触发器之外的底线：即使绕过插入触发器，已确认版本的生效区间也不能重叠")
    void confirmedPeriodsOfTheSameCalendarYearDoNotOverlapEvenWithoutTheTriggers() throws Exception {
        try (Connection db = connect()) {
            UUID calendar = calendar(db);
            confirmedFirstVersion(db, calendar, LocalDate.of(2026, 1, 1));
            run(db, "alter table organization_attendance.work_calendar_versions disable trigger tg_work_calendar_versions_insert");
            try {
                refused(EXCLUSION_VIOLATION, "ex_work_calendar_versions_overlap", () -> run(db, """
                        insert into organization_attendance.work_calendar_versions (id, work_calendar_id, year_number, version_number, confirmation_status, source_kind, effective_from_date,
                               effective_to_date, authored_by, authored_at, confirmed_by, confirmed_at, decision_audit_entry_id, official_notice_number, official_notice_url,
                               company_leave_register_kind, created_at, updated_at)
                        values (?, ?, 2026, 2, 'CONFIRMED', 'AUTHORED', '2026-12-01', '2026-12-31', ?, ?, ?, ?, ?, 'n', ?, 'NONE', ?, ?)
                        """, UUID.randomUUID(), calendar, AUTHOR, AUTHORED_AT, CONFIRMER, CONFIRMED_AT, UUID.randomUUID(), NOTICE_URL, AUTHORED_AT, AUTHORED_AT));
            } finally {
                run(db, "alter table organization_attendance.work_calendar_versions enable trigger tg_work_calendar_versions_insert");
            }
        }
    }

    @Test
    @DisplayName("迁入版本：没有作者与确认人、恒为第 1 版、要有核对签字的审计记录，只有属主角色写得进，运行账号被拒")
    void aMigratedVersionHasNoPeopleIsTheFirstAndOnlyTheOwnerWritesIt() throws Exception {
        String application = "calendar_app_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String password = "Pw1!" + UUID.randomUUID();
        try (Connection db = connect()) {
            run(db, "create role " + application + " login password '" + password + "'");
            run(db, "grant usage on schema organization_attendance to " + application);
            run(db, "grant select, insert, update on organization_attendance.work_calendars, organization_attendance.work_calendar_versions, organization_attendance.work_calendar_version_days to " + application);
            UUID calendar = calendar(db);

            refused(CHECK_VIOLATION, "ck_work_calendar_versions_author_matches_source_kind", () -> run(db, """
                    insert into organization_attendance.work_calendar_versions (id, work_calendar_id, year_number, version_number, confirmation_status, source_kind, effective_from_date,
                           authored_by, authored_at, created_at, updated_at)
                    values (?, ?, 2026, 1, 'PENDING_CONFIRMATION', 'MIGRATION', '2020-01-01', ?, ?, ?, ?)
                    """, UUID.randomUUID(), calendar, AUTHOR, AUTHORED_AT, AUTHORED_AT, AUTHORED_AT));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_author_matches_source_kind", () -> run(db, """
                    insert into organization_attendance.work_calendar_versions (id, work_calendar_id, year_number, version_number, confirmation_status, source_kind, effective_from_date,
                           created_at, updated_at)
                    values (?, ?, 2026, 1, 'PENDING_CONFIRMATION', 'AUTHORED', '2020-01-01', ?, ?)
                    """, UUID.randomUUID(), calendar, AUTHORED_AT, AUTHORED_AT));

            try (Connection runtime = DriverManager.getConnection(postgres.getJdbcUrl(), application, password)) {
                refused(INSUFFICIENT_PRIVILEGE, "owner role", () -> version(runtime, calendar, 1, "MIGRATION", LocalDate.of(2020, 1, 1)));
                UUID authored = version(runtime, calendar(db), 1, "AUTHORED", LocalDate.of(2026, 1, 1));
                assertThat(authored).as("the runtime account writes authored versions").isNotNull();
            }

            UUID migrated = version(db, calendar, 1, "MIGRATION", LocalDate.of(2020, 1, 1));
            day(db, migrated, SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_confirmation_columns", () -> confirm(db, migrated, CONFIRMER, CONFIRMED_AT, "NONE"));
            run(db, """
                    update organization_attendance.work_calendar_versions set confirmation_status = 'CONFIRMED', confirmed_at = ?, decision_audit_entry_id = ?,
                           official_notice_number = 'n', official_notice_url = ?, company_leave_register_kind = 'NONE' where id = ?
                    """, CONFIRMED_AT, UUID.randomUUID(), NOTICE_URL, migrated);
            assertThat(text(db, "select confirmation_status from organization_attendance.work_calendar_versions where id = ?", migrated)).isEqualTo("CONFIRMED");

            refused(CHECK_VIOLATION, "ck_work_calendar_versions_migration_is_first_version", () -> run(db, """
                    insert into organization_attendance.work_calendar_versions (id, work_calendar_id, year_number, version_number, confirmation_status, source_kind, effective_from_date,
                           created_at, updated_at)
                    values (?, ?, 2026, 2, 'PENDING_CONFIRMATION', 'MIGRATION', '2020-01-01', ?, ?)
                    """, UUID.randomUUID(), calendar, AUTHORED_AT, AUTHORED_AT));
            run(db, "drop owned by " + application);
            run(db, "drop role " + application);
        }
    }
}
