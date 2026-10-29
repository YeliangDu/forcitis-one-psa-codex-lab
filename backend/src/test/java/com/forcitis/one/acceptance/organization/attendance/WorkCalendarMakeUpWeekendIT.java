package com.forcitis.one.acceptance.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * DATA-REPAIR-timesheet-audit TA-02 (card TA-A, A1): a make-up workday row must fall on a Saturday or a Sunday, enforced by
 * ck_work_calendar_days_make_up_weekend. Fixed dates: 2026-10-03 Sat, 10-04 Sun, 10-05 Mon .. 10-09 Fri, 10-10 Sat.
 */
class WorkCalendarMakeUpWeekendIT extends AcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(WorkCalendarMakeUpWeekendIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String CONSTRAINT = "ck_work_calendar_days_make_up_weekend";
    private static final String CALENDARS = "/api/v1/organization/work-calendars";
    private static final RequestPostProcessor MAINTAINER = person(21, "organization.attendance:work-calendar:read",
            "organization.attendance:work-calendar:update");

    @BeforeEach
    void emptyCalendar() throws SQLException {
        try (Connection db = schemaConnection("organization_attendance"); Statement s = db.createStatement()) {
            s.execute("truncate table work_calendar_days, work_calendar_years cascade");
        }
    }

    private static UUID year(Connection db, int yearNumber) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement insert = db.prepareStatement("insert into work_calendar_years (id, year_number) values (?, ?)")) {
            insert.setObject(1, id);
            insert.setInt(2, yearNumber);
            insert.executeUpdate();
        }
        return id;
    }

    private static void day(Connection db, UUID yearId, String date, String kind) throws SQLException {
        try (PreparedStatement insert = db.prepareStatement(
                "insert into work_calendar_days (id, work_calendar_year_id, calendar_date, day_kind) values (?, ?, ?, ?)")) {
            insert.setObject(1, UUID.randomUUID());
            insert.setObject(2, yearId);
            insert.setObject(3, LocalDate.parse(date));
            insert.setString(4, kind);
            insert.executeUpdate();
        }
    }

    private static int count(Connection db, String date) throws SQLException {
        try (PreparedStatement q = db.prepareStatement("select count(*) from work_calendar_days where calendar_date = ?")) {
            q.setObject(1, LocalDate.parse(date));
            try (ResultSet rs = q.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void assertRefusedByTheCheck(String date, String kind, Connection db, UUID yearId) {
        try {
            day(db, yearId, date, kind);
        } catch (PSQLException refused) {
            assertThat(refused.getSQLState()).as("check violation: " + refused.getMessage()).isEqualTo("23514");
            assertThat(refused.getServerErrorMessage()).isNotNull();
            assertThat(refused.getServerErrorMessage().getConstraint()).isEqualTo(CONSTRAINT);
            return;
        } catch (SQLException other) {
            throw new AssertionError(other);
        }
        throw new AssertionError(kind + " on " + date + " was accepted");
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TA-02 周六、周日的调休上班日可以直接插入")
    void makeUpWorkdayOnSaturdayAndSunday() throws SQLException {
        try (Connection db = schemaConnection("organization_attendance")) {
            UUID y = year(db, 2026);
            day(db, y, "2026-10-03", "MAKE_UP_WORKDAY");
            day(db, y, "2026-10-04", "MAKE_UP_WORKDAY");
            assertThat(count(db, "2026-10-03")).isEqualTo(1);
            assertThat(count(db, "2026-10-04")).isEqualTo(1);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08", "2026-10-09"})
    @DisplayName("DATA-REPAIR-timesheet-audit TA-02 周三的调休上班日被拒绝，SQLState 23514，约束名 ck_work_calendar_days_make_up_weekend（周一至周五各一例）")
    void makeUpWorkdayOnAWeekdayIsRefused(String weekday) throws SQLException {
        try (Connection db = schemaConnection("organization_attendance")) {
            UUID y = year(db, 2026);
            assertRefusedByTheCheck(weekday, "MAKE_UP_WORKDAY", db, y);
            assertThat(count(db, weekday)).isZero();
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TA-02 工作日与周末的节假日都可以直接插入")
    void holidaysOnWeekdaysAndWeekends() throws SQLException {
        try (Connection db = schemaConnection("organization_attendance")) {
            UUID y = year(db, 2026);
            for (String date : List.of("2026-10-03", "2026-10-04", "2026-10-05", "2026-10-07", "2026-10-09")) {
                day(db, y, date, "HOLIDAY");
                assertThat(count(db, date)).as(date).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TA-02 经接口录入周三调休日仍返回 422 与 ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY，不是 500")
    void wednesdayMakeUpDayOverHttpIsA422() throws Exception {
        MockHttpServletResponse answer = mvc.perform(put(CALENDARS + "/2027").header("If-Match", "\"0\"").with(MAINTAINER).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\": [{\"calendarDate\": \"2027-01-06\", \"dayKind\": \"MAKE_UP_WORKDAY\"}]}"))
                .andReturn().getResponse();
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(422);
        assertThat((String) JsonPath.read(answer.getContentAsString(), "$.errorCode")).isEqualTo("ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY");
        try (Connection db = schemaConnection("organization_attendance")) {
            assertThat(count(db, "2027-01-06")).isZero();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Asia/Shanghai", "America/Los_Angeles", "Pacific/Kiritimati"})
    @DisplayName("DATA-REPAIR-timesheet-audit TA-02 会话时区为非上海时区（如 America/Los_Angeles、Pacific/Kiritimati）时，同一日期的判定结果相同")
    void sameVerdictInAnySessionTimeZone(String zone) throws SQLException {
        try (Connection db = schemaConnection("organization_attendance")) {
            try (Statement s = db.createStatement()) {
                s.execute("set time zone '" + zone + "'");
            }
            UUID y = year(db, 2026);
            day(db, y, "2026-10-03", "MAKE_UP_WORKDAY");
            day(db, y, "2026-10-04", "MAKE_UP_WORKDAY");
            assertRefusedByTheCheck("2026-10-07", "MAKE_UP_WORKDAY", db, y);
            assertRefusedByTheCheck("2026-10-05", "MAKE_UP_WORKDAY", db, y);
            assertRefusedByTheCheck("2026-10-09", "MAKE_UP_WORKDAY", db, y);
            day(db, y, "2026-10-07", "HOLIDAY");
            assertThat(count(db, "2026-10-03")).isEqualTo(1);
            assertThat(count(db, "2026-10-04")).isEqualTo(1);
            assertThat(count(db, "2026-10-07")).isEqualTo(1);
            assertThat(count(db, "2026-10-05")).isZero();
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TA-02 读 pg_constraint 核对约束定义：表为 work_calendar_days、名为 ck_work_calendar_days_make_up_weekend、类型为检查约束，day_kind 内联枚举 CHECK 仍在")
    void constraintDefinitionInCatalog() throws SQLException {
        List<String> named = new ArrayList<>();
        List<String> others = new ArrayList<>();
        try (Connection db = schemaConnection("organization_attendance");
                PreparedStatement q = db.prepareStatement("""
                        select c.conname, c.contype::text, pg_get_constraintdef(c.oid)
                        from pg_constraint c
                        where c.conrelid = 'organization_attendance.work_calendar_days'::regclass and c.contype = 'c'""");
                ResultSet rs = q.executeQuery()) {
            while (rs.next()) {
                String definition = rs.getString(3).toLowerCase().replaceAll("\\s+", "");
                if (CONSTRAINT.equals(rs.getString(1))) {
                    assertThat(rs.getString(2)).isEqualTo("c");
                    named.add(definition);
                } else {
                    others.add(definition);
                }
            }
        }
        assertThat(named).as("check constraint " + CONSTRAINT + " on work_calendar_days; other checks: " + others).hasSize(1);
        String definition = named.get(0);
        assertThat(definition).contains("day_kind").contains("'make_up_workday'").contains("isodow").containsPattern("\\(?6\\)?(::\\w+)?,\\(?7\\)?(::\\w+)?");
        assertThat(others).as("inline day_kind enumeration check").anySatisfy(d ->
                assertThat(d).contains("day_kind").contains("'holiday'").contains("'make_up_workday'").doesNotContain("isodow"));
    }
}
