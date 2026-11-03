package com.forcitis.one.scenario;

import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlEmployee;
import static com.forcitis.one.fixture.doubles.TestOrgIds.sqlOrgUnit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.manager;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static com.forcitis.one.organization.workforce.domain.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.TestIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.EmployeeLegalEntities;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.application.CreateLegalEntity;
import com.forcitis.one.organization.workforce.application.SnapshotSeeder;
import com.forcitis.one.organization.workforce.application.WorkforceIntegrationSupport;
import com.forcitis.one.organization.workforce.events.LegalEntityAttributed;
import com.forcitis.one.platform.identity.api.Permission;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class LegalEntityAttributionIntegrationTest extends WorkforceIntegrationSupport {
    static final RequestPostProcessor MAINTAINER = user(TestAccounts.account(9001).value().toString()).authorities(() -> Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE);
    static final RequestPostProcessor CORRECTOR = user(TestAccounts.account(9003).value().toString()).authorities(
            () -> Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_ATTRIBUTION_CORRECTION_CREATE, () -> Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ);
    static final RequestPostProcessor READER = user(TestAccounts.account(9002).value().toString()).authorities(() -> Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ);

    @Autowired
    MockMvc mvc;

    @Autowired
    CreateLegalEntity createLegalEntity;

    @Autowired
    EmployeeLegalEntities resolver;

    @Autowired
    BusinessZone zone;

    @Autowired
    ApplicationEvents events;

    LegalEntityId le1;
    LegalEntityId le2;

    @org.junit.jupiter.api.BeforeEach
    void theReaderIsTheOrganisationDataAdministrator() {
        for (long person : new long[] {9001, 9002, 9003}) {
            jdbc.sql("insert into platform_identity.user_accounts (user_id, employee_id) values (" + TestAccounts.sql(person) + "::uuid, " + sqlEmployee(person) + ") on conflict do nothing").update();
        }
        jdbc.sql("insert into platform_identity.account_role_grants (account_role_grant_id, user_id, role_id, effective_from_at, grant_channel_kind, grant_audit_entry_id, created_at, updated_at) select uuidv7(), " + TestAccounts.sql(9002) + "::uuid, role_id, now(), 'OPERATIONS', uuidv7(), now(), now() from platform_identity.roles where code = 'org_data_admin' on conflict do nothing").update();
    }

    @org.junit.jupiter.api.AfterEach
    void removeTheReaderAccount() {
        jdbc.sql("update platform_identity.account_role_grants set effective_to_at = greatest(now(), effective_from_at), revocation_cause_kind = 'OPERATIONS', revocation_audit_entry_id = uuidv7(), updated_at = now() where effective_to_at is null and user_id = (select user_id from platform_identity.user_accounts where employee_id = " + sqlEmployee(9002) + ")").update();
        com.forcitis.one.platform.identity.TestRoles.deleteAllGrants(jdbc);
        jdbc.sql("delete from platform_identity.user_accounts where employee_id in (" + sqlEmployee(9001) + ", " + sqlEmployee(9002) + ", " + sqlEmployee(9003) + ")").update();
    }

    @BeforeEach
    void twoLegalEntities() {
        jdbc.sql("alter table organization_workforce.legal_entity_attribution_corrections disable trigger user").update();
        jdbc.sql("delete from organization_workforce.legal_entity_attribution_corrections").update();
        jdbc.sql("alter table organization_workforce.legal_entity_attribution_corrections enable trigger user").update();
        com.forcitis.one.organization.workforce.application.VersionTables.truncate(jdbc, "truncate table organization_workforce.legal_entities restart identity cascade");
        le1 = createLegalEntity.execute("FST", "Synthetic one").legalEntityId();
        le2 = createLegalEntity.execute("FIT", "Synthetic two").legalEntityId();
    }

    private void synced(Long parentOfUnit3) {
        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L), unit(3, parentOfUnit3), unit(4, null)),
                List.of(head(2, 1), manager(2, 2)), List.of(post(100), post(101)),
                List.of(person(1, 2, 100L, null), person(2, 3, 101L, 1L), person(3, 3, 101L, 2L)));
        SnapshotSeeder.seed(jdbc, orgSource.snapshot);
        // The resolution reads the tree on a date from the version tables; since #934 nothing in main writes org unit versions, so the fixture opens one per row.
        jdbc.sql("""
                insert into organization_workforce.org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, code, name, parent_org_unit_id, status,
                       head_employee_id, regional_receivables_manager_id, region_kind, is_delivery_unit, revision_number)
                select uuidv7(), id, :day, code, name, parent_org_unit_id, status, head_employee_id, regional_receivables_manager_id, region_category, is_delivery_unit, 0
                  from organization_workforce.org_units
                """).param("day", today()).update();
        jdbc.sql("""
                insert into organization_workforce.assignment_versions (assignment_version_id, employee_id, effective_from_date, org_unit_id, position_id,
                       reports_to_employee_id, revision_number)
                select uuidv7(), employee_id, :day, org_unit_id, position_id, reports_to_employee_id, 0 from organization_workforce.assignments
                """).param("day", today()).update();
    }

    private static String unitText(long n) {
        return TestOrgIds.orgUnit(n).value().toString();
    }

    private LocalDate today() {
        return zone.today(clock);
    }

    private String etagOf(String url) throws Exception {
        return mvc.perform(get(url).with(READER)).andReturn().getResponse().getHeader("ETag");
    }

    private void attributeOrgUnit(long orgUnitId, LegalEntityId legalEntity, LocalDate from) throws Exception {
        String url = "/api/v1/organization/org-unit-legal-entities/" + unitText(orgUnitId);
        String etag = etagOf(url);
        var request = put(url).with(csrf()).with(MAINTAINER);
        mvc.perform((etag == null ? request.header("If-Match", "\"0\"") : request.header("If-Match", etag))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalEntityId\":\"" + legalEntity.value() + "\",\"effectiveFrom\":\"" + from + "\"}")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADR-0026 the resolution reads the tree of the day: after unit 3 moves under unit 4, the earlier day still resolves through the old parent")
    void resolutionFollowsTheTreeOfTheDay() throws Exception {
        synced(2L);
        LocalDate first = today();
        jdbc.sql("update organization_workforce.org_unit_versions set effective_to_date = :to where org_unit_id = " + sqlOrgUnit(3) + " and effective_to_date is null")
                .param("to", first.plusDays(3)).update();
        jdbc.sql("""
                insert into organization_workforce.org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, code, name, parent_org_unit_id, status,
                       is_delivery_unit, revision_number)
                select uuidv7(), id, :from, code, name, %s, status, is_delivery_unit, 0 from organization_workforce.org_units where id = %s
                """.formatted(sqlOrgUnit(4), sqlOrgUnit(3))).param("from", first.plusDays(3)).update();

        attributeOrgUnit(1, le1, first);
        attributeOrgUnit(4, le2, first);
        assertThat(resolver.on(user(2), first)).as("under unit 2, which is under unit 1").isEqualTo(le1);
        assertThat(resolver.on(user(2), first.plusDays(3))).as("after the move under unit 4").isEqualTo(le2);
    }

    @Test
    @DisplayName("ADR-0026 an employee takes the legal entity of the nearest attributed department; an employee's own attribution wins from its day")
    void resolution() throws Exception {
        synced(2L);
        LocalDate day = today();
        attributeOrgUnit(1, le1, day);
        assertThat(resolver.on(user(2), day)).isEqualTo(le1);
        assertThat(resolver.on(user(1), day)).isEqualTo(le1);

        attributeOrgUnit(3, le2, day);
        assertThat(resolver.on(user(2), day)).as("a subtree overrides the ancestor").isEqualTo(le2);
        assertThat(resolver.on(user(1), day)).isEqualTo(le1);

        mvc.perform(put("/api/v1/organization/employee-legal-entities/" + TestAccounts.employee(2).value()).header("If-Match", "\"0\"").with(csrf()).with(MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalEntityId\":\"" + le1.value() + "\",\"effectiveFrom\":\"" + day.plusDays(1) + "\"}")).andExpect(status().isOk());
        assertThat(resolver.on(user(2), day)).isEqualTo(le2);
        assertThat(resolver.on(user(2), day.plusDays(1))).isEqualTo(le1);
        assertThat(events.stream(LegalEntityAttributed.class)).hasSize(3);
    }

    @Test
    @DisplayName("B-1 no legal entity applies: the lookup fails with a domain exception, it never answers a default")
    void unresolvedFails() throws Exception {
        synced(2L);
        LocalDate day = today();

        assertThatThrownBy(() -> resolver.on(user(2), day)).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED));
        
        attributeOrgUnit(1, le1, day);
        assertThatThrownBy(() -> resolver.on(user(2), day.minusDays(1))).as("before the attribution began").isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> resolver.on(user(99), day)).as("an unknown employee").isInstanceOf(DomainException.class);
        assertThat(resolver.on(user(2), day)).isEqualTo(le1);
    }

    @Test
    @DisplayName("D-1a-27 the attribution endpoints: maintained by the legal entity maintainer, readable with org-unit read, bad input is refused")
    void endpoints() throws Exception {
        synced(2L);
        LocalDate day = today();
        attributeOrgUnit(1, le1, day);

        mvc.perform(get("/api/v1/organization/org-unit-legal-entities/" + unitText(1)).with(READER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.periods[0].legalEntityId").value(String.valueOf(le1.value())))
                .andExpect(jsonPath("$.periods[0].effectiveTo").doesNotExist());
        mvc.perform(put("/api/v1/organization/org-unit-legal-entities/" + unitText(1)).with(csrf()).with(READER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"legalEntityId\":\"" + le2.value() + "\",\"effectiveFrom\":\"" + day.plusDays(1) + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/organization/org-unit-legal-entities/" + unitText(404)).header("If-Match", "\"0\"").with(csrf()).with(MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"legalEntityId\":\"" + le1.value() + "\",\"effectiveFrom\":\"" + day + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/organization/org-unit-legal-entities/" + unitText(2)).header("If-Match", "\"0\"").with(csrf()).with(MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"legalEntityId\":\"" + TestIds.uuid(404) + "\",\"effectiveFrom\":\"" + day + "\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/v1/organization/org-unit-legal-entities/" + unitText(1)).header("If-Match", etagOf("/api/v1/organization/org-unit-legal-entities/" + unitText(1))).with(csrf()).with(MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"legalEntityId\":\"" + le2.value() + "\",\"effectiveFrom\":\"" + day.minusDays(5) + "\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/organization/org-unit-legal-entities/" + unitText(1) + ":end").header("If-Match", etagOf("/api/v1/organization/org-unit-legal-entities/" + unitText(1))).with(csrf()).with(MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"effectiveTo\":\"" + day.plusDays(10) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.periods[0].effectiveTo").value(day.plusDays(10).toString()));
        assertThatThrownBy(() -> resolver.on(user(2), day.plusDays(10))).as("the attribution has ended").isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("B3-M4 attributing and ending need the version: none is 428, a stale one 412, a weak tag 400; the first attribution uses If-Match: 0")
    void writesNeedTheVersion() throws Exception {
        synced(2L);
        LocalDate day = today();
        String url = "/api/v1/organization/org-unit-legal-entities/" + unitText(1);
        String body = "{\"legalEntityId\":\"" + le1.value() + "\",\"effectiveFrom\":\"" + day + "\"}";

        mvc.perform(put(url).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isPreconditionRequired());
        mvc.perform(put(url).header("If-Match", "\"3\"").with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
        mvc.perform(put(url).header("If-Match", "\"0\"").with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(header().exists("ETag"));
        mvc.perform(put(url).header("If-Match", "\"0\"").with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
        String etag = etagOf(url);
        mvc.perform(post(url + ":end").header("If-Match", "W/" + etag).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveTo\":\"" + day.plusDays(3) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(post(url + ":end").with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveTo\":\"" + day.plusDays(3) + "\"}")).andExpect(status().isPreconditionRequired());
        mvc.perform(post(url + ":end").header("If-Match", etag).with(csrf()).with(MAINTAINER).contentType(MediaType.APPLICATION_JSON)
                .content("{\"effectiveTo\":\"" + day.plusDays(3) + "\"}")).andExpect(status().isOk());
    }

    private String correct(String url, String etag, String body, RequestPostProcessor caller, int expectedStatus) throws Exception {
        var request = post(url + ":correct").with(csrf()).with(caller).contentType(MediaType.APPLICATION_JSON).content(body);
        if (etag != null) {
            request = request.header("If-Match", etag);
        }
        var result = mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    @DisplayName("RULE-385 a closed period is not rewritten: the correction is appended, the date resolves with the new end, the event is published")
    void rule385CorrectionIsAppendedAndResolutionFollowsIt() throws Exception {
        synced(2L);
        LocalDate day = today();
        String url = "/api/v1/organization/org-unit-legal-entities/" + unitText(1);
        attributeOrgUnit(1, le1, day);
        attributeOrgUnit(1, le2, day.plusDays(10));
        assertThat(resolver.on(user(1), day.plusDays(7))).isEqualTo(le1);

        String body = "{\"periodFrom\":\"" + day + "\",\"effectiveTo\":\"" + day.plusDays(5) + "\",\"reason\":\"typed the wrong end day\"}";
        String answer = correct(url, etagOf(url), body, CORRECTOR, 200);

        assertThat(answer).contains(day.plusDays(5).toString());
        assertThat(jdbc.sql("select effective_to_date from organization_workforce.org_unit_legal_entities where org_unit_id = " + sqlOrgUnit(1) + " and effective_from_date = :d")
                .param("d", java.sql.Date.valueOf(day)).query(java.sql.Date.class).single().toLocalDate()).as("the stored row is not rewritten").isEqualTo(day.plusDays(10));
        assertThat(jdbc.sql("select count(*) from organization_workforce.legal_entity_attribution_corrections").query(Long.class).single()).isEqualTo(1);
        assertThat(resolver.on(user(1), day.plusDays(4))).isEqualTo(le1);
        assertThatThrownBy(() -> resolver.on(user(1), day.plusDays(7))).as("the day is no longer covered").isInstanceOf(DomainException.class);
        assertThat(resolver.on(user(1), day.plusDays(10))).isEqualTo(le2);
        assertThat(events.stream(com.forcitis.one.organization.workforce.events.LegalEntityAttributionCorrected.class)).singleElement()
                .satisfies(e -> {
                    assertThat(e.previousEffectiveTo()).isEqualTo(day.plusDays(10));
                    assertThat(e.newEffectiveTo()).isEqualTo(day.plusDays(5));
                    assertThat(e.reason().value()).isEqualTo("typed the wrong end day");
                    assertThat(e.envelope().actorId()).isEqualTo(TestAccounts.account(9003).value());
                });
        assertThatThrownBy(() -> jdbc.sql("update organization_workforce.legal_entity_attribution_corrections set correction_reason = 'x'").update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("delete from organization_workforce.legal_entity_attribution_corrections").update()).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("truncate table organization_workforce.legal_entity_attribution_corrections").update()).hasMessageContaining("append-only");

        attributeOrgUnit(1, le1, day.plusDays(20));
        assertThat(jdbc.sql("select effective_to_date from organization_workforce.org_unit_legal_entities where org_unit_id = " + sqlOrgUnit(1) + " and effective_from_date = :d")
                .param("d", java.sql.Date.valueOf(day)).query(java.sql.Date.class).single().toLocalDate()).as("a later attribution leaves the corrected period alone").isEqualTo(day.plusDays(10));
        assertThat(resolver.on(user(1), day.plusDays(25))).isEqualTo(le1);
    }

    @Test
    @DisplayName("RULE-385 a correction that runs into another period, names an open period, has no reason or no version is refused and writes nothing")
    void rule385Refusals() throws Exception {
        synced(2L);
        LocalDate day = today();
        String url = "/api/v1/organization/org-unit-legal-entities/" + unitText(1);
        attributeOrgUnit(1, le1, day);
        attributeOrgUnit(1, le2, day.plusDays(10));
        String etag = etagOf(url);
        String overlap = "{\"periodFrom\":\"" + day + "\",\"effectiveTo\":\"" + day.plusDays(15) + "\",\"reason\":\"r\"}";
        String open = "{\"periodFrom\":\"" + day.plusDays(10) + "\",\"effectiveTo\":\"" + day.plusDays(15) + "\",\"reason\":\"r\"}";
        String noReason = "{\"periodFrom\":\"" + day + "\",\"effectiveTo\":\"" + day.plusDays(5) + "\",\"reason\":\" \"}";
        String ok = "{\"periodFrom\":\"" + day + "\",\"effectiveTo\":\"" + day.plusDays(5) + "\",\"reason\":\"r\"}";

        assertThat(correct(url, etag, overlap, CORRECTOR, 422)).contains("ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_OVERLAP");
        assertThat(correct(url, etag, open, CORRECTOR, 422)).contains("ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_CLOSED");
        assertThat(correct(url, etag, noReason, CORRECTOR, 422)).contains("ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED");
        correct(url, null, ok, CORRECTOR, 428);
        correct(url, "\"99\"", ok, CORRECTOR, 412);
        correct(url, etag, ok, MAINTAINER, 403);
        correct(url, etag, ok, READER, 403);
        org.mockito.Mockito.verify(audit, org.mockito.Mockito.times(2)).recordIndependently(org.mockito.ArgumentMatchers.argThat(
                entry -> entry.action().equals("organization.workforce.legal-entity-attribution.correction-refused")));
        assertThat(jdbc.sql("select count(*) from organization_workforce.legal_entity_attribution_corrections").query(Long.class).single()).isZero();
    }
}
