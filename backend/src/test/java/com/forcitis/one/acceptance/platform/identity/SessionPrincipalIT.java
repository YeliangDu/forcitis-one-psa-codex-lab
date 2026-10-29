package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.platform.identity.api.Permission;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * R-1b P2 (ADR-0033 section 3 item 2): the session principal is the account's user id as a canonical UUID. A principal of any other form
 * is a broken session and the request is refused as unauthenticated: no fallback to reading it as a number, no 500, no 400.
 */
class SessionPrincipalIT extends AccountsAcceptanceSupport {

    static final TestDatabase postgres = TestPostgres.database(SessionPrincipalIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** The old numeric form, free text, a form UUID.fromString would still accept, and a canonical UUID in upper case. */
    static final List<String> BROKEN_PRINCIPALS = List.of("70001", "not-a-user-id", "1-2-3-4-5", "01900000-0000-7000-8000-00000000000A");

    private record Probe(String what, RequestBuilder request) {
    }

    /** Reads the current user, the approval inbox (current user in a use case), the audit list (principal read in the controller) and an idempotent create (principal read in the filter). */
    private List<String> answers(RequestPostProcessor caller) throws Exception {
        List<Probe> probes = List.of(
                new Probe("current user", get("/api/v1/platform/sessions:current").with(caller)),
                new Probe("approval inbox", get("/api/v1/platform/approval-tasks").with(caller)),
                new Probe("audit list", get(AUDIT_ENTRIES).param("limit", "1").with(caller)),
                new Probe("idempotent create", post(LEGAL_ENTITIES).header("Idempotency-Key", UUID.randomUUID().toString()).with(csrf()).with(caller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"SPX\",\"name\":\"Synthetic principal check\"}")));
        List<String> answered = new ArrayList<>();
        for (Probe probe : probes) {
            MockHttpServletResponse response = mvc.perform(probe.request()).andReturn().getResponse();
            answered.add(response.getStatus() + " " + probe.what());
        }
        return answered;
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 2 条：会话主体名不是规范 uuid（旧的数字形式、任意文本、宽松形式、大写）即视为会话损坏，按未登录拒绝（401），不回退到数字解析")
    void aPrincipalThatIsNotACanonicalUuidIsRefused() throws Exception {
        for (String broken : BROKEN_PRINCIPALS) {
            RequestPostProcessor caller = principal(broken, Permission.Codes.PLATFORM_AUDIT_ENTRY_READ,
                    Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE, Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ);

            assertThat(answers(caller)).as("answers to the principal %s", broken).allMatch(answer -> answer.startsWith("401 "));
        }
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 2 条（对照）：登录得到的会话主体是账号的 uuid，同一会话读当前用户得到同一个 userId")
    void theSessionOfALoginCarriesTheAccountUuid() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID person = newEmployeeId();
        employee(person, unit, null);
        SignedIn signedIn = signIn(person);

        MockHttpServletResponse current = mvc.perform(get("/api/v1/platform/sessions:current").cookie(new Cookie("SESSION", signedIn.session())))
                .andReturn().getResponse();

        assertThat(current.getStatus()).as(current.getContentAsString()).isEqualTo(200);
        assertThat((String) JsonPath.read(current.getContentAsString(), "$.userId")).isEqualTo(signedIn.userId().toString());
        assertThat(employeeOfAccount(signedIn.userId())).isEqualTo(person);
    }
}
