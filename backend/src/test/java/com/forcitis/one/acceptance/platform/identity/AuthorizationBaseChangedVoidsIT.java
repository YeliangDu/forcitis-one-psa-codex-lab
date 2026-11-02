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
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #938, old card 0034-G7-02 (ADR-0034 decision 2 item 5; A-7: audit platform.identity.authorization.voided plus event AuthorizationVoided):
 * a sensitive request records its base, the account version and the current version of every related role. When the base has moved
 * between submission and confirmation, the confirmed request does not apply: it ends VOIDED with one void audit entry and one
 * AuthorizationVoided event, never stays CONFIRMED, and grants and role definitions stay as they were. A request whose base did not
 * move, or where only an unrelated role moved, still applies. All people are real accounts in production sessions (RULING-P2-8).
 */
class AuthorizationBaseChangedVoidsIT extends AccountsAcceptanceSupport {

    static final String ROLES = "/api/v1/platform/roles";
    static final String ACCOUNTS = "/api/v1/platform/user-accounts/";
    static final String REQUESTS = "/api/v1/platform/authorization-requests";
    static final String VOIDED_ACTION = "platform.identity.authorization.voided";
    static final String VOIDED_EVENT = "AuthorizationVoided";
    static final String SENSITIVE = Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE;
    static final String PLAIN = Permission.Codes.PLATFORM_APPROVAL_TASK_READ;

    static final TestDatabase postgres = TestPostgres.database(AuthorizationBaseChangedVoidsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private record People(SignedIn requester, SignedIn beneficiary, SignedIn third) {
    }

    private People people() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        return new People(administrator(unit), administrator(unit), administrator(unit));
    }

