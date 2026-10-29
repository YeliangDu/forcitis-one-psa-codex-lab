package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.platform.identity.api.Permission;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P2 review P2-M2 (ADR-0033 section 3 item 3 「本人」判定清单: I-12 后半句、授权申请的确认人不是申请人; ADR-0034): a sensitive change
 * waits for a second person, compared as accounts. The requester, the person who would receive the roles and a holder of the role being
 * changed cannot confirm. All people are real accounts (WeCom login, UUIDs that equal no employee id) given super_admin with the
 * operations command, and act in their own sessions after entering the administrator mode over HTTP (RULING-P2-8).
 */
class AuthorizationConfirmerAccountsIT extends AccountsAcceptanceSupport {

    static final String ROLES = "/api/v1/platform/roles";
    static final String ACCOUNTS = "/api/v1/platform/user-accounts/";
    static final String REQUESTS = "/api/v1/platform/authorization-requests";

    static final TestDatabase postgres = TestPostgres.database(AuthorizationConfirmerAccountsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private UUID newAdministrator(UUID unit) {
        UUID person = newEmployeeId();
        try {
            employee(person, unit, null);
        } catch (java.sql.SQLException failed) {
            throw new IllegalStateException(failed);
        }
        grantByOperations(person, "super_admin");
        return person;
    }

    private SignedIn inAdministratorMode(UUID person) throws Exception {
        return administratorMode(signIn(person));
    }

    private MockHttpServletResponse decide(SignedIn who, String requestId) throws Exception {
        return mvc.perform(post(REQUESTS + "/" + requestId + ":confirm").with(csrf()).with(session(who))).andReturn().getResponse();
    }

    private static String requestIdOf(MockHttpServletResponse accepted) throws Exception {
        assertThat(accepted.getStatus()).as(accepted.getContentAsString()).isEqualTo(202);
        return JsonPath.read(accepted.getContentAsString(), "$.requestId");
    }

    private static void refusedAsConfirmer(MockHttpServletResponse answer, String who) throws Exception {
        assertThat(answer.getStatus()).as(who + ": " + answer.getContentAsString()).isEqualTo(403);
        assertThat((String) JsonPath.read(answer.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED");
    }

    private List<String> rolesOf(SignedIn reader, SignedIn account) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ACCOUNTS + account.userId()).with(session(reader))).andReturn().getResponse();
        assertThat(read.getStatus()).as(read.getContentAsString()).isEqualTo(200);
        return JsonPath.read(read.getContentAsString(), "$.roleCodes");
    }

    @Test
    @DisplayName("P2-M2 I-12 后半句（真实账号、生产会话，按用户 ID 比较）：给角色加敏感权限待第二人确认；申请人与该角色的持有人都不能确认（403 PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED），第三人可以")
    void aHolderOfTheChangedRoleCannotConfirm() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID requesterEmployee = newAdministrator(unit);
        UUID holderEmployee = newAdministrator(unit);
        UUID thirdEmployee = newAdministrator(unit);
        SignedIn requester = inAdministratorMode(requesterEmployee);
        MockHttpServletResponse created = mvc.perform(post(ROLES).with(csrf()).with(session(requester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"synthetic_task_reader\",\"name\":\"Synthetic task reader\",\"enabled\":true,\"permissionCodes\":[\""
                        + Permission.Codes.PLATFORM_APPROVAL_TASK_READ + "\"]}")).andReturn().getResponse();
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        String roleId = JsonPath.read(created.getContentAsString(), "$.roleId");
        grantByOperations(holderEmployee, "synthetic_task_reader");
        SignedIn holder = inAdministratorMode(holderEmployee);
        SignedIn third = inAdministratorMode(thirdEmployee);
        MockHttpServletResponse role = mvc.perform(get(ROLES + "/" + roleId).with(session(requester))).andReturn().getResponse();
        assertThat(role.getStatus()).as(role.getContentAsString()).isEqualTo(200);

        String requestId = requestIdOf(mvc.perform(put(ROLES + "/" + roleId).header("If-Match", role.getHeader("ETag")).with(csrf())
                .with(session(requester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Synthetic task reader\",\"enabled\":true,\"permissionCodes\":[\"" + Permission.Codes.PLATFORM_APPROVAL_TASK_READ + "\",\""
                        + Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE + "\"]}")).andReturn().getResponse());

        refusedAsConfirmer(decide(requester, requestId), "the requester");
        refusedAsConfirmer(decide(holder, requestId), "a holder of the role");
        MockHttpServletResponse byThird = decide(third, requestId);
        assertThat(byThird.getStatus()).as(byThird.getContentAsString()).isEqualTo(204);
    }

    @Test
    @DisplayName("P2-M2 授权申请的确认人不是申请人（真实账号、生产会话，按用户 ID 比较）：给人授敏感角色待确认；申请人与受益人都不能确认，第三人确认后生效；申请记录的 requestedBy、subjectUserId 是两人的 uuid")
    void theRequesterAndTheBeneficiaryCannotConfirm() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        SignedIn requester = inAdministratorMode(newAdministrator(unit));
        SignedIn beneficiary = inAdministratorMode(newAdministrator(unit));
        SignedIn third = inAdministratorMode(newAdministrator(unit));
        MockHttpServletResponse account = mvc.perform(get(ACCOUNTS + beneficiary.userId()).with(session(requester))).andReturn().getResponse();
        assertThat(account.getStatus()).as(account.getContentAsString()).isEqualTo(200);

        String requestId = requestIdOf(mvc.perform(post(ACCOUNTS + beneficiary.userId() + ":replace-roles").header("If-Match", account.getHeader("ETag"))
                .with(csrf()).with(session(requester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCodes\":[\"employee\",\"super_admin\",\"security_auditor\"]}")).andReturn().getResponse());

        MockHttpServletResponse listed = mvc.perform(get(REQUESTS).with(session(third))).andReturn().getResponse();
        assertThat(listed.getStatus()).as(listed.getContentAsString()).isEqualTo(200);
        assertThat((List<String>) JsonPath.read(listed.getContentAsString(), "$.items[?(@.requestId == '" + requestId + "')].requestedBy"))
                .containsExactly(requester.userId().toString());
        assertThat((List<String>) JsonPath.read(listed.getContentAsString(), "$.items[?(@.requestId == '" + requestId + "')].subjectUserId"))
                .containsExactly(beneficiary.userId().toString());
        refusedAsConfirmer(decide(requester, requestId), "the requester");
        refusedAsConfirmer(decide(beneficiary, requestId), "the beneficiary");
        assertThat(rolesOf(third, beneficiary)).doesNotContain("security_auditor");

        MockHttpServletResponse byThird = decide(third, requestId);

        assertThat(byThird.getStatus()).as(byThird.getContentAsString()).isEqualTo(204);
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(rolesOf(third, beneficiary)).contains("security_auditor"));
    }
}
