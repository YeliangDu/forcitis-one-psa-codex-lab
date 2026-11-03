package com.forcitis.one.platform.identity;

import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.platform.audit.api.AuditEntry;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("SensitiveGrantTwoPerson and SelfGrantForbidden: ADR-0026 section 4, 6; ADR-0027 section 3 item 2 (I-12)")
class SensitiveGrantTwoPersonIntegrationTest extends IdentityIntegrationTest {

    private MvcResult replaceRoles(String session, long target, String... roleCodes) throws Exception {
        String codes = String.join("\",\"", roleCodes);
        return mvc.perform(post("/api/v1/platform/user-accounts/" + accountOrNone(target) + ":replace-roles").header("If-Match", accountVersion(target)).with(xsrf()).cookie(new Cookie("SESSION", session))
                .contentType(MediaType.APPLICATION_JSON).content("{\"roleCodes\":[\"" + codes + "\"]}")).andReturn();
    }

    private MvcResult createRole(String session, String code, String permission) throws Exception {
        return mvc.perform(post("/api/v1/platform/roles").with(xsrf()).cookie(new Cookie("SESSION", session)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"" + code + "\",\"enabled\":true,\"permissionCodes\":[\"" + permission + "\"]}")).andReturn();
    }

    private MvcResult decide(String session, String requestId, String verb) throws Exception {
        return mvc.perform(post("/api/v1/platform/authorization-requests/" + requestId + ":" + verb).with(xsrf()).cookie(new Cookie("SESSION", session))).andReturn();
    }

    private String requestIdOf(MvcResult accepted) throws Exception {
        assertThat(accepted.getResponse().getStatus()).as(accepted.getResponse().getContentAsString()).isEqualTo(202);
        return body(accepted).path("requestId").asString();
    }

    private boolean holds(long user, String roleCode) {
        return jdbc.sql("select count(*) from platform_identity.account_role_grants ar join platform_identity.roles r on r.role_id = ar.role_id where ar.effective_to_at is null and ar.user_id = (select ua.user_id from platform_identity.user_accounts ua where ua.employee_id = :u) and r.code = :c")
                .param("u", TestAccounts.employee(user).value()).param("c", roleCode).query(Long.class).single() == 1;
    }

