package com.forcitis.one.scenario;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.identity.IdentityIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("B1-scope: org units answer only within the caller's scope (ADR-0026 section 1, RULE-350/351/352)")
class OrgUnitScopeIntegrationTest extends IdentityIntegrationTest {

    static final String UNITS = "/api/v1/organization/org-units";

    @BeforeEach
    void threeUnits() {
        jdbc.sql("""
                insert into organization_workforce.org_units (id, name, parent_org_unit_id, status) values
                    (10, 'Own department', null, 'ACTIVE'), (11, 'Below it', 10, 'ACTIVE'), (20, 'Another department', null, 'ACTIVE')
                on conflict do nothing
                """).update();
        jdbc.sql("""
                insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values
                    (901, 'E901', 'In own department', 'EMPLOYED'), (902, 'E902', 'In another department', 'EMPLOYED')
                on conflict do nothing
                """).update();
        jdbc.sql("insert into organization_workforce.assignments (employee_id, org_unit_id) values (901, 10), (902, 20) on conflict do nothing").update();
    }

    @AfterEach
    void removeTheUnits() {
        jdbc.sql("delete from organization_workforce.assignments where employee_id in (901, 902)").update();
        jdbc.sql("delete from organization_workforce.employees where id in (901, 902)").update();
        jdbc.sql("delete from organization_workforce.org_units where id in (10, 11, 20)").update();
    }

    private void customRole(String code, String scope) {
        jdbc.sql("insert into platform_identity.roles (role_id, code, name, kind, status, access_scope, is_administrative) values (uuidv7(), :code, :code, 'CUSTOM', 'ENABLED', :scope, false)")
                .param("code", code).param("scope", scope).update();
        jdbc.sql("insert into platform_identity.role_permissions (role_id, permission_code) select role_id, 'organization.workforce:org-unit:read' from platform_identity.roles where code = :code")
                .param("code", code).update();
    }

    private String sessionWithRole(long user, String role) throws Exception {
        createAccount(user, "employee", role);
        return loginWithWecom(user);
    }

    private MvcResult read(String session, String path) throws Exception {
        return mvc.perform(get(UNITS + path).cookie(new Cookie("SESSION", session))).andReturn();
    }

    private List<String> listed(String session) throws Exception {
        MvcResult result = read(session, "");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return body(result).valueStream().map(unit -> unit.path("orgUnitId").asString()).sorted().toList();
    }

    private List<AuditEntry> denials() {
        return recordedIndependently().stream().filter(entry -> entry.action().equals("organization.workforce.org-unit.denied")).toList();
    }

    @Test
    @DisplayName("B1-scope a role whose scope is the person alone sees no department; reading one by id is a 404 and is audited")
    void selfSeesNoDepartment() throws Exception {
        customRole("scope-self", "SELF");
        String session = sessionWithRole(21, "scope-self");

        assertThat(listed(session)).isEmpty();
        assertThat(read(session, "/10").getResponse().getStatus()).isEqualTo(404);
        assertThat(denials()).singleElement().satisfies(entry -> {
            assertThat(entry.actorId()).isEqualTo(TestAccounts.account(21));
            assertThat(entry.subjectKey()).isEqualTo("org-unit:10");
        });
    }

    @Test
    @DisplayName("B1-scope a role scoped to the own department sees that department only: below it and the others are 404 and audited")
    void ownDepartmentOnly() throws Exception {
        customRole("scope-own", "OWN_ORG_UNIT");
        String session = sessionWithRole(22, "scope-own");

        assertThat(listed(session)).containsExactly("10");
        assertThat(read(session, "/10").getResponse().getStatus()).isEqualTo(200);
        assertThat(read(session, "/11").getResponse().getStatus()).isEqualTo(404);
        assertThat(read(session, "/20").getResponse().getStatus()).isEqualTo(404);
        assertThat(denials()).extracting(AuditEntry::subjectKey).containsExactlyInAnyOrder("org-unit:11", "org-unit:20");
    }

    @Test
    @DisplayName("B1-scope a role scoped to the own department and below sees the subtree, and the detail names only what the caller may see")
    void subtree() throws Exception {
        customRole("scope-subtree", "OWN_ORG_UNIT_SUBTREE");
        String session = sessionWithRole(23, "scope-subtree");

        assertThat(listed(session)).containsExactly("10", "11");
        assertThat(read(session, "/20").getResponse().getStatus()).isEqualTo(404);
        assertThat(body(read(session, "/10")).path("orgUnitAndBelowIds").valueStream().map(id -> id.asString()).sorted().toList())
                .containsExactly("10", "11");
    }

