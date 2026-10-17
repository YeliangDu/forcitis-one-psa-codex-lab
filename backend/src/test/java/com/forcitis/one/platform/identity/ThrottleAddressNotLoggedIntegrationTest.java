package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestPropertySource;

/** GUIDING_PRINCIPLES section 11 O-3: a throttled login is refused without writing the client address to the application log. */
@TestPropertySource(properties = "forcitis.platform.identity.login-throttle.limits.login-states=1")
class ThrottleAddressNotLoggedIntegrationTest extends IdentityIntegrationTest {
    static final String CLIENT_ADDRESS = "203.0.113.77";

    final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    Level before;

    @BeforeEach
    void capture() {
        before = root.getLevel();
        root.setLevel(Level.INFO);
        logged.start();
        root.addAppender(logged);
    }

    @AfterEach
    void release() {
        root.detachAppender(logged);
        root.setLevel(before);
        logged.stop();
    }

    @Test
    @DisplayName("O-3: the rate-limit refusal does not put the client address into any log line or the response")
    void rateLimitRefusalOmitsClientAddress() throws Exception {
        postLogin("/api/v1/platform/login-states", "{\"entry\":\"WORKBENCH\"}", null, CLIENT_ADDRESS);

        var refused = postLogin("/api/v1/platform/login-states", "{\"entry\":\"WORKBENCH\"}", null, CLIENT_ADDRESS);

        assertThat(refused.getResponse().getStatus()).isEqualTo(429);
        assertThat(refused.getResponse().getContentAsString()).doesNotContain(CLIENT_ADDRESS);
        assertThat(logged.list).anySatisfy(event -> assertThat(event.getFormattedMessage()).contains("login-states"));
        assertThat(logged.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(CLIENT_ADDRESS));
    }
}
