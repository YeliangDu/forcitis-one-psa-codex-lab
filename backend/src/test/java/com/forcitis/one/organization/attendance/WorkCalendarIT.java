package com.forcitis.one.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.api.WorkCalendar;
import com.forcitis.one.organization.attendance.events.WorkCalendarYearMaintained;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "forcitis.platform.approval.assignment.process-administrator-user-id=9001",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"})
@AutoConfigureMockMvc
@RecordApplicationEvents
@Testcontainers
class WorkCalendarIT {
    static final String CALENDARS = "/api/v1/organization/work-calendars";

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18")
            .withInitScript("init-schemas.sql")
            .withUrlParam("currentSchema", "platform_approval");

    @Autowired
    MockMvc mvc;

    @Autowired
    WorkCalendar workCalendar;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ApplicationEvents events;

    @BeforeEach
    void empty() {
        jdbc.sql("truncate table organization_attendance.work_calendar_days, organization_attendance.work_calendar_years restart identity cascade").update();
    }

    static RequestPostProcessor caller(String... authorities) {
        return user("9001").authorities(java.util.Arrays.stream(authorities).<GrantedAuthority>map(a -> () -> a).toList());
    }

    static final RequestPostProcessor UPDATE = caller("organization.attendance:work-calendar:update", "organization.attendance:locked-day:update");
    static final RequestPostProcessor UPDATE_WITHOUT_LOCKED_DAYS = caller("organization.attendance:work-calendar:update");
    static final RequestPostProcessor READ = caller("organization.attendance:work-calendar:read");

    static final String BODY_2026 = """
            {"lockedDaysConfirmed": true, "days": [{"calendarDate": "2026-10-10", "dayKind": "MAKE_UP_WORKDAY"}, {"calendarDate": "2026-10-12", "dayKind": "HOLIDAY"}]}
            """;

    @Test
    @DisplayName("an administrator saves a year; the answers of WorkCalendar follow it and the save publishes WorkCalendarYearMaintained")
    void maintainThenAsk() throws Exception {
        mvc.perform(put(CALENDARS + "/2026").header("If-None-Match", "*").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY_2026))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.calendarYear").value(2026))
                .andExpect(jsonPath("$.days.length()").value(2));

