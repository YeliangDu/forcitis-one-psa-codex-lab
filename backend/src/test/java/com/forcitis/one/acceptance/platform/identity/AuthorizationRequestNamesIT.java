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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Card #1286: GET /authorization-requests resolves requestedByName and subjectUserName from employees.display_name;
 * missing employees and an absent subject are explicit JSON nulls, while account ids remain. roleLabels pairs each sorted
 * roleCodes entry with the name exposed by GET /roles. Role-change requests retain roleName. Reading still requires
 * platform.identity:account-role:update. The seeded 授权申请 child of 系统管理 routes to /admin/authorization-requests and is
 * visible through sessions:current only with that permission, including administrator mode and excluding employee/auditor sessions.
 * All callers use real accounts and production sessions, as in AuthorizationConfirmerAccountsIT.
 */
class AuthorizationRequestNamesIT extends AccountsAcceptanceSupport {

    static final String ROLES = "/api/v1/platform/roles";
    static final String ACCOUNTS = "/api/v1/platform/user-accounts/";
    static final String REQUESTS = "/api/v1/platform/authorization-requests";
    static final String CURRENT = "/api/v1/platform/sessions:current";
    static final String REQUESTS_ROUTE = "/admin/authorization-requests";

    static final TestDatabase postgres = TestPostgres.database(AuthorizationRequestNamesIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

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

    private static String requestIdOf(MockHttpServletResponse accepted) throws Exception {
        assertThat(accepted.getStatus()).as("a sensitive change waits for confirmation: %s", accepted.getContentAsString()).isEqualTo(202);
        return JsonPath.read(accepted.getContentAsString(), "$.requestId");
    }

    private String requestRoles(SignedIn requester, SignedIn beneficiary) throws Exception {
        MockHttpServletResponse account = mvc.perform(get(ACCOUNTS + beneficiary.userId()).with(session(requester))).andReturn().getResponse();
        assertThat(account.getStatus()).as("read the beneficiary account before replacing roles: %s", account.getContentAsString()).isEqualTo(200);
        return requestIdOf(mvc.perform(post(ACCOUNTS + beneficiary.userId() + ":replace-roles").header("If-Match", account.getHeader("ETag"))
                .with(csrf()).with(session(requester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCodes\":[\"employee\",\"super_admin\",\"security_auditor\"]}")).andReturn().getResponse());
    }

    private List<Map<String, Object>> pendingRequests(SignedIn reader) throws Exception {
        MockHttpServletResponse listed = mvc.perform(get(REQUESTS).with(session(reader))).andReturn().getResponse();
        assertThat(listed.getStatus()).as("an administrator can read pending requests: %s", listed.getContentAsString()).isEqualTo(200);
        return JsonPath.read(listed.getContentAsString(), "$.items");
    }

    private static Map<String, Object> itemFor(List<Map<String, Object>> items, String requestId) {
        List<Map<String, Object>> matches = items.stream().filter(item -> requestId.equals(item.get("requestId"))).toList();
        assertThat(matches).as("pending request %s appears exactly once", requestId).hasSize(1);
        return matches.getFirst();
    }

    private static String displayNameOf(SignedIn person) {
        return "Synthetic person " + serialOf(person.employeeId());
    }

    private static void hasPeople(Map<String, Object> item, SignedIn requester, SignedIn beneficiary) {
        assertThat(item).as("the request keeps both account ids and resolves each employee's own display name")
                .containsEntry("requestedBy", requester.userId().toString())
                .containsEntry("requestedByName", displayNameOf(requester))
                .containsEntry("subjectUserId", beneficiary.userId().toString())
                .containsEntry("subjectUserName", displayNameOf(beneficiary));
    }

    private MockHttpServletResponse currentSession(SignedIn caller) throws Exception {
        MockHttpServletResponse current = mvc.perform(get(CURRENT).with(session(caller))).andReturn().getResponse();
        assertThat(current.getStatus()).as("read the caller's current session: %s", current.getContentAsString()).isEqualTo(200);
        return current;
    }

    private static List<Map<String, Object>> allNavigationNodes(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> found = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            found.add(node);
            List<Map<String, Object>> children = JsonPath.read(node, "$.children");
            found.addAll(allNavigationNodes(children));
        }
        return found;
    }

    @Test
    @DisplayName("#1286 人员授权待办保留申请人与受益人的账号 ID，解析各自姓名，并按排序后的角色代码逐项返回角色显示名")
    void namesAreResolved() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        SignedIn requester = inAdministratorMode(newAdministrator(unit));
        SignedIn beneficiary = inAdministratorMode(newAdministrator(unit));
        SignedIn reader = inAdministratorMode(newAdministrator(unit));
        String requestId = requestRoles(requester, beneficiary);

        Map<String, String> roleNames = new LinkedHashMap<>();
        String cursor = null;
        do {
            var query = get(ROLES).param("limit", "100").with(session(reader));
            if (cursor != null) {
                query = query.param("cursor", cursor);
            }
            MockHttpServletResponse roles = mvc.perform(query).andReturn().getResponse();
            assertThat(roles.getStatus()).as("read role display names through the role catalogue: %s", roles.getContentAsString()).isEqualTo(200);
            List<Map<String, Object>> page = JsonPath.read(roles.getContentAsString(), "$.items");
            for (Map<String, Object> role : page) {
                roleNames.put((String) role.get("code"), (String) role.get("name"));
            }
            cursor = JsonPath.read(roles.getContentAsString(), "$.nextCursor");
        } while (cursor != null);

        Map<String, Object> item = itemFor(pendingRequests(reader), requestId);
        hasPeople(item, requester, beneficiary);
        List<String> roleCodes = JsonPath.read(item, "$.roleCodes");
        assertThat(roleCodes).as("the requested role codes remain present and sorted")
                .containsExactly("employee", "security_auditor", "super_admin");
        assertThat(item).as("the pending item explicitly supplies roleLabels").containsKey("roleLabels");
        List<Map<String, Object>> labels = JsonPath.read(item, "$.roleLabels");
        assertThat(labels).as("there is exactly one label for every requested role code").hasSize(roleCodes.size());
        for (int i = 0; i < roleCodes.size(); i++) {
            String code = roleCodes.get(i);
            assertThat(roleNames).as("the HTTP catalogue contains the requested role %s", code).containsKey(code);
            assertThat(roleNames.get(code)).as("the existing role %s has a display name", code).isNotNull();
            assertThat(labels.get(i)).as("roleLabels[%s] follows roleCodes and uses the catalogue name of %s", i, code)
                    .containsEntry("code", code).containsEntry("name", roleNames.get(code));
        }
    }

