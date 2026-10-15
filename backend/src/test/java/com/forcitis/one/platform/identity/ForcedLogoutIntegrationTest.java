package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("ForcedLogout: ADR-0027 section 3 item 5 (D-1a-163)")
class ForcedLogoutIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    MeterRegistry meters;

    private MvcResult forceLogout(String session, long target, String body) throws Exception {
        return mvc.perform(post("/api/v1/platform/user-accounts/" + accountOrNone(target) + ":force-logout").with(xsrf()).cookie(new Cookie("SESSION", session))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private int status(String session) throws Exception {
        return mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("only the administrator mode may do it: an ordinary session, even of a super_admin holder, is refused")
    void onlyTheAdministratorMode() throws Exception {
        createAccount(9, "super_admin", "employee");
        String ordinary = loginWithWecom(9);
        String victim = loginWithWecom(1001);

        assertThat(forceLogout(ordinary, 1001, "{\"reason\":\"lost phone\"}").getResponse().getStatus()).isEqualTo(403);
        assertThat(status(victim)).isEqualTo(200);
    }

    @Test
    @DisplayName("it ends every session of one person, audits who did it to whom and why, and leaves others alone")
    void endsTheSessionsAndAudits() throws Exception {
        String victim = loginWithWecom(1001);
        String other = loginWithWecom(1002);
        String administrator = administratorSession(9);

        MvcResult result = forceLogout(administrator, 1001, "{\"reason\":\"lost phone\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(status(victim)).isEqualTo(401);
        assertThat(status(other)).isEqualTo(200);
        assertThat(recorded()).filteredOn(entry -> entry.action().equals("platform.identity.session.revoked")).singleElement().satisfies(entry -> {
            assertThat(entry.actorId().value()).isEqualTo(accountOf(9));
            assertThat(entry.subjectKey()).isEqualTo("user-account:" + accountOf(1001));
            assertThat(entry.detail()).contains("lost phone");
            assertThat(entry.sourceAddress()).as("who did it, from where").isEqualTo("127.0.0.1");
            assertThat(entry.outcome()).isEqualTo(AuditOutcome.SUCCEEDED);
        });
    }

    @Test
    @DisplayName("a reason is required, and one person cannot end their own sessions this way")
    void reasonRequiredAndNotOneself() throws Exception {
        loginWithWecom(1001);
        String administrator = administratorSession(9);

        assertThat(forceLogout(administrator, 1001, "{\"reason\":\"  \"}").getResponse().getStatus()).isEqualTo(422);
        assertThat(forceLogout(administrator, 1001, "{}").getResponse().getStatus()).isEqualTo(422);
        assertThat(forceLogout(administrator, 1001, "{\"reason\":\"" + "x".repeat(201) + "\"}").getResponse().getStatus()).isEqualTo(422);
        MvcResult self = forceLogout(administrator, 9, "{\"reason\":\"test\"}");
        assertThat(self.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(self).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_FORCED_LOGOUT_SELF_FORBIDDEN");
        assertThat(forceLogout(administrator, 4040, "{\"reason\":\"test\"}").getResponse().getStatus()).isEqualTo(404);
        assertThat(status(administrator)).as("the administrator is still signed in").isEqualTo(200);
        assertThat(recorded()).extracting(AuditEntry::action).doesNotContain("platform.identity.session.revoked");
    }

    @Test
    @DisplayName("ending the sessions of a super_admin holder or a security auditor raises an alert")
    void alertsWhenThePersonIsPrivileged() throws Exception {
        loginWithWecom(1001);
        createAccount(1001, "security_auditor");
        String administrator = administratorSession(9);
        double before = meters.counter("forcitis.platform.identity.administrator.logouts").count();

        forceLogout(administrator, 1001, "{\"reason\":\"suspected misuse\"}");
        assertThat(meters.counter("forcitis.platform.identity.administrator.logouts").count()).isEqualTo(before + 1);

        loginWithWecom(1003);
        forceLogout(administrator, 1003, "{\"reason\":\"lost phone\"}");
        assertThat(meters.counter("forcitis.platform.identity.administrator.logouts").count()).as("an ordinary employee raises none").isEqualTo(before + 1);
    }
}
