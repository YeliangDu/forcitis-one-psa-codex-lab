package com.forcitis.one.scenario;

import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlEmployee;
import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlOrgUnit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.tree;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.TestIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.organization.workforce.application.SyncOrgSnapshot;
import com.forcitis.one.organization.workforce.events.SyncRunReleased;
import org.springframework.test.context.event.ApplicationEvents;
import com.forcitis.one.organization.workforce.application.WorkforceIntegrationSupport;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.organization.workforce.domain.SyncRunStatus;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.api.Permission;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class WorkforceHttpIntegrationTest extends WorkforceIntegrationSupport {
    static final String REASON = "{\"reason\":\"synthetic: the shrink is real\"}";

    static final String ORG_UNITS = "/api/v1/organization/org-units";
    static final String SYNC_RUNS = "/api/v1/organization/sync-runs";
    static final java.util.UUID SOME_RUN = TestIds.uuid(1);

    @Autowired
    MockMvc mvc;

    @Autowired
    SyncOrgSnapshot sync;

    @Autowired
    ApplicationEvents events;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping;

    private static RequestPostProcessor caller(String... authorities) {
        return user(TestAccounts.account(9001).value().toString()).authorities(java.util.Arrays.stream(authorities).<org.springframework.security.core.GrantedAuthority>map(a -> () -> a).toList());
    }

    private static final RequestPostProcessor READ_ORG = caller(Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ);
    private static final RequestPostProcessor READ_RUNS = caller(Permission.Codes.ORGANIZATION_WORKFORCE_SYNC_RUN_READ);
    private static final RequestPostProcessor EXECUTE = caller(Permission.Codes.ORGANIZATION_WORKFORCE_SYNC_RUN_EXECUTE);

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
        sync.execute(SyncTrigger.SCHEDULED, false);
    }

    @Test
    @DisplayName("every workforce endpoint refuses anonymous callers (401) and callers without the permission (403)")
    void permissionsAreEnforced() throws Exception {
        synced();
        for (String path : List.of(ORG_UNITS, ORG_UNITS + "/" + unitText(2), SYNC_RUNS, SYNC_RUNS + "/" + SOME_RUN)) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(caller("some.other:thing:read"))).andExpect(status().isForbidden());
        }
        mvc.perform(get(ORG_UNITS).with(READ_RUNS)).andExpect(status().isForbidden());
        mvc.perform(get(SYNC_RUNS).with(READ_ORG)).andExpect(status().isForbidden());
        mvc.perform(post(SYNC_RUNS + "/" + SOME_RUN + ":release").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(SYNC_RUNS + "/" + SOME_RUN + ":release").with(csrf()).with(READ_RUNS).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post(SYNC_RUNS + "/" + SOME_RUN + ":release").with(EXECUTE).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RULE-359/368/369 org data is read-only in the new system: the only writes are the release of a held run, the read-only comparison trigger and, since #933, the three personnel command endpoints and, since #959, the profile change and, since #980, its confirmation and rejection")
    void orgDataHasNoEditEndpoints() {
        List<String> writes = new ArrayList<>();
        List<String> reads = new ArrayList<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> info.getPatternValues().stream()
                .filter(p -> p.contains("/organization/") && !p.contains("/legal-entities") && !p.contains("-legal-entities/") && !p.contains("/work-calendars")).forEach(p -> info.getMethodsCondition().getMethods().forEach(m -> {
                    (m.name().equals("GET") ? reads : writes).add(m.name() + " " + p);
                })));

        assertThat(writes).containsExactlyInAnyOrder("POST /api/v1/organization/sync-runs/{syncRunId}:release", "POST /api/v1/organization/sync-runs:reconcile",
                "POST /api/v1/organization/personnel-commands", "POST /api/v1/organization/personnel-commands/{personnelCommandId}:withdraw",
                "POST /api/v1/organization/separation-holds/{separationHoldId}:release", "POST /api/v1/organization/employees/{employeeId}:change-profile",
                // #980: the second person of a WeCom user id rebind confirms or rejects the waiting profile change; the list of the waiting ones is the fifth read.
                "POST /api/v1/organization/employee-profile-change-requests/{requestId}:confirm", "POST /api/v1/organization/employee-profile-change-requests/{requestId}:reject");
        assertThat(reads).hasSize(5);
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
    @DisplayName("sync records: newest first, one record with its differences and data exceptions")
    void syncRuns() throws Exception {
        orgSource.snapshot = tree(person(1, 2, 100L, null), person(2, 99, 100L, null));
        java.util.UUID first = sync.execute(SyncTrigger.SCHEDULED, false).syncRunId().value();
        java.util.UUID second = sync.execute(SyncTrigger.SCHEDULED, false).syncRunId().value();

        mvc.perform(get(SYNC_RUNS).with(READ_RUNS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].syncRunId").value(second.toString()))
                .andExpect(jsonPath("$.items[1].syncRunId").value(first.toString()))
                .andExpect(jsonPath("$.items[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[1].isolatedRowCount").value(1))
                .andExpect(jsonPath("$.items[1].employedWithoutWecomCount").value(0))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        mvc.perform(get(SYNC_RUNS).param("limit", "1").with(READ_RUNS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty());
        mvc.perform(get(SYNC_RUNS + "/" + first).with(READ_RUNS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncRun.trigger").value("SCHEDULED"))
                .andExpect(jsonPath("$.diffs[?(@.changeKind == 'ISSUE')].issueKind").value("ISOLATED_MISSING_ORG_UNIT"))
                .andExpect(jsonPath("$.diffs[?(@.subjectKey == 'employee:" + TestAccounts.employee(1).value() + "')].changeKind").value("CREATED"));
        mvc.perform(get(SYNC_RUNS + "/" + TestIds.uuid(999)).with(READ_RUNS)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("H-2 sync records page by start time and id, so runs that started at the same moment are neither skipped nor repeated; differences keep the order they were found in")
    void syncRunsPageByStartTimeAndId() throws Exception {
        jdbc.sql("truncate table organization_workforce.sync_diffs, organization_workforce.sync_runs cascade").update();
        java.time.Instant moment = java.time.Instant.parse("2026-10-14T01:00:00Z");
        run(TestIds.uuid(10), moment);
        run(TestIds.uuid(11), moment);
        run(TestIds.uuid(12), moment.plusSeconds(3600));
        run(TestIds.uuid(9), moment.minusSeconds(3600));
        for (int number = 1; number <= 3; number++) {
            jdbc.sql("insert into organization_workforce.sync_diffs (id, sync_run_id, diff_number, entity_kind, subject_key, change_kind)"
                    + " values (:id, :run, :number, 'EMPLOYEE', :subject, 'CREATED')")
                    .param("id", TestIds.uuid(100 - number)).param("run", TestIds.uuid(12)).param("number", number).param("subject", "employee:" + (4 - number)).update();
        }

        java.util.List<String> seen = new java.util.ArrayList<>();
        String cursor = null;
        do {
            var request = get(SYNC_RUNS).param("limit", "2").with(READ_RUNS);
            if (cursor != null) {
                request = request.param("cursor", cursor);
            }
            String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            com.jayway.jsonpath.JsonPath.<java.util.List<String>>read(body, "$.items[*].syncRunId").forEach(seen::add);
            cursor = com.jayway.jsonpath.JsonPath.read(body, "$.nextCursor");
        } while (cursor != null);

        assertThat(seen).containsExactly(TestIds.uuid(12).toString(), TestIds.uuid(11).toString(), TestIds.uuid(10).toString(), TestIds.uuid(9).toString());
        mvc.perform(get(SYNC_RUNS + "/" + TestIds.uuid(12)).with(READ_RUNS))
                .andExpect(jsonPath("$.diffs[*].subjectKey").value(org.hamcrest.Matchers.contains("employee:3", "employee:2", "employee:1")));
    }

    private void run(java.util.UUID id, java.time.Instant startedAt) {
        jdbc.sql("insert into organization_workforce.sync_runs (id, trigger_kind, status, started_at) values (:id, 'SCHEDULED', 'SUCCEEDED', :startedAt)")
                .param("id", id).param("startedAt", java.sql.Timestamp.from(startedAt)).update();
    }

    @Test
    @DisplayName("RULE-358 releasing a held run needs the execute permission, a CSRF token and a HELD run; the actor is the session user")
    void releaseHeldRun() throws Exception {
        List<SourcePerson> ten = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            ten.add(person(i, 2, 100L, null));
        }
        orgSource.snapshot = tree(ten.toArray(SourcePerson[]::new));
        sync.execute(SyncTrigger.SCHEDULED, false);
        orgSource.snapshot = tree(ten.subList(0, 6).toArray(SourcePerson[]::new));
        var held = sync.execute(SyncTrigger.SCHEDULED, false);
        assertThat(held.status()).isEqualTo(SyncRunStatus.HELD);
        String path = SYNC_RUNS + "/" + held.syncRunId().value() + ":release";

        mvc.perform(post(path).with(EXECUTE).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"reason\":\"headcount change is real\"}")).andExpect(status().isForbidden());

        mvc.perform(post(path).with(csrf()).with(EXECUTE).contentType(MediaType.APPLICATION_JSON).content(REASON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RELEASED"))
                .andExpect(jsonPath("$.syncRun.trigger").value("MANUAL"))
                .andExpect(jsonPath("$.syncRun.status").value("SUCCEEDED"));
        assertThat(events.stream(SyncRunReleased.class)).singleElement().satisfies(e -> {
            assertThat(e.envelope().actorType()).isEqualTo(ActorType.USER);
            assertThat(e.envelope().actorId()).isEqualTo(TestAccounts.account(9001).value());
        });
        assertThat(jdbc.sql("select count(*) from organization_workforce.employees where employment_status = 'SEPARATED'").query(Integer.class).single()).isEqualTo(4);

        mvc.perform(post(path).with(csrf()).with(EXECUTE).contentType(MediaType.APPLICATION_JSON).content(REASON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL"))
                .andExpect(header().string("Content-Type", "application/problem+json"));
        mvc.perform(post(SYNC_RUNS + "/" + TestIds.uuid(777) + ":release").with(csrf()).with(EXECUTE).contentType(MediaType.APPLICATION_JSON).content(REASON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_WORKFORCE_SYNC_RUN_NOT_FOUND"));
    }

    @Test
    @DisplayName("a release that cannot be applied (the source down) keeps the hold and says why")
    void releaseWhileSourceIsDown() throws Exception {
        List<SourcePerson> ten = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            ten.add(person(i, 2, 100L, null));
        }
        orgSource.snapshot = tree(ten.toArray(SourcePerson[]::new));
        sync.execute(SyncTrigger.SCHEDULED, false);
        orgSource.snapshot = tree(ten.subList(0, 6).toArray(SourcePerson[]::new));
        var held = sync.execute(SyncTrigger.SCHEDULED, false);

        orgSource.unavailable = true;
        mvc.perform(post(SYNC_RUNS + "/" + held.syncRunId().value() + ":release").with(csrf()).with(EXECUTE).contentType(MediaType.APPLICATION_JSON).content(REASON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_WORKFORCE_SYNC_RUN_APPLY_FAILED"));

        assertThat(jdbc.sql("select status from organization_workforce.sync_runs where id = ?").param(held.syncRunId().value()).query(String.class).single())
                .isEqualTo("HELD");
    }

    @Test
    @DisplayName("D-1a-10 the comparison can be triggered by hand by the sync executor and lists the differences")
    void reconciliationOnDemand() throws Exception {
        synced();
        mvc.perform(post(SYNC_RUNS + ":reconcile").with(csrf()).with(EXECUTE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.zeroDifference").value(true));
        mvc.perform(post(SYNC_RUNS + ":reconcile").with(csrf()).with(READ_RUNS)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DATA-REPAIR-timesheet-audit TB-01: a release reason of 201 characters is refused with 400 and leaves the run held; 200 characters pass")
    void releaseReasonIsBounded() throws Exception {
        List<SourcePerson> ten = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            ten.add(person(i, 2, 100L, null));
        }
        orgSource.snapshot = tree(ten.toArray(SourcePerson[]::new));
        sync.execute(SyncTrigger.SCHEDULED, false);
        orgSource.snapshot = tree(ten.subList(0, 6).toArray(SourcePerson[]::new));
        var held = sync.execute(SyncTrigger.SCHEDULED, false);
        String path = SYNC_RUNS + "/" + held.syncRunId().value() + ":release";

        mvc.perform(post(path).with(csrf()).with(EXECUTE).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_WORKFORCE_RELEASE_REASON_REQUIRED"));
        assertThat(jdbc.sql("select status from organization_workforce.sync_runs where id = ?").param(held.syncRunId().value()).query(String.class).single())
                .as("the refused release leaves the run held").isEqualTo("HELD");
        mvc.perform(post(path).with(csrf()).with(EXECUTE).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "x".repeat(200) + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("D-1a-164 a release without a reason is refused; the reason of an accepted release reaches the audit trail through the event")
    void releaseNeedsAReason() throws Exception {
        List<SourcePerson> ten = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            ten.add(person(i, 2, 100L, null));
        }
        orgSource.snapshot = tree(ten.toArray(SourcePerson[]::new));
        sync.execute(SyncTrigger.SCHEDULED, false);
        orgSource.snapshot = tree(ten.subList(0, 6).toArray(SourcePerson[]::new));
        var held = sync.execute(SyncTrigger.SCHEDULED, false);
        String path = SYNC_RUNS + "/" + held.syncRunId().value() + ":release";

        mvc.perform(post(path).with(csrf()).with(EXECUTE).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_WORKFORCE_RELEASE_REASON_REQUIRED"));
        mvc.perform(post(path).with(csrf()).with(EXECUTE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).with(csrf()).with(EXECUTE).contentType(MediaType.APPLICATION_JSON).content(REASON)).andExpect(status().isOk());

        assertThat(events.stream(SyncRunReleased.class)).singleElement()
                .satisfies(e -> assertThat(e.reason().value()).isEqualTo("synthetic: the shrink is real"));
    }
}
