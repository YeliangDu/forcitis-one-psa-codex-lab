package com.forcitis.one.organization.workforce.legal;

import static com.forcitis.one.organization.workforce.domain.Fixtures.head;
import static com.forcitis.one.organization.workforce.domain.Fixtures.person;
import static com.forcitis.one.organization.workforce.domain.Fixtures.post;
import static com.forcitis.one.organization.workforce.domain.Fixtures.source;
import static com.forcitis.one.organization.workforce.domain.Fixtures.unit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.application.CreateLegalEntity;
import com.forcitis.one.organization.workforce.application.SyncOrgSnapshot;
import com.forcitis.one.organization.workforce.application.WorkforceIntegrationSupport;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.identity.api.Permission;
import com.forcitis.one.organization.workforce.application.VersionTables;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@DisplayName("SelfLegalEntityEditForbidden: ADR-0027 section 2 item 2")
class OwnLegalEntityAttributionForbiddenIntegrationTest extends WorkforceIntegrationSupport {
    static final String ORG_UNITS = "/api/v1/organization/org-unit-legal-entities/";
    static final String EMPLOYEES = "/api/v1/organization/employee-legal-entities/";

    static RequestPostProcessor maintainer(String userId) {
        return user(userId).authorities(() -> Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    SyncOrgSnapshot sync;

    @Autowired
    CreateLegalEntity createLegalEntity;

    @Autowired
    BusinessZone zone;

    LegalEntityId legalEntity;

    String lastEtag;

    @BeforeEach
    void orgWithOneLegalEntity() {
        VersionTables.truncate(jdbc, "truncate table organization_workforce.legal_entities restart identity cascade");
        legalEntity = createLegalEntity.execute("FST", "Synthetic one", null).legalEntityId();
        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L), unit(3, 2L), unit(4, null)), List.of(head(2, 1)), List.of(post(100)),
                List.of(person(1, 2, 100L, null), person(2, 3, 100L, 1L), person(3, 4, 100L, null)));
        sync.execute(SyncTrigger.SCHEDULED, false);
    }

    private String body() {
        return "{\"legalEntityId\":\"" + legalEntity.value() + "\",\"effectiveFrom\":\"" + zone.today(clock) + "\"}";
    }

    private int attributeEmployee(String actor, long employee) throws Exception {
        var response = mvc.perform(put(EMPLOYEES + employee).header("If-Match", "\"0\"").with(csrf()).with(maintainer(actor))
                .contentType(MediaType.APPLICATION_JSON).content(body())).andReturn().getResponse();
        lastEtag = response.getHeader("ETag");
        return response.getStatus();
    }

    private int attributeOrgUnit(String actor, long orgUnit) throws Exception {
        var response = mvc.perform(put(ORG_UNITS + orgUnit).header("If-Match", "\"0\"").with(csrf()).with(maintainer(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(body())).andReturn().getResponse();
        lastEtag = response.getHeader("ETag");
        return response.getStatus();
    }

    @Test
    @DisplayName("nobody sets their own employee entry: 403, audited in its own transaction, nothing stored")
    void ownEmployeeEntryIsRefused() throws Exception {
        mvc.perform(put(EMPLOYEES + 1).header("If-Match", "\"0\"").with(csrf()).with(maintainer("1")).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ORGANIZATION_WORKFORCE_OWN_ATTRIBUTION_FORBIDDEN"));

        assertThat(count("legal_entity_attributions")).isZero();
        verify(audit).recordIndependently(argThat((AuditEntry entry) -> entry.action().equals("organization.workforce.legal-entity-attribution.refused")
                && entry.actorId().value() == 1 && entry.subjectKey().equals("legal-entity-attribution:employee:1")));
    }

    @Test
    @DisplayName("nor can they end their own entry")
    void endingTheOwnEntryIsRefused() throws Exception {
        assertThat(attributeEmployee("9001", 1)).isEqualTo(200);

        mvc.perform(post(EMPLOYEES + "1:end").header("If-Match", lastEtag).with(csrf()).with(maintainer("1")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveTo\":\"" + zone.today(clock).plusDays(30) + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("nobody sets the legal entity of their own department or of a department above it, which they inherit")
    void ownDepartmentChainIsRefused() throws Exception {
        assertThat(attributeOrgUnit("1", 2)).as("own department").isEqualTo(403);
        assertThat(attributeOrgUnit("1", 1)).as("the department above").isEqualTo(403);
        assertThat(attributeOrgUnit("2", 3)).as("own department, one level down").isEqualTo(403);
        assertThat(attributeOrgUnit("2", 2)).as("the department above").isEqualTo(403);

        assertThat(count("legal_entity_attributions")).isZero();
    }

    @Test
    @DisplayName("everything else stays possible: other people, other departments, departments below, and administrators outside the organisation")
    void otherAttributionsAreAllowed() throws Exception {
        assertThat(attributeEmployee("1", 2)).as("another person").isEqualTo(200);
        assertThat(attributeEmployee("2", 1)).as("another person, the other way round").isEqualTo(200);
        assertThat(attributeOrgUnit("1", 3)).as("a department below one's own").isEqualTo(200);
        assertThat(attributeOrgUnit("1", 4)).as("an unrelated department").isEqualTo(200);
        assertThat(attributeOrgUnit("9001", 2)).as("someone who is not in the organisation snapshot").isEqualTo(200);
        verify(audit, never()).recordIndependently(argThat((AuditEntry entry) -> entry.action().equals("organization.workforce.legal-entity-attribution.refused")));
    }

    private int correct(String url, String etag, String actor) throws Exception {
        String body = "{\"periodFrom\":\"" + zone.today(clock) + "\",\"effectiveTo\":\"" + zone.today(clock).plusDays(5) + "\",\"reason\":\"typed the wrong end day\"}";
        return mvc.perform(post(url + ":correct").header("If-Match", etag).with(csrf())
                .with(user(actor).authorities(() -> "organization.workforce:legal-entity-attribution-correction:create"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("INT-ADR-H1 nobody corrects their own attribution either: the own employee entry and the own department chain are 403, audited, and write no correction")
    void correctingOwnAttributionIsRefused() throws Exception {
        assertThat(attributeEmployee("9001", 1)).isEqualTo(200);
        String employeeEtag = lastEtag;
        assertThat(attributeOrgUnit("9001", 2)).isEqualTo(200);
        String unitEtag = lastEtag;

        assertThat(correct(EMPLOYEES + 1, employeeEtag, "1")).as("own employee entry").isEqualTo(403);
        assertThat(correct(ORG_UNITS + 2, unitEtag, "1")).as("own department").isEqualTo(403);
        assertThat(correct(ORG_UNITS + 2, unitEtag, "2")).as("own department of the other person").isEqualTo(403);

        assertThat(count("legal_entity_attribution_corrections")).isZero();
        verify(audit, atLeastOnce()).recordIndependently(argThat((AuditEntry entry) -> entry.action().equals("organization.workforce.legal-entity-attribution.refused")
                && entry.actorId().value() == 1));
    }
}
