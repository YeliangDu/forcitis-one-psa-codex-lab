package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.platform.identity.application.PurgeExpiredSessions;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * FLAKY-SESS: the purge judges expiry with the application clock (fixed at NOW in acceptance tests), so the session written at login
 * must be stamped by the same clock. Run once, deterministically: no dependence on the minute boundary of the scheduled job.
 */
@Testcontainers
class SessionPurgeClockIT extends AccountsAcceptanceSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    private static final String LOGIN_REQUIRED = "/api/v1/platform/sessions:current";

    @Autowired
    private PurgeExpiredSessions purgeExpiredSessions;

    private int statusOf(SignedIn signedIn) throws Exception {
        return mvc.perform(get(LOGIN_REQUIRED).with(session(signedIn))).andReturn().getResponse().getStatus();
    }

    private SignedIn freshLogin() throws Exception {
        long unit = newOrgUnitId();
        orgUnit(unit, null, null);
        long person = newEmployeeId();
        employee(person, unit, null);
        SignedIn signedIn = signIn(person);
        assertThat(statusOf(signedIn)).as("the session works right after login").isEqualTo(200);
        return signedIn;
    }

    @Test
    @DisplayName("FLAKY-SESS 刚登录、未过期的会话，清理作业执行一次后仍有效（200），不被误删")
    void aFreshSessionSurvivesAPurge() throws Exception {
        SignedIn signedIn = freshLogin();

        int removed = purgeExpiredSessions.execute();

        assertThat(removed).as("sessions the purge removed").isZero();
        assertThat(statusOf(signedIn)).as("the session after the purge").isEqualTo(200);
    }

    @Test
    @DisplayName("FLAKY-SESS（对照）固定时钟推进到超过空闲期（10 小时）后，清理删除该会话，带会话请求得 401")
    void anIdleSessionIsStillPurged() throws Exception {
        SignedIn signedIn = freshLogin();
        clock.set(NOW.plus(Duration.ofHours(11)));

        int removed = purgeExpiredSessions.execute();

        assertThat(removed).as("sessions the purge removed").isGreaterThanOrEqualTo(1);
        assertThat(statusOf(signedIn)).as("the session after idling past the timeout and the purge").isEqualTo(401);
    }
}
