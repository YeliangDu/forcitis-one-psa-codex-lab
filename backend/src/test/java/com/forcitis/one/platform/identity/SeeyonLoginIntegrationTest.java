package com.forcitis.one.platform.identity;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** C1a-02 / RULE-320 / RULE-321: Seeyon OA single sign-on with a server-side ticket check (the OA is a WireMock server). */
class SeeyonLoginIntegrationTest extends IdentityIntegrationTest {

    static final String LOGIN = "/api/v1/platform/sessions:seeyon-login";
    static final String OA_PATH = "/seeyon/thirdpartyController.do";
    static final String LEGACY = "https://legacy.example.invalid/login";

    private void oaSays(String ticket, int status, String body) {
        OA.stubFor(get(urlPathEqualTo(OA_PATH)).withQueryParam("ticket", equalTo(ticket))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", "text/plain").withBody(body)));
    }

    private void personWithLoginName(String loginName, long id, EmploymentStatus status) {
        EmployeeProfile employee = knownEmployee(id, status);
        when(loginNames.findEmployeeByLoginName(loginName)).thenReturn(Optional.of(employee));
    }

    private MvcResult redeem(String ticket) throws Exception {
        return postJson(LOGIN, "{\"ticket\":\"" + ticket + "\"}");
    }

    private String errorCode(MvcResult result) throws Exception {
        return body(result).path("errorCode").asString();
    }

    @Test
    @DisplayName("RULE-320 the server asks OA about the ticket and starts a session for the login name OA returns")
    void validTicket() throws Exception {
        oaSays("T1", 200, "13800000000");
        personWithLoginName("13800000000", 1001, EmploymentStatus.EMPLOYED);

        MvcResult result = redeem("T1");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).path("userId").asString()).isEqualTo("1001");
        assertThat(cookie(result, "SESSION")).isNotBlank();
        OA.verify(1, getRequestedFor(urlPathEqualTo(OA_PATH)).withQueryParam("ticket", equalTo("T1")));
    }

    @Test
    @DisplayName("RULE-320 each ticket works once")
    void replay() throws Exception {
        oaSays("T1", 200, "13800000000");
        personWithLoginName("13800000000", 1001, EmploymentStatus.EMPLOYED);
        assertThat(redeem("T1").getResponse().getStatus()).isEqualTo(200);

        MvcResult second = redeem("T1");

        assertThat(second.getResponse().getStatus()).isEqualTo(401);
        assertThat(errorCode(second)).isEqualTo("PLATFORM_IDENTITY_TICKET_REPLAYED");
        assertThat(jdbc.sql("select ticket_source from platform_identity.sso_ticket_uses").query(String.class).single()).isEqualTo("SEEYON");
        assertThat(jdbc.sql("select count(*) from platform_identity.sso_ticket_uses where ticket_hash = 'T1'").query(Long.class).single())
                .as("only a hash is stored").isZero();
    }

    @Test
    @DisplayName("RULE-320 a ticket for an unknown login name is refused and burned (the mark comes before the lookup)")
    void unknownLoginName() throws Exception {
        oaSays("T2", 200, "ghost");
        when(loginNames.findEmployeeByLoginName("ghost")).thenReturn(Optional.empty());

        MvcResult first = redeem("T2");
        MvcResult second = redeem("T2");

        assertThat(errorCode(first)).isEqualTo("PLATFORM_IDENTITY_LOGIN_NAME_UNKNOWN");
        assertThat(errorCode(second)).isEqualTo("PLATFORM_IDENTITY_TICKET_REPLAYED");
    }

    @Test
    @DisplayName("RULE-320 a disabled account and a separated employee are refused")
    void sameGate() throws Exception {
        oaSays("T3", 200, "disabled-user");
        personWithLoginName("disabled-user", 1001, EmploymentStatus.EMPLOYED);
        createAccount(1001);
        jdbc.sql("update platform_identity.user_accounts set status = 'DISABLED'").update();
        MvcResult disabled = redeem("T3");
        assertThat(disabled.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(disabled)).isEqualTo("PLATFORM_IDENTITY_ACCOUNT_DISABLED");

        oaSays("T4", 200, "gone-user");
        personWithLoginName("gone-user", 1002, EmploymentStatus.SEPARATED);
        MvcResult separated = redeem("T4");
        assertThat(separated.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(separated)).isEqualTo("PLATFORM_IDENTITY_EMPLOYEE_SEPARATED");
    }

    @Test
    @DisplayName("RULE-321 OA answering with an empty body or its HTML login page means an invalid ticket")
    void invalidTicket() throws Exception {
        oaSays("E1", 200, "");
        oaSays("H1", 200, "<html><body>Please sign in</body></html>");
        oaSays("S1", 200, "has space");

        for (String ticket : new String[] {"E1", "H1", "S1"}) {
            MvcResult result = redeem(ticket);
            assertThat(result.getResponse().getStatus()).as(ticket).isEqualTo(401);
            assertThat(errorCode(result)).isEqualTo("PLATFORM_IDENTITY_TICKET_INVALID");
        }
    }

    @Test
    @DisplayName("RULE-321 OA down or slow: 503 with the 'back to the old system login' link, and nothing is burned")
    void oaUnavailable() throws Exception {
        oaSays("D1", 500, "boom");
        OA.stubFor(get(urlPathEqualTo(OA_PATH)).withQueryParam("ticket", equalTo("SLOW"))
                .willReturn(aResponse().withStatus(200).withBody("13800000000").withFixedDelay(3000)));

        MvcResult down = redeem("D1");
        MvcResult slow = redeem("SLOW");

        assertThat(down.getResponse().getStatus()).isEqualTo(503);
        assertThat(errorCode(down)).isEqualTo("PLATFORM_IDENTITY_SEEYON_UNAVAILABLE");
        assertThat(body(down).path("legacyLoginUrl").asString()).isEqualTo(LEGACY);
        assertThat(slow.getResponse().getStatus()).as("timeout is configured to 1 s in tests").isEqualTo(503);
        assertThat(count("sso_ticket_uses")).isZero();
    }

    @Test
    @DisplayName("RULE-321 tickets longer than 256 characters are refused without asking OA")
    void ticketLength() throws Exception {
        MvcResult result = redeem("x".repeat(257));

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        OA.verify(0, getRequestedFor(urlPathEqualTo(OA_PATH)));
    }

    @Test
    @DisplayName("RULE-319 a memberId or user id sent by the client is never trusted: only the checked ticket names the person")
    void clientClaimsAreIgnored() throws Exception {
        MvcResult seeyon = postJson(LOGIN, "{\"ticket\":\"unknown\",\"memberId\":\"1001\",\"userId\":1001}");
        MvcResult wecom = postJson(WecomLoginIntegrationTest.LOGIN, "{\"code\":\"c\",\"entry\":\"MINI_PROGRAM\",\"userId\":1001,\"memberId\":1001}");
        MvcResult emergency = postJson(EmergencyLoginIntegrationTest.LOGIN, "{\"loginName\":\"nobody\",\"userId\":1001}");

        for (MvcResult result : new MvcResult[] {seeyon, wecom, emergency}) {
            assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(401);
            assertThat(cookie(result, "SESSION")).isNull();
        }
        assertThat(count("user_accounts")).isZero();
    }

    @Test
    @DisplayName("RULE-335 the ticket travels in the request body: the endpoint does not read it from a URL")
    void ticketNotFromUrl() throws Exception {
        oaSays("T9", 200, "13800000000");
        personWithLoginName("13800000000", 1001, EmploymentStatus.EMPLOYED);

        int status = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(LOGIN + "?ticket=T9")
                .with(xsrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(401);
        OA.verify(0, getRequestedFor(urlPathEqualTo(OA_PATH)));
    }
}
