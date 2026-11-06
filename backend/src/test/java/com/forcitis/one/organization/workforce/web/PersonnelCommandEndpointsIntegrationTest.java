package com.forcitis.one.organization.workforce.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.application.PersonnelSeedSupport;
import com.forcitis.one.organization.workforce.application.WorkforceIntegrationSupport;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** #933: the three personnel command endpoints answer 401 without a login, 403 without their permission and 200 with it; the use cases behind them are covered by #871, #873 and #875. */
class PersonnelCommandEndpointsIntegrationTest extends WorkforceIntegrationSupport {
    static final String COMMANDS = "/api/v1/organization/personnel-commands";
    static final String HOLDS = "/api/v1/organization/separation-holds/";

    @Autowired
    MockMvc mvc;

    @Autowired
    BusinessZone zone;

    @MockitoBean
    SeparationReview review;

    private static RequestPostProcessor caller(String permission) {
        return user(Uuids.timeOrdered(java.time.Clock.systemUTC()).toString()).authorities(() -> permission);
    }

    private String transfer(PersonnelSeedSupport.Person person, UUID toUnit, LocalDate effective) {
        return "{\"employeeId\":\"" + person.employee() + "\",\"commandKind\":\"TRANSFER\",\"effectiveDate\":\"" + effective + "\",\"targetOrgUnitId\":\"" + toUnit + "\"}";
    }

    @Test
    @DisplayName("#933 POST personnel-commands: 401 without a login, 403 without personnel-command:create, 200 with it and the command is pending")
    void enterEndpoint() throws Exception {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "EP-1");
        UUID target = PersonnelSeedSupport.orgUnit(jdbc, clock);
        String body = transfer(person, target, zone.today(clock).plusDays(7));

        mvc.perform(post(COMMANDS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(COMMANDS).with(csrf()).with(caller("organization.workforce:separation-hold:approve")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertThat(count("personnel_commands")).isZero();

        mvc.perform(post(COMMANDS).with(csrf()).with(caller("organization.workforce:personnel-command:create")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.personnelCommandId").isNotEmpty());
        assertThat(jdbc.sql("select status from organization_workforce.personnel_commands").query(String.class).single()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("#933 POST personnel-commands without employeeId or commandKind is a 400 at the edge")
    void enterNeedsEmployeeAndKind() throws Exception {
        mvc.perform(post(COMMANDS).with(csrf()).with(caller("organization.workforce:personnel-command:create")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("#1557 a NUL in a text field of a command body is a 400 at the edge, not a 500, and the command stays pending")
    void nulInTextIsRefusedAtTheEdge() throws Exception {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "EP-NUL");
        UUID target = PersonnelSeedSupport.orgUnit(jdbc, clock);
        String entered = mvc.perform(post(COMMANDS).with(csrf()).with(caller("organization.workforce:personnel-command:create")).contentType(MediaType.APPLICATION_JSON)
                .content(transfer(person, target, zone.today(clock).plusDays(7)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(entered, "$.personnelCommandId");

        mvc.perform(post(COMMANDS + "/" + id + ":withdraw").with(csrf()).with(caller("organization.workforce:personnel-command:create")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"before\\u0000after\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PLATFORM_WEB_REQUEST_INVALID"));
        assertThat(jdbc.sql("select status from organization_workforce.personnel_commands").query(String.class).single()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("#933 POST personnel-commands/{id}:withdraw: 401, 403, 200 and the command is withdrawn")
    void withdrawEndpoint() throws Exception {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "EP-2");
        UUID target = PersonnelSeedSupport.orgUnit(jdbc, clock);
        String entered = mvc.perform(post(COMMANDS).with(csrf()).with(caller("organization.workforce:personnel-command:create")).contentType(MediaType.APPLICATION_JSON)
                .content(transfer(person, target, zone.today(clock).plusDays(7)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(entered, "$.personnelCommandId");
        String body = "{\"reason\":\"synthetic withdrawal\"}";

        mvc.perform(post(COMMANDS + "/" + id + ":withdraw").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(COMMANDS + "/" + id + ":withdraw").with(csrf()).with(caller("organization.workforce:separation-hold:approve")).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isForbidden());
        mvc.perform(post(COMMANDS + "/" + id + ":withdraw").with(csrf()).with(caller("organization.workforce:personnel-command:create")).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk());

        assertThat(jdbc.sql("select status from organization_workforce.personnel_commands").query(String.class).single()).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("#933 POST separation-holds/{id}:release: 401, 403, 200 and the held separation takes effect")
    void releaseEndpoint() throws Exception {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "EP-3");
        UUID command = Uuids.timeOrdered(clock);
        UUID hold = Uuids.timeOrdered(clock);
        jdbc.sql("""
                insert into organization_workforce.personnel_commands (personnel_command_id, employment_relationship_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at)
                values (:command, :relationship, :employee, 'SEPARATE', :day, 'HELD', now(), :by, now())""").param("command", command).param("relationship", com.forcitis.one.organization.workforce.application.EmploymentRelationshipSeed.relationship(jdbc, person.employee())).param("employee", person.employee())
                .param("day", zone.today(clock)).param("by", Uuids.timeOrdered(clock)).update();
        jdbc.sql("""
                insert into organization_workforce.separation_holds (separation_hold_id, personnel_command_id, employee_id, status, created_at, updated_at)
                values (:hold, :command, :employee, 'HELD', now(), now())""").param("hold", hold).param("command", command).param("employee", person.employee()).update();
        when(review.review(any(), any(), any())).thenReturn(new SeparationReview.Verdict(Set.of(), true, 2, "", "", Set.of()));
        String body = "{\"reason\":\"synthetic release\"}";

        mvc.perform(post(HOLDS + hold + ":release").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(HOLDS + hold + ":release").with(csrf()).with(caller("organization.workforce:personnel-command:create")).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isForbidden());
        mvc.perform(post(HOLDS + hold + ":release").with(csrf()).with(caller("organization.workforce:separation-hold:approve")).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk());

        assertThat(jdbc.sql("select status from organization_workforce.personnel_commands").query(String.class).single()).isEqualTo("EFFECTIVE");
        assertThat(jdbc.sql("select status from organization_workforce.separation_holds").query(String.class).single()).isEqualTo("RELEASED");
    }
}
