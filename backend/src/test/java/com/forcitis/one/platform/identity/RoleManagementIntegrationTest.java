package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class RoleManagementIntegrationTest extends IdentityIntegrationTest {

    static final String ROLES = "/api/v1/platform/roles";

    @Autowired
    RoleDirectory roleDirectory;

    RequestPostProcessor admin;
    RequestPostProcessor reader;

    @BeforeEach
    void callers() {
        createAccount(9, "super_admin");
        admin = user("9").authorities(() -> Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE, () -> Permission.Codes.PLATFORM_IDENTITY_ROLE_READ);
        reader = user("8").authorities(() -> Permission.Codes.PLATFORM_IDENTITY_ROLE_READ);
    }

    private MvcResult createRole(String code, String name, String permissions, String scope) throws Exception {
        String scopeJson = scope == null ? "" : ",\"accessScope\":\"" + scope + "\"";
        return mvc.perform(post(ROLES).with(xsrf()).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"enabled\":true" + scopeJson
                        + ",\"permissionCodes\":[" + permissions + "]}")).andReturn();
    }

    private long roleId(String code) {
        return jdbc.sql("select role_id from platform_identity.roles where code = :c").param("c", code).query(Long.class).single();
    }

    private String errorCode(MvcResult result) throws Exception {
        return body(result).path("errorCode").asString();
    }

    private String versionOf(long roleId) {
        return "\"" + jdbc.sql("select version from platform_identity.roles where role_id = :id").param("id", roleId).query(Long.class).single() + "\"";
    }

    @Test
    @DisplayName("RULE-345 reading roles needs platform.identity:role:read; changing them needs platform.identity:role:update")
    void permissions() throws Exception {
        assertThat(mvc.perform(get(ROLES).with(user("1"))).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get(ROLES).with(reader)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(post(ROLES).with(xsrf()).with(reader).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"x\",\"name\":\"x\"}")).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get(ROLES)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("RULE-345 a new role is enabled and starts at the least-privilege scope SELF")
    void newRoleDefaultsToSelf() throws Exception {
        MvcResult result = createRole("analyst", "Analyst", "\"platform.approval:task:read\"", null);

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(result).path("accessScope").asString()).isEqualTo("SELF");
        assertThat(body(result).path("kind").asString()).isEqualTo("CUSTOM");
        assertThat(body(result).path("enabled").asBoolean()).isTrue();
        assertThat(jdbc.sql("select access_scope from platform_identity.roles where code = 'analyst'").query(String.class).single()).isEqualTo("SELF");
        assertThat(recordedEvents.stream(com.forcitis.one.platform.identity.events.RoleCreated.class)).hasSize(1);
    }

    @Test
    @DisplayName("RULE-345 the code super_admin is reserved; names and codes are unique, ignoring case")
    void reservedAndUnique() throws Exception {
        assertThat(errorCode(createRole("super_admin", "Another", "", null))).isEqualTo("PLATFORM_IDENTITY_ROLE_RESERVED_CODE");
        assertThat(errorCode(createRole("SUPER_ADMIN", "Another", "", null))).isEqualTo("PLATFORM_IDENTITY_ROLE_RESERVED_CODE");
        assertThat(createRole("analyst", "Analyst", "", null).getResponse().getStatus()).isEqualTo(201);

        MvcResult sameCode = createRole("ANALYST", "Other name", "", null);
        MvcResult sameName = createRole("other", "analyst", "", null);

        assertThat(sameCode.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(sameCode)).isEqualTo("PLATFORM_IDENTITY_ROLE_DUPLICATE_CODE");
        assertThat(sameName.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(sameName)).isEqualTo("PLATFORM_IDENTITY_ROLE_DUPLICATE_NAME");
        assertThat(errorCode(createRole("bad code!", "x", "", null))).isEqualTo("PLATFORM_IDENTITY_ROLE_INVALID_CODE");
        assertThat(errorCode(createRole("blank", "  ", "", null))).isEqualTo("PLATFORM_IDENTITY_ROLE_BLANK_NAME");
        assertThat(errorCode(createRole("odd", "Odd", "\"made.up:thing:read\"", null))).isEqualTo("PLATFORM_IDENTITY_PERMISSION_UNKNOWN");
    }

    @Test
    @DisplayName("RULE-345 system roles are frozen: no update, no delete; custom roles can be changed and deleted with their links")
    void systemRolesAreFrozen() throws Exception {
        long employee = roleId("employee");
        long superAdmin = roleId("super_admin");
        for (long id : new long[] {employee, superAdmin}) {
            assertThat(mvc.perform(put(ROLES + "/" + id).header("If-Match", versionOf(id)).with(xsrf()).with(admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Renamed\",\"enabled\":false,\"permissionCodes\":[]}")).andReturn().getResponse().getStatus())
                    .isEqualTo(409);
            assertThat(mvc.perform(delete(ROLES + "/" + id).header("If-Match", versionOf(id)).with(xsrf()).with(admin)).andReturn().getResponse().getStatus()).isEqualTo(409);
        }

        createRole("analyst", "Analyst", "\"platform.approval:task:read\"", null);
        long analyst = roleId("analyst");
        createAccount(1001, "analyst");
        MvcResult renamed = mvc.perform(put(ROLES + "/" + analyst).header("If-Match", versionOf(analyst)).with(xsrf()).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Senior analyst\",\"enabled\":true,\"accessScope\":\"OWN_ORG_UNIT_SUBTREE\",\"permissionCodes\":[\"platform.approval:task:approve\"]}"))
                .andReturn();
        assertThat(renamed.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(renamed).path("accessScope").asString()).isEqualTo("OWN_ORG_UNIT_SUBTREE");
        assertThat(jdbc.sql("select permission_code from platform_identity.role_permissions where role_id = :id").param("id", analyst)
                .query(String.class).list()).as("the permission set is replaced as a whole (RULE-346)")
                .containsExactly("platform.approval:task:approve");

        assertThat(mvc.perform(delete(ROLES + "/" + analyst).header("If-Match", versionOf(analyst)).with(xsrf()).with(admin)).andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbc.sql("select count(*) from platform_identity.role_permissions where role_id = :id").param("id", analyst)
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from platform_identity.account_roles where role_id = :id").param("id", analyst)
                .query(Long.class).single()).as("RULE-345: links go with the role").isZero();
    }

    @Test
    @DisplayName("RULE-343 only enabled roles count: disabling a role takes its permissions away at once")
    void onlyEnabledRolesCount() throws Exception {
        createRole("analyst", "Analyst", "\"platform.numbering:numbering-rule:read\"", null);
        createAccount(1001, "analyst");
        String session = loginWithWecom(1001);
        assertThat(permissionsOf(session)).contains("platform.numbering:numbering-rule:read");

        MvcResult disabled = mvc.perform(put(ROLES + "/" + roleId("analyst")).header("If-Match", versionOf(roleId("analyst"))).with(xsrf()).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Analyst\",\"enabled\":false,\"permissionCodes\":[\"platform.numbering:numbering-rule:read\"]}")).andReturn();
        assertThat(disabled.getResponse().getStatus()).isEqualTo(200);

        assertThat(permissionsOf(session)).doesNotContain("platform.numbering:numbering-rule:read");
    }

    @Test
    @DisplayName("RULE-341 a permission nobody linked is denied: an employee holds only what their role links")
    void unlinkedPermissionIsDenied() throws Exception {
        String session = loginWithWecom(1001);

        assertThat(permissionsOf(session)).doesNotContain(Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE, Permission.Codes.TIMESHEET_APPROVE);
        assertThat(mvc.perform(get(ROLES).cookie(new Cookie("SESSION", session))).andReturn().getResponse().getStatus())
                .as("a real session without the permission").isEqualTo(403);
    }

    @Test
    @DisplayName("RULE-346 assigning roles to an account replaces the whole set and ends the person's sessions")
    void replaceAccountRoles() throws Exception {
        createRole("analyst", "Analyst", "", null);
        createRole("auditor", "Auditor", "", null);
        createRole("planner", "Planner", "", null);
        createAccount(1001, "analyst", "auditor");
        String session = loginWithWecom(1001);

        MvcResult result = mvc.perform(post("/api/v1/platform/user-accounts/1001:replace-roles").with(xsrf()).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"auditor\",\"planner\"]}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbc.sql("""
                select r.code from platform_identity.account_roles ar join platform_identity.roles r on r.role_id = ar.role_id
                 where ar.user_id = 1001 order by r.code
                """).query(String.class).list()).containsExactly("auditor", "planner");
        assertThat(mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn().getResponse().getStatus())
                .as("RULE-338: a role change ends the sessions").isEqualTo(401);
        assertThat(mvc.perform(post("/api/v1/platform/user-accounts/1001:replace-roles").with(xsrf()).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"ghost\"]}")).andReturn().getResponse().getStatus())
                .isEqualTo(404);
        assertThat(mvc.perform(post("/api/v1/platform/user-accounts/555:replace-roles").with(xsrf()).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[]}")).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("RULE-345 nobody can hand out more than they hold: no escalation through role editing")
    void noEscalation() throws Exception {
        jdbc.sql("insert into platform_identity.roles (code, name) values ('role-editor', 'Role editor')").update();
        jdbc.sql("insert into platform_identity.role_permissions (role_id, permission_code) select role_id, :p from platform_identity.roles where code = 'role-editor'")
                .param("p", Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE).update();
        createAccount(5, "role-editor");
        createAccount(1001);
        RequestPostProcessor editor = user("5").authorities(() -> Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE);

        MvcResult broad = mvc.perform(post(ROLES).with(xsrf()).with(editor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"mine\",\"name\":\"Mine\",\"permissionCodes\":[\"cost.timesheet:timesheet:approve\"]}")).andReturn();
        MvcResult toSuperAdmin = mvc.perform(post("/api/v1/platform/user-accounts/5:replace-roles").with(xsrf()).with(editor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"role-editor\",\"super_admin\"]}")).andReturn();
        MvcResult within = mvc.perform(post(ROLES).with(xsrf()).with(editor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"small\",\"name\":\"Small\",\"permissionCodes\":[\"" + Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE + "\"]}")).andReturn();

        assertThat(broad.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(broad)).isEqualTo("PLATFORM_IDENTITY_ROLE_NOT_ALLOWED");
        assertThat(toSuperAdmin.getResponse().getStatus()).isEqualTo(403);
        assertThat(within.getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbc.sql("select count(*) from platform_identity.account_roles ar join platform_identity.roles r on r.role_id = ar.role_id where r.code = 'super_admin' and ar.user_id = 5")
                .query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("RULE-343/349 RoleDirectory lists only enabled accounts of employed people through enabled roles")
    void roleDirectory() {
        for (long id : new long[] {1, 2, 3, 4}) {
            knownEmployee(id, id == 3 ? EmploymentStatus.SEPARATED : EmploymentStatus.EMPLOYED);
            createAccount(id, "employee");
        }
        jdbc.sql("update platform_identity.user_accounts set status = 'DISABLED' where user_id = 2").update();

        assertThat(roleDirectory.findRoleHolders(new RoleCode("employee"))).containsExactlyInAnyOrder(new UserId(1), new UserId(4));
        assertThat(roleDirectory.findRoleHolders(new RoleCode("nobody-has-this"))).isEmpty();

        jdbc.sql("insert into platform_identity.roles (code, name, status) values ('retired', 'Retired', 'DISABLED')").update();
        createAccount(4, "retired");
        assertThat(roleDirectory.findRoleHolders(new RoleCode("retired"))).as("a disabled role has no holders").isEmpty();
    }

    private Set<String> permissionsOf(String session) throws Exception {
        var me = body(mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn());
        return Set.copyOf(me.path("permissionCodes").valueStream().map(n -> n.asString()).toList());
    }
}
