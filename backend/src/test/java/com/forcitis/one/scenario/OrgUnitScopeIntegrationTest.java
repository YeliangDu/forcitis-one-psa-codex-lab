package com.forcitis.one.scenario;

import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlEmployee;
import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlOrgUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.UUID;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.platform.identity.TestRoles;
import com.forcitis.one.platform.identity.IdentityIntegrationTest;
import com.forcitis.one.platform.audit.api.AuditEntry;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;

@DisplayName("B1-scope: org units answer only within the caller's scope (ADR-0026 section 1, RULE-350/351/352)")
class OrgUnitScopeIntegrationTest extends IdentityIntegrationTest {

    static final String UNITS = "/api/v1/organization/org-units";

    @BeforeEach
    void threeUnits() {
        jdbc.sql("""
                insert into organization_workforce.org_units (id, name, parent_org_unit_id, status) values
                    (%1$s, 'Own department', null, 'ACTIVE'), (%2$s, 'Below it', %1$s, 'ACTIVE'), (%3$s, 'Another department', null, 'ACTIVE')
                on conflict do nothing
                """.formatted(sqlOrgUnit(10), sqlOrgUnit(11), sqlOrgUnit(20))).update();
        jdbc.sql("""
                insert into organization_workforce.employees (id, employee_code, display_name, employment_status) values
                    (%s, 'E901', 'In own department', 'EMPLOYED'), (%s, 'E902', 'In another department', 'EMPLOYED')
                on conflict do nothing
                """.formatted(sqlEmployee(901), sqlEmployee(902))).update();
        jdbc.sql("insert into organization_workforce.assignments (employee_id, org_unit_id) values (%s, %s), (%s, %s) on conflict do nothing"
                .formatted(sqlEmployee(901), sqlOrgUnit(10), sqlEmployee(902), sqlOrgUnit(20))).update();
    }

    @AfterEach
    void removeTheUnits() {
        jdbc.sql("delete from organization_workforce.assignments where employee_id in (%s, %s)".formatted(sqlEmployee(901), sqlEmployee(902))).update();
        jdbc.sql("delete from organization_workforce.employees where id in (%s, %s)".formatted(sqlEmployee(901), sqlEmployee(902))).update();
        jdbc.sql("delete from organization_workforce.org_units where id in (%s, %s, %s)".formatted(sqlOrgUnit(10), sqlOrgUnit(11), sqlOrgUnit(20))).update();
    }

    private static String unit(long n) {
        return TestOrgIds.orgUnit(n).value().toString();
    }

    private static String employee(long n) {
        return TestAccounts.employee(n).value().toString();
    }

    private void customRole(String code, String scope) {
        TestRoles.insertCustom(jdbc, code, code, "ENABLED", scope, "organization.workforce:org-unit:read");
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
        assertThat(read(session, "/" + unit(10)).getResponse().getStatus()).isEqualTo(404);
        assertThat(denials()).singleElement().satisfies(entry -> {
            assertThat(entry.actorId()).isEqualTo(TestAccounts.account(21));
            assertThat(entry.subject()).isEqualTo(AuditSubject.of(AuditSubjectKind.ORG_UNIT, UUID.fromString(unit(10))));
        });
    }

    @Test
    @DisplayName("B1-scope a role scoped to the own department sees that department only: below it and the others are 404 and audited")
    void ownDepartmentOnly() throws Exception {
        customRole("scope-own", "OWN_ORG_UNIT");
        String session = sessionWithRole(22, "scope-own");

        assertThat(listed(session)).containsExactly(unit(10));
        assertThat(read(session, "/" + unit(10)).getResponse().getStatus()).isEqualTo(200);
        assertThat(read(session, "/" + unit(11)).getResponse().getStatus()).isEqualTo(404);
        assertThat(read(session, "/" + unit(20)).getResponse().getStatus()).isEqualTo(404);
        assertThat(denials()).extracting(AuditEntry::subject).containsExactlyInAnyOrder(AuditSubject.of(AuditSubjectKind.ORG_UNIT, UUID.fromString(unit(11))), AuditSubject.of(AuditSubjectKind.ORG_UNIT, UUID.fromString(unit(20))));
    }

    @Test
    @DisplayName("B1-scope a role scoped to the own department and below sees the subtree, and the detail names only what the caller may see")
    void subtree() throws Exception {
        customRole("scope-subtree", "OWN_ORG_UNIT_SUBTREE");
        String session = sessionWithRole(23, "scope-subtree");

        assertThat(listed(session)).containsExactlyInAnyOrder(unit(10), unit(11));
        assertThat(read(session, "/" + unit(20)).getResponse().getStatus()).isEqualTo(404);
        assertThat(body(read(session, "/" + unit(10))).path("orgUnitAndBelowIds").valueStream().map(id -> id.asString()).sorted().toList())
                .containsExactlyInAnyOrder(unit(10), unit(11));
    }

