package com.forcitis.one.platform.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.application.ClientOrigin;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;

/** GUIDING_PRINCIPLES section 11 O-3: a failed refusal-audit write is logged by failure type only, never with the client address in its message. */
class AccessDeniedAuditLogTest {
    static final String CLIENT_ADDRESS = "203.0.113.77";

    final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    final Logger logger = (Logger) LoggerFactory.getLogger(AccessDeniedAudit.class);

    @BeforeEach
    void capture() {
        logged.start();
        logger.addAppender(logged);
    }

    @AfterEach
    void release() {
        logger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @DisplayName("O-3: when the access-refusal audit entry cannot be written, the log line carries the failure type but not the client address from its message")
    void failedRefusalAuditWriteOmitsClientAddress() {
        AuditLog audit = mock(AuditLog.class);
        doThrow(new IllegalStateException("Failing row contains (" + CLIENT_ADDRESS + ")")).when(audit).recordIndependently(any());
        ClientOrigin origin = mock(ClientOrigin.class);
        when(origin.address()).thenReturn(CLIENT_ADDRESS);
        AccessDeniedAudit denied = new AccessDeniedAudit(audit, origin, Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneOffset.UTC),
                new SimpleMeterRegistry());
        var person = UsernamePasswordAuthenticationToken.authenticated(TestAccounts.account(6001).value().toString(), "n/a", java.util.List.of());

        denied.publishAuthorizationEvent(() -> person, "resource", new AuthorizationDecision(false));

        assertThat(logged.list).anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("IllegalStateException"));
        assertThat(logged.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(CLIENT_ADDRESS));
    }
}
