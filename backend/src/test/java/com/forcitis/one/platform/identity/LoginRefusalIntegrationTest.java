package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.identity.domain.SignedTicket;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Two checks that lived in the removed single sign-on test but never depended on it (#1174); they now run through the logins that remain. */
class LoginRefusalIntegrationTest extends IdentityIntegrationTest {

    @Test
    @DisplayName("RULE-319 a memberId or user id sent by the client is never trusted: only the checked proof names the person")
    void clientClaimsAreIgnored() throws Exception {
        MvcResult ticket = postJson(SignedTicketLoginIntegrationTest.LOGIN, "{\"ticket\":\"unknown\",\"memberId\":\"1001\",\"userId\":1001}");
        MvcResult wecom = postJson(WecomLoginIntegrationTest.LOGIN, "{\"code\":\"c\",\"entry\":\"MINI_PROGRAM\",\"userId\":1001,\"memberId\":1001}");
        MvcResult emergency = postJson(EmergencyLoginIntegrationTest.LOGIN, "{\"loginName\":\"nobody\",\"userId\":1001}");

        for (MvcResult result : new MvcResult[] {ticket, wecom, emergency}) {
            assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(401);
            assertThat(cookie(result, "SESSION")).isNull();
        }
        assertThat(count("user_accounts")).isZero();
    }

    /** A signed-ticket session starts unconfirmed (C-M7) and holds no permission, so every path is refused with 403. */
    private String unconfirmedTicketSession(long id) throws Exception {
        knownEmployee(id, EmploymentStatus.EMPLOYED);
        createAccount(id, "employee");
        organisationSourceKnows(id);
        jdbc.sql("""
                insert into platform_identity.ticket_issuers (ticket_issuer_id, issuer_code, signing_key_code, key_reference, is_enabled, allowed_role_codes)
                values (uuidv7(), 'km', 'km-key-1', 'env:FORCITIS_TICKET_KEY_KM', true, array['employee']) on conflict do nothing
                """).update();
        String ticket = SignedTicket.sign(SignedTicketLoginIntegrationTest.KM_SECRET, "km-key-1", "km", id, UUID.randomUUID().toString().replace("-", ""),
                clock.instant().minusSeconds(1), clock.instant().plusSeconds(30));
        MvcResult login = postJson(SignedTicketLoginIntegrationTest.LOGIN, "{\"ticket\":\"" + ticket + "\"}");
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        return cookie(login, "SESSION");
    }

    @Test
    @DisplayName("a refusal at a path that looks like a secret, or is longer than an audit text, is still an audit entry and carries no raw path")
    void refusalAtOddPathsIsStillAudited() throws Exception {
        // The same refusal text from the same session inside a minute is written once, so each path gets its own session.
        String[] paths = {"/api/v1/platform/roles/token:1", "/api/v1/platform/roles/" + "y".repeat(2500)};
        for (int i = 0; i < paths.length; i++) {
            String session = unconfirmedTicketSession(1005 + i);
            assertThat(mvc.perform(get(paths[i]).cookie(new Cookie("SESSION", session))).andReturn().getResponse().getStatus()).isEqualTo(403);
        }

        assertThat(recordedIndependently()).filteredOn(entry -> entry.action().equals("platform.identity.access.denied")).hasSize(2)
                .extracting(AuditEntry::detail)
                .allSatisfy(detail -> assertThat(detail).isEqualTo("access refused: GET unmatched").doesNotContain("token:").doesNotContain("yyyy"));
    }
}
