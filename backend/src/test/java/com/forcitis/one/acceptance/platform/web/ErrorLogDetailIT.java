package com.forcitis.one.acceptance.platform.web;

import static com.forcitis.one.platform.approval.OrdinarySessions.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * D1-2 (A 4-H2): what a failed request writes to the log. An unexpected failure is logged with its class name and the correlation id
 * of the request and nothing the exception carries (message, causes, stack trace); a database error never puts the offending value
 * (a person's e-mail address, say) on disk. Every value is generated at run time, no personal data is written in this file.
 *
 * The failure is raised from behind the public GET /api/v1/organization/org-units: the org-unit visibility lookup (a kernel interface)
 * is replaced so that it throws, once with a plain exception and once with a real unique-constraint violation of the container database.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "forcitis.platform.approval.assignment.process-administrator-user-id=9001", "spring.datasource.hikari.connection-timeout=2000"})
@AutoConfigureMockMvc
class ErrorLogDetailIT {

    private static final String ENDPOINT = "/api/v1/organization/org-units";
    private static final String CONTACTS = "public.d1_2_contacts";

    static class ProbeFailure extends RuntimeException {
        ProbeFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @MockitoBean
    OrgUnitAccess orgUnitAccess;

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    static final TestDatabase postgres = TestPostgres.database(ErrorLogDetailIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private ListAppender<ILoggingEvent> captured;
    private Logger root;

    @BeforeEach
    void captureAllLogging() {
        jdbc.sql("create table if not exists " + CONTACTS + " (email text not null unique)").update();
        jdbc.sql("delete from " + CONTACTS).update();
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
    }

    @AfterEach
    void releaseLogging() {
        root.detachAppender(captured);
        captured.stop();
    }

    private MockHttpServletResponse callAs(UUID correlationId) throws Exception {
        return mvc.perform(get(ENDPOINT).header("X-Correlation-Id", correlationId.toString())
                        .with(user("5").authorities(com.forcitis.one.platform.approval.OrdinarySessions.with(
                                Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ))))
                .andReturn().getResponse();
    }

    /** Everything one event would put on disk: logger, level, message and arguments, context fields, key-values, the whole exception. */
    private static String asWritten(ILoggingEvent event) {
        StringBuilder line = new StringBuilder(event.getLoggerName()).append(' ').append(event.getLevel()).append(' ')
                .append(event.getFormattedMessage());
        if (event.getArgumentArray() != null) {
            for (Object argument : event.getArgumentArray()) {
                line.append(' ').append(argument);
            }
        }
        event.getMDCPropertyMap().forEach((key, value) -> line.append(' ').append(key).append('=').append(value));
        if (event.getKeyValuePairs() != null) {
            event.getKeyValuePairs().forEach(pair -> line.append(' ').append(pair.key).append('=').append(pair.value));
        }
        if (event.getThrowableProxy() != null) {
            line.append('\n').append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
        }
        return line.toString();
    }

    private String everythingWritten() {
        return String.join("\n", List.copyOf(captured.list).stream().map(ErrorLogDetailIT::asWritten).toList());
    }

    @Test
    @DisplayName("D1-2 唯一约束冲突时日志不含冲突值（邮箱不落盘）")
    void uniqueViolationDoesNotPutTheConflictingValueInTheLog() throws Exception {
        String email = "d1-2-" + UUID.randomUUID() + "@" + UUID.randomUUID().toString().substring(0, 8) + ".invalid";
        jdbc.sql("insert into " + CONTACTS + " (email) values (:email)").param("email", email).update();
        when(orgUnitAccess.visibleOrgUnits(anyString())).thenAnswer(call -> {
            jdbc.sql("insert into " + CONTACTS + " (email) values (:email)").param("email", email).update();
            return OrgUnitAccess.Visible.all();
        });
        UUID correlationId = UUID.randomUUID();
        captured.list.clear();

        MockHttpServletResponse response = callAs(correlationId);

        assertThat(response.getStatus()).as("the unique violation must surface as a server error of the request").isEqualTo(500);
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(correlationId.toString());
        assertThat(captured.list).as("the failed request must be logged").isNotEmpty();
        assertThat(everythingWritten()).as("the log must not contain the value that conflicted").doesNotContain(email);
    }

    @Test
    @DisplayName("D1-2 未知异常日志只含类名与 correlationId")
    void unexpectedFailureIsLoggedWithClassNameAndCorrelationIdOnly() throws Exception {
        String marker = "marker-" + UUID.randomUUID();
        String causeMarker = "cause-" + UUID.randomUUID();
        when(orgUnitAccess.visibleOrgUnits(anyString())).thenThrow(
                new ProbeFailure("lookup failed for " + marker, new IllegalArgumentException("inner " + causeMarker)));
        UUID correlationId = UUID.randomUUID();
        captured.list.clear();

        MockHttpServletResponse response = callAs(correlationId);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(correlationId.toString());
        List<String> ofThisRequest = List.copyOf(captured.list).stream()
                .filter(event -> correlationId.toString().equals(event.getMDCPropertyMap().get("correlationId")))
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .map(ErrorLogDetailIT::asWritten).toList();
        assertThat(ofThisRequest).as("an error line carrying the correlation id of the request").isNotEmpty();
        assertThat(ofThisRequest).as("an error line naming the exception class").anyMatch(line -> line.contains("ProbeFailure"));
        assertThat(everythingWritten()).as("the exception message must not be written").doesNotContain(marker);
        assertThat(everythingWritten()).as("the message of the cause (stack trace) must not be written").doesNotContain(causeMarker);
    }
}
