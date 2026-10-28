package com.forcitis.one.platform.identity;

import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.identity.events.RoleCreated;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
        admin = user(TestAccounts.account(9).value().toString()).authorities(() -> Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE, () -> Permission.Codes.PLATFORM_IDENTITY_ROLE_READ,
                () -> Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE);
        reader = user(TestAccounts.account(8).value().toString()).authorities(() -> Permission.Codes.PLATFORM_IDENTITY_ROLE_READ);
    }

    private MvcResult createRole(String code, String name, String permissions, String scope) throws Exception {
        String scopeJson = scope == null ? "" : ",\"accessScope\":\"" + scope + "\"";
        return mvc.perform(post(ROLES).with(xsrf()).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"enabled\":true" + scopeJson
                        + ",\"permissionCodes\":[" + permissions + "]}")).andReturn();
    }

    private UUID roleId(String code) {
        return jdbc.sql("select role_id from platform_identity.roles where code = :c").param("c", code).query(UUID.class).single();
    }

    private String errorCode(MvcResult result) throws Exception {
        return body(result).path("errorCode").asString();
    }

    private String versionOf(UUID roleId) {
        return "\"" + jdbc.sql("select version from platform_identity.roles where role_id = :id").param("id", roleId).query(Long.class).single() + "\"";
    }

    @Test
    @DisplayName("RULE-345 reading roles needs platform.identity:role:read; changing them needs platform.identity:role:update")
    void permissions() throws Exception {
        assertThat(mvc.perform(get(ROLES).with(user(TestAccounts.account(1).value().toString()))).andReturn().getResponse().getStatus()).isEqualTo(403);
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
        assertThat(jdbc.sql("""
                select v.access_scope_kind from platform_identity.roles r
                  join platform_identity.role_versions v on v.role_id = r.role_id and v.effective_to_at is null
                 where r.code = 'analyst'
                """).query(String.class).single()).isEqualTo("SELF");
        assertThat(recordedEvents.stream(RoleCreated.class)).hasSize(1);
    }

    @Test
    @DisplayName("a role id in a response body is the canonical UUID of the role: created, read, listed and updated")
    void roleIdsAreCanonicalUuids() throws Exception {
        MvcResult created = createRole("analyst", "Analyst", "\"platform.approval:task:read\"", null);
        UUID analyst = roleId("analyst");
        String canonical = analyst.toString();

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(created).path("roleId").asString()).isEqualTo(canonical);
        assertThat(created.getResponse().getHeader("Location")).isEqualTo(ROLES + "/" + canonical);

        MvcResult read = mvc.perform(get(ROLES + "/" + canonical).with(reader)).andReturn();
        assertThat(read.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(read).path("roleId").asString()).isEqualTo(canonical);

        MvcResult listed = mvc.perform(get(ROLES).with(reader)).andReturn();
        List<String> listedIds = new ArrayList<>();
        body(listed).path("items").forEach(item -> listedIds.add(item.path("roleId").asString()));
        assertThat(listedIds).contains(canonical).allSatisfy(id -> assertThat(Uuids.parseCanonical(id).version()).isEqualTo(7));

        MvcResult updated = mvc.perform(put(ROLES + "/" + canonical).header("If-Match", versionOf(analyst)).with(xsrf()).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Senior analyst\",\"enabled\":true,\"permissionCodes\":[]}")).andReturn();
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(updated).path("roleId").asString()).isEqualTo(canonical);
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
    @DisplayName("RULE-345 system roles are frozen: no update, no delete; custom roles can be changed, and deleted once no account holds them")
    void systemRolesAreFrozen() throws Exception {
        UUID employee = roleId("employee");
        UUID superAdmin = roleId("super_admin");
        for (UUID id : new UUID[] {employee, superAdmin}) {
            assertThat(mvc.perform(put(ROLES + "/" + id).header("If-Match", versionOf(id)).with(xsrf()).with(admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Renamed\",\"enabled\":false,\"permissionCodes\":[]}")).andReturn().getResponse().getStatus())
                    .isEqualTo(409);
            assertThat(mvc.perform(delete(ROLES + "/" + id).header("If-Match", versionOf(id)).with(xsrf()).with(admin)).andReturn().getResponse().getStatus()).isEqualTo(409);
        }

        createRole("analyst", "Analyst", "\"platform.approval:task:read\"", null);
        UUID analyst = roleId("analyst");
        createAccount(1001, "analyst");
        MvcResult renamed = mvc.perform(put(ROLES + "/" + analyst).header("If-Match", versionOf(analyst)).with(xsrf()).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Senior analyst\",\"enabled\":true,\"accessScope\":\"OWN_ORG_UNIT_SUBTREE\",\"permissionCodes\":[\"platform.approval:task:approve\"]}"))
                .andReturn();
        assertThat(renamed.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(renamed).path("accessScope").asString()).isEqualTo("OWN_ORG_UNIT_SUBTREE");
        assertThat(TestRoles.currentPermissionCodes(jdbc, analyst)).as("the permission set is replaced as a whole (RULE-346)")
                .containsExactly("platform.approval:task:approve");

        MvcResult held = mvc.perform(delete(ROLES + "/" + analyst).header("If-Match", versionOf(analyst)).with(xsrf()).with(admin)).andReturn();
        assertThat(held.getResponse().getStatus()).isEqualTo(409);
        assertThat(errorCode(held)).isEqualTo("PLATFORM_IDENTITY_ROLE_IN_USE");
        assertThat(jdbc.sql("select count(*) from platform_identity.account_roles where role_id = :id").param("id", analyst)
                .query(Long.class).single()).as("a role held by an account is not deleted and its link stays").isEqualTo(1);

        assertThat(mvc.perform(post("/api/v1/platform/user-accounts/" + accountOrNone(1001) + ":replace-roles").header("If-Match", accountVersion(1001)).with(xsrf()).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[]}")).andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(mvc.perform(delete(ROLES + "/" + analyst).header("If-Match", versionOf(analyst)).with(xsrf()).with(admin)).andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(TestRoles.currentPermissionCodes(jdbc, analyst)).as("ADR-0034 decision 1 item 5: the deleted role has no current version, so no permission").isEmpty();
        assertThat(jdbc.sql("select count(*) from platform_identity.role_versions where role_id = :id and effective_to_at is null").param("id", analyst)
                .query(Long.class).single()).isZero();
        assertThat(mvc.perform(get(ROLES + "/" + analyst).with(reader)).andReturn().getResponse().getStatus()).as("a deleted role is not found").isEqualTo(404);
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

        MvcResult result = mvc.perform(post("/api/v1/platform/user-accounts/" + accountOrNone(1001) + ":replace-roles").header("If-Match", accountVersion(1001)).with(xsrf()).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"auditor\",\"planner\"]}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(jdbc.sql("""
                select r.code from platform_identity.account_roles ar join platform_identity.roles r on r.role_id = ar.role_id
                 where ar.user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = :employee) order by r.code
                """).param("employee", TestAccounts.employee(1001).value()).query(String.class).list()).containsExactly("auditor", "planner");
        assertThat(mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn().getResponse().getStatus())
                .as("RULE-338: a role change ends the sessions").isEqualTo(401);
        assertThat(mvc.perform(post("/api/v1/platform/user-accounts/" + accountOrNone(1001) + ":replace-roles").header("If-Match", accountVersion(1001)).with(xsrf()).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"ghost\"]}")).andReturn().getResponse().getStatus())
                .isEqualTo(404);
        assertThat(mvc.perform(post("/api/v1/platform/user-accounts/" + accountOrNone(555) + ":replace-roles").header("If-Match", accountVersion(555)).with(xsrf()).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[]}")).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("RULE-345 nobody can hand out more than they hold: no escalation through role editing")
    void noEscalation() throws Exception {
        TestRoles.insertCustom(jdbc, "role-editor", "Role editor", "ENABLED", "SELF", Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE,
                Permission.Codes.PLATFORM_APPROVAL_TASK_READ);
        createAccount(5, "role-editor");
        createAccount(1001);
        RequestPostProcessor editor = user(TestAccounts.account(5).value().toString()).authorities(() -> Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE,
                () -> Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE);

        MvcResult broad = mvc.perform(post(ROLES).with(xsrf()).with(editor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"mine\",\"name\":\"Mine\",\"permissionCodes\":[\"cost.timesheet:timesheet:approve\"]}")).andReturn();
        MvcResult toSuperAdmin = mvc.perform(post("/api/v1/platform/user-accounts/" + accountOrNone(1001) + ":replace-roles").header("If-Match", accountVersion(1001)).with(xsrf()).with(editor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"role-editor\",\"super_admin\"]}")).andReturn();
        MvcResult within = mvc.perform(post(ROLES).with(xsrf()).with(editor).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"small\",\"name\":\"Small\",\"permissionCodes\":[\"" + Permission.Codes.PLATFORM_APPROVAL_TASK_READ + "\"]}")).andReturn();

        assertThat(broad.getResponse().getStatus()).isEqualTo(403);
        assertThat(errorCode(broad)).isEqualTo("PLATFORM_IDENTITY_ROLE_NOT_ALLOWED");
        assertThat(toSuperAdmin.getResponse().getStatus()).isEqualTo(403);
        assertThat(within.getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbc.sql("select count(*) from platform_identity.account_roles ar join platform_identity.roles r on r.role_id = ar.role_id where r.code = 'super_admin' and ar.user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = " + TestOrgIds.sqlEmployee(1001) + ")")
                .query(Long.class).single()).isZero();
    }

    @Test
    @DisplayName("RULE-343/349 RoleDirectory lists only enabled accounts of employed people through enabled roles")
    void roleDirectory() {
        for (long id : new long[] {1, 2, 3, 4}) {
            knownEmployee(id, id == 3 ? EmploymentStatus.SEPARATED : EmploymentStatus.EMPLOYED);
            createAccount(id, "employee");
        }
        jdbc.sql("update platform_identity.user_accounts set status = 'DISABLED' where employee_id = " + TestOrgIds.sqlEmployee(2)).update();

        assertThat(roleDirectory.findRoleHolders(new RoleCode("employee"))).containsExactlyInAnyOrder(TestAccounts.account(1), TestAccounts.account(4));
        assertThat(roleDirectory.findRoleHolders(new RoleCode("nobody-has-this"))).isEmpty();

        TestRoles.insertCustom(jdbc, "retired", "Retired", "DISABLED", "SELF");
        createAccount(4, "retired");
        assertThat(roleDirectory.findRoleHolders(new RoleCode("retired"))).as("a disabled role has no holders").isEmpty();
    }

    private Set<String> permissionsOf(String session) throws Exception {
        var me = body(mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn());
        return Set.copyOf(me.path("permissionCodes").valueStream().map(n -> n.asString()).toList());
    }

    @Test
    @DisplayName("B3-M4 account writes need the version of the account: none is 428, an old one 412, a weak or unreadable tag 400; the GET gives the ETag")
    void accountWritesNeedTheVersion() throws Exception {
        createRole("analyst", "Analyst", "", null);
        createAccount(1001, "analyst");
        String url = "/api/v1/platform/user-accounts/" + accountOrNone(1001);
        String etag = mvc.perform(get(url).with(admin)).andReturn().getResponse().getHeader("ETag");
        assertThat(etag).isEqualTo(accountVersion(1001));

        for (String action : new String[] {":replace-roles", ":enable", ":disable"}) {
            var request = post(url + action).with(xsrf()).with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[]}");
            assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).as(action + " without a version").isEqualTo(428);
            assertThat(mvc.perform(request.header("If-Match", "\"99\"")).andReturn().getResponse().getStatus()).as(action + " with an old version").isEqualTo(412);
        }
        var weak = post(url + ":enable").with(xsrf()).with(admin).header("If-Match", "W/" + etag);
        assertThat(mvc.perform(weak).andReturn().getResponse().getStatus()).as("a weak tag").isEqualTo(400);
        assertThat(mvc.perform(post(url + ":enable").with(xsrf()).with(admin).header("If-Match", "abc")).andReturn().getResponse().getStatus())
                .as("an unreadable tag").isEqualTo(400);

        MvcResult changed = mvc.perform(post(url + ":replace-roles").with(xsrf()).with(admin).header("If-Match", etag)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[]}")).andReturn();
        assertThat(changed.getResponse().getStatus()).isEqualTo(204);
        assertThat(changed.getResponse().getHeader("ETag")).isNotEqualTo(etag);
    }

    @Test
    @DisplayName("Nit replacing the roles with the roles the account already has changes nothing: no event, no new version, the sessions stay")
    void sameRolesChangeNothing() throws Exception {
        createRole("analyst", "Analyst", "", null);
        createAccount(1001, "analyst");
        String before = accountVersion(1001);

        MvcResult result = mvc.perform(post("/api/v1/platform/user-accounts/" + accountOrNone(1001) + ":replace-roles").with(xsrf()).with(admin).header("If-Match", before)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"analyst\"]}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(accountVersion(1001)).isEqualTo(before);
    }

    @Test
    @DisplayName("a role and an account keep the lock version and the event count apart: both are stored and move once per change that announces an event")
    void storedCountersFollowTheChanges() throws Exception {
        createRole("analyst", "Analyst", "", null);
        UUID id = roleId("analyst");
        mvc.perform(put(ROLES + "/" + id).header("If-Match", versionOf(id)).with(xsrf()).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Senior analyst\",\"enabled\":true,\"permissionCodes\":[]}")).andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));
        mvc.perform(put(ROLES + "/" + id).header("If-Match", versionOf(id)).with(xsrf()).with(admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Senior analyst\",\"enabled\":false,\"permissionCodes\":[]}")).andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

        assertThat(jdbc.sql("select version, event_count from platform_identity.roles where role_id = :id").param("id", id)
                .query((rs, n) -> List.of(rs.getLong(1), rs.getLong(2))).single()).as("three saves; the last one revised and disabled the role, so it announced two events: 1 + 1 + 2").isEqualTo(List.of(3L, 4L));

        createAccount(1001, "analyst");
        mvc.perform(post("/api/v1/platform/user-accounts/" + accountOrNone(1001) + ":replace-roles").header("If-Match", accountVersion(1001)).with(xsrf()).with(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[]}")).andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(204));
        mvc.perform(post("/api/v1/platform/user-accounts/" + accountOrNone(1001) + ":disable").header("If-Match", accountVersion(1001)).with(xsrf()).with(admin))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(204));

        assertThat(jdbc.sql("select version, event_count from platform_identity.user_accounts where employee_id = " + TestOrgIds.sqlEmployee(1001))
                .query((rs, n) -> List.of(rs.getLong(1), rs.getLong(2))).single()).as("roles replaced, disabled").isEqualTo(List.of(2L, 2L));
    }

    @Test
    @DisplayName("INT-SEC-H2 platformPermissionsStayInTheAdministratorMode: a custom role cannot be created or revised with a platform permission, and one that already holds it gives none in an ordinary session")
    void platformPermissionsStayInTheAdministratorMode() throws Exception {
        MvcResult created = createRole("ops", "Ops", "\"" + Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE + "\"", null);
        assertThat(created.getResponse().getStatus()).isEqualTo(422);
        assertThat(errorCode(created)).isEqualTo("PLATFORM_IDENTITY_PERMISSION_ADMINISTRATOR_MODE_ONLY");
        assertThat(createRole("hold", "Hold", "\"" + Permission.Codes.ORGANIZATION_WORKFORCE_SEPARATION_HOLD_APPROVE + "\"", null).getResponse().getStatus())
                .as("separation-hold:approve is allowed outside the administrator mode (ADR-0028)").isNotEqualTo(422);

        TestRoles.insertCustom(jdbc, "legacy-editor", "Legacy editor", "ENABLED", "SELF", Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE,
                Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE, Permission.Codes.PLATFORM_IDENTITY_SESSION_DELETE, Permission.Codes.PLATFORM_IDENTITY_ROLE_READ,
                Permission.Codes.PLATFORM_APPROVAL_TASK_READ);
        createAccount(1001, "employee", "legacy-editor");
        String session = loginWithWecom(1001);

        assertThat(permissionsOf(session)).contains(Permission.Codes.PLATFORM_APPROVAL_TASK_READ).doesNotContain(Permission.Codes.PLATFORM_IDENTITY_ROLE_UPDATE,
                Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE, Permission.Codes.PLATFORM_IDENTITY_SESSION_DELETE, Permission.Codes.PLATFORM_IDENTITY_ROLE_READ);
        assertThat(mvc.perform(get(ROLES).cookie(new Cookie("SESSION", session))).andReturn().getResponse().getStatus()).isEqualTo(403);
    }
}
