package com.forcitis.one.platform.identity;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.organization.workforce.api.LoginLookup;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.events.AccountOpened;
import com.forcitis.one.platform.integration.api.WecomEntry;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class WecomLoginIntegrationTest extends IdentityIntegrationTest {

    static final String LOGIN = "/api/v1/platform/sessions:wecom-login";
    static final String STATES = "/api/v1/platform/login-states";

    private String newState(String entry) throws Exception {
        return newLoginState(entry);
    }

    private MvcResult login(String code, String state, String entry) throws Exception {
        String stateJson = state == null ? "" : ",\"state\":\"" + state + "\"";
        return postLogin(LOGIN, "{\"code\":\"" + code + "\"" + stateJson + ",\"entry\":\"" + entry + "\"}", state);
    }

    @Test
    @DisplayName("RULE-322 WeCom workbench login maps the WeCom user id exactly and starts a session with a safe cookie")
    void workbenchLogin() throws Exception {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        wecomAccepts("c1", WecomEntry.WORKBENCH, 1001);

        MvcResult result = login("c1", newState("WORKBENCH"), "WORKBENCH");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("userId").asString()).isEqualTo(accountOf(1001).toString());
        assertThat(body(result).path("absoluteExpiresAt").asString()).isNotBlank();
        String cookie = setCookieHeader(result, "SESSION");
        assertThat(cookie).contains("HttpOnly").contains("Secure").contains("SameSite=Lax");
        assertThat(result.getResponse().getHeader("Location")).as("RULE-335: nothing in a redirect URL").isNull();
        verify(wecomGateway).resolveWecomUserId("c1", WecomEntry.WORKBENCH);
        verify(orgDirectory).findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("wx1001")));
    }

    @Test
    @DisplayName("RULE-330 every login is audited; success stamps the last login, failures survive a rollback")
    void loginsAreAudited() throws Exception {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        wecomAccepts("c1", WecomEntry.WORKBENCH, 1001);
        login("c1", newState("WORKBENCH"), "WORKBENCH");
        login("bad", newState("WORKBENCH"), "WORKBENCH");

        assertThat(recorded()).anySatisfy(entry -> {
            assertThat(entry.action()).isEqualTo("platform.identity.login.succeeded");
            assertThat(entry.actorId().value()).isEqualTo(accountOf(1001));
            assertThat(entry.detail()).contains("method=WECOM_WORKBENCH").doesNotContain("address=").doesNotContain("agent=");
            assertThat(entry.sourceAddress()).isEqualTo("127.0.0.1");
        });
        assertThat(recordedIndependently()).anySatisfy(entry -> assertThat(entry.action()).isEqualTo("platform.identity.login.failed"));
        assertThat(jdbc.sql("select last_login_address from platform_identity.user_accounts where employee_id = " + TestOrgIds.sqlEmployee(1001))
                .query(String.class).single()).isEqualTo("127.0.0.1");
        assertThat(jdbc.sql("select last_login_at is not null from platform_identity.user_accounts where employee_id = " + TestOrgIds.sqlEmployee(1001))
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    @DisplayName("RULE-323 mini program login uses the same mapping and needs no OAuth state")
    void miniProgramLogin() throws Exception {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        wecomAccepts("mini", WecomEntry.MINI_PROGRAM, 1001);

        assertThat(login("mini", null, "MINI_PROGRAM").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("RULE-324 PC scan login is a WeCom login too: exact user id only, no guessing from names or e-mail")
    void scanLogin() throws Exception {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        wecomAccepts("scan", WecomEntry.QR_CODE_LOGIN, 1001);

        assertThat(login("scan", newState("QR_CODE_LOGIN"), "QR_CODE_LOGIN").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("RULE-324 a WeCom user without an employee is refused and no account or session appears")
    void unmappedWecomUserIsRefused() throws Exception {
        when(wecomGateway.resolveWecomUserId("c1", WecomEntry.WORKBENCH)).thenReturn(Optional.of(new WecomUserId("stranger")));
        when(orgDirectory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("stranger")))).thenReturn(Optional.empty());

        MvcResult result = login("c1", newState("WORKBENCH"), "WORKBENCH");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(result).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_WECOM_NOT_BOUND");
        assertThat(cookie(result, "SESSION")).isNull();
        assertThat(count("user_accounts")).isZero();
        verify(orgDirectory, never()).findEmployee(any());
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01: an unbound WeCom user is audited as 'wecom user not bound', the WeCom user id never reaches detail")
    void unboundWecomUserStaysOutOfDetail() throws Exception {
        when(wecomGateway.resolveWecomUserId("c1", WecomEntry.WORKBENCH)).thenReturn(Optional.of(new WecomUserId("stranger-id")));
        when(orgDirectory.findEmployeeByLogin(new LoginLookup.ByWecomUser(new WecomUserId("stranger-id")))).thenReturn(Optional.empty());

        login("c1", newState("WORKBENCH"), "WORKBENCH");

        assertThat(recordedIndependently()).filteredOn(entry -> entry.action().equals("platform.identity.login.failed")).singleElement()
                .satisfies(entry -> assertThat(entry.detail()).contains("reason=wecom user not bound").doesNotContain("stranger-id"));
    }

    @Test
    @DisplayName("RULE-322 a code WeCom rejects is refused")
    void rejectedCode() throws Exception {
        when(wecomGateway.resolveWecomUserId("bad", WecomEntry.WORKBENCH)).thenReturn(Optional.empty());

        MvcResult result = login("bad", newState("WORKBENCH"), "WORKBENCH");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(result).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_WECOM_CODE_INVALID");
    }

    @Test
    @DisplayName("I-9 the OAuth state is required for redirect entries, tied to its entry, and works once")
    void stateRules() throws Exception {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        wecomAccepts("c1", WecomEntry.WORKBENCH, 1001);
        wecomAccepts("c1", WecomEntry.QR_CODE_LOGIN, 1001);

        assertThat(errorCode(login("c1", null, "WORKBENCH"))).as("missing state").isEqualTo("PLATFORM_IDENTITY_STATE_INVALID");
        assertThat(errorCode(login("c1", "never-issued", "WORKBENCH"))).as("unknown state").isEqualTo("PLATFORM_IDENTITY_STATE_INVALID");
        assertThat(errorCode(login("c1", newState("WORKBENCH"), "QR_CODE_LOGIN"))).as("state of another entry")
                .isEqualTo("PLATFORM_IDENTITY_STATE_INVALID");

        String state = newState("WORKBENCH");
        assertThat(login("c1", state, "WORKBENCH").getResponse().getStatus()).isEqualTo(200);
        assertThat(errorCode(login("c1", state, "WORKBENCH"))).as("state used twice").isEqualTo("PLATFORM_IDENTITY_STATE_INVALID");
    }

    @Test
    @DisplayName("I-9 an expired state is refused and the database stores only its hash")
    void expiredState() throws Exception {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        wecomAccepts("c1", WecomEntry.WORKBENCH, 1001);
        String state = newState("WORKBENCH");
        assertThat(jdbc.sql("select count(*) from platform_identity.login_states where state_hash = :s").param("s", state)
                .query(Long.class).single()).as("no clear-text state in the database").isZero();

        clock.advance(Duration.ofMinutes(11));

        assertThat(errorCode(login("c1", state, "WORKBENCH"))).isEqualTo("PLATFORM_IDENTITY_STATE_INVALID");
    }

    @Test
    @DisplayName("RULE-326 a disabled account is refused at the WeCom entry")
    void disabledAccount() throws Exception {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        wecomAccepts("c1", WecomEntry.WORKBENCH, 1001);
        createAccount(1001);
        jdbc.sql("update platform_identity.user_accounts set status = 'DISABLED' where employee_id = " + TestOrgIds.sqlEmployee(1001)).update();

        MvcResult result = login("c1", newState("WORKBENCH"), "WORKBENCH");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(result).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_ACCOUNT_DISABLED");
        assertThat(cookie(result, "SESSION")).isNull();
    }

    @Test
    @DisplayName("RULE-327 a separated employee gets an explicit business error, no crash, no account")
    void separatedEmployee() throws Exception {
        knownEmployee(1001, EmploymentStatus.SEPARATED);
        wecomAccepts("c1", WecomEntry.WORKBENCH, 1001);

        MvcResult result = login("c1", newState("WORKBENCH"), "WORKBENCH");

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(result).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_EMPLOYEE_SEPARATED");
        assertThat(count("user_accounts")).as("RULE-344: separated people get nothing").isZero();
    }

    @Test
    @DisplayName("RULE-344 a new employee gets the configured default role explicitly, once, with an audit entry")
    void defaultRoleIsGrantedOnce() throws Exception {
        loginWithWecom(1001);

        assertThat(jdbc.sql("""
                select r.code from platform_identity.account_role_grants ar join platform_identity.roles r on r.role_id = ar.role_id
                 where ar.effective_to_at is null and ar.user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = :employee)
                """).param("employee", TestAccounts.employee(1001).value()).query(String.class).list()).containsExactly("employee");
        assertThat(recordedEvents.stream(AccountOpened.class)).singleElement().satisfies(e -> assertThat(e.userId().value()).isEqualTo(accountOf(1001)));

        jdbc.sql("update platform_identity.account_role_grants set effective_to_at = greatest(now(), effective_from_at), revocation_cause_kind = 'OPERATIONS', revocation_audit_entry_id = uuidv7(), updated_at = now() where effective_to_at is null and user_id = (select user_id from platform_identity.user_accounts where employee_id = " + TestOrgIds.sqlEmployee(1001) + ")").update();
        long grantRows = count("account_role_grants");
        loginWithWecom(1001);
        assertThat(count("account_role_grants where effective_to_at is null")).isZero();
        assertThat(count("account_role_grants")).as("a removed default role is not given back and no row is deleted").isEqualTo(grantRows);
    }

    @Test
    @DisplayName("RULE-336 identity comes from the session only: forged headers and unknown cookies are 401, not anonymous")
    void identityOnlyFromSession() throws Exception {
        assertThat(mvc.perform(get("/api/v1/platform/sessions:current").header("login-user", "{\"id\":1,\"userType\":2}")
                .header("X-User-Id", "1")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get("/api/v1/platform/sessions:current").cookie(new Cookie("SESSION", "bm90LWEtc2Vzc2lvbg==")))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("RULE-341/348 the current user sees the permissions of their enabled roles and only the navigation they may use")
    void currentUser() throws Exception {
        String session = loginWithWecom(1001);

        MvcResult result = mvc.perform(get("/api/v1/platform/sessions:current").cookie(new Cookie("SESSION", session))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var me = body(result);
        assertThat(me.path("userId").asString()).isEqualTo(accountOf(1001).toString());
        assertThat(me.path("displayName").asString()).isEqualTo("Employee 1001");
        assertThat(me.path("orgUnitName").asString()).isEqualTo("Delivery");
        assertThat(me.path("permissionCodes").valueStream().map(n -> n.asString()).toList())
                .containsExactly("platform.approval:task:approve", "platform.approval:task:read", "platform.audit:own-login-record:read",
                        "platform.file:file:create", "platform.file:file:read");
        assertThat(me.path("navigation").valueStream().map(n -> n.path("name").asString()).toList())
                .containsExactly("待办");
    }

    private String errorCode(MvcResult result) throws Exception {
        return body(result).path("errorCode").asString();
    }

    @Test
    @DisplayName("M-13 the state is bound to the browser that asked for it: the binding cookie is HttpOnly, SameSite=Lax and short-lived, and only its hash is stored")
    void bindingCookie() throws Exception {
        MvcResult issued = postJson(STATES, "{\"entry\":\"WORKBENCH\"}");

        String header = setCookieHeader(issued, "loginBinding");
        assertThat(header).contains("HttpOnly", "SameSite=Lax", "Path=/api/v1/platform", "Max-Age=600", "Secure");
        String binding = cookie(issued, "loginBinding");
        assertThat(jdbc.sql("select count(*) from platform_identity.login_states where binding_hash = :b").param("b", binding)
                .query(Long.class).single()).as("no clear-text binding in the database").isZero();
        assertThat(body(issued).toString()).as("the binding never travels in the body").doesNotContain(binding);
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        wecomAccepts("c9", WecomEntry.WORKBENCH, 1001);
        assertThat(body(login("c9", newState("WORKBENCH"), "WORKBENCH")).path("identityConfirmationRequired").asBoolean())
                .as("a WeCom login proves the person itself: nothing to confirm").isFalse();
    }

    @Test
    @DisplayName("M-13 login CSRF: a state presented without the binding cookie, or with another browser's, is refused and stays usable for its own browser")
    void stateFromAnotherBrowser() throws Exception {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        wecomAccepts("c1", WecomEntry.WORKBENCH, 1001);
        String state = newState("WORKBENCH");
        String body = "{\"code\":\"c1\",\"state\":\"" + state + "\",\"entry\":\"WORKBENCH\"}";

        MvcResult withoutCookie = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(LOGIN).with(xsrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)).andReturn();
        MvcResult otherBrowser = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(LOGIN).with(xsrf())
                .cookie(new jakarta.servlet.http.Cookie("loginBinding", "someone-elses-binding"))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body)).andReturn();

        assertThat(errorCode(withoutCookie)).isEqualTo("PLATFORM_IDENTITY_STATE_INVALID");
        assertThat(errorCode(otherBrowser)).isEqualTo("PLATFORM_IDENTITY_STATE_INVALID");
        MvcResult own = postLogin(LOGIN, body, state);
        assertThat(own.getResponse().getStatus()).as("the state was not burnt by the attacker's attempts").isEqualTo(200);
        assertThat(setCookieHeader(own, "loginBinding")).as("the binding is cleared after the login").contains("Max-Age=0");
    }

    private String lastLoginAddress() {
        return jdbc.sql("select last_login_address from platform_identity.user_accounts where employee_id = " + TestOrgIds.sqlEmployee(1001))
                .query(String.class).optional().orElse(null);
    }

    private ListAppender<ILoggingEvent> watchAlerts() {
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                "com.forcitis.one.platform.identity.infrastructure.persistence.JdbcAccountRepository")).addAppender(logged);
        return logged;
    }

    private void unwatch(ListAppender<ILoggingEvent> logged) {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                "com.forcitis.one.platform.identity.infrastructure.persistence.JdbcAccountRepository")).detachAppender(logged);
    }

    private MvcResult loginFrom(String address) throws Exception {
        knownEmployee(1001, EmploymentStatus.EMPLOYED);
        wecomAccepts("c1", WecomEntry.WORKBENCH, 1001);
        String state = newState("WORKBENCH");
        return postLogin(LOGIN, "{\"code\":\"c1\",\"state\":\"" + state + "\",\"entry\":\"WORKBENCH\"}", state, address);
    }

    @Test
    @DisplayName("DATA-REPAIR-identity ID-12 a 70-character or non-address client address: the login succeeds, no address is stored and an ALERT is logged")
    void id12UnusableAddressDoesNotFailTheLogin() throws Exception {
        for (String bad : new String[] {"7".repeat(70), "not-an-address", "203.0.113.9:8080 junk"}) {
            ListAppender<ILoggingEvent> logged = watchAlerts();
            try {
                assertThat(loginFrom(bad).getResponse().getStatus()).isEqualTo(200);
                assertThat(lastLoginAddress()).isNull();
                assertThat(jdbc.sql("select last_login_at is not null from platform_identity.user_accounts where employee_id = " + TestOrgIds.sqlEmployee(1001))
                        .query(Boolean.class).single()).isTrue();
                assertThat(logged.list).anySatisfy(event -> assertThat(event.getFormattedMessage()).startsWith("ALERT ").doesNotContain(bad));
            } finally {
                unwatch(logged);
            }
        }
    }

    @Test
    @DisplayName("DATA-REPAIR-identity ID-12 an IPv6 address with a zone id is stored as it is")
    void id12ZonedIpv6IsStored() throws Exception {
        assertThat(loginFrom("fe80::1%eth0").getResponse().getStatus()).isEqualTo(200);
        assertThat(lastLoginAddress()).isEqualTo("fe80::1%eth0");
    }
}
