package com.forcitis.one.organization.workforce.legal;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.EmployeeLegalEntities;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.application.CreateLegalEntity;
import com.forcitis.one.organization.workforce.application.SyncOrgSnapshot;
import com.forcitis.one.organization.workforce.application.WorkforceIntegrationSupport;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import com.forcitis.one.organization.workforce.events.LegalEntityAttributed;
import com.forcitis.one.platform.identity.api.Permission;
import java.time.Duration;
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
    static final RequestPostProcessor MAINTAINER = user("9001").authorities(() -> Permission.Codes.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_UPDATE);
    static final RequestPostProcessor READER = user("9002").authorities(() -> Permission.Codes.ORGANIZATION_WORKFORCE_ORG_UNIT_READ);

    @Autowired
    MockMvc mvc;

    @Autowired
    SyncOrgSnapshot sync;

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

    @BeforeEach
    void twoLegalEntities() {
        jdbc.sql("truncate table organization_workforce.legal_entities restart identity cascade").update();
        le1 = new LegalEntityId(createLegalEntity.execute("FST", "Synthetic one").legalEntityId());
        le2 = new LegalEntityId(createLegalEntity.execute("FIT", "Synthetic two").legalEntityId());
    }

    private void synced(Long parentOfUnit3) {
        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L), unit(3, parentOfUnit3), unit(4, null)),
                List.of(head(2, 1), manager(2, 2)), List.of(post(100), post(101)),
                List.of(person(1, 2, 100L, null), person(2, 3, 101L, 1L), person(3, 3, 101L, 2L)));
        sync.execute(SyncTrigger.SCHEDULED, false);
    }

    private LocalDate today() {
        return zone.today(clock);
    }

    private void attributeOrgUnit(long orgUnitId, LegalEntityId legalEntity, LocalDate from) throws Exception {
        mvc.perform(put("/api/v1/organization/org-unit-legal-entities/" + orgUnitId + "").with(csrf()).with(MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalEntityId\":" + legalEntity.value() + ",\"effectiveFrom\":\"" + from + "\"}")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("T1 a sync writes the first version of every org unit and assignment; an unchanged sync adds none")
    void firstVersions() {
        synced(2L);
        assertThat(count("org_unit_versions")).isEqualTo(4);
        assertThat(count("assignment_versions")).isEqualTo(3);

        sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(count("org_unit_versions")).isEqualTo(4);
        assertThat(count("assignment_versions")).isEqualTo(3);
    }

    @Test
    @DisplayName("T1 a change on a later day closes the open version at that day and starts a new one; the earlier day still sees the old tree")
    void changeClosesTheVersion() throws Exception {
        synced(2L);
        LocalDate first = today();
        clock.advance(Duration.ofDays(3));
        orgSource.snapshot = source(List.of(unit(1, null), unit(2, 1L), unit(3, 4L), unit(4, null)),
                List.of(head(2, 1), manager(2, 2)), List.of(post(100), post(101)),
                List.of(person(1, 2, 100L, null), person(2, 3, 101L, 1L), person(3, 3, 101L, 2L)));
        sync.execute(SyncTrigger.SCHEDULED, false);

        assertThat(count("org_unit_versions")).isEqualTo(5);
        assertThat(jdbc.sql("select effective_to_date from organization_workforce.org_unit_versions where org_unit_id = 3 order by effective_from_date limit 1")
                .query(java.sql.Date.class).single().toLocalDate()).isEqualTo(first.plusDays(3));

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

        mvc.perform(put("/api/v1/organization/employee-legal-entities/2").with(csrf()).with(MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalEntityId\":" + le1.value() + ",\"effectiveFrom\":\"" + day.plusDays(1) + "\"}")).andExpect(status().isOk());
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

        mvc.perform(get("/api/v1/organization/org-unit-legal-entities/1").with(READER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.periods[0].legalEntityId").value(String.valueOf(le1.value())))
                .andExpect(jsonPath("$.periods[0].effectiveTo").doesNotExist());
        mvc.perform(put("/api/v1/organization/org-unit-legal-entities/1").with(csrf()).with(READER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"legalEntityId\":2,\"effectiveFrom\":\"" + day.plusDays(1) + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/organization/org-unit-legal-entities/404").with(csrf()).with(MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"legalEntityId\":1,\"effectiveFrom\":\"" + day + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/organization/org-unit-legal-entities/2").with(csrf()).with(MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"legalEntityId\":404,\"effectiveFrom\":\"" + day + "\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/v1/organization/org-unit-legal-entities/1").with(csrf()).with(MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"legalEntityId\":2,\"effectiveFrom\":\"" + day.minusDays(5) + "\"}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/organization/org-unit-legal-entities/1:end").with(csrf()).with(MAINTAINER)
                .contentType(MediaType.APPLICATION_JSON).content("{\"effectiveTo\":\"" + day.plusDays(10) + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.periods[0].effectiveTo").value(day.plusDays(10).toString()));
        assertThatThrownBy(() -> resolver.on(user(2), day.plusDays(10))).as("the attribution has ended").isInstanceOf(DomainException.class);
    }
}