    @Test
    @DisplayName("B1-scope when the scope cannot be worked out the answer is a refusal, never the full list")
    void scopeFailureRefuses() throws Exception {
        customRole("scope-own-2", "OWN_ORG_UNIT");
        String session = sessionWithRole(24, "scope-own-2");
        assertThat(listed(session)).containsExactly(unit(10));

        when(orgDirectory.findEmployee(TestAccounts.employee(24))).thenThrow(new IllegalStateException("organisation unavailable"));

        MvcResult list = read(session, "");
        assertThat(list.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(list).path("errorCode").asString()).isEqualTo("PLATFORM_IDENTITY_ACCESS_SCOPE_UNAVAILABLE");
        // #730 comment 6606893624: a detail never refuses with 403 (that would tell the caller the id exists); it answers like a missing id
        assertThat(read(session, "/" + unit(10)).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("B1-scope a person with no department gets nothing from a department scope")
    void unknownDepartmentSeesNothing() throws Exception {
        customRole("scope-own-3", "OWN_ORG_UNIT");
        String session = sessionWithRole(25, "scope-own-3");
        assertThat(listed(session)).containsExactly(unit(10));

        when(orgDirectory.findEmployee(TestAccounts.employee(25))).thenReturn(java.util.Optional.empty());

        // #730 comment 6606893624: the resolver answered "nothing" (no employee), so the scope is empty: list 200 with zero rows, detail 404
        assertThat(listed(session)).isEmpty();
        assertThat(read(session, "/" + unit(10)).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("B1-scope the organisation data administrator, whose scope is everything, is not affected")
    void orgDataAdministratorSeesEverything() throws Exception {
        createAccount(31, "org_data_admin");
        String session = loginWithWecom(31);

        assertThat(listed(session)).containsExactlyInAnyOrder(unit(10), unit(11), unit(20));
        assertThat(read(session, "/" + unit(20)).getResponse().getStatus()).isEqualTo(200);
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

        assertThat(readAttribution(session, "/org-unit-legal-entities/" + unit(10)).getResponse().getStatus()).isEqualTo(200);
        assertThat(readAttribution(session, "/employee-legal-entities/" + employee(901)).getResponse().getStatus()).isEqualTo(200);
        MvcResult otherUnit = readAttribution(session, "/org-unit-legal-entities/" + unit(20));
        MvcResult otherEmployee = readAttribution(session, "/employee-legal-entities/" + employee(902));
        MvcResult missingUnit = readAttribution(session, "/org-unit-legal-entities/" + unit(999));

        assertThat(otherUnit.getResponse().getStatus()).isEqualTo(404);
        assertThat(otherEmployee.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(otherUnit).path("errorCode").asString()).as("the same answer as for a unit that does not exist")
                .isEqualTo(body(missingUnit).path("errorCode").asString());
        assertThat(recordedIndependently()).filteredOn(entry -> entry.action().equals("organization.workforce.legal-entity-attribution.denied"))
                .extracting(AuditEntry::subject).containsExactlyInAnyOrder(AuditSubject.of(AuditSubjectKind.ORG_UNIT, UUID.fromString(unit(20))), AuditSubject.of(AuditSubjectKind.EMPLOYEE, UUID.fromString(employee(902))));
        assertThat(recordedIndependently()).filteredOn(entry -> entry.action().equals("organization.workforce.legal-entity-attribution.denied"))
                .as("each refusal is audited as the person who asked").extracting(AuditEntry::actorId).containsOnly(TestAccounts.account(26));
    }

    @Test
    @DisplayName("B1-scope a person-only scope reads no legal entity attribution at all, and the organisation data administrator reads every one")
    void legalEntityAttributionForSelfAndForTheAdministrator() throws Exception {
        customRole("scope-self-2", "SELF");
        String self = sessionWithRole(27, "scope-self-2");
        createAccount(32, "org_data_admin");
        String administrator = loginWithWecom(32);

        assertThat(readAttribution(self, "/org-unit-legal-entities/" + unit(10)).getResponse().getStatus()).isEqualTo(404);
        assertThat(readAttribution(self, "/employee-legal-entities/" + employee(901)).getResponse().getStatus()).isEqualTo(404);
        assertThat(readAttribution(administrator, "/org-unit-legal-entities/" + unit(20)).getResponse().getStatus()).isEqualTo(200);
        assertThat(readAttribution(administrator, "/employee-legal-entities/" + employee(902)).getResponse().getStatus()).isEqualTo(200);
    }
}
