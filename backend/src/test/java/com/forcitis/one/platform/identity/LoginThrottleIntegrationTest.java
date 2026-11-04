package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {"forcitis.platform.identity.login-throttle.limits.login-states=3",
        "forcitis.platform.identity.login-throttle.limits.wecom-login=4",
        "forcitis.platform.identity.login-throttle.limits.ticket-login=2",
        "forcitis.platform.identity.login-throttle.limits.emergency-login=2"})
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

    @Test
    @DisplayName("H-4 each kind has its own budget: the ticket and emergency logins are counted too, and one kind running out does not block another")
    void everyLoginKindHasItsOwnBudget() throws Exception {
        String[][] logins = {{"/api/v1/platform/sessions:ticket-login", "{\"ticket\":\"x\"}"},
                {"/api/v1/platform/sessions:emergency-login", "{\"loginName\":\"nobody\",\"password\":\"x\",\"totpCode\":\"000000\"}"}};
        for (String[] login : logins) {
            for (int i = 0; i < 2; i++) {
                assertThat(postJson(login[0], login[1]).getResponse().getStatus()).as(login[0]).isNotEqualTo(429);
            }
            var refused = postJson(login[0], login[1]);
            assertThat(refused.getResponse().getStatus()).as(login[0]).isEqualTo(429);
            assertThat(body(refused).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_LOGIN_RATE_LIMITED");
        }
        assertThat(postJson("/api/v1/platform/sessions:wecom-login", "{\"code\":\"c\",\"state\":\"s\",\"entry\":\"WORKBENCH\"}").getResponse().getStatus())
                .as("another kind still has its budget").isNotEqualTo(429);
    }
}