    @Test
    @DisplayName("B1-scope when the scope cannot be worked out the answer is a refusal, never the full list")
    void scopeFailureRefuses() throws Exception {
        customRole("scope-own-2", "OWN_ORG_UNIT");
        String session = sessionWithRole(24, "scope-own-2");
        assertThat(listed(session)).containsExactly("10");

        when(orgDirectory.findEmployee(new EmployeeId(24))).thenThrow(new IllegalStateException("organisation unavailable"));

        MvcResult list = read(session, "");
        assertThat(list.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(list).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_ACCESS_SCOPE_UNAVAILABLE");
        assertThat(read(session, "/10").getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("B1-scope a person with no department gets nothing from a department scope")
    void unknownDepartmentSeesNothing() throws Exception {
        customRole("scope-own-3", "OWN_ORG_UNIT");
        String session = sessionWithRole(25, "scope-own-3");
        assertThat(listed(session)).containsExactly("10");

        when(orgDirectory.findEmployee(new EmployeeId(25))).thenReturn(java.util.Optional.empty());

        assertThat(read(session, "").getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("B1-scope the organisation data administrator, whose scope is everything, is not affected")
    void orgDataAdministratorSeesEverything() throws Exception {
        createAccount(31, "org_data_admin");
        String session = loginWithWecom(31);

        assertThat(listed(session)).containsExactly("10", "11", "20");
        assertThat(read(session, "/20").getResponse().getStatus()).isEqualTo(200);
        assertThat(denials()).isEmpty();
    }

    private static final String BASE = "/api/v1/organization";

    private MvcResult readAttribution(String session, String path) throws Exception {
        return mvc.perform(get(BASE + path).cookie(new Cookie("SESSION", session))).andReturn();
    }

    @Test
    @DisplayName("B1-scope the legal entity of a unit or an employee outside the caller's scope is a 404 like a missing one and is audited; inside it is readable")
    void legalEntityAttributionFollowsTheScope() throws Exception {
        customRole("scope-own-4", "OWN_ORG_UNIT");
        String session = sessionWithRole(26, "scope-own-4");

        assertThat(readAttribution(session, "/org-unit-legal-entities/10").getResponse().getStatus()).isEqualTo(200);
        assertThat(readAttribution(session, "/employee-legal-entities/901").getResponse().getStatus()).isEqualTo(200);
        MvcResult otherUnit = readAttribution(session, "/org-unit-legal-entities/20");
        MvcResult otherEmployee = readAttribution(session, "/employee-legal-entities/902");
        MvcResult missingUnit = readAttribution(session, "/org-unit-legal-entities/999");

        assertThat(otherUnit.getResponse().getStatus()).isEqualTo(404);
        assertThat(otherEmployee.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(otherUnit).path("errorCode").asString()).as("the same answer as for a unit that does not exist")
                .isEqualTo(body(missingUnit).path("errorCode").asString());
        assertThat(recordedIndependently()).filteredOn(entry -> entry.action().equals("organization.workforce.legal-entity-attribution.denied"))
                .extracting(AuditEntry::subjectKey).containsExactlyInAnyOrder("org_unit:20", "employee:902");
    }

    @Test
    @DisplayName("B1-scope a person-only scope reads no legal entity attribution at all, and the organisation data administrator reads every one")
    void legalEntityAttributionForSelfAndForTheAdministrator() throws Exception {
        customRole("scope-self-2", "SELF");
        String self = sessionWithRole(27, "scope-self-2");
        createAccount(32, "org_data_admin");
        String administrator = loginWithWecom(32);

        assertThat(readAttribution(self, "/org-unit-legal-entities/10").getResponse().getStatus()).isEqualTo(404);
        assertThat(readAttribution(self, "/employee-legal-entities/901").getResponse().getStatus()).isEqualTo(404);
        assertThat(readAttribution(administrator, "/org-unit-legal-entities/20").getResponse().getStatus()).isEqualTo(200);
        assertThat(readAttribution(administrator, "/employee-legal-entities/902").getResponse().getStatus()).isEqualTo(200);
    }
}
