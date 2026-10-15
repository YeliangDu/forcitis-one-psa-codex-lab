package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.platform.identity.api.Permission;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * R-1b P2 (ADR-0033 section 3 item 3 「本人」判定清单, I-12 first half): nobody changes their own roles. Both sides are accounts, so the
 * caller's user id is set against the user id in the path; the account id differs from the employee id, and the path takes the
 * account's UUID (G-A1), not a number.
 */
@Testcontainers
class OwnRolesIT extends AccountsAcceptanceSupport {

    static final String ACCOUNTS = "/api/v1/platform/user-accounts/";
    static final String ROLES = "/api/v1/platform/roles";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = database();

    private static RequestPostProcessor roleAdministrator(UUID userId) {
        return as(userId, Permission.Codes.PLATFORM_IDENTITY_ROLE_READ, Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE,
                Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE);
    }

    private MockHttpServletResponse replaceRoles(UUID caller, String account, List<String> roleCodes) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ACCOUNTS + account).with(roleAdministrator(caller))).andReturn().getResponse();
        String etag = read.getStatus() == 200 ? read.getHeader("ETag") : "\"0\"";
        String body = "{\"roleCodes\":[" + String.join(",", roleCodes.stream().map(code -> "\"" + code + "\"").toList()) + "]}";
        return mvc.perform(post(ACCOUNTS + account + ":replace-roles").header("If-Match", etag).with(csrf()).with(roleAdministrator(caller))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    private List<String> rolesOf(UUID caller, UUID account) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ACCOUNTS + account).with(roleAdministrator(caller))).andReturn().getResponse();
        assertThat(read.getStatus()).as(read.getContentAsString()).isEqualTo(200);
        return JsonPath.read(read.getContentAsString(), "$..roleCodes[*]");
    }

    @Test
    @DisplayName("R-1b P2 ADR-0033 第 3 项第 3 条 / I-12：给自己授角色被拒（403 PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN，两侧都比较用户 ID），角色不变，拒绝记审计且操作人为本人的用户 ID；给他人授角色照常生效")
    void nobodyChangesTheirOwnRoles() throws Exception {
        long unit = newOrgUnitId();
        orgUnit(unit, null, null);
        long administratorEmployee = newEmployeeId();
        long colleagueEmployee = newEmployeeId();
        employee(administratorEmployee, unit, null);
        employee(colleagueEmployee, unit, null);
        UUID administrator = signIn(administratorEmployee).userId();
        UUID colleague = signIn(colleagueEmployee).userId();
        MockHttpServletResponse role = mvc.perform(post(ROLES).with(csrf()).with(roleAdministrator(administrator)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"synthetic_own_roles\",\"name\":\"Synthetic own roles\",\"enabled\":true,\"permissionCodes\":[]}")).andReturn().getResponse();
        assertThat(role.getStatus()).as(role.getContentAsString()).isEqualTo(201);
        List<String> before = rolesOf(administrator, administrator);

        MockHttpServletResponse own = replaceRoles(administrator, administrator.toString(), List.of("employee", "synthetic_own_roles"));

        assertThat(own.getStatus()).as(own.getContentAsString()).isEqualTo(403);
        assertThat((String) JsonPath.read(own.getContentAsString(), "$.errorCode")).isEqualTo("PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN");
        assertThat(rolesOf(administrator, administrator)).containsExactlyInAnyOrderElementsOf(before);
        assertThat(auditEntriesBy(administrator, "platform.identity.grant.refused")).extracting(entry -> entry.get("actorId"))
                .containsOnly(administrator.toString()).isNotEmpty();

        MockHttpServletResponse other = replaceRoles(administrator, colleague.toString(), List.of("employee", "synthetic_own_roles"));

        assertThat(other.getStatus()).as(other.getContentAsString()).isEqualTo(204);
        assertThat(rolesOf(administrator, colleague)).contains("synthetic_own_roles");
    }

    @Test
    @DisplayName("R-1b P2 G-A1 / ADR-0033 第 6 项第 2 条：账号路径只认用户 ID 的 uuid；拿本人的员工 ID 当路径不会命中本人账号（400），不授出任何角色")
    void theAccountPathTakesTheUserIdNotTheEmployeeId() throws Exception {
        long unit = newOrgUnitId();
        orgUnit(unit, null, null);
        long administratorEmployee = newEmployeeId();
        long colleagueEmployee = newEmployeeId();
        employee(administratorEmployee, unit, null);
        employee(colleagueEmployee, unit, null);
        UUID administrator = signIn(administratorEmployee).userId();
        signIn(colleagueEmployee);

        MockHttpServletResponse byEmployeeId = replaceRoles(administrator, String.valueOf(colleagueEmployee), List.of("employee"));

        assertThat(byEmployeeId.getStatus()).as(byEmployeeId.getContentAsString()).isEqualTo(400);
    }
}