    private SignedIn administrator(UUID unit) throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);
        grantByOperations(person, "super_admin");
        return administratorMode(signIn(person));
    }

    private String plainRole(SignedIn maker, String code) throws Exception {
        MockHttpServletResponse created = mvc.perform(post(ROLES).with(csrf()).with(session(maker)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Synthetic " + code + "\",\"enabled\":true,\"permissionCodes\":[\"" + PLAIN + "\"]}"))
                .andReturn().getResponse();
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        return JsonPath.read(created.getContentAsString(), "$.roleId");
    }

    private MockHttpServletResponse putRole(SignedIn who, String roleId, String name, String... permissionCodes) throws Exception {
        MockHttpServletResponse role = mvc.perform(get(ROLES + "/" + roleId).with(session(who))).andReturn().getResponse();
        assertThat(role.getStatus()).as(role.getContentAsString()).isEqualTo(200);
        return mvc.perform(put(ROLES + "/" + roleId).header("If-Match", role.getHeader("ETag")).with(csrf()).with(session(who))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\",\"enabled\":true,\"permissionCodes\":[\""
                        + String.join("\",\"", permissionCodes) + "\"]}")).andReturn().getResponse();
    }

    /** A direct, non-sensitive change of the role (a new name): applied at once, so the role gets a new current version. */
    private void renameDirectly(SignedIn who, String roleId, String name) throws Exception {
        MockHttpServletResponse renamed = putRole(who, roleId, name, PLAIN);
        assertThat(renamed.getStatus()).as("renaming a role without sensitive permissions applies at once: " + renamed.getContentAsString()).isEqualTo(200);
    }

    private List<String> rolesOf(SignedIn reader, SignedIn account) throws Exception {
        MockHttpServletResponse read = mvc.perform(get(ACCOUNTS + account.userId()).with(session(reader))).andReturn().getResponse();
        assertThat(read.getStatus()).as(read.getContentAsString()).isEqualTo(200);
        return JsonPath.read(read.getContentAsString(), "$.roleCodes");
    }

    private String requestRoles(SignedIn requester, SignedIn beneficiary, List<String> roleCodes) throws Exception {
        MockHttpServletResponse account = mvc.perform(get(ACCOUNTS + beneficiary.userId()).with(session(requester))).andReturn().getResponse();
        assertThat(account.getStatus()).as(account.getContentAsString()).isEqualTo(200);
        MockHttpServletResponse accepted = mvc.perform(post(ACCOUNTS + beneficiary.userId() + ":replace-roles").header("If-Match", account.getHeader("ETag"))
                .with(csrf()).with(session(requester)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleCodes\":[\"" + String.join("\",\"", roleCodes) + "\"]}")).andReturn().getResponse();
        return requestIdOf(accepted);
    }

    private String requestSensitiveRoleChange(SignedIn requester, String roleId, String name) throws Exception {
        return requestIdOf(putRole(requester, roleId, name, PLAIN, SENSITIVE));
    }

    private static String requestIdOf(MockHttpServletResponse accepted) throws Exception {
        assertThat(accepted.getStatus()).as("a sensitive change waits for a second person: " + accepted.getContentAsString()).isEqualTo(202);
        return JsonPath.read(accepted.getContentAsString(), "$.requestId");
    }

    private void confirm(SignedIn third, String requestId) throws Exception {
        MockHttpServletResponse confirmed = mvc.perform(post(REQUESTS + "/" + requestId + ":confirm").with(csrf()).with(session(third))).andReturn().getResponse();
        assertThat(confirmed.getStatus()).as("the confirmation itself is accepted: " + confirmed.getContentAsString()).isEqualTo(204);
    }

    private String statusOf(String requestId) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity");
             PreparedStatement read = identity.prepareStatement("select request_status from authorization_requests where request_id = ?::uuid")) {
            read.setString(1, requestId);
            try (ResultSet rs = read.executeQuery()) {
                assertThat(rs.next()).as("request " + requestId + " exists").isTrue();
                return rs.getString(1);
            }
        }
    }

    /** The listener runs after the commit of the confirmation; waits until the request leaves PENDING and CONFIRMED. */
    private String settledStatusOf(String requestId) {
        return Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> statusOf(requestId), status -> !status.equals("PENDING") && !status.equals("CONFIRMED"));
    }

    private Map<String, Object> requestRow(String requestId) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity");
             PreparedStatement read = identity.prepareStatement("select base_account_version_number, base_role_version_ids, subject_user_id"
                     + " from authorization_requests where request_id = ?::uuid")) {
            read.setString(1, requestId);
            try (ResultSet rs = read.executeQuery()) {
                assertThat(rs.next()).isTrue();
                Array ids = rs.getArray(2);
                return Map.of("baseAccount", rs.getObject(1) == null ? -1L : rs.getLong(1),
                        "baseRoleVersions", ids == null ? List.of() : Arrays.stream((Object[]) ids.getArray()).map(Object::toString).toList());
            }
        }
    }

    private long accountVersionOf(UUID userId) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity");
             PreparedStatement read = identity.prepareStatement("select version from user_accounts where user_id = ?")) {
            read.setObject(1, userId);
            try (ResultSet rs = read.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getLong(1);
            }
        }
    }

    private String currentRoleVersionOf(String roleId) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity");
             PreparedStatement read = identity.prepareStatement("select role_version_id::text from role_versions where role_id = ?::uuid and effective_to_at is null")) {
            read.setString(1, roleId);
            try (ResultSet rs = read.executeQuery()) {
                assertThat(rs.next()).as("role " + roleId + " has a current version").isTrue();
                return rs.getString(1);
            }
        }
    }

    private Set<String> closedRoleVersionsAmong(List<String> roleVersionIds) throws SQLException {
        Set<String> closed = new HashSet<>();
        try (Connection identity = schemaConnection("platform_identity");
             PreparedStatement read = identity.prepareStatement("select role_version_id::text from role_versions where effective_to_at is not null and role_version_id = any (?::uuid[])")) {
            read.setArray(1, identity.createArrayOf("text", roleVersionIds.toArray()));
            try (ResultSet rs = read.executeQuery()) {
                while (rs.next()) {
                    closed.add(rs.getString(1));
                }
            }
        }
        return closed;
    }

    private Map<String, Object> roleDefinition(SignedIn reader, String roleId) throws Exception {
        MockHttpServletResponse role = mvc.perform(get(ROLES + "/" + roleId).with(session(reader))).andReturn().getResponse();
        assertThat(role.getStatus()).as(role.getContentAsString()).isEqualTo(200);
        return Map.of("name", JsonPath.read(role.getContentAsString(), "$.name"), "permissionCodes", JsonPath.read(role.getContentAsString(), "$.permissionCodes"));
    }

    private List<Map<String, Object>> voidAuditsOf(String requestId) throws Exception {
        List<Map<String, Object>> found = new ArrayList<>();
        for (Map<String, Object> entry : auditQuery(null, null, VOIDED_ACTION)) {
            if (VOIDED_ACTION.equals(entry.get("action")) && requestId.equals(entry.get("subjectId"))) {
                found.add(entry);
            }
        }
        return found;
    }

    private int voidEventsOf(String requestId) throws SQLException {
        Set<String> events = new HashSet<>();
        Pattern eventId = Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]+)\"");
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                try (PreparedStatement read = registry.prepareStatement("select serialized_event from " + table + " where event_type like ? and serialized_event like ?")) {
                    read.setString(1, "%." + VOIDED_EVENT);
                    read.setString(2, "%" + requestId + "%");
                    try (ResultSet rs = read.executeQuery()) {
                        while (rs.next()) {
                            Matcher found = eventId.matcher(rs.getString(1));
                            events.add(found.find() ? found.group(1) : rs.getString(1));
                        }
                    }
                }
            }
        }
        return events.size();
    }

    private void assertVoided(String requestId) throws Exception {
        assertThat(settledStatusOf(requestId)).as("G7-02: the confirmed request whose base moved ends VOIDED, not CONFIRMED").isEqualTo("VOIDED");
        List<Map<String, Object>> audits = Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> voidAuditsOf(requestId), found -> !found.isEmpty());
        assertThat(audits).as("A-7: exactly one " + VOIDED_ACTION + " entry about the request").hasSize(1);
        assertThat(voidEventsOf(requestId)).as("A-7: exactly one " + VOIDED_EVENT + " event naming the request").isEqualTo(1);
    }

    @Test
    @DisplayName("#938 0034-G7-02 账号版本变了：给人授敏感角色的申请提交后，该账号被另行授了别的角色（账号版本变），第三人确认后申请作废为 VOIDED，写一条 platform.identity.authorization.voided 审计与一个 AuthorizationVoided 事件，申请的敏感角色没有授出")
    void aMovedAccountVersionVoidsTheRequest() throws Exception {
        People people = people();
        plainRole(people.requester(), "g702_account_side");
        List<String> wanted = new ArrayList<>(rolesOf(people.requester(), people.beneficiary()));
        wanted.add("security_auditor");
        String requestId = requestRoles(people.requester(), people.beneficiary(), wanted);
        long base = (long) requestRow(requestId).get("baseAccount");
        assertThat(base).as("ADR-0034 2.5: the request records the account version at submission").isEqualTo(accountVersionOf(people.beneficiary().userId()));

        grantByOperations(people.beneficiary().employeeId(), "g702_account_side");
        assertThat(accountVersionOf(people.beneficiary().userId())).as("premise: the account version moved after the submission").isNotEqualTo(base);
        confirm(people.third(), requestId);

        assertVoided(requestId);
        assertThat(rolesOf(people.third(), people.beneficiary())).as("the voided request granted nothing").doesNotContain("security_auditor").contains("g702_account_side");
    }

    @Test
    @DisplayName("#938 0034-G7-02 相关角色版本变了（账号授权申请）：申请授出的一个角色在提交后被直接改名（有了新的当前版本），第三人确认后申请作废为 VOIDED，审计与事件各一，申请里的角色都没有授出")
    void aMovedRequestedRoleVersionVoidsTheAccountRequest() throws Exception {
        People people = people();
        String roleId = plainRole(people.requester(), "g702_requested");
        List<String> wanted = new ArrayList<>(rolesOf(people.requester(), people.beneficiary()));
        wanted.add("g702_requested");
        wanted.add("security_auditor");
        String requestId = requestRoles(people.requester(), people.beneficiary(), wanted);
        @SuppressWarnings("unchecked")
        List<String> baseRoleVersions = (List<String>) requestRow(requestId).get("baseRoleVersions");
        String requestedVersion = currentRoleVersionOf(roleId);
        assertThat(baseRoleVersions).as("ADR-0034 2.5 (column comment of base_role_version_ids): the base holds the current version of every requested role")
                .contains(requestedVersion);

        long accountBase = (long) requestRow(requestId).get("baseAccount");

        renameDirectly(people.requester(), roleId, "Synthetic g702_requested renamed");
        assertThat(closedRoleVersionsAmong(baseRoleVersions)).as("premise: the requested role's base version is no longer current").containsExactly(requestedVersion);
        assertThat(accountVersionOf(people.beneficiary().userId())).as("premise: only the role version moved, not the account version").isEqualTo(accountBase);
        confirm(people.third(), requestId);

        assertVoided(requestId);
        assertThat(rolesOf(people.third(), people.beneficiary())).as("the voided request granted nothing").doesNotContain("security_auditor", "g702_requested");
    }

    @Test
    @DisplayName("#938 0034-G7-02 相关角色版本变了（账号授权申请拿掉的已持有角色）：受益人提交时持有角色 R，申请去掉 R 并加敏感角色；基准含 R 当时的版本；R 在提交后被直接改名（账号版本不变），第三人确认后申请作废为 VOIDED，审计与事件各一，受益人仍持有 R、没有敏感角色")
    void aMovedDroppedRoleVersionVoidsTheAccountRequest() throws Exception {
        People people = people();
        String roleId = plainRole(people.requester(), "g702_dropped");
        grantByOperations(people.beneficiary().employeeId(), "g702_dropped");
        List<String> wanted = new ArrayList<>(rolesOf(people.requester(), people.beneficiary()));
        assertThat(wanted).as("premise: the beneficiary holds the role at submission").contains("g702_dropped");
        wanted.remove("g702_dropped");
        wanted.add("security_auditor");
        String requestId = requestRoles(people.requester(), people.beneficiary(), wanted);
        @SuppressWarnings("unchecked")
        List<String> baseRoleVersions = (List<String>) requestRow(requestId).get("baseRoleVersions");
        String droppedVersion = currentRoleVersionOf(roleId);
        assertThat(baseRoleVersions).as("ADR-0034 2.5: the base holds the current version of every role held at submission, also one the request drops")
                .contains(droppedVersion);
        long accountBase = (long) requestRow(requestId).get("baseAccount");

        renameDirectly(people.requester(), roleId, "Synthetic g702_dropped renamed");
        assertThat(closedRoleVersionsAmong(baseRoleVersions)).as("premise: the dropped role's base version is no longer current").containsExactly(droppedVersion);
        assertThat(accountVersionOf(people.beneficiary().userId())).as("premise: only the role version moved, not the account version").isEqualTo(accountBase);
        confirm(people.third(), requestId);

        assertVoided(requestId);
        assertThat(rolesOf(people.third(), people.beneficiary())).as("the voided request changed nothing").contains("g702_dropped").doesNotContain("security_auditor");
    }

    @Test
    @DisplayName("#938 0034-G7-02 相关角色版本变了（角色修改申请）：给角色加敏感权限的申请提交后，该角色被直接改名，第三人确认后申请作废为 VOIDED，审计与事件各一，角色定义仍是改名后的、不含敏感权限")
    @SuppressWarnings("unchecked")
    void aMovedRoleVersionVoidsTheRoleRequest() throws Exception {
        People people = people();
        String roleId = plainRole(people.requester(), "g702_role_side");
        String baseVersion = currentRoleVersionOf(roleId);
        String requestId = requestSensitiveRoleChange(people.requester(), roleId, "Synthetic g702_role_side");
        assertThat(requestRow(requestId).get("baseRoleVersions")).as("ADR-0034 2.5: a role change records that role's current version as its one base").isEqualTo(List.of(baseVersion));

        renameDirectly(people.requester(), roleId, "Synthetic g702_role_side renamed");
        confirm(people.third(), requestId);

        assertVoided(requestId);
        Map<String, Object> role = roleDefinition(people.third(), roleId);
        assertThat(role.get("name")).as("the direct change stays").isEqualTo("Synthetic g702_role_side renamed");
        assertThat((List<Object>) role.get("permissionCodes")).as("the voided request changed nothing").containsExactly(PLAIN);
    }

    @Test
    @DisplayName("#938 0034-G7-02 对照：基准没变的账号授权申请，第三人确认后照常生效（敏感角色授出），不作废，没有作废审计与事件")
    void anUnmovedBaseStillApplies() throws Exception {
        People people = people();
        List<String> wanted = new ArrayList<>(rolesOf(people.requester(), people.beneficiary()));
        wanted.add("security_auditor");
        String requestId = requestRoles(people.requester(), people.beneficiary(), wanted);

        confirm(people.third(), requestId);

        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(rolesOf(people.third(), people.beneficiary())).contains("security_auditor"));
        assertThat(statusOf(requestId)).as("an applied request is not voided").isEqualTo("CONFIRMED");
        assertThat(voidAuditsOf(requestId)).isEmpty();
        assertThat(voidEventsOf(requestId)).isZero();
    }

    @Test
    @DisplayName("#938 0034-G7-02 边界：提交后只有与申请无关的角色有了新版本（该账号既不持有、申请也不涉及），确认后照常生效，不作废")
    void anUnrelatedRoleMovingDoesNotVoid() throws Exception {
        People people = people();
        String unrelated = plainRole(people.requester(), "g702_unrelated");
        List<String> wanted = new ArrayList<>(rolesOf(people.requester(), people.beneficiary()));
        wanted.add("security_auditor");
        String requestId = requestRoles(people.requester(), people.beneficiary(), wanted);

        renameDirectly(people.requester(), unrelated, "Synthetic g702_unrelated renamed");
        confirm(people.third(), requestId);

        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(rolesOf(people.third(), people.beneficiary())).contains("security_auditor"));
        assertThat(statusOf(requestId)).isEqualTo("CONFIRMED");
        assertThat(voidAuditsOf(requestId)).isEmpty();
        assertThat(voidEventsOf(requestId)).isZero();
    }
}
