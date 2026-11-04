package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.platform.identity.application.PurgeExpiredSessions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * FLAKY-SESS: the purge judges expiry on the same time basis as the framework writes into the session table; the injected Clock takes
 * no part. Run once, deterministically: no dependence on the minute boundary of the scheduled job.
 */
class SessionPurgeClockIT extends AccountsAcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(SessionPurgeClockIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final String LOGIN_REQUIRED = "/api/v1/platform/sessions:current";

    @Autowired
    private PurgeExpiredSessions purgeExpiredSessions;

    private int statusOf(SignedIn signedIn) throws Exception {
        return mvc.perform(get(LOGIN_REQUIRED).with(session(signedIn))).andReturn().getResponse().getStatus();
    }

    private SignedIn freshLogin() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID person = newEmployeeId();
        employee(person, unit, null);
        SignedIn signedIn = signIn(person);
        assertThat(statusOf(signedIn)).as("the session works right after login").isEqualTo(200);
        return signedIn;
    }

    /** Session times are the framework's own (system clock, epoch millis): the row is aged on that basis, last access 11 h ago, expiry 1 h ago. */
    private void makeIdlePastTheTimeout(SignedIn signedIn) throws Exception {
        long now = System.currentTimeMillis();
        try (Connection identity = schemaConnection("platform_identity");
             PreparedStatement age = identity.prepareStatement(
                     "update sessions set creation_time = ?, last_access_time = ?, expiry_time = ? where principal_name = ?")) {
            age.setLong(1, now - Duration.ofHours(11).toMillis());
            age.setLong(2, now - Duration.ofHours(11).toMillis());
            age.setLong(3, now - Duration.ofHours(1).toMillis());
            age.setString(4, signedIn.userId().toString());
            assertThat(age.executeUpdate()).as("session rows aged").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("FLAKY-SESS 刚登录、未过期的会话，清理作业执行一次后仍有效（200），不被误删")
    void aFreshSessionSurvivesAPurge() throws Exception {
        SignedIn signedIn = freshLogin();
        clock.set(NOW.plus(Duration.ofDays(400)));

        int removed = purgeExpiredSessions.execute();

        assertThat(removed).as("sessions the purge removed").isZero();
        clock.set(NOW);
        assertThat(statusOf(signedIn)).as("the session after the purge").isEqualTo(200);
    }

    // RULE-425
    @Test
    @DisplayName("FLAKY-SESS（对照）会话的最后访问时间早于空闲期（10 小时）之前，清理删除该会话，带会话请求得 401")
    void anIdleSessionIsStillPurged() throws Exception {
        SignedIn signedIn = freshLogin();
        makeIdlePastTheTimeout(signedIn);

        int removed = purgeExpiredSessions.execute();

        assertThat(removed).as("sessions the purge removed").isEqualTo(1);
        assertThat(auditQuery(null, null, "platform.identity.session.expired"))
                .filteredOn(entry -> "USER_ACCOUNT".equals(entry.get("subjectKind")) && signedIn.userId().toString().equals(entry.get("subjectId")))
                .singleElement().satisfies(entry -> assertThat(entry.get("detail")).isEqualTo("cause=IDLE"));
        assertThat(statusOf(signedIn)).as("the session after idling past the timeout and the purge").isEqualTo(401);
    }
}
