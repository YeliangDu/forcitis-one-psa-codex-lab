package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** Card #1499: under the profile local a session of the local test login can enter the administrator mode as a separate step, only when its account holds super_admin. */
@ActiveProfiles("local")
@TestPropertySource(properties = "spring.flyway.enabled=true")
class LocalAdministratorModeIT extends IdentityIntegrationTest {

    static final String ENTER = "/api/v1/platform/sessions:enter-local-administrator-mode";
    static final String ROLES = "/api/v1/platform/roles";

    @Autowired
    MeterRegistry meters;

    @Autowired
    com.forcitis.one.kernel.SettableClock settable;

    @org.junit.jupiter.api.AfterEach
    void realTime() {
        settable.reset();
        clock.reset();
    }

    /** Under the profile local one clock serves both; here the sessions read the test clock, so it follows the endpoint whenever the endpoint moved. */
    private int moveClock(java.time.Instant at) throws Exception {
        java.time.Duration by = java.time.Duration.between(clock.instant(), at);
        int status = mvc.perform(post("/api/v1/platform/local-clocks:set").with(xsrf()).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"at\":\"" + at + "\"}")).andReturn().getResponse().getStatus();
        if (status == 200) {
            clock.advance(by);
        }
        return status;
    }

    private int resetClock() throws Exception {
        int status = mvc.perform(post("/api/v1/platform/local-clocks:reset").with(xsrf())).andReturn().getResponse().getStatus();
        if (status == 200) {
            clock.reset();
        }
        return status;
    }

    @BeforeEach
    void people() {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        createAccount(1001, "employee");
        knownEmployee(1002, EmploymentStatus.EMPLOYED);
        createAccount(1002, "employee", "super_admin");
    }

    private Cookie login(UUID account) throws Exception {
        MvcResult login = postJson(LocalTestLoginIT.LOGIN, "{\"accountId\":\"" + account + "\"}");
        return new Cookie("SESSION", cookie(login, "SESSION"));
    }

    private MvcResult enter(Cookie session) throws Exception {
        return mvc.perform(post(ENTER).cookie(session).with(xsrf())).andReturn();
    }

    @Test
    @DisplayName("#1499 the local login of a super_admin holder is ordinary until the explicit step, then holds the platform permissions; the mode is audited without LOCAL_TEST in the column")
    void separateStep() throws Exception {
        Cookie session = login(accountOf(1002));
        assertThat(mvc.perform(get(ROLES).cookie(session)).andReturn().getResponse().getStatus()).isEqualTo(403);

        double entriesBefore = meters.counter("forcitis.platform.identity.administrator.mode.entries").count();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        root.addAppender(logged);
        MvcResult entered;
        try {
            entered = enter(session);
        } finally {
            root.detachAppender(logged);
        }
        assertThat(entered.getResponse().getStatus()).isEqualTo(200);
        assertThat(meters.counter("forcitis.platform.identity.administrator.mode.entries").count()).isEqualTo(entriesBefore + 1.0);
        assertThat(logged.list).filteredOn(line -> line.getFormattedMessage().startsWith("ALERT platform.identity administrator mode entered")).hasSize(1)
                .allSatisfy(line -> assertThat(line.getLevel()).isEqualTo(Level.ERROR));
        assertThat(java.time.Instant.parse(body(entered).path("administratorModeExpiresAt").asString())).as("the response names the end of the mode, the same one-hour limit as the WeCom step")
                .isBetween(java.time.Instant.now().plus(java.time.Duration.ofMinutes(59)), java.time.Instant.now().plus(java.time.Duration.ofMinutes(61)));
        Cookie elevated = new Cookie("SESSION", cookie(entered, "SESSION"));   // the session id is rotated when the mode starts
        assertThat(mvc.perform(get(ROLES).cookie(elevated)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(enter(elevated).getResponse().getStatus()).as("the mode is never extended").isEqualTo(403);
        assertThat(recorded()).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("platform.identity.administrator-mode.entered");
            assertThat(entry.loginMethod()).isNull();
        });
        assertThat(recorded()).noneMatch(entry -> "LOCAL_TEST".equals(entry.loginMethod()));
    }

    @Test
    @DisplayName("#1499 an account without super_admin cannot enter the mode, and the refusal is audited")
    void withoutSuperAdmin() throws Exception {
        Cookie session = login(accountOf(1001));

        assertThat(enter(session).getResponse().getStatus()).isEqualTo(403);
        assertThat(recordedIndependently()).anySatisfy(entry -> assertThat(entry.action()).isEqualTo("platform.identity.administrator-mode.refused"));
        assertThat(mvc.perform(get(ROLES).cookie(session)).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("#1499 a session of another login method cannot use the local step, even for a super_admin holder")
    void onlyTheLocalLogin() throws Exception {
        createAccount(9, "super_admin", "employee");
        Cookie wecom = new Cookie("SESSION", loginWithWecom(9));

        assertThat(enter(wecom).getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get(ROLES).cookie(wecom)).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("#1499 no session, no mode")
    void needsASession() throws Exception {
        assertThat(mvc.perform(post(ENTER).with(xsrf())).andReturn().getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    @DisplayName("#1499 the clock cannot be moved back to lengthen a running administrator mode")
    void clockBackWhileOpen() throws Exception {
        Cookie elevated = new Cookie("SESSION", cookie(enter(login(accountOf(1002))), "SESSION"));
        java.time.Instant now = java.time.Instant.now();

        assertThat(resetClock()).as("a reset of a clock that was never moved changes nothing and is not refused").isEqualTo(200);
        assertThat(moveClock(now.minus(java.time.Duration.ofDays(1)))).isEqualTo(409);
        assertThat(moveClock(now.plus(java.time.Duration.ofMinutes(30)))).as("forward is allowed: it can only shorten the mode").isEqualTo(200);
        assertThat(mvc.perform(get(ROLES).cookie(elevated)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(moveClock(now.plus(java.time.Duration.ofMinutes(5)))).as("back inside the hour, still before its end").isEqualTo(409);
    }

    @Test
    @DisplayName("#1499 the clock cannot be moved back after the mode ended, so an ended mode does not become valid again")
    void clockBackAfterEnd() throws Exception {
        Cookie elevated = new Cookie("SESSION", cookie(enter(login(accountOf(1002))), "SESSION"));
        java.time.Instant now = java.time.Instant.now();

        assertThat(moveClock(now.plus(java.time.Duration.ofMinutes(61)))).isEqualTo(200);
        assertThat(mvc.perform(get(ROLES).cookie(elevated)).andReturn().getResponse().getStatus()).as("the hour is over").isEqualTo(403);
        assertThat(moveClock(now.plus(java.time.Duration.ofMinutes(1)))).isEqualTo(409);
        assertThat(resetClock()).as("the real time is also earlier than the end").isEqualTo(409);
        assertThat(mvc.perform(get(ROLES).cookie(elevated)).andReturn().getResponse().getStatus()).as("no revival").isEqualTo(403);
    }
}
