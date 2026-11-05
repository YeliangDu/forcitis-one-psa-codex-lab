package com.forcitis.one.acceptance.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.api.AttendanceError;
import com.forcitis.one.organization.attendance.api.StandardHours;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Card 1310: minutesOn contract lines (h)-(j): entities without own non-voided versions use
 * DEFAULT calendar rules and overrides; an own timeline takes exclusive precedence, including
 * a NOT_FOUND plus one ALERT before its first version; first own publication switches answers.
 * This class owns its migrated database and lazily publishes exactly one backdated DEFAULT first
 * version under a static guard. Every test uses fresh synthetic entities and is order independent.
 * Fixtures use HTTP and observations use only StandardHours and captured ALERT lines.
 */
class StandardHoursDefaultFallbackIT extends AcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(StandardHoursDefaultFallbackIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String VERSIONS = "/api/v1/organization/standard-hours-versions";

    /** Real Shanghai date: the database triggers use the real clock, and a version never starts before it. */
    private static final LocalDate TODAY = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
    /** First Monday on or after today with four weeks left in its year; the calendar fixtures and every assertion hang off it. */
    private static final LocalDate A = anchor();
    private static final int YEAR = A.getYear();
    private static final String CALENDAR = "/api/v1/organization/work-calendars/" + YEAR;

    private static LocalDate anchor() {
        LocalDate monday = TODAY;
        while (monday.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
            monday = monday.plusDays(1);
        }
        if (monday.plusDays(28).getYear() != monday.getYear()) {
            monday = LocalDate.of(monday.getYear() + 1, 1, 1);
            while (monday.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
                monday = monday.plusDays(1);
            }
        }
        return monday;
    }

    private static String d(int days) {
        return A.plusDays(days).toString();
    }
    private static final RequestPostProcessor PUBLISHER = person(31, "organization.attendance:standard-hours:publish");
    private static final RequestPostProcessor MAINTAINER = person(21, "organization.attendance:work-calendar:read",
            "organization.attendance:work-calendar:update", "organization.attendance:locked-day:update");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static boolean defaultPublished;

    @Autowired
    private StandardHours standardHours;

    @BeforeEach
    void useShanghaiTodayAndMaintainTheAnchorYearCalendar() throws Exception {
        setToday(TODAY);
        MockHttpServletResponse found = mvc.perform(get(CALENDAR).with(MAINTAINER)).andReturn().getResponse();
        if (found.getStatus() != 200) {
            Map<String, Object> body = Map.of("lockedDaysConfirmed", true, "days", List.of(
                    Map.of("calendarDate", d(1), "dayKind", "HOLIDAY"),
                    Map.of("calendarDate", d(5), "dayKind", "MAKE_UP_WORKDAY"),
                    Map.of("calendarDate", d(12), "dayKind", "MAKE_UP_WORKDAY")));
            MockHttpServletResponse maintained = mvc.perform(put(CALENDAR).header("If-Match", "\"0\"")
                    .with(MAINTAINER).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.writeValueAsString(body))).andReturn().getResponse();
            assertStatus(maintained, 200, YEAR + " calendar is maintained once through HTTP");
        }
    }

    @Test
    @DisplayName("法人无自有版本时使用默认时间线，遵守周末节假日、调休及仅上班日生效的日期例外")
    void entityWithoutOwnVersionUsesDefaultCalendarRulesAndOverrides() throws Exception {
        UUID scope = newScope();
        ensureDefaultVersion();

        assertMinutes(scope, d(2), 465, "entity without own version uses DEFAULT workdayMinutes");
        assertMinutes(scope, d(19), 0, "DEFAULT ordinary Saturday rests");
        assertMinutes(scope, d(6), 0, "DEFAULT Sunday override cannot turn rest into work");
        assertMinutes(scope, d(1), 0, "DEFAULT holiday override cannot turn rest into work");
        assertMinutes(scope, d(5), 405, "DEFAULT make-up day uses adjustedWorkdayMinutes");
        assertMinutes(scope, d(12), 315, "DEFAULT make-up day override wins");
        assertMinutes(scope, d(3), 255, "DEFAULT ordinary workday override wins");
        assertMinutes(scope, d(2), 465, "DEFAULT override leaves preceding workday unchanged");
        assertMinutes(scope, d(4), 465, "DEFAULT override leaves following workday unchanged");
    }

    @Test
    @DisplayName("法人存在自有版本即仅使用自有时间线，首版之前即使默认已覆盖仍报缺失并告警")
    void ownTimelineWinsAndNeverFallsBackBeforeItsFirstVersion() throws Exception {
        UUID scope = newScope();
        UUID defaultOnly = newScope();
        ensureDefaultVersion();
        Map<String, Object> own = publication(scope, A.plusDays(7));
        own.put("workdayMinutes", 390);
        own.put("adjustedWorkdayMinutes", 330);
        publish(own, 1);

        assertMinutes(defaultOnly, d(4), 465, "DEFAULT actually covers the date before the own timeline starts");
        assertMissing(scope, d(4), "own timeline forbids fallback even though DEFAULT covers the date");
        assertMinutes(scope, d(7), 390, "own workday minutes win from the exact first effective date");
        assertMinutes(scope, d(8), 390, "own workday minutes win over different DEFAULT minutes");
        assertMinutes(scope, d(12), 330, "own adjusted minutes win without importing DEFAULT's date override");
        assertMinutes(defaultOnly, d(12), 315, "another entity still sees the DEFAULT override on the same date");
    }

    @Test
    @DisplayName("法人发布首个自有版本后同一日期的答案由默认切换为自有，默认例外不再参与且其他法人不变")
    void firstOwnPublicationSwitchesPreviouslyReadDefaultAnswers() throws Exception {
        UUID scope = newScope();
        UUID defaultOnly = newScope();
        ensureDefaultVersion();
        assertMinutes(scope, d(2), 465, "before own publication ordinary workday uses DEFAULT");
        assertMinutes(scope, d(3), 255, "before own publication date override comes from DEFAULT");
        assertMinutes(scope, d(5), 405, "before own publication make-up workday uses DEFAULT");

        Map<String, Object> own = publication(scope, A);
        own.put("workdayMinutes", 390);
        own.put("adjustedWorkdayMinutes", 330);
        publish(own, 1);

        assertMinutes(scope, d(2), 390, "same entity and date switch to own workday minutes after publication");
        assertMinutes(scope, d(3), 390, "cached DEFAULT override is discarded when own timeline appears");
        assertMinutes(scope, d(5), 330, "same make-up date switches to own adjusted minutes");
        assertMinutes(defaultOnly, d(2), 465, "another entity continues using DEFAULT ordinary minutes");
        assertMinutes(defaultOnly, d(3), 255, "another entity retains the DEFAULT date override");
    }

    @Test
    @DisplayName("法人唯一的自有版本被作废后不再算自有时间线，同一天回到默认时间线")
    void entityWhoseOnlyOwnVersionIsVoidedFallsBackToDefault() throws Exception {
        UUID scope = newScope();
        ensureDefaultVersion();
        LocalDate start = A.plusDays(15);
        Map<String, Object> own = publication(scope, start);
        own.put("workdayMinutes", 390);
        String ownId = publish(own, 1);
        assertMinutes(scope, start.toString(), 390, "own first version answers on its start date before the void");

        assertStatus(voidVersion(ownId, Map.of("reason", "Synthetic card 1310 cancellation")), 200,
                "publisher can void the only own future version");

        assertMinutes(scope, start.toString(), 465, "a voided own version does not block the DEFAULT fallback on its former start date");
        assertMinutes(scope, d(2), 465, "dates before the voided start also come from DEFAULT");
        assertMinutes(scope, d(3), 255, "DEFAULT date override applies again to the entity");
    }

    private MockHttpServletResponse voidVersion(String id, Map<String, Object> body) throws Exception {
        return mvc.perform(post(VERSIONS + "/" + id + ":void").with(PUBLISHER).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))).andReturn().getResponse();
    }

    private void ensureDefaultVersion() throws Exception {
        synchronized (StandardHoursDefaultFallbackIT.class) {
            if (!defaultPublished) {
                Map<String, Object> body = publication(null, A);
                body.put("workdayMinutes", 465);
                body.put("adjustedWorkdayMinutes", 405);
                body.put("dateOverrides", List.of(
                        override(A.plusDays(6), 195, "Synthetic DEFAULT Sunday override"),
                        override(A.plusDays(1), 180, "Synthetic DEFAULT holiday override"),
                        override(A.plusDays(3), 255, "Synthetic DEFAULT workday override"),
                        override(A.plusDays(12), 315, "Synthetic DEFAULT make-up override")));
                publish(body, 1);
                defaultPublished = true;
            }
        }
    }

    private void assertMinutes(UUID scope, String date, int expected, String rule) {
        int mark = logMark();
        assertThat(standardHours.minutesOn(new LegalEntityId(scope), LocalDate.parse(date)))
                .as(rule + " at " + date).isEqualTo(expected);
        assertThat(alertsSince(mark)).as(rule + ": successful lookup emits no ALERT").isEmpty();
    }

    private void assertMissing(UUID scope, String date, String rule) {
        int mark = logMark();
        assertThatThrownBy(() -> standardHours.minutesOn(new LegalEntityId(scope), LocalDate.parse(date)))
                .as(rule + " at " + date).isInstanceOfSatisfying(DomainException.class, failure ->
                        assertThat(failure.errorCode()).as(rule + ": exact missing standard-hours error")
                                .isEqualTo(AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_NOT_FOUND));
        assertThat(alertsSince(mark)).as(rule + ": exactly one ALERT per failed lookup").hasSize(1)
                .allSatisfy(line -> assertThat(line).as(rule + ": ALERT identifies attendance")
                        .startsWith("ALERT organization.attendance"));
    }

    private UUID newScope() throws Exception {
        return createLegalEntity(newEntityCode(), "Synthetic card 1310 entity");
    }

    /** A legal entity code is 2 to 10 upper-case letters: SH and eight random letters. */
    private static String newEntityCode() {
        StringBuilder code = new StringBuilder("SH");
        for (int i = 0; i < 8; i++) {
            code.append((char) ('A' + java.util.concurrent.ThreadLocalRandom.current().nextInt(26)));
        }
        return code.toString();
    }

    private static Map<String, Object> publication(UUID scope, LocalDate effectiveDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (scope != null) {
            body.put("legalEntityId", scope.toString());
        }
        body.put("effectiveFromDate", effectiveDate.toString());
        body.put("workdayMinutes", 480);
        body.put("adjustedWorkdayMinutes", 420);
        body.put("basisRef", "Synthetic card 1310 policy");
        return body;
    }

    private static Map<String, Object> override(LocalDate date, int minutes, String reason) {
        return Map.of("workDate", date.toString(), "minutes", minutes, "reason", reason);
    }

    private MockHttpServletResponse postPublication(Map<String, Object> body, RequestPostProcessor caller) throws Exception {
        return mvc.perform(post(VERSIONS).with(caller).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))).andReturn().getResponse();
    }

    private String publish(Map<String, Object> body, int expectedNumber) throws Exception {
        MockHttpServletResponse response = postPublication(body, PUBLISHER);
        assertStatus(response, 200, "valid publication returns 200");
        String content = response.getContentAsString();
        String id = JsonPath.read(content, "$.standardHoursVersionId");
        assertThat(id).as("publication returns a UUID standardHoursVersionId")
                .matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        assertThat((Integer) JsonPath.read(content, "$.versionNumber")).as("publication returns its per-scope version number")
                .isEqualTo(expectedNumber);
        return id;
    }

    private static void assertStatus(MockHttpServletResponse response, int expected, String rule) throws Exception {
        assertThat(response.getStatus()).as(rule + "; response: " + response.getContentAsString()).isEqualTo(expected);
    }
}
