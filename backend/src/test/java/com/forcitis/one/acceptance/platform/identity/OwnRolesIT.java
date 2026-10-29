package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P2 (ADR-0033 section 3 item 3 「本人」判定清单, I-12 first half): nobody changes their own roles. Both sides are accounts, so the
 * caller's user id is set against the user id in the path; the account id differs from the employee id, and the path takes the
 * account's UUID (G-A1), not a number.
 */
class OwnRolesIT extends AccountsAcceptanceSupport {

    static final String ACCOUNTS = "/api/v1/platform/user-accounts/";
    static final String ROLES = "/api/v1/platform/roles";

    static final TestDatabase postgres = TestPostgres.database(OwnRolesIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    /** A super_admin holder (operations command), signed in through WeCom and in the administrator mode of their own session. */
    private SignedIn roleAdministrator(UUID employee) throws Exception {
        grantByOperations(employee, "super_admin");
        return administratorMode(signIn(employee));
    }

    private MockHttpServletResponse replaceRoles(SignedIn caller, String account, List<String> roleCodes) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ACCOUNTS + account).with(session(caller))).andReturn().getResponse();
        String etag = read.getStatus() == 200 ? read.getHeader("ETag") : "\"0\"";
        String body = "{\"roleCodes\":[" + String.join(",", roleCodes.stream().map(code -> "\"" + code + "\"").toList()) + "]}";
        return mvc.perform(post(ACCOUNTS + account + ":replace-roles").header("If-Match", etag).with(csrf()).with(session(caller))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    private List<String> rolesOf(SignedIn caller, UUID account) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ACCOUNTS + account).with(session(caller))).andReturn().getResponse();
        assertThat(read.getStatus()).as(read.getContentAsString()).isEqualTo(200);
        return JsonPath.read(read.getContentAsString(), "$..roleCodes[*]");
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 3 条 / I-12：给自己授角色被拒（403 PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN，两侧都比较用户 ID），角色不变，拒绝记审计且操作人为本人的用户 ID；给他人授角色照常生效")
    void nobodyChangesTheirOwnRoles() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID administratorEmployee = newEmployeeId();
        UUID colleagueEmployee = newEmployeeId();
        employee(administratorEmployee, unit, null);
        employee(colleagueEmployee, unit, null);
        SignedIn administratorSession = roleAdministrator(administratorEmployee);
        UUID administrator = administratorSession.userId();
        UUID colleague = signIn(colleagueEmployee).userId();
        MockHttpServletResponse role = mvc.perform(post(ROLES).with(csrf()).with(session(administratorSession)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"synthetic_own_roles\",\"name\":\"Synthetic own roles\",\"enabled\":true,\"permissionCodes\":[]}")).andReturn().getResponse();
        assertThat(role.getStatus()).as(role.getContentAsString()).isEqualTo(201);
        List<String> before = rolesOf(administratorSession, administrator);

        MockHttpServletResponse own = replaceRoles(administratorSession, administrator.toString(), List.of("employee", "synthetic_own_roles"));

        assertThat(own.getStatus()).as(own.getContentAsString()).isEqualTo(403);
        assertThat((String) JsonPath.read(own.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN");
        assertThat(rolesOf(administratorSession, administrator)).containsExactlyInAnyOrderElementsOf(before);
        assertThat(auditEntriesBy(administrator, "platform.identity.grant.refused")).extracting(entry -> entry.get("actorId"))
                .containsOnly(administrator.toString()).isNotEmpty();

        MockHttpServletResponse other = replaceRoles(administratorSession, colleague.toString(), List.of("employee", "synthetic_own_roles"));

        assertThat(other.getStatus()).as(other.getContentAsString()).isEqualTo(204);
        assertThat(rolesOf(administratorSession, colleague)).contains("synthetic_own_roles");
    }

    @Test
    @DisplayName("R-1b P2 G-A1 / ADR-0033 第 6 项第 2 条 / RULING-P3B-4：账号路径只认用户 ID；拿员工的 uuid 当账号路径答 404，不授出任何角色、不写授予审计")
    void theAccountPathTakesTheUserIdNotTheEmployeeId() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID administratorEmployee = newEmployeeId();
        UUID colleagueEmployee = newEmployeeId();
        employee(administratorEmployee, unit, null);
        employee(colleagueEmployee, unit, null);
        SignedIn administrator = roleAdministrator(administratorEmployee);
        UUID colleague = signIn(colleagueEmployee).userId();
        List<String> before = rolesOf(administrator, colleague);

        MockHttpServletResponse role = mvc.perform(post(ROLES).with(csrf()).with(session(administrator)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"synthetic_path_roles\",\"name\":\"Synthetic path roles\",\"enabled\":true,\"permissionCodes\":[]}")).andReturn().getResponse();
        assertThat(role.getStatus()).as(role.getContentAsString()).isEqualTo(201);
        List<String> wanted = new java.util.ArrayList<>(before);
        wanted.add("synthetic_path_roles");

        MockHttpServletResponse byEmployeeId = replaceRoles(administrator, colleagueEmployee.toString(), wanted);

        assertThat(byEmployeeId.getStatus()).as(byEmployeeId.getContentAsString()).isEqualTo(404);
        assertThat(rolesOf(administrator, colleague)).as("the colleague's roles").containsExactlyInAnyOrderElementsOf(before);

        MockHttpServletResponse control = replaceRoles(administrator, colleague.toString(), wanted);
        assertThat(control.getStatus()).as(control.getContentAsString()).isEqualTo(204);
        List<java.util.Map<String, Object>> grants = auditEntriesBy(administrator.userId(), AUDIT_ROLES_REPLACED);
        assertThat(grants).as("grant audit entries of the administrator: only the control, on the colleague's account").hasSize(1)
                .allSatisfy(entry -> {
                    assertThat(entry.get("subjectKind")).isEqualTo("USER_ACCOUNT");
                    assertThat(String.valueOf(entry.get("subjectId"))).isEqualTo(colleague.toString()).isNotEqualTo(colleagueEmployee.toString());
                });
        assertThat(auditQuery("actorId", administrator.userId().toString(), "platform.identity.grant.refused")).as("no refusal recorded for the 404").isEmpty();
    }

    /** The audit action of a role replacement, from its domain event. */
    static final String AUDIT_ROLES_REPLACED = "platform.identity.AccountRolesReplaced";
}
