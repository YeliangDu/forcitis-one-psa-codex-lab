package com.forcitis.one.organization.workforce.web;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** #1579: the personnel command endpoint carries the acting-duty fields, so an acting start entered over HTTP is stored with them. */
class ActingStartOverHttpIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    MockMvc mvc;

    @Autowired
    BusinessZone zone;

    @MockitoBean
    SeparationReview review;

    @Test
    @DisplayName("#1579 POST personnel-commands with ACTING_START stores the covered unit, post, person, end day and reason on a pending command")
    void actingStartOverHttp() throws Exception {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "AH-1");
        PersonnelSeedSupport.Person covered = PersonnelSeedSupport.employed(jdbc, clock, "AH-2");
        LocalDate first = zone.today(clock).plusDays(3);
        String body = "{\"employeeId\":\"" + person.employee() + "\",\"commandKind\":\"ACTING_START\",\"effectiveDate\":\"" + first + "\",\"actingCoveredOrgUnitId\":\""
                + covered.orgUnit() + "\",\"actingCoveredPositionId\":\"" + covered.position() + "\",\"actingCoveredEmployeeId\":\"" + covered.employee()
                + "\",\"actingEndsBeforeDate\":\"" + first.plusDays(5) + "\",\"reason\":\"synthetic coverage\"}";

        mvc.perform(post("/api/v1/organization/personnel-commands").with(csrf()).with(user(Uuids.timeOrdered(java.time.Clock.systemUTC()).toString())
                        .authorities(() -> "organization.workforce:personnel-command:create")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.personnelCommandId").isNotEmpty());

        Map<String, Object> row = jdbc.sql("""
                select status, acting_covered_org_unit_id, acting_covered_position_id, acting_covered_employee_id, acting_ends_before_date, reason
                  from organization_workforce.personnel_commands where command_kind = 'ACTING_START' and employee_id = :employee""").param("employee", person.employee())
                .query().singleRow();
        assertThat(row).containsEntry("status", "PENDING").containsEntry("acting_covered_org_unit_id", covered.orgUnit())
                .containsEntry("acting_covered_position_id", covered.position()).containsEntry("acting_covered_employee_id", covered.employee())
                .containsEntry("acting_ends_before_date", java.sql.Date.valueOf(first.plusDays(5))).containsEntry("reason", "synthetic coverage");
    }
}