    @Test
    @DisplayName("giving a sensitive role waits: nothing changes until another administrator confirms; then the grant is applied")
    void sensitiveGrantNeedsASecondPerson() throws Exception {
        String first = administratorSession(9);
        String second = administratorSession(10);
        loginWithWecom(1001);

        String requestId = requestIdOf(replaceRoles(first, 1001, "employee", "security_auditor"));

        assertThat(holds(1001, "security_auditor")).isFalse();
        assertThat(decide(first, requestId, "confirm").getResponse().getStatus()).as("the requester cannot confirm").isEqualTo(403);
        assertThat(holds(1001, "security_auditor")).isFalse();
        assertThat(decide(second, requestId, "confirm").getResponse().getStatus()).isEqualTo(204);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(holds(1001, "security_auditor")).isTrue());
        assertThat(decide(second, requestId, "confirm").getResponse().getStatus()).as("decided once").isEqualTo(409);
    }

    @Test
    @DisplayName("the person who would receive the roles cannot confirm their own grant")
    void theRecipientCannotConfirm() throws Exception {
        String first = administratorSession(9);
        String second = administratorSession(10);

        String requestId = requestIdOf(replaceRoles(first, 10, "employee", "super_admin", "security_auditor"));

        MvcResult byRecipient = decide(second, requestId, "confirm");
        assertThat(byRecipient.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(byRecipient).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED");
    }

    @Test
    @DisplayName("I-12 an administrator cannot give themselves a role, and so cannot reach the audit trail through security_auditor")
    void noSelfGrantAndNoWayToTheAuditTrail() throws Exception {
        String administrator = administratorSession(9);

        MvcResult self = replaceRoles(administrator, 9, "employee", "super_admin", "security_auditor");

        assertThat(self.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(self).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN");
        assertThat(holds(9, "security_auditor")).isFalse();
        assertThat(recordedIndependently()).extracting(AuditEntry::action).contains("platform.identity.grant.refused");
        assertThat(mvc.perform(get("/api/v1/platform/audit-entries:export").cookie(new Cookie("SESSION", administrator))).andReturn().getResponse().getStatus())
                .as("the administrator mode carries no audit permission").isEqualTo(403);
    }

    @Test
    @DisplayName("the detour through a new role is closed too: a role with an audit permission waits for a second person, who is not the requester, and still cannot be handed to oneself")
    void noDetourThroughANewRole() throws Exception {
        String first = administratorSession(9);
        String second = administratorSession(10);

        String requestId = requestIdOf(createRole(first, "trail-reader", "platform.audit:audit-entry:export"));

        assertThat(jdbc.sql("select count(*) from platform_identity.roles where code = 'trail-reader'").query(Long.class).single()).isZero();
        assertThat(decide(first, requestId, "confirm").getResponse().getStatus()).isEqualTo(403);
        assertThat(decide(second, requestId, "confirm").getResponse().getStatus()).isEqualTo(204);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(
                () -> assertThat(jdbc.sql("select count(*) from platform_identity.roles where code = 'trail-reader'").query(Long.class).single()).isEqualTo(1));
        assertThat(replaceRoles(first, 9, "employee", "super_admin", "trail-reader").getResponse().getStatus()).isEqualTo(403);
        assertThat(holds(9, "trail-reader")).isFalse();
    }

    @Test
    @DisplayName("adding a sensitive permission to an existing role waits; nobody changes the definition of a role they hold; changes without one go through at once")
    void roleDefinitions() throws Exception {
        String first = administratorSession(9);
        String second = administratorSession(10);
        assertThat(createRole(first, "reader", "platform.approval:task:read").getResponse().getStatus()).isEqualTo(201);
        java.util.UUID reader = jdbc.sql("select role_id from platform_identity.roles where code = 'reader'").query(java.util.UUID.class).single();

        MvcResult sensitive = mvc.perform(put("/api/v1/platform/roles/" + reader).header("If-Match", roleVersion(reader)).with(xsrf()).cookie(new Cookie("SESSION", first))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"reader\",\"enabled\":true,\"permissionCodes\":[\"platform.numbering:numbering-rule:approve\"]}")).andReturn();
        String requestId = requestIdOf(sensitive);
        assertThat(TestRoles.currentPermissionCodes(jdbc, reader)).containsExactly("platform.approval:task:read");
        assertThat(decide(second, requestId, "confirm").getResponse().getStatus()).isEqualTo(204);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(TestRoles.currentPermissionCodes(jdbc, reader))
                .containsExactly("platform.numbering:numbering-rule:approve"));
        assertThat(jdbc.sql("""
                select count(*) from platform_identity.role_versions
                 where role_id = :id and effective_to_at is null and authorization_request_id = cast(:request as uuid) and recorded_by is not null
                """).param("id", reader).param("request", requestId).query(Long.class).single())
                .as("ADR-0034-IMPL A-5: the version applied after the confirmation names the request and its author").isEqualTo(1);

        createAccount(10, "reader");
        MvcResult heldByCaller = mvc.perform(put("/api/v1/platform/roles/" + reader).header("If-Match", roleVersion(reader)).with(xsrf()).cookie(new Cookie("SESSION", loginAgain(second)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"renamed\",\"enabled\":true,\"permissionCodes\":[]}")).andReturn();
        assertThat(heldByCaller.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(heldByCaller).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_OWN_ROLE_DEFINITION_FORBIDDEN");
    }

    private String loginAgain(String session) throws Exception {
        accessCache.evictAll();
        return session;
    }

    @Test
    @DisplayName("a request that is refused changes nothing; a non-sensitive grant needs nobody else")
    void rejectionAndOrdinaryGrants() throws Exception {
        String first = administratorSession(9);
        String second = administratorSession(10);
        loginWithWecom(1001);
        createRole(first, "planner", "platform.approval:task:read");

        assertThat(replaceRoles(first, 1001, "employee", "planner").getResponse().getStatus()).as("no sensitive role involved").isEqualTo(204);
        assertThat(holds(1001, "planner")).isTrue();

        String requestId = requestIdOf(replaceRoles(first, 1001, "employee", "planner", "security_auditor"));
        assertThat(decide(second, requestId, "reject").getResponse().getStatus()).isEqualTo(204);
        assertThat(decide(second, requestId, "confirm").getResponse().getStatus()).isEqualTo(409);
        assertThat(holds(1001, "security_auditor")).isFalse();
    }

    @Test
    @DisplayName("B1-R1 separation-hold 敏感: a custom role carrying separation-hold:approve waits for a second person like any sensitive permission")
    void separationHoldApproveIsSensitive() throws Exception {
        String first = administratorSession(9);

        MvcResult created = createRole(first, "hold-approver", "organization.workforce:separation-hold:approve");

        requestIdOf(created);
        assertThat(jdbc.sql("select count(*) from platform_identity.roles where code = 'hold-approver'").query(Long.class).single()).isZero();
        assertThat(com.forcitis.one.platform.identity.domain.SensitivePermissions.CODES).contains("organization.workforce:separation-hold:approve");
    }

    @Test
    @DisplayName("B1-R1 a holder of the role being changed cannot confirm adding a sensitive permission to it; a third administrator can")
    void aHolderOfTheRoleCannotConfirmItsChange() throws Exception {
        String first = administratorSession(9);
        String holder = administratorSession(10);
        String third = administratorSession(11);
        assertThat(createRole(first, "reader3", "platform.approval:task:read").getResponse().getStatus()).isEqualTo(201);
        java.util.UUID role = jdbc.sql("select role_id from platform_identity.roles where code = 'reader3'").query(java.util.UUID.class).single();
        createAccount(10, "reader3");
        accessCache.evictAll();

        String requestId = requestIdOf(mvc.perform(put("/api/v1/platform/roles/" + role).header("If-Match", roleVersion(role)).with(xsrf()).cookie(new Cookie("SESSION", first))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"reader3\",\"enabled\":true,\"permissionCodes\":[\"platform.numbering:numbering-rule:approve\"]}")).andReturn());

        MvcResult byHolder = decide(holder, requestId, "confirm");
        assertThat(byHolder.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(byHolder).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED");
        assertThat(decide(third, requestId, "confirm").getResponse().getStatus()).isEqualTo(204);
    }
}
