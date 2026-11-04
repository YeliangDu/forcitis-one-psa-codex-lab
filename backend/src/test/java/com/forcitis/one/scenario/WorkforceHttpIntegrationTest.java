package com.forcitis.one.scenario;

import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlEmployee;
import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlOrgUnit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.organization.workforce.application.SnapshotSeeder;
import com.forcitis.one.organization.workforce.application.WorkforceIntegrationSupport;
import com.forcitis.one.platform.identity.api.Permission;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class WorkforceHttpIntegrationTest extends WorkforceIntegrationSupport {
    static final String ORG_UNITS = "/api/v1/organization/org-units";
    static final String RECONCILE = "/api/v1/organization/org-reconciliations:run";

    @Autowired
    MockMvc mvc;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping;

    private static RequestPostProcessor caller(String... authorities) {
        return user(TestAccounts.account(9001).value().toString()).authorities(java.util.Arrays.stream(authorities).<org.springframework.security.core.GrantedAuthority>map(a -> () -> a).toList());
    }

    private static final RequestPostProcessor READ_ORG = caller(Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ);
    private static final RequestPostProcessor RECONCILER = caller(Permission.Codes.ORGANIZATION_WORKFORCE_ORG_RECONCILIATION_EXECUTE);

    @org.junit.jupiter.api.BeforeEach
    void theCallerIsTheOrganisationDataAdministrator() {
        jdbc.sql("insert into platform_identity.user_accounts (user_id, employee_id) values (" + TestAccounts.sql(9001) + "::uuid, " + sqlEmployee(9001) + ") on conflict do nothing").update();
        jdbc.sql("insert into platform_identity.account_role_grants (account_role_grant_id, user_id, role_id, effective_from_at, grant_channel_kind, grant_audit_entry_id, created_at, updated_at) select uuidv7(), " + TestAccounts.sql(9001) + "::uuid, role_id, now(), 'OPERATIONS', uuidv7(), now(), now() from platform_identity.roles where code = 'org_data_admin' on conflict do nothing").update();
    }

    @org.junit.jupiter.api.AfterEach
    void removeTheCallerAccount() {
        jdbc.sql("update platform_identity.account_role_grants set effective_to_at = greatest(now(), effective_from_at), revocation_cause_kind = 'OPERATIONS', revocation_audit_entry_id = uuidv7(), updated_at = now() where effective_to_at is null and user_id = (select user_id from platform_identity.user_accounts where employee_id = " + sqlEmployee(9001) + ")").update();
        com.forcitis.one.platform.identity.TestRoles.deleteAllGrants(jdbc);
        jdbc.sql("delete from platform_identity.user_accounts where employee_id = " + sqlEmployee(9001)).update();
    }

    private static String unitText(long n) {
        return TestOrgIds.orgUnit(n).value().toString();
    }

    private void synced() {
        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L), unit(3, 2L), unit(4, 1L)),
                List.of(head(2, 1)), List.of(post(100)), List.of(person(1, 2, 100L, null), person(2, 3, 100L, 1L)));
        SnapshotSeeder.seed(jdbc, orgSource.snapshot);
    }

    @Test
    @DisplayName("every workforce endpoint refuses anonymous callers (401) and callers without the permission (403)")
    void permissionsAreEnforced() throws Exception {
        synced();
        for (String path : List.of(ORG_UNITS, ORG_UNITS + "/" + unitText(2))) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(caller("some.other:thing:read"))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(RECONCILER)).andExpect(status().isForbidden());
        }
        mvc.perform(post(RECONCILE).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(RECONCILE).with(csrf()).with(READ_ORG)).andExpect(status().isForbidden());
        mvc.perform(post(RECONCILE).with(RECONCILER)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RULE-359/368/369 org data is read-only in the new system: the only writes are the read-only comparison trigger and, since #933, the three personnel command endpoints and, since #959, the profile change and, since #980, its confirmation and rejection and, since #1169, the two org-structure command endpoints and, since #1170, the two countersign decisions")
    void orgDataHasNoEditEndpoints() {
        List<String> writes = new ArrayList<>();
        List<String> reads = new ArrayList<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().stream()
                .filter(p -> p.contains("/organization/") && !p.contains("/legal-entities") && !p.contains("-legal-entities/") && !p.contains("/work-calendars")).forEach(p -> info.getMethodsCondition().getMethods().forEach(m -> {
                    (m.name().equals("GET") ? reads : writes).add(m.name() + " " + p);
                })));

        assertThat(writes).containsExactlyInAnyOrder("POST /api/v1/organization/org-reconciliations:run",
                "POST /api/v1/organization/personnel-commands", "POST /api/v1/organization/personnel-commands/{personnelCommandId}:withdraw",
                "POST /api/v1/organization/separation-holds/{separationHoldId}:release", "POST /api/v1/organization/employees/{employeeId}:change-profile",
                // #980: the second person of a WeCom user id rebind confirms or rejects the waiting profile change; the list of the waiting ones is the third read.
                "POST /api/v1/organization/employee-profile-change-requests/{requestId}:confirm", "POST /api/v1/organization/employee-profile-change-requests/{requestId}:reject",
                // #1169: org data is still not edited in place; a change to the org tree is entered as a command and withdrawn the same way, as for personnel commands.
                "POST /api/v1/organization/org-structure-commands", "POST /api/v1/organization/org-structure-commands/{orgStructureCommandId}:withdraw",
                // #1170: the finance head decides a countersign request; no data is edited in place.
                "POST /api/v1/organization/attribution-countersigns/{attributionCountersignId}:approve", "POST /api/v1/organization/attribution-countersigns/{attributionCountersignId}:reject");
        assertThat(reads).containsExactlyInAnyOrder("GET /api/v1/organization/org-units", "GET /api/v1/organization/org-units/{orgUnitId}",
                "GET /api/v1/organization/employee-profile-change-requests",
                "GET /api/v1/organization/attribution-countersigns",
                // #910 S3: the decider's read of a countersign request, the basis of an org-structure command and the merge rehearsal.
                "GET /api/v1/organization/attribution-countersigns/{attributionCountersignId}", "GET /api/v1/organization/attribution-countersign-bases/{orgStructureCommandId}",
                "GET /api/v1/organization/org-units/{orgUnitId}:preview-merge",
                // #1171: the roster of people (data-scoped, HR role) and the position directory; reads only.
                "GET /api/v1/organization/employees", "GET /api/v1/organization/employees/{employeeId}",
                "GET /api/v1/organization/positions", "GET /api/v1/organization/positions/{positionId}");
    }

    @Test
    @DisplayName("the org tree lists parents first with string ids; revoked units only on request")
    void orgTree() throws Exception {
        synced();
        jdbc.sql("update organization_workforce.org_units set status = 'REVOKED', revoked_at = now() where id = " + sqlOrgUnit(4)).update();

        mvc.perform(get(ORG_UNITS).with(READ_ORG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].orgUnitId").value(unitText(1)))
                .andExpect(jsonPath("$[0].parentOrgUnitId").doesNotExist())
                .andExpect(jsonPath("$[1].orgUnitId").value(unitText(2)))
                .andExpect(jsonPath("$[1].parentOrgUnitId").value(unitText(1)))
                .andExpect(jsonPath("$[1].headEmployeeId").value(TestAccounts.employee(1).value().toString()))
                .andExpect(jsonPath("$[2].status").value("ACTIVE"));
        mvc.perform(get(ORG_UNITS).param("includeRevoked", "true").with(READ_ORG))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.orgUnitId == '" + unitText(4) + "')].status").value("REVOKED"));
    }

    @Test
    @DisplayName("RULE-360 one org unit comes with its ancestors nearest first and its unit-and-below scope without revoked units")
    void orgUnitDetail() throws Exception {
        synced();

        mvc.perform(get(ORG_UNITS + "/" + unitText(3)).with(READ_ORG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgUnit.orgUnitId").value(unitText(3)))
                .andExpect(jsonPath("$.ancestorOrgUnitIds[0]").value(unitText(2)))
                .andExpect(jsonPath("$.ancestorOrgUnitIds[1]").value(unitText(1)))
                .andExpect(jsonPath("$.orgUnitAndBelowIds.length()").value(1));
        mvc.perform(get(ORG_UNITS + "/" + unitText(1)).with(READ_ORG))
                .andExpect(jsonPath("$.orgUnitAndBelowIds.length()").value(4));
        mvc.perform(get(ORG_UNITS + "/" + unitText(404)).with(READ_ORG)).andExpect(status().isNotFound());
    }


    @Test
    @DisplayName("D-1a-10 the comparison can be triggered by hand by the org reconciliation executor and lists the differences")
    void reconciliationOnDemand() throws Exception {
        synced();
        mvc.perform(post(RECONCILE).with(csrf()).with(RECONCILER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.zeroDifference").value(true));
        mvc.perform(post(RECONCILE).with(csrf()).with(READ_ORG)).andExpect(status().isForbidden());
    }
}