    @Test
    @DisplayName("#1286 无持有人角色的敏感权限变更待办解析申请人姓名，人员对象 ID 和姓名显式为空，roleName 保持原契约")
    void aRoleRequestIsNamedToo() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        SignedIn requester = inAdministratorMode(newAdministrator(unit));
        SignedIn reader = inAdministratorMode(newAdministrator(unit));
        String code = "synthetic_names_" + serialOf(requester.employeeId());
        String name = "Synthetic request names role " + serialOf(requester.employeeId());
        MockHttpServletResponse created = mvc.perform(post(ROLES).with(csrf()).with(session(requester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"enabled\":true,\"permissionCodes\":[\""
                        + Permission.Codes.PLATFORM_APPROVAL_TASK_READ + "\"]}")).andReturn().getResponse();
        assertThat(created.getStatus()).as("create a custom role without sensitive permissions: %s", created.getContentAsString()).isEqualTo(201);
        String roleId = JsonPath.read(created.getContentAsString(), "$.roleId");
        // ADR-0032: nobody is granted this role; widening a held role would be refused with 409 before a request exists.
        MockHttpServletResponse role = mvc.perform(get(ROLES + "/" + roleId).with(session(requester))).andReturn().getResponse();
        assertThat(role.getStatus()).as("read the unheld role and its ETag: %s", role.getContentAsString()).isEqualTo(200);
        String requestId = requestIdOf(mvc.perform(put(ROLES + "/" + roleId).header("If-Match", role.getHeader("ETag")).with(csrf())
                .with(session(requester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"enabled\":true,\"permissionCodes\":[\"" + Permission.Codes.PLATFORM_APPROVAL_TASK_READ + "\",\""
                        + Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE + "\"]}")).andReturn().getResponse());

        Map<String, Object> item = itemFor(pendingRequests(reader), requestId);
        assertThat(item).as("a role-change request keeps its requester and roleName and explicitly has no subject user")
                .containsEntry("requestedBy", requester.userId().toString())
                .containsEntry("requestedByName", displayNameOf(requester))
                .containsEntry("roleName", name);
        assertThat(item.get("subjectUserId")).as("a role request has no subject user").isNull();
        assertThat(item.get("subjectUserName")).as("so it has no subject name either").isNull();
    }

    @Test
    @DisplayName("#1286 申请人员工记录消失后待办仍出现一次，保留申请人账号 ID，姓名显式为空且受益人姓名不变")
    void aMissingNameStaysNullAndTheIdStays() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        SignedIn requester = inAdministratorMode(newAdministrator(unit));
        SignedIn beneficiary = inAdministratorMode(newAdministrator(unit));
        SignedIn reader = inAdministratorMode(newAdministrator(unit));
        String requestId = requestRoles(requester, beneficiary);

        removeFromSnapshot(requester.employeeId());

        Map<String, Object> item = itemFor(pendingRequests(reader), requestId);
        assertThat(item).as("a missing applicant employee is JSON null, not an omitted name or a lost request")
                .containsEntry("requestedBy", requester.userId().toString())
                .containsEntry("subjectUserId", beneficiary.userId().toString()).containsEntry("subjectUserName", displayNameOf(beneficiary));
        assertThat(item.get("requestedByName")).as("the applicant name is null once no employee is left behind the account").isNull();
        // Not covered: a role row gone behind a pending request (no HTTP way to delete a role; no state-changing SQL here).
    }

