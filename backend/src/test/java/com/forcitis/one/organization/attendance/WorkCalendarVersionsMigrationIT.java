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
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
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
    /** The confirmation day is the database's own day, so the dates of the no-retroactivity cases hang on the year after today's. */
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    static final int NEXT_YEAR = TODAY.getYear() + 1;
    static final LocalDate JANUARY_SATURDAY = LocalDate.of(NEXT_YEAR, 1, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SATURDAY));
    static final LocalDate MARCH_SATURDAY = LocalDate.of(NEXT_YEAR, 3, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SATURDAY));
    static final LocalDate DECEMBER_SATURDAY = LocalDate.of(NEXT_YEAR, 12, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SATURDAY));

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
        return versionOf(db, calendar, 2026, number, sourceKind, from);
    }

    private static UUID versionOf(Connection db, UUID calendar, int year, int number, String sourceKind, LocalDate from) throws SQLException {
        UUID id = UUID.randomUUID();
        boolean migration = sourceKind.equals("MIGRATION");
        run(db, """
                insert into organization_attendance.work_calendar_versions (id, work_calendar_id, year_number, version_number, confirmation_status, source_kind, effective_from_date,
                       authored_by, authored_at, created_at, updated_at)
                values (?, ?, ?, ?, 'PENDING_CONFIRMATION', ?, ?, ?, ?, ?, ?)
                """, id, calendar, year, number, sourceKind, from, migration ? null : AUTHOR, migration ? null : AUTHORED_AT, AUTHORED_AT, AUTHORED_AT);
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
            refused(RESTRICT_VIOLATION, "organization_attendance.work_calendars is append-only", () -> run(db, "truncate table organization_attendance.work_calendars cascade"));
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
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_confirmed_after_authored", () -> confirm(db, version, CONFIRMER, AUTHORED_AT.minusDays(1), "NONE"));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_official_notice_url", () -> confirmWith(db, version, "n", "http://example.com/a", "NONE", null));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_official_notice_number", () -> confirmWith(db, version, " ", NOTICE_URL, "NONE", null));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_company_leave_register_key", () -> confirmWith(db, version, "n", NOTICE_URL, "REGISTERED", null));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_company_leave_register_key", () -> confirmWith(db, version, "n", NOTICE_URL, "NONE", "a-key"));
            refused(RESTRICT_VIOLATION, "can only be confirmed or withdrawn once", () -> run(db,
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
            refused(RESTRICT_VIOLATION, "can only be confirmed or withdrawn once", () -> run(db, "update organization_attendance.work_calendar_versions set effective_to_date = '2026-12-31' where id = ?", version));
            refused(RESTRICT_VIOLATION, "can only be confirmed or withdrawn once", () -> run(db, "update organization_attendance.work_calendar_versions set official_notice_number = 'x' where id = ?", version));
            refused(RESTRICT_VIOLATION, "append-only", () -> run(db, "delete from organization_attendance.work_calendar_versions where id = ?", version));
            refused(RESTRICT_VIOLATION, "truncate on organization_attendance.work_calendar_versions", () -> run(db, "truncate table organization_attendance.work_calendar_versions cascade"));
            refused(CHECK_VIOLATION, "waits for confirmation", () -> day(db, version, SUNDAY, "HOLIDAY", "OFFICIAL", null, null));
            refused(RESTRICT_VIOLATION, "append-only", () -> run(db, "delete from organization_attendance.work_calendar_version_days where work_calendar_version_id = ?", version));
            refused(RESTRICT_VIOLATION, "append-only", () -> run(db, "update organization_attendance.work_calendar_version_days set day_kind = 'HOLIDAY' where work_calendar_version_id = ?", version));
            refused(RESTRICT_VIOLATION, "organization_attendance.work_calendar_version_days is append-only", () -> run(db, "truncate table organization_attendance.work_calendar_version_days"));
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

    private static UUID confirmedFirstOfNextYear(Connection db, UUID calendar) throws SQLException {
        UUID first = versionOf(db, calendar, NEXT_YEAR, 1, "AUTHORED", LocalDate.of(NEXT_YEAR, 1, 1));
        day(db, first, JANUARY_SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
        confirm(db, first, CONFIRMER, CONFIRMED_AT, "NONE");
        return first;
    }

    @Test
    @DisplayName("不追溯：确认日取库的事务时间，生效日不得早于它（倒填 confirmed_at 无用），生效日之前的例外日必须与上一版本一致；确认新版本关闭上一版本")
    void aNewVersionDoesNotActOnThePastAndClosesThePreviousOne() throws Exception {
        LocalDate midYear = LocalDate.of(NEXT_YEAR, 6, 1);
        try (Connection db = connect()) {
            UUID calendar = calendar(db);
            confirmedFirstOfNextYear(db, calendar);
            UUID backdated = versionOf(db, calendar, NEXT_YEAR, 2, "AUTHORED", TODAY.minusDays(1));
            day(db, backdated, JANUARY_SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
            refused(CHECK_VIOLATION, "earlier than the confirmation day", () -> confirm(db, backdated, CONFIRMER, TODAY.minusDays(30).atStartOfDay().atOffset(java.time.ZoneOffset.UTC), "NONE"));
        }
        try (Connection db = connect()) {
            UUID calendar = calendar(db);
            confirmedFirstOfNextYear(db, calendar);
            UUID rewritesThePast = versionOf(db, calendar, NEXT_YEAR, 2, "AUTHORED", midYear);
            day(db, rewritesThePast, JANUARY_SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
            day(db, rewritesThePast, MARCH_SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
            refused(CHECK_VIOLATION, "differ from the previous version", () -> confirm(db, rewritesThePast, CONFIRMER, CONFIRMED_AT, "NONE"));
        }
        try (Connection db = connect()) {
            UUID calendar = calendar(db);
            confirmedFirstOfNextYear(db, calendar);
            UUID changesBasisOnly = versionOf(db, calendar, NEXT_YEAR, 2, "AUTHORED", midYear);
            day(db, changesBasisOnly, JANUARY_SATURDAY, "HOLIDAY", "COMPANY", null, null);
            refused(CHECK_VIOLATION, "differ from the previous version", () -> confirm(db, changesBasisOnly, CONFIRMER, CONFIRMED_AT, "REGISTERED"));
        }
        try (Connection db = connect()) {
            UUID calendar = calendar(db);
            UUID first = confirmedFirstOfNextYear(db, calendar);
            assertThat(text(db, "select effective_to_date::text from organization_attendance.work_calendar_versions where id = ?", first)).isNull();
            UUID second = versionOf(db, calendar, NEXT_YEAR, 2, "AUTHORED", midYear);
            day(db, second, JANUARY_SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
            day(db, second, DECEMBER_SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
            confirm(db, second, CONFIRMER, CONFIRMED_AT, "NONE");
            assertThat(text(db, "select effective_to_date::text from organization_attendance.work_calendar_versions where id = ?", first))
                    .as("the previous version ends where the new one starts").isEqualTo(midYear.toString());
            assertThat(text(db, "select effective_to_date::text from organization_attendance.work_calendar_versions where id = ?", second)).isNull();
            refused(RESTRICT_VIOLATION, "can only be confirmed or withdrawn once", () -> run(db,
                    "update organization_attendance.work_calendar_versions set effective_to_date = ? where id = ?", midYear.plusDays(1), first));
        }
    }

    @Test
    @DisplayName("首个已确认版本不得晚于该年 1 月 1 日：第 1 版有 CHECK，前面的版本都撤回后的首个已确认版本由触发器管")
    void theFirstConfirmedVersionCoversTheStartOfTheYear() throws Exception {
        try (Connection db = connect()) {
            UUID calendar = calendar(db);
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_first_version_covers_year", () -> versionOf(db, calendar, 2026, 1, "AUTHORED", LocalDate.of(2026, 1, 2)));
            UUID first = versionOf(db, calendar, 2026, 1, "AUTHORED", LocalDate.of(2026, 1, 1));
            withdraw(db, first, AUTHOR, "wrong year start");
            UUID second = versionOf(db, calendar, 2026, 2, "AUTHORED", TODAY.plusDays(5));
            day(db, second, SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
            refused(CHECK_VIOLATION, "starts on 1 January at the latest", () -> confirm(db, second, CONFIRMER, CONFIRMED_AT, "NONE"));
        }
        try (Connection db = connect()) {
            UUID calendar = calendar(db);
            withdraw(db, version(db, calendar, 1, "AUTHORED", LocalDate.of(2026, 1, 1)), AUTHOR, "redo");
            UUID second = version(db, calendar, 2, "AUTHORED", LocalDate.of(2026, 1, 1));
            day(db, second, SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
            confirm(db, second, CONFIRMER, CONFIRMED_AT, "NONE");
            assertThat(text(db, "select confirmation_status from organization_attendance.work_calendar_versions where id = ?", second)).isEqualTo("CONFIRMED");
        }
    }

    private static void withdraw(Connection db, UUID version, UUID by, String reason) throws SQLException {
        run(db, """
                update organization_attendance.work_calendar_versions set confirmation_status = 'WITHDRAWN', withdrawn_by = ?, withdrawn_at = ?, withdraw_reason_text = ? where id = ?
                """, by, CONFIRMED_AT, reason, version);
    }

    @Test
    @DisplayName("撤回：待确认可撤回成终态，撤回要有人、时间和原因，已确认不能撤回，撤回后不能再确认或加日期，版本号不回收且不占待确认名额")
    void aPendingVersionCanBeWithdrawnAndOnlyThat() throws Exception {
        try (Connection db = connect()) {
            UUID calendar = calendar(db);
            UUID first = version(db, calendar, 1, "AUTHORED", LocalDate.of(2026, 1, 1));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_withdrawal_columns", () -> run(db,
                    "update organization_attendance.work_calendar_versions set confirmation_status = 'WITHDRAWN' where id = ?", first));
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_withdrawal_columns", () -> withdraw(db, first, AUTHOR, " padded "));
            refused(RESTRICT_VIOLATION, "can only be confirmed or withdrawn once", () -> run(db,
                    "update organization_attendance.work_calendar_versions set withdrawn_by = ?, withdrawn_at = ?, withdraw_reason_text = 'x' where id = ?", AUTHOR, CONFIRMED_AT, first));
            withdraw(db, first, AUTHOR, "录错了例外日");
            assertThat(text(db, "select confirmation_status from organization_attendance.work_calendar_versions where id = ?", first)).isEqualTo("WITHDRAWN");

            refused(RESTRICT_VIOLATION, "can only be confirmed or withdrawn once", () -> confirm(db, first, CONFIRMER, CONFIRMED_AT, "NONE"));
            refused(RESTRICT_VIOLATION, "can only be confirmed or withdrawn once", () -> withdraw(db, first, AUTHOR, "again"));
            refused(CHECK_VIOLATION, "waits for confirmation", () -> day(db, first, SATURDAY, "HOLIDAY", "OFFICIAL", null, null));
            refused(CHECK_VIOLATION, "version_number must be 2", () -> version(db, calendar, 1, "AUTHORED", LocalDate.of(2026, 1, 1)));
            UUID second = version(db, calendar, 2, "AUTHORED", LocalDate.of(2026, 1, 1));
            confirmedMeansNoWithdrawal(db, second);
        }
    }

    private static void confirmedMeansNoWithdrawal(Connection db, UUID version) throws SQLException {
        day(db, version, SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
        confirm(db, version, CONFIRMER, CONFIRMED_AT, "NONE");
        refused(RESTRICT_VIOLATION, "can only be confirmed or withdrawn once", () -> withdraw(db, version, AUTHOR, "too late"));
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
    @DisplayName("运行账号借临时对象也关不了已确认版本：关闭只在确认下一版本时、截止日等于待确认下一版本的起算日")
    void aTemporaryTriggerCannotCloseAConfirmedVersion() throws Exception {
        String application = "calendar_app_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String password = "Pw1!" + UUID.randomUUID();
        try (Connection db = connect()) {
            run(db, "create role " + application + " login password '" + password + "'");
            run(db, "grant usage on schema organization_attendance to " + application);
            run(db, "grant select, insert, update on organization_attendance.work_calendars, organization_attendance.work_calendar_versions, organization_attendance.work_calendar_version_days to " + application);
            UUID calendar = calendar(db);
            UUID confirmed = confirmedFirstVersion(db, calendar, LocalDate.of(2026, 1, 1));
            try (Connection runtime = DriverManager.getConnection(postgres.getJdbcUrl(), application, password)) {
                run(runtime, "create temp table spoof (id uuid)");
                run(runtime, """
                        create function pg_temp.close_it() returns trigger language plpgsql as $$
                        begin
                            update organization_attendance.work_calendar_versions set effective_to_date = effective_from_date where id = new.id;
                            return new;
                        end $$
                        """);
                run(runtime, "create trigger spoof_close before insert on spoof for each row execute function pg_temp.close_it()");
                refused(RESTRICT_VIOLATION, "can only be confirmed or withdrawn once", () -> run(runtime, "insert into spoof values (?)", confirmed));
            }
            assertThat(text(db, "select effective_to_date::text from organization_attendance.work_calendar_versions where id = ?", confirmed)).isNull();
            run(db, "drop owned by " + application);
            run(db, "drop role " + application);
        }
    }

    @Test
    @DisplayName("迁入版本：没有作者、恒为第 1 版，只有属主角色或迁入角色写得进，运行账号被拒；确认由业务人员做，确认人必填")
    void aMigratedVersionHasNoAuthorIsTheFirstAndIsConfirmedByAPerson() throws Exception {
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
                // The temporary pg_class is made first: plpgsql keeps the plan of its first run, so a spoof made later would not be read.
                run(runtime, "create temp table pg_class (oid oid, relowner oid)");
                run(runtime, "insert into pg_class select 'organization_attendance.work_calendar_versions'::regclass, oid from pg_catalog.pg_roles where rolname = current_user");
                refused(INSUFFICIENT_PRIVILEGE, "owner role", () -> version(runtime, calendar, 1, "MIGRATION", LocalDate.of(2020, 1, 1)));
                run(runtime, "drop table pg_temp.pg_class");
                UUID authored = version(runtime, calendar(db), 1, "AUTHORED", LocalDate.of(2026, 1, 1));
                assertThat(authored).as("the runtime account writes authored versions").isNotNull();
            }

            UUID migrated = version(db, calendar, 1, "MIGRATION", LocalDate.of(2020, 1, 1));
            day(db, migrated, SATURDAY, "HOLIDAY", "OFFICIAL", null, null);
            refused(CHECK_VIOLATION, "ck_work_calendar_versions_confirmation_columns", () -> run(db, """
                    update organization_attendance.work_calendar_versions set confirmation_status = 'CONFIRMED', confirmed_at = ?, decision_audit_entry_id = ?,
                           official_notice_number = 'n', official_notice_url = ?, company_leave_register_kind = 'NONE' where id = ?
                    """, CONFIRMED_AT, UUID.randomUUID(), NOTICE_URL, migrated));
            try (Connection runtime = DriverManager.getConnection(postgres.getJdbcUrl(), application, password)) {
                confirm(runtime, migrated, CONFIRMER, CONFIRMED_AT, "NONE");
            }
            assertThat(text(db, "select confirmation_status from organization_attendance.work_calendar_versions where id = ?", migrated)).isEqualTo("CONFIRMED");
            assertThat(text(db, "select confirmed_by::text from organization_attendance.work_calendar_versions where id = ?", migrated)).isEqualTo(CONFIRMER.toString());

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
