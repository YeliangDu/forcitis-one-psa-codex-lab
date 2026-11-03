package com.forcitis.one.platform.identity;

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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** #1032 (ADR-0034 decision 2 item 5, last clause): a confirmed role change whose apply is refused (here: another role took the new name after the request was made) ends VOIDED, not CONFIRMED. */
class AuthorizationApplyRefusedVoidsIT extends AccountsAcceptanceSupport {

    static final String ROLES = "/api/v1/platform/roles";
    static final String REQUESTS = "/api/v1/platform/authorization-requests";
    static final String VOIDED_ACTION = "platform.identity.authorization.voided";
    static final String SENSITIVE = Permission.Codes.PLATFORM_NUMBERING_RULE_APPROVE;
    static final String PLAIN = Permission.Codes.PLATFORM_APPROVAL_TASK_READ;

    static final TestDatabase postgres = TestPostgres.database(AuthorizationApplyRefusedVoidsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private SignedIn administrator(UUID unit) throws Exception {
        UUID person = newEmployeeId();
        employee(person, unit, null);
        grantByOperations(person, "super_admin");
        return administratorMode(signIn(person));
    }

    private String plainRole(SignedIn maker, String code) throws Exception {
        MockHttpServletResponse created = mvc.perform(post(ROLES).with(csrf()).with(session(maker)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Synthetic " + code + "\",\"enabled\":true,\"permissionCodes\":[\"" + PLAIN + "\"]}")).andReturn().getResponse();
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        return JsonPath.read(created.getContentAsString(), "$.roleId");
    }

    private MockHttpServletResponse putRole(SignedIn who, String roleId, String name, String... permissionCodes) throws Exception {
        MockHttpServletResponse role = mvc.perform(get(ROLES + "/" + roleId).with(session(who))).andReturn().getResponse();
        return mvc.perform(put(ROLES + "/" + roleId).header("If-Match", role.getHeader("ETag")).with(csrf()).with(session(who)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"enabled\":true,\"permissionCodes\":[\"" + String.join("\",\"", permissionCodes) + "\"]}")).andReturn().getResponse();
    }

    private String statusOf(String requestId) throws SQLException {
        try (Connection identity = schemaConnection("platform_identity");
             PreparedStatement read = identity.prepareStatement("select request_status from authorization_requests where request_id = ?::uuid")) {
            read.setString(1, requestId);
            try (ResultSet rs = read.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString(1);
            }
        }
    }

    @Test
    @DisplayName("#1032 the new name is taken by another role between the request and the confirmation: the apply is refused and the request ends VOIDED with one void audit entry; the role keeps its definition")
    void aRefusedApplyVoidsTheRequest() throws Exception {
        UUID unit = newOrgUnitId();
        orgUnit(unit, null, null);
        SignedIn requester = administrator(unit);
        SignedIn third = administrator(unit);
        String roleA = plainRole(requester, "i1032_a");
        String roleB = plainRole(requester, "i1032_b");
        MockHttpServletResponse accepted = putRole(requester, roleA, "Synthetic i1032 renamed", PLAIN, SENSITIVE);
        assertThat(accepted.getStatus()).as(accepted.getContentAsString()).isEqualTo(202);
        String requestId = JsonPath.read(accepted.getContentAsString(), "$.requestId");
        MockHttpServletResponse renamed = putRole(requester, roleB, "Synthetic i1032 renamed", PLAIN);
        assertThat(renamed.getStatus()).as("the other role takes the name at once: " + renamed.getContentAsString()).isEqualTo(200);

        MockHttpServletResponse confirmed = mvc.perform(post(REQUESTS + "/" + requestId + ":confirm").with(csrf()).with(session(third))).andReturn().getResponse();
        assertThat(confirmed.getStatus()).isEqualTo(204);

        String settled = Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(100))
                .until(() -> statusOf(requestId), status -> !status.equals("PENDING") && !status.equals("CONFIRMED"));
        assertThat(settled).isEqualTo("VOIDED");
        List<Map<String, Object>> audits = auditQuery(null, null, VOIDED_ACTION).stream().filter(entry -> requestId.equals(entry.get("subjectId"))).toList();
        assertThat(audits).hasSize(1);
        MockHttpServletResponse roleNow = mvc.perform(get(ROLES + "/" + roleA).with(session(third))).andReturn().getResponse();
        assertThat((List<String>) JsonPath.read(roleNow.getContentAsString(), "$.permissionCodes")).as("nothing was applied").doesNotContain(SENSITIVE);
    }
}
