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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * RULE-340 as superseded by ADR-0026 D-7 (ADR-0027 section 2, ADR-0028): the role code super_admin bypasses nothing. A holder of the
 * enabled role asks for a business function that no role of theirs links and is refused, in the ordinary session and in the
 * administrator mode alike; the administrator mode carries only the fixed list of platform permissions. Everything is asked over HTTP
 * in the session of a real WeCom login; the permission list the session reports is read from sessions:current.
 */
class SuperAdminGrantsNothingIT extends AccountsAcceptanceSupport {

    static final String CURRENT = "/api/v1/platform/sessions:current";
    static final String WORK_CALENDAR = "/api/v1/organization/work-calendars/" + TODAY.getYear();
    static final String NUMBERING_RULE = "/api/v1/platform/numbering-rules/TIMESHEET";

    /** Business functions that no role of the super_admin holder links: legal entity update, work calendar update, numbering rule read. */
    static final List<String> BUSINESS_PERMISSIONS = List.of(Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE,
            Permission.Codes.ORGANIZATION_ATTENDANCE_WORK_CALENDAR_UPDATE, Permission.Codes.PLATFORM_NUMBERING_RULE_READ,
            Permission.Codes.PLATFORM_NUMBERING_RULE_UPDATE, Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE,
            Permission.Codes.PLATFORM_AUDIT_ENTRY_READ, Permission.Codes.TIMESHEET_APPROVE);

    static final TestDatabase postgres = TestPostgres.database(SuperAdminGrantsNothingIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private SignedIn superAdministrator() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID person = newEmployeeId();
        employee(person, unit, null);
        grantByOperations(person, "super_admin");
        return signIn(person);
    }

    private MockHttpServletResponse createLegalEntityAs(SignedIn caller) throws Exception {
        return mvc.perform(post(LEGAL_ENTITIES).with(csrf()).with(session(caller)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + newLegalEntityCode("SA") + "\",\"name\":\"Synthetic super admin check\"}")).andReturn().getResponse();
    }

    private List<String> permissionsOf(SignedIn caller) throws Exception {
        MockHttpServletResponse current = mvc.perform(get(CURRENT).with(session(caller))).andReturn().getResponse();
        assertThat(current.getStatus()).as(current.getContentAsString()).isEqualTo(200);
        return JsonPath.read(current.getContentAsString(), "$.permissionCodes");
    }

    private void assertRefusedEverywhere(SignedIn caller, String sessionKind) throws Exception {
        MockHttpServletResponse created = createLegalEntityAs(caller);
        assertThat(created.getStatus()).as("%s: legal entity update, answered %s", sessionKind, created.getContentAsString()).isEqualTo(403);

        MockHttpServletResponse calendar = mvc.perform(put(WORK_CALENDAR).header("If-Match", "\"0\"").with(csrf()).with(session(caller))
                .contentType(MediaType.APPLICATION_JSON).content("{\"days\":[],\"lockedDaysConfirmed\":true}")).andReturn().getResponse();
        assertThat(calendar.getStatus()).as("%s: work calendar update, answered %s", sessionKind, calendar.getContentAsString()).isEqualTo(403);

        MockHttpServletResponse rule = mvc.perform(get(NUMBERING_RULE).with(session(caller))).andReturn().getResponse();
        assertThat(rule.getStatus()).as("%s: numbering rule read, answered %s", sessionKind, rule.getContentAsString()).isEqualTo(403);

        MockHttpServletResponse audit = mvc.perform(get(AUDIT_ENTRIES).param("limit", "1").with(session(caller))).andReturn().getResponse();
        assertThat(audit.getStatus()).as("%s: audit read, answered %s", sessionKind, audit.getContentAsString()).isEqualTo(403);
    }

    @Test
    @DisplayName("RULE-340 / ADR-0026 D-7：持有启用的 super_admin 角色的用户在普通会话里检查未关联到其任何角色的业务权限，被拒（403）；会话报告的权限里没有任何业务权限")
    void aSuperAdminHolderIsRefusedBusinessFunctionsInTheOrdinarySession() throws Exception {
        SignedIn administrator = superAdministrator();

        assertRefusedEverywhere(administrator, "ordinary session");

        assertThat(permissionsOf(administrator)).as("permissions of an ordinary session of a super_admin holder").doesNotContainAnyElementsOf(BUSINESS_PERMISSIONS);
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID colleague = newEmployeeId();
        employee(colleague, unit, null);
        SignedIn withoutSuperAdmin = signIn(colleague);
        assertThat(permissionsOf(administrator)).as("same filing, the only difference being the super_admin role: the same permissions")
                .containsExactlyInAnyOrderElementsOf(permissionsOf(withoutSuperAdmin));
        assertRefusedEverywhere(withoutSuperAdmin, "colleague without super_admin");
    }

    @Test
    @DisplayName("RULE-340 / ADR-0027 第 2 项 / ADR-0028：管理员模式也不绕过业务权限检查；它只带固定的平台权限清单，不含业务、敏感读取和审计")
    void theAdministratorModeCarriesOnlyThePlatformPermissionList() throws Exception {
        SignedIn inMode = administratorMode(superAdministrator());

        assertRefusedEverywhere(inMode, "administrator mode");

        assertThat(permissionsOf(inMode)).as("permissions of the administrator mode")
                .containsExactlyInAnyOrder(Permission.Codes.PLATFORM_IDENTITY_ROLE_READ, Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE,
                        Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE, Permission.Codes.PLATFORM_IDENTITY_SESSION_DELETE,
                        Permission.Codes.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE);
    }

    @Test
    @DisplayName("RULE-340 / RULE-341（对照）：同一业务功能由关联了该权限的启用角色授予时通过，说明上面的拒绝来自 super_admin 不绕过，而不是功能本身不可用")
    void theSameFunctionIsGrantedThroughALinkedRole() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID person = newEmployeeId();
        employee(person, unit, null);
        grantByOperations(person, "org_data_admin");
        SignedIn maintainer = signIn(person);

        MockHttpServletResponse created = createLegalEntityAs(maintainer);

        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
    }
}
