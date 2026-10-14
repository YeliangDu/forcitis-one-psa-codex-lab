package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.organization.workforce.events.OrgSnapshotChanged;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.integration.api.WecomEntry;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

class SessionLifecycleIntegrationTest extends IdentityIntegrationTest {

    static final String CURRENT = "/api/v1/platform/sessions:current";

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate tx;

    private int currentStatus(String session) throws Exception {
        return mvc.perform(get(CURRENT).cookie(new Cookie("SESSION", session))).andReturn().getResponse().getStatus();
    }

    private void publish(OrgSnapshotChanged event) {
        tx.executeWithoutResult(status -> events.publishEvent(event));
    }

    @Test
    @DisplayName("RULE-331 the session lives on the server: idle timeout 10 h, stored with the session")
    void idleTimeoutIsTenHours() throws Exception {
        loginWithWecom(1001);

        assertThat(jdbc.sql("select max_inactive_interval from platform_identity.sessions where principal_name = '1001'")
                .query(Integer.class).single()).isEqualTo(Duration.ofHours(10).toSeconds());
    }

    @Test
    @DisplayName("RULE-332 a session idle for longer than the timeout is gone")
    void idleSessionExpires() throws Exception {
        String session = loginWithWecom(1001);
        assertThat(currentStatus(session)).isEqualTo(200);

        jdbc.sql("update platform_identity.sessions set last_access_time = 1, expiry_time = 1 where principal_name = '1001'").update();

        assertThat(currentStatus(session)).isEqualTo(401);
    }

    @Test
    @DisplayName("RULE-332 the absolute cap ends even an active session after 24 h")
    void absoluteCap() throws Exception {
        String session = loginWithWecom(1001);

        clock.advance(Duration.ofHours(23));
        assertThat(currentStatus(session)).as("still inside the cap").isEqualTo(200);
        clock.advance(Duration.ofHours(2));

        assertThat(currentStatus(session)).as("24 h are over").isEqualTo(401);
        assertThat(currentStatus(session)).as("and the session is deleted, not just ignored").isEqualTo(401);
        assertThat(count("sessions")).isZero();
    }

