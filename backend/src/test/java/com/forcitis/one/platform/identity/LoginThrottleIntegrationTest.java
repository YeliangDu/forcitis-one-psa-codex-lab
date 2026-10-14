package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "forcitis.platform.identity.login-throttle.max-per-minute=3")
class LoginThrottleIntegrationTest extends IdentityIntegrationTest {

    @Test
    @DisplayName("M-13 a client address that asks for more login states than the budget gets 429 with the rate-limit error code")
    void loginStatesAreThrottled() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(postJson("/api/v1/platform/login-states", "{\"entry\":\"WORKBENCH\"}").getResponse().getStatus()).isEqualTo(200);
        }

        var refused = postJson("/api/v1/platform/login-states", "{\"entry\":\"WORKBENCH\"}");

        assertThat(refused.getResponse().getStatus()).isEqualTo(429);
        assertThat(body(refused).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_LOGIN_RATE_LIMITED");
        assertThat(jdbc.sql("select count(*) from platform_identity.login_states").query(Long.class).single()).isEqualTo(3L);
    }
}
