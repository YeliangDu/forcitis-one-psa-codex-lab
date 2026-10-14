package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.integration.api.SsoVerdict;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class SsoLoginIntegrationTest extends IdentityIntegrationTest {

    static final String LOGIN = "/api/v1/platform/sessions:sso-login";
    static final String LEGACY = "https://legacy.example.invalid/login";

    private void portalSays(String ticket, SsoVerdict verdict) {
        when(ssoVerifier.verify(ticket)).thenReturn(verdict);
    }

    private void personWithLoginName(String loginName, long id, EmploymentStatus status) {
        EmployeeProfile employee = knownEmployee(id, status);
        when(orgDirectory.findEmployeeByLoginName(loginName)).thenReturn(Optional.of(employee));
    }

    private MvcResult redeem(String ticket) throws Exception {
        return postJson(LOGIN, "{\"ticket\":\"" + ticket + "\"}");
    }

    private String errorCode(MvcResult result) throws Exception {
        return body(result).path("errorCode").asString();
    }

    @Test
    @DisplayName("RULE-320 success: the server asks the portal about the ticket and starts a session for the login name it returns")
    void validTicket() throws Exception {
        portalSays("T1", new SsoVerdict.LoginName("13800000000"));
        personWithLoginName("13800000000", 1001, EmploymentStatus.EMPLOYED);

        MvcResult result = redeem("T1");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("userId").asString()).isEqualTo("1001");
        assertThat(cookie(result, "SESSION")).isNotBlank();
        verify(ssoVerifier).verify("T1");
        verify(orgDirectory).findEmployeeByLoginName("13800000000");
        assertThat(jdbc.sql("select ticket_source from platform_identity.sso_ticket_uses").query(String.class).single()).isEqualTo("SSO");
    }

    @Test
    @DisplayName("RULE-320 each ticket works once")
    void replay() throws Exception {
        portalSays("T1", new SsoVerdict.LoginName("13800000000"));
        personWithLoginName("13800000000", 1001, EmploymentStatus.EMPLOYED);
        assertThat(redeem("T1").getResponse().getStatus()).isEqualTo(200);

        MvcResult second = redeem("T1");

        assertThat(second.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(second)).isEqualTo("PLATFORM_IDENTITY_TICKET_REPLAYED");
        assertThat(jdbc.sql("select count(*) from platform_identity.sso_ticket_uses where ticket_hash = 'T1'").query(Long.class).single())
                .as("only a hash is stored").isZero();
    }

    @Test
    @DisplayName("RULE-320 failure: a ticket for an unknown login name is refused and burned (the mark comes before the lookup)")
    void unknownLoginName() throws Exception {
        portalSays("T2", new SsoVerdict.LoginName("ghost"));
        when(orgDirectory.findEmployeeByLoginName("ghost")).thenReturn(Optional.empty());

        MvcResult first = redeem("T2");
        MvcResult second = redeem("T2");

        assertThat(errorCode(first)).isEqualTo("PLATFORM_IDENTITY_LOGIN_NAME_UNKNOWN");
        assertThat(errorCode(second)).isEqualTo("PLATFORM_IDENTITY_TICKET_REPLAYED");
    }

    @Test
    @DisplayName("RULE-320 disabled: a disabled account and a separated employee are refused")
    void sameGate() throws Exception {
        portalSays("T3", new SsoVerdict.LoginName("disabled-user"));
        personWithLoginName("disabled-user", 1001, EmploymentStatus.EMPLOYED);
        createAccount(1001);
        jdbc.sql("update platform_identity.user_accounts set status = 'DISABLED'").update();
        MvcResult disabled = redeem("T3");
        assertThat(disabled.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(disabled)).isEqualTo("PLATFORM_IDENTITY_ACCOUNT_DISABLED");
        assertThat(cookie(disabled, "SESSION")).isNull();

        portalSays("T4", new SsoVerdict.LoginName("gone-user"));
        personWithLoginName("gone-user", 1002, EmploymentStatus.SEPARATED);
        MvcResult separated = redeem("T4");
        assertThat(separated.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(separated)).isEqualTo("PLATFORM_IDENTITY_EMPLOYEE_SEPARATED");
        assertThat(cookie(separated, "SESSION")).isNull();
    }

    @Test
    @DisplayName("RULE-321 failure: a ticket the portal does not know (empty body, login page, unusable name) is an invalid ticket")
    void invalidTicket() throws Exception {
        portalSays("E1", new SsoVerdict.Invalid());

        MvcResult result = redeem("E1");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(result)).isEqualTo("PLATFORM_IDENTITY_TICKET_INVALID");
        verify(orgDirectory, never()).findEmployeeByLoginName(anyString());
        assertThat(count("sso_ticket_uses")).as("an invalid ticket is not burned").isZero();
    }

    @Test
    @DisplayName("RULE-321 the portal is down or slow: 503 with the 'back to the old system login' link, and nothing is burned")
    void portalUnavailable() throws Exception {
        portalSays("D1", new SsoVerdict.Unavailable());

        MvcResult down = redeem("D1");

        assertThat(down.getResponse().getStatus()).isEqualTo(503);
        assertThat(errorCode(down)).isEqualTo("PLATFORM_IDENTITY_SSO_UNAVAILABLE");
        assertThat(body(down).path("legacyLoginUrl").asString()).isEqualTo(LEGACY);
        assertThat(count("sso_ticket_uses")).isZero();
    }

    @Test
    @DisplayName("RULE-321 tickets longer than 256 characters are refused without asking the portal")
    void ticketLength() throws Exception {
        MvcResult result = redeem("x".repeat(257));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        verify(ssoVerifier, never()).verify(anyString());
    }

    @Test
    @DisplayName("RULE-319 a memberId or user id sent by the client is never trusted: only the checked ticket names the person")
    void clientClaimsAreIgnored() throws Exception {
        MvcResult sso = postJson(LOGIN, "{\"ticket\":\"unknown\",\"memberId\":\"1001\",\"userId\":1001}");
        MvcResult wecom = postJson(WecomLoginIntegrationTest.LOGIN, "{\"code\":\"c\",\"entry\":\"MINI_PROGRAM\",\"userId\":1001,\"memberId\":1001}");
        MvcResult emergency = postJson(EmergencyLoginIntegrationTest.LOGIN, "{\"loginName\":\"nobody\",\"userId\":1001}");

        for (MvcResult result : new MvcResult[] {sso, wecom, emergency}) {
            assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(401);
            assertThat(cookie(result, "SESSION")).isNull();
        }
        assertThat(count("user_accounts")).isZero();
    }

    @Test
    @DisplayName("RULE-335 the ticket travels in the request body: the endpoint does not read it from a URL")
    void ticketNotFromUrl() throws Exception {
        portalSays("T9", new SsoVerdict.LoginName("13800000000"));
        personWithLoginName("13800000000", 1001, EmploymentStatus.EMPLOYED);

        int status = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(LOGIN + "?ticket=T9")
                .with(xsrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(401);
        verify(ssoVerifier, never()).verify(anyString());
    }
}
