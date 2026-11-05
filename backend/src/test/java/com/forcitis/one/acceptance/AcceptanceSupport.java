package com.forcitis.one.acceptance;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.BusinessDay;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.fixture.doubles.MutableClock;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.SessionRoles;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.jayway.jsonpath.JsonPath;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Shared ground of the acceptance tests: the whole application on a real PostgreSQL (each test class declares its own container),
 * a clock fixed at {@link #NOW}, callers as ordinary sessions, and helpers that use only public HTTP paths or one module's own schema.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
@Import(AcceptanceSupport.FixedClock.class)
public abstract class AcceptanceSupport {

    /** The test clock starts at 10:00 on the business today; see {@link BusinessDay} for why this is not a fixed date. */
    public static final Instant NOW = BusinessDay.tenOClock();
    public static final LocalDate TODAY = BusinessDay.today();
    public static final ZoneId BUSINESS_ZONE = BusinessDay.ZONE;
    public static final String LEGAL_ENTITIES = "/api/v1/organization/legal-entities";
    public static final String AUDIT_ENTRIES = "/api/v1/platform/audit-entries";

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        MutableClock acceptanceClock() {
            return new MutableClock(NOW, ZoneOffset.UTC);
        }
    }

    /** A container of the class's own, only for tests that read the server log (K3b4AuditPagingIT, WorkCalendarYearsPagingIT); others use TestPostgres.database. */
    protected static PostgreSQLContainer database() {
        return TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");
    }

    @MockitoBean
    protected WecomGateway wecomGateway;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private DataSource dataSource;

    private ListAppender<ILoggingEvent> logged;
    private Logger root;

    @BeforeEach
    void startAtTheFixedInstantAndCaptureLogging() {
        clock.set(NOW);
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logged = new ListAppender<>();
        logged.start();
        root.addAppender(logged);
    }

    @AfterEach
    void releaseLogging() {
        root.detachAppender(logged);
        logged.stop();
    }

    protected void setToday(LocalDate day) {
        clock.set(day.atTime(10, 0).atZone(BUSINESS_ZONE).toInstant());
    }

    protected int logMark() {
        return logged.list.size();
    }

    protected List<String> alertsSince(int mark) {
        List<ILoggingEvent> events = List.copyOf(logged.list);
        List<String> alerts = new ArrayList<>();
        for (ILoggingEvent event : events.subList(Math.min(mark, events.size()), events.size())) {
            if (event.getLevel().isGreaterOrEqual(Level.ERROR) && event.getFormattedMessage().startsWith("ALERT ")) {
                alerts.add(event.getFormattedMessage());
            }
        }
        return alerts;
    }

    // UserId moves from long to UUID in another R-1b part (P2); the session principal follows whichever form the build has.
    protected static String principalName(int person) {
        Class<?> idType = UserId.class.isRecord() ? UserId.class.getRecordComponents()[0].getType() : long.class;
        return idType == UUID.class ? String.format("01900000-0000-7000-8000-%012d", person) : String.valueOf(70000 + person);
    }

    protected static RequestPostProcessor person(int person, String... permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + SessionRoles.ORDINARY));
        for (String permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        return SecurityMockMvcRequestPostProcessors.user(principalName(person)).authorities(authorities);
    }

    protected static final RequestPostProcessor LEGAL_ENTITY_MAINTAINER = person(1, Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE,
            Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ);

    protected static final RequestPostProcessor AUDIT_READER = person(2, Permission.Codes.PLATFORM_AUDIT_ENTRY_READ);

    protected UUID createLegalEntity(String code, String name) throws Exception {
        MockHttpServletResponse created = mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(LEGAL_ENTITY_MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}")).andReturn().getResponse();
        if (created.getStatus() != 201) {
            throw new AssertionError("creating legal entity " + code + " answered " + created.getStatus() + ": " + created.getContentAsString());
        }
        return UUID.fromString(JsonPath.read(created.getContentAsString(), "$.legalEntityId"));
    }

    /** The audit actions recorded so far whose name starts with the prefix, read through the audit HTTP query. */
    protected List<String> auditedActions(String actionPrefix) throws Exception {
        MockHttpServletResponse found = mvc.perform(get(AUDIT_ENTRIES).param("actionPrefix", actionPrefix).param("limit", "200").with(AUDIT_READER))
                .andReturn().getResponse();
        if (found.getStatus() != 200) {
            throw new AssertionError("the audit query answered " + found.getStatus() + ": " + found.getContentAsString());
        }
        return JsonPath.read(found.getContentAsString(), "$.items[*].action");
    }

    /** A connection of its own whose search path is one module schema, so that SQL names only that module's tables. */
    protected Connection schemaConnection(String schema) throws SQLException {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        Connection connection = DriverManager.getConnection(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword());
        try (Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
        }
        return connection;
    }
}
