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
    private static final String CALENDAR = "/api/v1/organization/work-calendars/2026";
    private static final RequestPostProcessor PUBLISHER = person(31, "organization.attendance:standard-hours:publish");
    private static final RequestPostProcessor MAINTAINER = person(21, "organization.attendance:work-calendar:read",
            "organization.attendance:work-calendar:update");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static boolean defaultPublished;

    @Autowired
    private StandardHours standardHours;

    @BeforeEach
    void useShanghaiTodayAndMaintain2026Calendar() throws Exception {
        setToday(TODAY);
        MockHttpServletResponse found = mvc.perform(get(CALENDAR).with(MAINTAINER)).andReturn().getResponse();
        if (found.getStatus() != 200) {
            Map<String, Object> body = Map.of("lockedDaysConfirmed", true, "days", List.of(
                    Map.of("calendarDate", "2026-11-10", "dayKind", "HOLIDAY"),
                    Map.of("calendarDate", "2026-11-14", "dayKind", "MAKE_UP_WORKDAY"),
                    Map.of("calendarDate", "2026-11-21", "dayKind", "MAKE_UP_WORKDAY")));
            MockHttpServletResponse maintained = mvc.perform(put(CALENDAR).header("If-Match", "\"0\"")
                    .with(MAINTAINER).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(JSON.writeValueAsString(body))).andReturn().getResponse();
            assertStatus(maintained, 200, "2026 calendar is maintained once through HTTP");
        }
    }

    @Test
    @DisplayName("法人无自有版本时使用默认时间线，遵守周末节假日、调休及仅上班日生效的日期例外")
    void entityWithoutOwnVersionUsesDefaultCalendarRulesAndOverrides() throws Exception {
        UUID scope = newScope();
        ensureDefaultVersion();

        assertMinutes(scope, "2026-11-04", 465, "entity without own version uses DEFAULT workdayMinutes");
        assertMinutes(scope, "2026-11-07", 0, "DEFAULT ordinary Saturday rests");
        assertMinutes(scope, "2026-11-08", 0, "DEFAULT Sunday override cannot turn rest into work");
        assertMinutes(scope, "2026-11-10", 0, "DEFAULT holiday override cannot turn rest into work");
        assertMinutes(scope, "2026-11-14", 405, "DEFAULT make-up day uses adjustedWorkdayMinutes");
        assertMinutes(scope, "2026-11-21", 315, "DEFAULT make-up day override wins");
        assertMinutes(scope, "2026-11-12", 255, "DEFAULT ordinary workday override wins");
        assertMinutes(scope, "2026-11-11", 465, "DEFAULT override leaves preceding workday unchanged");
        assertMinutes(scope, "2026-11-13", 465, "DEFAULT override leaves following workday unchanged");
    }

    @Test
    @DisplayName("法人存在自有版本即仅使用自有时间线，首版之前即使默认已覆盖仍报缺失并告警")
    void ownTimelineWinsAndNeverFallsBackBeforeItsFirstVersion() throws Exception {
        UUID scope = newScope();
        UUID defaultOnly = newScope();
        ensureDefaultVersion();
        Map<String, Object> own = publication(scope, LocalDate.of(2026, 11, 20));
        own.put("workdayMinutes", 390);
        own.put("adjustedWorkdayMinutes", 330);
        publish(own, 1);

        assertMinutes(defaultOnly, "2026-11-19", 465, "DEFAULT actually covers the date before the own timeline starts");
        assertMissing(scope, "2026-11-19", "own timeline forbids fallback even though DEFAULT covers the date");
        assertMinutes(scope, "2026-11-20", 390, "own workday minutes win from the exact first effective date");
        assertMinutes(scope, "2026-11-23", 390, "own workday minutes win over different DEFAULT minutes");
        assertMinutes(scope, "2026-11-21", 330, "own adjusted minutes win without importing DEFAULT's date override");
        assertMinutes(defaultOnly, "2026-11-21", 315, "another entity still sees the DEFAULT override on the same date");
    }

    @Test
    @DisplayName("法人发布首个自有版本后同一日期的答案由默认切换为自有，默认例外不再参与且其他法人不变")
    void firstOwnPublicationSwitchesPreviouslyReadDefaultAnswers() throws Exception {
        UUID scope = newScope();
        UUID defaultOnly = newScope();
        ensureDefaultVersion();
        assertMinutes(scope, "2026-11-04", 465, "before own publication ordinary workday uses DEFAULT");
        assertMinutes(scope, "2026-11-12", 255, "before own publication date override comes from DEFAULT");
        assertMinutes(scope, "2026-11-14", 405, "before own publication make-up workday uses DEFAULT");

        Map<String, Object> own = publication(scope, TODAY);
        own.put("workdayMinutes", 390);
        own.put("adjustedWorkdayMinutes", 330);
        publish(own, 1);

        assertMinutes(scope, "2026-11-04", 390, "same entity and date switch to own workday minutes after publication");
        assertMinutes(scope, "2026-11-12", 390, "cached DEFAULT override is discarded when own timeline appears");
        assertMinutes(scope, "2026-11-14", 330, "same make-up date switches to own adjusted minutes");
        assertMinutes(defaultOnly, "2026-11-04", 465, "another entity continues using DEFAULT ordinary minutes");
        assertMinutes(defaultOnly, "2026-11-12", 255, "another entity retains the DEFAULT date override");
    }

    private void ensureDefaultVersion() throws Exception {
        synchronized (StandardHoursDefaultFallbackIT.class) {
            if (!defaultPublished) {
                Map<String, Object> body = publication(null, TODAY.minusDays(30));
                body.put("workdayMinutes", 465);
                body.put("adjustedWorkdayMinutes", 405);
                body.put("dateOverrides", List.of(
                        override(LocalDate.of(2026, 11, 8), 195, "Synthetic DEFAULT Sunday override"),
                        override(LocalDate.of(2026, 11, 10), 180, "Synthetic DEFAULT holiday override"),
                        override(LocalDate.of(2026, 11, 12), 255, "Synthetic DEFAULT workday override"),
                        override(LocalDate.of(2026, 11, 21), 315, "Synthetic DEFAULT make-up override")));
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
        return createLegalEntity("SH1310-" + UUID.randomUUID().toString().substring(0, 8), "Synthetic card 1310 entity");
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