        LegalEntityId fst = new LegalEntityId(1);
        assertThat(workCalendar.isWorkday(LocalDate.parse("2026-10-10"), fst)).isTrue();
        assertThat(workCalendar.isWorkday(LocalDate.parse("2026-10-12"), fst)).isFalse();
        assertThat(workCalendar.endOfWorkdayAfter(LocalDate.parse("2026-10-09"), 2, fst)).isEqualTo(Instant.parse("2026-10-13T16:00:00Z"));
        assertThat(events.stream(WorkCalendarYearMaintained.class)).singleElement().satisfies(e -> {
            assertThat(e.envelope().aggregateId()).isEqualTo("default:2026");
            assertThat(e.envelope().actorId()).isEqualTo("9001");
            assertThat(e.envelope().correlationId()).isNotNull();
        });
    }

    @Test
    @DisplayName("saving the same year again replaces its days and moves the version")
    void maintainTwice() throws Exception {
        mvc.perform(put(CALENDARS + "/2026").header("If-None-Match", "*").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY_2026)).andExpect(status().isOk());
        mvc.perform(put(CALENDARS + "/2026").header("If-Match", "\"1\"").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"lockedDaysConfirmed\": true, \"days\": []}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\"")).andExpect(jsonPath("$.days.length()").value(0));

        assertThat(jdbc.sql("select count(*) from organization_attendance.work_calendar_days").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select version from organization_attendance.work_calendar_years").query(Long.class).single()).isEqualTo(2L);
        mvc.perform(get(CALENDARS).with(READ)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].calendarYear").value(2026));
        mvc.perform(get(CALENDARS + "/2026").with(READ)).andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.days.length()").value(0));
    }

    @Test
    @DisplayName("an unmaintained year answers 404 on read and WorkCalendar fails for it")
    void unmaintained() throws Exception {
        mvc.perform(get(CALENDARS + "/2031").with(READ)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_ATTENDANCE_YEAR_NOT_FOUND"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> workCalendar.isWorkday(LocalDate.parse("2031-01-02"), new LegalEntityId(1)))
                .hasMessageContaining("2031");
    }

    @Test
    @DisplayName("invalid input is a 422 business error: a make-up workday on a weekday, a day of another year, an unknown kind")
    void invalidInput() throws Exception {
        for (String day : new String[] {"{\"calendarDate\": \"2026-10-09\", \"dayKind\": \"MAKE_UP_WORKDAY\"}",
                "{\"calendarDate\": \"2027-01-01\", \"dayKind\": \"HOLIDAY\"}",
                }) {
            mvc.perform(put(CALENDARS + "/2026").header("If-None-Match", "*").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lockedDaysConfirmed\": true, \"days\": [" + day + "]}"))
                    .andExpect(status().isUnprocessableContent());
        }
        mvc.perform(put(CALENDARS + "/2026").header("If-None-Match", "*").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\": [{\"calendarDate\": \"2026-10-01\", \"dayKind\": \"PARTY\"}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_REQUEST_INVALID"));
        mvc.perform(put(CALENDARS + "/1900").header("If-None-Match", "*").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"days\": []}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("reading needs work-calendar:read and writing needs work-calendar:update")
    void permissions() throws Exception {
        mvc.perform(get(CALENDARS)).andExpect(status().isUnauthorized());
        mvc.perform(get(CALENDARS).with(UPDATE)).andExpect(status().isForbidden());
        mvc.perform(put(CALENDARS + "/2026").header("If-None-Match", "*").with(READ).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"days\": []}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("writes without a version are refused with 428; a stale version with 412; a never-maintained year needs If-None-Match: *")
    void versionPreconditions() throws Exception {
        mvc.perform(put(CALENDARS + "/2026").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY_2026))
                .andExpect(status().isPreconditionRequired()).andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_VERSION_REQUIRED"));
        mvc.perform(put(CALENDARS + "/2026").header("If-Match", "\"1\"").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY_2026))
                .andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_VERSION_MISMATCH"));
        mvc.perform(put(CALENDARS + "/2026").header("If-None-Match", "*").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY_2026))
                .andExpect(status().isOk());
        mvc.perform(put(CALENDARS + "/2026").header("If-None-Match", "*").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY_2026))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(put(CALENDARS + "/2026").header("If-Match", "\"7\"").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY_2026))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    @DisplayName("T2 a change to a day before today is refused until the maintainer confirms it; the audit event names the locked days")
    void lockedDays() throws Exception {
        String unconfirmed = "{\"days\": [{\"calendarDate\": \"2026-10-12\", \"dayKind\": \"HOLIDAY\"}]}";
        mvc.perform(put(CALENDARS + "/2026").header("If-None-Match", "*").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(unconfirmed))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errorCode").value("ORGANIZATION_ATTENDANCE_LOCKED_DAY_UNCONFIRMED"));
        mvc.perform(put(CALENDARS + "/2026").header("If-None-Match", "*").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY_2026))
                .andExpect(status().isOk());

        assertThat(events.stream(WorkCalendarYearMaintained.class)).singleElement().satisfies(e -> {
            assertThat(e.lockedDatesChanged()).containsExactly(LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-12"));
            assertThat(e.changedDates()).hasSize(2);
        });
    }

    @Test
    @DisplayName("T2 confirming a change to past days needs the locked-days permission; without it the answer is a 403 ProblemDetail, with it the save goes through")
    void lockedDaysNeedTheirOwnPermission() throws Exception {
        mvc.perform(put(CALENDARS + "/2026").header("If-None-Match", "*").with(UPDATE_WITHOUT_LOCKED_DAYS).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY_2026))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ORGANIZATION_ATTENDANCE_LOCKED_DAY_FORBIDDEN"));
        mvc.perform(put(CALENDARS + "/2026").header("If-None-Match", "*").with(UPDATE_WITHOUT_LOCKED_DAYS).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\": [{\"calendarDate\": \"2026-12-25\", \"dayKind\": \"HOLIDAY\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(put(CALENDARS + "/2026").header("If-Match", "\"1\"").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY_2026))
                .andExpect(status().isOk());
    }
}
