package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.Uuids;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** #1089: EmployeeCorrected names what changed in today's assignment and the department and position of today before and after the correction. */
class PersonnelCorrectionEventIT extends WorkforceIntegrationSupport {
    static final String COMMANDS = "/api/v1/organization/personnel-commands";

    @Autowired
    MockMvc mvc;

    @Autowired
    BusinessZone zone;

    private static RequestPostProcessor hr() {
        return user(Uuids.timeOrdered(Clock.systemUTC()).toString()).authorities(() -> "organization.workforce:personnel-command:create");
    }

    private String enter(String body) throws Exception {
        String answer = mvc.perform(post(COMMANDS).with(csrf()).with(hr()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        return JsonPath.read(answer, "$.personnelCommandId");
    }

    private String correctedEvent(String command) {
        return jdbc.sql("""
                select max(p.serialized_event) from (select event_type, serialized_event from platform_integration.event_publication
                                                     union all
                                                     select event_type, serialized_event from platform_integration.event_publication_archive) p
                 where p.event_type like '%.EmployeeCorrected' and p.serialized_event like :command
                """).param("command", "%" + command + "%").query(String.class).single();
    }

    @Test
    @DisplayName("#1089 a correction dated before today that changes the department lists ORG_UNIT and carries the department of today before and after; the position stays the same on both sides")
    void aDepartmentCorrectionNamesTheUnitsOfToday() throws Exception {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "CE-1");
        UUID target = PersonnelSeedSupport.orgUnit(jdbc, clock);
        LocalDate day = zone.today(clock).minusDays(10);

        String command = enter("{\"employeeId\":\"" + person.employee() + "\",\"commandKind\":\"CORRECTION\",\"effectiveDate\":\"" + day + "\",\"targetOrgUnitId\":\"" + target + "\"}");

        String event = correctedEvent(command);
        assertThat(event).contains("\"triggerFieldKinds\":[\"ORG_UNIT\"]").contains(person.orgUnit().toString()).contains(target.toString());
    }

    @Test
    @DisplayName("#1089 a correction that touches neither department nor position publishes an empty trigger list and the same department on both sides")
    void aCorrectionOfTheFeeEarnerFlagListsNothing() throws Exception {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "CE-2");

        String command = enter("{\"employeeId\":\"" + person.employee() + "\",\"commandKind\":\"CORRECTION\",\"effectiveDate\":\"" + zone.today(clock).minusDays(10)
                + "\",\"isTargetFeeEarner\":true}");

        String event = correctedEvent(command);
        assertThat(event).contains("\"triggerFieldKinds\":[]").doesNotContain("ORG_UNIT");
        assertThat(event.split(person.orgUnit().toString(), -1).length - 1).as("the department of today is named on both sides").isEqualTo(2);
    }
}