    @Test
    @DisplayName("#1286 受益人员工记录消失后待办保留受益人账号 ID，姓名显式为空且申请人姓名不变")
    void aMissingSubjectNameStaysNullAndTheIdStays() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        SignedIn requester = inAdministratorMode(newAdministrator(unit));
        SignedIn beneficiary = inAdministratorMode(newAdministrator(unit));
        SignedIn reader = inAdministratorMode(newAdministrator(unit));
        String requestId = requestRoles(requester, beneficiary);

        removeFromSnapshot(beneficiary.employeeId());

        Map<String, Object> item = itemFor(pendingRequests(reader), requestId);
        assertThat(item).as("a missing beneficiary employee is JSON null while both account ids and the applicant name remain")
                .containsEntry("requestedBy", requester.userId().toString()).containsEntry("requestedByName", displayNameOf(requester))
                .containsEntry("subjectUserId", beneficiary.userId().toString());
        assertThat(item.get("subjectUserName")).as("the beneficiary name is null once no employee is left behind the account").isNull();
    }

    @Test
    @DisplayName("#1286 授权申请读取沿用账号角色更新权限：默认员工返回 403，管理员模式返回 200")
    void readStaysUnderItsPermission() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        UUID employeeId = newEmployeeId();
        employee(employeeId, unit, null);
        SignedIn plainEmployee = signIn(employeeId);
        SignedIn reader = inAdministratorMode(newAdministrator(unit));

        MockHttpServletResponse denied = mvc.perform(get(REQUESTS).with(session(plainEmployee))).andReturn().getResponse();
        assertThat(denied.getStatus()).as("the default employee lacks %s: %s", Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE,
                denied.getContentAsString()).isEqualTo(403);
        MockHttpServletResponse allowed = mvc.perform(get(REQUESTS).with(session(reader))).andReturn().getResponse();
        assertThat(allowed.getStatus()).as("administrator mode already has %s: %s", Permission.Codes.PLATFORM_IDENTITY_ACCOUNT_ROLE_UPDATE,
                allowed.getContentAsString()).isEqualTo(200);
    }

    @Test
    @DisplayName("#1286 授权申请菜单仅向有账号角色更新权限的管理员模式显示于系统管理之下，默认员工和仅授安全审计员均不可见")
    void theMenuItemIsVisibleOnlyWithTheUpdatePermission() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        SignedIn administrator = inAdministratorMode(newAdministrator(unit));
        UUID plainPerson = newEmployeeId();
        employee(plainPerson, unit, null);
        SignedIn plainEmployee = signIn(plainPerson);
        UUID auditorPerson = newEmployeeId();
        employee(auditorPerson, unit, null);
        grantByOperations(auditorPerson, "security_auditor");
        SignedIn auditor = signIn(auditorPerson);

        List<Map<String, Object>> plainNavigation = JsonPath.read(currentSession(plainEmployee).getContentAsString(), "$.navigation");
        List<Map<String, Object>> auditorNavigation = JsonPath.read(currentSession(auditor).getContentAsString(), "$.navigation");
        assertThat(allNavigationNodes(plainNavigation)).as("no default employee navigation node links to authorization requests")
                .noneMatch(node -> REQUESTS_ROUTE.equals(node.get("routePath")));
        assertThat(allNavigationNodes(auditorNavigation)).as("security_auditor alone exposes no authorization request link anywhere")
                .noneMatch(node -> REQUESTS_ROUTE.equals(node.get("routePath")));

        List<Map<String, Object>> adminNavigation = JsonPath.read(currentSession(administrator).getContentAsString(), "$.navigation");
        List<Map<String, Object>> systemNodes = allNavigationNodes(adminNavigation).stream()
                .filter(node -> "系统管理".equals(node.get("name"))).toList();
        assertThat(systemNodes).as("administrator navigation contains the system management parent").hasSize(1);
        List<Map<String, Object>> children = JsonPath.read(systemNodes.getFirst(), "$.children");
        List<Map<String, Object>> requestNodes = children.stream().filter(node -> REQUESTS_ROUTE.equals(node.get("routePath"))).toList();
        assertThat(requestNodes).as("system management has exactly one direct authorization request child at %s", REQUESTS_ROUTE).hasSize(1);
        assertThat(requestNodes.getFirst()).as("the seeded child has the specified label and route")
                .containsEntry("name", "授权申请").containsEntry("routePath", REQUESTS_ROUTE);
    }

    @Test
    @DisplayName("#1286 两条不同人员的待办各出现一次，申请人与受益人姓名分别属于本条申请，不串用其他待办的姓名")
    void twoPendingRequestsKeepTheirOwnNames() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        SignedIn firstRequester = inAdministratorMode(newAdministrator(unit));
        SignedIn firstBeneficiary = inAdministratorMode(newAdministrator(unit));
        SignedIn secondRequester = inAdministratorMode(newAdministrator(unit));
        SignedIn secondBeneficiary = inAdministratorMode(newAdministrator(unit));
        SignedIn reader = inAdministratorMode(newAdministrator(unit));
        String firstId = requestRoles(firstRequester, firstBeneficiary);
        String secondId = requestRoles(secondRequester, secondBeneficiary);

        assertThat(firstId).as("two submissions for different accounts produce different requests").isNotEqualTo(secondId);
        List<Map<String, Object>> items = pendingRequests(reader);
        hasPeople(itemFor(items, firstId), firstRequester, firstBeneficiary);
        hasPeople(itemFor(items, secondId), secondRequester, secondBeneficiary);
    }
}