    @Test
    @DisplayName("RULE-333 logging in changes the session id: the old id is dead, the new one works (no session fixation)")
    void sessionIdChangesAtLogin() throws Exception {
        String first = loginWithWecom(1001);
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        wecomAccepts("again", WecomEntry.WORKBENCH, 1001);
        String state = newLoginState("WORKBENCH");

        MvcResult second = mvc.perform(post(WecomLoginIntegrationTest.LOGIN).with(xsrf()).cookie(new Cookie("SESSION", first), new Cookie("loginBinding", bindings.get(state)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"again\",\"state\":\"" + state + "\",\"entry\":\"WORKBENCH\"}")).andReturn();

        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        String rotated = cookie(second, "SESSION");
        assertThat(rotated).isNotNull().isNotEqualTo(first);
        assertThat(currentStatus(first)).isEqualTo(401);
        assertThat(currentStatus(rotated)).isEqualTo(200);
    }

    @Test
    @DisplayName("RULE-334 logout deletes the session on the server and is audited")
    void logout() throws Exception {
        String session = loginWithWecom(1001);

        MvcResult result = mvc.perform(post("/api/v1/platform/sessions:logout").with(xsrf()).cookie(new Cookie("SESSION", session))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(currentStatus(session)).isEqualTo(401);
        assertThat(count("sessions")).isZero();
        assertThat(recorded()).extracting(AuditEntry::action).contains("platform.identity.logout");
    }

    @Test
    @DisplayName("RULE-334 logout without a session is refused like every other protected call")
    void logoutNeedsASession() throws Exception {
        assertThat(mvc.perform(post("/api/v1/platform/sessions:logout").with(xsrf())).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("RULE-339 an unknown or malformed session cookie is a plain 401 (the client restarts the login)")
    void unknownSession() throws Exception {
        assertThat(currentStatus("bm90LWEtc2Vzc2lvbg==")).isEqualTo(401);
        assertThat(currentStatus("???")).isEqualTo(401);
        assertThat(mvc.perform(get(CURRENT)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("RULE-338 disabling an account deletes its sessions at once and leaves other people's alone")
    void disablingRevokesSessions() throws Exception {
        String victim = loginWithWecom(1001);
        String other = loginWithWecom(1002);
        createAccount(9, "super_admin");

        MvcResult result = mvc.perform(post("/api/v1/platform/user-accounts/1001:disable").with(xsrf())
                .with(user("9").authorities(() -> Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(currentStatus(victim)).isEqualTo(401);
        assertThat(currentStatus(other)).isEqualTo(200);
        assertThat(jdbc.sql("select count(*) from platform_identity.sessions where principal_name = '1001'").query(Long.class).single()).isZero();
        assertThat(recordedEvents.stream(com.forcitis.one.platform.identity.events.AccountDisabled.class)).singleElement()
                .satisfies(e -> assertThat(e.envelope().actorId()).isEqualTo("9"));
    }

    @Test
    @DisplayName("RULE-338 the per-request check catches a separated employee within 60 s even if no event arrives")
    void sessionResolutionRechecksEmployment() throws Exception {
        String session = loginWithWecom(1001);
        assertThat(currentStatus(session)).as("employed: the answer is now cached").isEqualTo(200);
        knownEmployee(1001, EmploymentStatus.SEPARATED);

        assertThat(currentStatus(session)).as("within the cache time the answer may be stale, never longer than 60 s").isEqualTo(200);
        clock.advance(Duration.ofSeconds(61));

        assertThat(currentStatus(session)).isEqualTo(401);
        assertThat(count("sessions")).as("the session is deleted, not only ignored").isZero();
    }

    @Test
    @DisplayName("RULE-363/366 OrgSnapshotChanged: separated people lose every session at once")
    void separationEventRevokesSessions() throws Exception {
        String victim = loginWithWecom(1001);
        String other = loginWithWecom(1002);

        publish(new OrgSnapshotChanged(TestEnvelopes.of("organization.workforce.OrgSnapshotChanged", "SyncRun", "1"), List.of(), List.of(new UserId(1001)), List.of(), LocalDate.of(2026, 10, 14)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(
                jdbc.sql("select count(*) from platform_identity.sessions where principal_name = '1001'").query(Long.class).single()).isZero());
        assertThat(currentStatus(victim)).isEqualTo(401);
        assertThat(currentStatus(other)).isEqualTo(200);
        assertThat(recorded()).extracting(AuditEntry::action).contains("platform.identity.session.revoked");
    }

    @Test
    @DisplayName("RULE-344 sync: an employed new person gets an account with the default role once; separated people get nothing")
    void syncCreatesAccounts() throws Exception {
        knownEmployee(2001, EmploymentStatus.EMPLOYED);
        knownEmployee(2002, EmploymentStatus.SEPARATED);

        publish(new OrgSnapshotChanged(TestEnvelopes.of("organization.workforce.OrgSnapshotChanged", "SyncRun", "1"), List.of(new UserId(2001), new UserId(2002)), List.of(), List.of(), LocalDate.of(2026, 10, 14)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count("user_accounts")).isEqualTo(1));
        assertThat(jdbc.sql("select user_id from platform_identity.user_accounts").query(Long.class).single()).isEqualTo(2001);
        assertThat(jdbc.sql("""
                select r.code from platform_identity.account_roles ar join platform_identity.roles r on r.role_id = ar.role_id
                 where ar.user_id = 2001
                """).query(String.class).list()).containsExactly("employee");

        jdbc.sql("delete from platform_identity.account_roles where user_id = 2001").update();
        publish(new OrgSnapshotChanged(TestEnvelopes.of("organization.workforce.OrgSnapshotChanged", "SyncRun", "1"), List.of(new UserId(2001)), List.of(), List.of(), LocalDate.of(2026, 10, 14)));
        Thread.sleep(1000);
        assertThat(count("account_roles")).isZero();
    }

    @Test
    @DisplayName("C1a-05 CSRF for the single-page app: the XSRF-TOKEN cookie is written back in a header; without it a write is 403")
    void csrfHandshake() throws Exception {
        MvcResult first = mvc.perform(get(CURRENT)).andReturn();
        assertThat(first.getResponse().getStatus()).isEqualTo(401);
        String token = cookie(first, "XSRF-TOKEN");
        assertThat(token).as("the cookie is issued even on a 401").isNotBlank();

        int without = mvc.perform(post(WecomLoginIntegrationTest.STATES).contentType(MediaType.APPLICATION_JSON).content("{\"entry\":\"WORKBENCH\"}"))
                .andReturn().getResponse().getStatus();
        int cookieOnly = mvc.perform(post(WecomLoginIntegrationTest.STATES).cookie(new Cookie("XSRF-TOKEN", token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"entry\":\"WORKBENCH\"}")).andReturn().getResponse().getStatus();
        int withHeader = mvc.perform(post(WecomLoginIntegrationTest.STATES).cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"entry\":\"WORKBENCH\"}")).andReturn().getResponse().getStatus();

        assertThat(without).isEqualTo(403);
        assertThat(cookieOnly).isEqualTo(403);
        assertThat(withHeader).isEqualTo(200);
    }
}
