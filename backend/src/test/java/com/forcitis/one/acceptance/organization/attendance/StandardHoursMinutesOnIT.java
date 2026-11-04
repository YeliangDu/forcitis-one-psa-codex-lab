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
 * Card 1310: minutesOn contract lines (a)-(g): calendar precedence and per-version overrides,
 * successor boundaries, missing own timelines without DEFAULT, one ALERT per missing answer,
 * future-version voiding, propagation of YEAR_NOT_MAINTAINED and silent success paths.
 * Each test creates a synthetic legal entity through HTTP; this class never publishes DEFAULT.
 * Fixtures use HTTP and observations use the StandardHours Java API and captured ALERT lines.
 */
class StandardHoursMinutesOnIT extends AcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(StandardHoursMinutesOnIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String VERSIONS = "/api/v1/organization/standard-hours-versions";
    private static final String CALENDAR = "/api/v1/organization/work-calendars/2026";
    private static final RequestPostProcessor PUBLISHER = person(31, "organization.attendance:standard-hours:publish");
    private static final RequestPostProcessor MAINTAINER = person(21, "organization.attendance:work-calendar:read",
            "organization.attendance:work-calendar:update", "organization.attendance:locked-day:update");
    private static final JsonMapper JSON = JsonMapper.builder().build();

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
    @DisplayName("工作日取普通分钟、调休取调整分钟，日期例外仅覆盖上班日且不影响相邻日")
    void calendarKindDecidesAndOverridesApplyOnWorkingDaysOnly() throws Exception {
        UUID scope = newScope();
        Map<String, Object> body = publication(scope, TODAY);
        body.put("dateOverrides", List.of(
                override(LocalDate.of(2026, 11, 8), 180, "Synthetic Sunday override"),
                override(LocalDate.of(2026, 11, 10), 210, "Synthetic holiday override"),
                override(LocalDate.of(2026, 11, 12), 240, "Synthetic workday override"),
                override(LocalDate.of(2026, 11, 21), 300, "Synthetic make-up override")));
        publish(body, 1);

        assertMinutes(scope, "2026-11-04", 480, "ordinary Wednesday uses workdayMinutes");
        assertMinutes(scope, "2026-11-07", 0, "ordinary Saturday rests");
        assertMinutes(scope, "2026-11-08", 0, "Sunday rests even with an override");
        assertMinutes(scope, "2026-11-10", 0, "weekday holiday rests even with an override");
        assertMinutes(scope, "2026-11-14", 420, "make-up Saturday without an override uses adjustedWorkdayMinutes");
        assertMinutes(scope, "2026-11-21", 300, "make-up Saturday override wins over adjustedWorkdayMinutes");
        assertMinutes(scope, "2026-11-12", 240, "ordinary workday override wins over workdayMinutes");
        assertMinutes(scope, "2026-11-09", 480, "day before the holiday retains ordinary minutes");
        assertMinutes(scope, "2026-11-11", 480, "day before the workday override retains ordinary minutes");
        assertMinutes(scope, "2026-11-13", 480, "day after the workday override retains ordinary minutes");
        assertMinutes(scope, "2026-11-20", 480, "Friday before the make-up override retains ordinary minutes");
        assertMinutes(scope, "2026-11-22", 0, "Sunday after the make-up override still rests");
    }

    @Test
    @DisplayName("后续版从生效当天接替前版，前一天仍取前版且日期例外不跨版本泄漏")
    void successorStartsInclusivelyAndUsesOnlyItsOwnOverrides() throws Exception {
        UUID scope = newScope();
        Map<String, Object> first = publication(scope, TODAY);
        first.put("dateOverrides", List.of(override(LocalDate.of(2026, 12, 3), 195, "Synthetic predecessor override")));
        publish(first, 1);
        Map<String, Object> second = publication(scope, LocalDate.of(2026, 12, 1));
        second.put("workdayMinutes", 360);
        second.put("adjustedWorkdayMinutes", 330);
        second.put("dateOverrides", List.of(override(LocalDate.of(2026, 12, 2), 285, "Synthetic successor override")));
        publish(second, 2);

        assertMinutes(scope, "2026-11-02", 480, "first version applies on its effective start date");
        assertMinutes(scope, "2026-11-30", 480, "day before successor still uses predecessor");
        assertMinutes(scope, "2026-12-01", 360, "successor applies on its effective start date");
        assertMinutes(scope, "2026-12-02", 285, "successor uses its own date override");
        assertMinutes(scope, "2026-12-03", 360, "predecessor override cannot leak into successor interval");
        assertMinutes(scope, "2026-12-31", 360, "latest version remains open ended within the maintained year");
    }

    @Test
    @DisplayName("法人首版之前无生效版本即报缺失并记录一条告警，首版生效后正常且无告警")
    void dateBeforeFirstOwnVersionIsMissingWithoutDefaultFallback() throws Exception {
        UUID scope = newScope();
        publish(publication(scope, LocalDate.of(2026, 11, 20)), 1);

        assertMissing(scope, "2026-11-19", "before first own version with DEFAULT absent");
        assertMinutes(scope, "2026-11-20", 480, "first own version starts inclusively");
        assertMinutes(scope, "2026-11-23", 480, "workday after first own version answers without ALERT");
    }

    @Test
    @DisplayName("法人和默认范围均无版本时工作日与周末都报缺失并各记一条告警，不编造默认分钟")
    void absentTimelineFailsOnBothWorkdaysAndRestDays() throws Exception {
        UUID scope = newScope();

        assertMissing(scope, "2026-11-04", "missing workday must throw instead of inventing 450, 480 or 7.5");
        assertMissing(scope, "2026-11-07", "missing weekend must throw instead of short-circuiting to zero");
    }

    @Test
    @DisplayName("未来最新版本作废前在起始日取新值，作废后同日恢复前版分钟")
    void voidedFutureVersionIsExcludedFromTheTimeline() throws Exception {
        UUID scope = newScope();
        publish(publication(scope, TODAY), 1);
        Map<String, Object> future = publication(scope, LocalDate.of(2026, 12, 1));
        future.put("workdayMinutes", 360);
        String futureId = publish(future, 2);
        assertMinutes(scope, "2026-12-01", 360, "future version answers before it is voided");

        assertStatus(voidVersion(futureId, Map.of("reason", "Synthetic card 1310 cancellation"), PUBLISHER),
                200, "publisher can void the latest future version");

        assertMinutes(scope, "2026-12-01", 480, "void excludes successor and reopens predecessor at the former boundary");
        assertMinutes(scope, "2026-12-02", 480, "reopened predecessor remains in force after the voided start date");
    }

    @Test
    @DisplayName("标准工时版本覆盖到未维护的年度时透传日历年度缺失错误，不转换为标准工时缺失")
    void unmaintainedYearPropagatesTheCalendarError() throws Exception {
        UUID scope = newScope();
        publish(publication(scope, TODAY), 1);

        assertThatThrownBy(() -> standardHours.minutesOn(new LegalEntityId(scope), LocalDate.of(2027, 1, 4)))
                .as("an open-ended version must still consult the unmaintained 2027 calendar")
                .isInstanceOfSatisfying(DomainException.class, failure ->
                        assertThat(failure.errorCode()).as("calendar error propagates without standard-hours translation")
                                .isEqualTo(AttendanceError.ORGANIZATION_ATTENDANCE_YEAR_NOT_MAINTAINED)
                                .isNotEqualTo(AttendanceError.ORGANIZATION_ATTENDANCE_STANDARD_HOURS_NOT_FOUND));
    }

    @Test
    @DisplayName("成功查询普通工作日、休息日、节假日、调休及日期例外均不记录告警，重复读取结果稳定")
    void successfulReadsNeverEmitAlerts() throws Exception {
        UUID scope = newScope();
        Map<String, Object> body = publication(scope, TODAY.minusDays(10));
        body.put("dateOverrides", List.of(override(LocalDate.of(2026, 11, 12), 255, "Synthetic silent override")));
        publish(body, 1);
        int mark = logMark();

        for (int read = 0; read < 2; read++) {
            assertMinutes(scope, "2026-11-04", 480, "successful ordinary workday read " + read);
            assertMinutes(scope, "2026-11-07", 0, "successful rest day read " + read);
            assertMinutes(scope, "2026-11-10", 0, "successful holiday read " + read);
            assertMinutes(scope, "2026-11-14", 420, "successful make-up workday read " + read);
            assertMinutes(scope, "2026-11-12", 255, "successful override read " + read);
        }

        assertThat(alertsSince(mark)).as("all successful initial and repeated reads produce no ALERT lines").isEmpty();
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

    private MockHttpServletResponse voidVersion(String id, Map<String, Object> body, RequestPostProcessor caller) throws Exception {
        return mvc.perform(post(VERSIONS + "/" + id + ":void").with(caller).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))).andReturn().getResponse();
    }

    private static void assertStatus(MockHttpServletResponse response, int expected, String rule) throws Exception {
        assertThat(response.getStatus()).as(rule + "; response: " + response.getContentAsString()).isEqualTo(expected);
    }
}
