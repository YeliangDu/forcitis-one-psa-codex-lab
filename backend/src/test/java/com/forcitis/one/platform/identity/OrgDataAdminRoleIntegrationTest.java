package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.platform.identity.domain.SensitivePermissions;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OrgDataAdminRole, AccessDeniedAudit and PrivilegedRequestAudit: ADR-0027 section 2 item 1, ADR-0026 section 5 item 4 and section 6 item 5")
class OrgDataAdminRoleIntegrationTest extends IdentityIntegrationTest {

    @Test
    @DisplayName("the organisation data administrator does the day-to-day master data in an ordinary session and holds nothing sensitive or posting-like")
    void organisationDataAdministratorRole() throws Exception {
        createAccount(20, "org_data_admin");
        String session = loginWithWecom(20);

        List<String> permissions = body(mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", session))).andReturn())
                .path("permissionCodes").valueStream().map(n -> n.asString()).toList();

        assertThat(permissions).contains(Permission.Codes.ORGANIZATION_WORKFORCE_SYNC_RUN_READ, Permission.Codes.ORGANIZATION_WORKFORCE_SYNC_RUN_EXECUTE,
                Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE, Permission.Codes.PLATFORM_NUMBERING_RULE_UPDATE,
                Permission.Codes.ORGANIZATION_ATTENDANCE_WORK_CALENDAR_UPDATE);
        assertThat(permissions).as("no sensitive permission and no confirmation of a template").doesNotContainAnyElementsOf(SensitivePermissions.CODES);
        assertThat(permissions).noneMatch(code -> code.startsWith("cost.") || code.startsWith("delivery.") || code.contains(":approve") && !code.startsWith("platform.approval"));
    }

    @Test
    @DisplayName("every refusal of a signed-in person is an audit entry, written once a minute per person and resource")
    void everyRefusalIsAudited() throws Exception {
        String session = loginWithWecom(1001);
        String path = "/api/v1/platform/roles";

        assertThat(mvc.perform(get(path).cookie(new Cookie("SESSION", session))).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get(path).cookie(new Cookie("SESSION", session))).andReturn().getResponse().getStatus()).isEqualTo(403);

        assertThat(recordedIndependently()).filteredOn(entry -> entry.action().equals("platform.identity.access.denied")).hasSize(1)
                .singleElement().satisfies(entry -> {
                    assertThat(entry.actorId().value()).isEqualTo(1001);
                    assertThat(entry.subjectKey()).startsWith("operation:RoleController.list");
                });
    }

    @Test
    @DisplayName("an anonymous request is a 401 and not an audit entry about a person")
    void anonymousRequestsAreNotAuditedAsPeople() throws Exception {
        assertThat(mvc.perform(get("/api/v1/platform/roles")).andReturn().getResponse().getStatus()).isEqualTo(401);

        assertThat(recordedIndependently()).extracting(AuditEntry::action).doesNotContain("platform.identity.access.denied");
    }

    @Test
    @DisplayName("in the administrator mode every write and every read of the authorisation data is a privileged.request entry; ordinary reads are not")
    void privilegedRequestsAreAudited() throws Exception {
        String administrator = administratorSession(9);

        mvc.perform(get("/api/v1/platform/roles").cookie(new Cookie("SESSION", administrator))).andReturn();
        mvc.perform(get(SessionLifecycleIntegrationTest.CURRENT).cookie(new Cookie("SESSION", administrator))).andReturn();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/platform/user-accounts/404:disable").with(xsrf())
                .cookie(new Cookie("SESSION", administrator))).andReturn();

        assertThat(recordedIndependently()).filteredOn(entry -> entry.action().equals("platform.identity.privileged.request"))
                .extracting(AuditEntry::subjectKey)
                .containsExactly("request:GET /api/v1/platform/roles", "request:POST /api/v1/platform/user-accounts/404:disable");
    }
}
