package com.forcitis.one.organization.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.attendance.api.WorkCalendar;
import com.forcitis.one.organization.attendance.application.DayEntry;
import com.forcitis.one.organization.attendance.application.MaintainWorkCalendarCommand;
import com.forcitis.one.organization.attendance.application.MaintainLockedWorkCalendarDays;
import com.forcitis.one.organization.attendance.api.DayException;
import com.forcitis.one.organization.attendance.api.LockedDayChange;
import com.forcitis.one.organization.attendance.events.WorkCalendarLockedDaysChanged;
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
@org.springframework.context.annotation.Import(WorkCalendarIT.FixedClock.class)
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

    static final java.time.Instant CLOCK_START = java.time.Instant.parse("2026-10-01T00:00:00Z");

    @org.springframework.boot.test.context.TestConfiguration
    static class FixedClock {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        com.forcitis.one.fixture.doubles.MutableClock clock() {
            return new com.forcitis.one.fixture.doubles.MutableClock(CLOCK_START, java.time.ZoneOffset.UTC);
        }
    }

    @Autowired
    com.forcitis.one.fixture.doubles.MutableClock clock;

    @Autowired
    MaintainLockedWorkCalendarDays maintainLocked;

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
        clock.set(CLOCK_START);
        jdbc.sql("truncate table organization_attendance.work_calendar_days, organization_attendance.work_calendar_years restart identity cascade").update();
    }

    static RequestPostProcessor caller(String... authorities) {
        return user("9001").authorities(java.util.Arrays.stream(authorities).<GrantedAuthority>map(a -> () -> a).toList());
    }

    static final RequestPostProcessor UPDATE = caller("organization.attendance:work-calendar:update");
    static final RequestPostProcessor READ = caller("organization.attendance:work-calendar:read");

    static final String BODY_2026 = """
            {"days": [{"calendarDate": "2026-10-10", "dayKind": "MAKE_UP_WORKDAY"}, {"calendarDate": "2026-10-12", "dayKind": "HOLIDAY"}]}
            """;

    @Test
    @DisplayName("an administrator saves a year; the answers of WorkCalendar follow it and the save publishes WorkCalendarYearMaintained")
    void maintainThenAsk() throws Exception {
        mvc.perform(put(CALENDARS + "/2026").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY_2026))
                .andExpect(status().isOk())
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
        mvc.perform(put(CALENDARS + "/2026").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY_2026)).andExpect(status().isOk());
        mvc.perform(put(CALENDARS + "/2026").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"days\": []}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days.length()").value(0));

        assertThat(jdbc.sql("select count(*) from organization_attendance.work_calendar_days").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select version from organization_attendance.work_calendar_years").query(Long.class).single()).isEqualTo(2L);
        mvc.perform(get(CALENDARS).with(READ)).andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(2026));
        mvc.perform(get(CALENDARS + "/2026").with(READ)).andExpect(status().isOk()).andExpect(jsonPath("$.days.length()").value(0));
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
                "{\"calendarDate\": \"2026-10-01\", \"dayKind\": \"PARTY\"}"}) {
            mvc.perform(put(CALENDARS + "/2026").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"days\": [" + day + "]}"))
                    .andExpect(status().isUnprocessableContent());
        }
        mvc.perform(put(CALENDARS + "/1900").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"days\": []}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("reading needs work-calendar:read and writing needs work-calendar:update")
    void permissions() throws Exception {
        mvc.perform(get(CALENDARS)).andExpect(status().isUnauthorized());
        mvc.perform(get(CALENDARS).with(UPDATE)).andExpect(status().isForbidden());
        mvc.perform(put(CALENDARS + "/2026").with(READ).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"days\": []}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("T2 over HTTP a past day cannot change: 422 ORGANIZATION_ATTENDANCE_LOCKED_DAY_UNCONFIRMED, nothing saved")
    void pastDaysAreLockedOverHttp() throws Exception {
        mvc.perform(put(CALENDARS + "/2026").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY_2026)).andExpect(status().isOk());
        clock.set(java.time.Instant.parse("2026-10-14T00:00:00Z"));

        mvc.perform(put(CALENDARS + "/2026").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"days\": []}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_ATTENDANCE_LOCKED_DAY_UNCONFIRMED"));

        assertThat(jdbc.sql("select count(*) from organization_attendance.work_calendar_days").query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    @DisplayName("T2 with lockedDaysConfirmed the change is saved and publishes WorkCalendarLockedDaysChanged with the changed past days; the answers follow")
    void confirmedChangeIsSavedAndAudited() throws Exception {
        mvc.perform(put(CALENDARS + "/2026").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY_2026)).andExpect(status().isOk());
        clock.set(java.time.Instant.parse("2026-10-14T00:00:00Z"));

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken("9001", null, "organization.attendance:locked-day:update"));
        try (var acting = com.forcitis.one.kernel.WorkContext.enter(java.util.UUID.randomUUID(), null, "organization.attendance.test")) {
            maintainLocked.execute(new MaintainWorkCalendarCommand(2026, java.util.List.of(new DayEntry(LocalDate.parse("2026-10-10"), "MAKE_UP_WORKDAY"))));
        }

        assertThat(workCalendar.isWorkday(LocalDate.parse("2026-10-12"), new LegalEntityId(1))).isTrue();
        assertThat(events.stream(WorkCalendarLockedDaysChanged.class)).singleElement().satisfies(e ->
                assertThat(e.changes()).containsExactly(new LockedDayChange(LocalDate.parse("2026-10-12"), DayException.HOLIDAY, DayException.NONE)));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("T2 the exception is its own use case with its own permission: without it the call is refused and nothing changes")
    void lockedDaysNeedTheirOwnPermission() throws Exception {
        mvc.perform(put(CALENDARS + "/2026").with(UPDATE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY_2026)).andExpect(status().isOk());
        clock.set(java.time.Instant.parse("2026-10-14T00:00:00Z"));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.TestingAuthenticationToken("9001", null, "organization.attendance:work-calendar:update"));
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> maintainLocked.execute(new MaintainWorkCalendarCommand(2026, java.util.List.of())))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        assertThat(jdbc.sql("select count(*) from organization_attendance.work_calendar_days").query(Integer.class).single()).isEqualTo(2);
    }
}
