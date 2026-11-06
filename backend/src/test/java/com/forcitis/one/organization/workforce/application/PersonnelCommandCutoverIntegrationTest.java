package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.Uuids;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** #933: the cut-over to personnel commands: the due-date job takes ordinary commands too, a separation entered over HTTP reaches identity. */
class PersonnelCommandCutoverIntegrationTest extends WorkforceIntegrationSupport {
    static final String COMMANDS = "/api/v1/organization/personnel-commands";

    @Autowired
    MockMvc mvc;

    @Autowired
    BusinessZone zone;

    @Autowired
    PersonnelCommandDueJob dueJob;

    private static RequestPostProcessor hr() {
        return user(Uuids.timeOrdered(new com.forcitis.one.fixture.doubles.SteppingClock()).toString()).authorities(() -> "organization.workforce:personnel-command:create");
    }

    private String enter(String body) throws Exception {
        String answer = mvc.perform(post(COMMANDS).with(csrf()).with(hr()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        return JsonPath.read(answer, "$.personnelCommandId");
    }

    private String statusOf(String command) {
        return jdbc.sql("select status from organization_workforce.personnel_commands where personnel_command_id = :id").param("id", UUID.fromString(command)).query(String.class).single();
    }

    private long events(String type, String command) {
        return jdbc.sql("""
                select count(distinct p.serialized_event) from (select event_type, serialized_event from platform_integration.event_publication
                                                                union all
                                                                select event_type, serialized_event from platform_integration.event_publication_archive) p
                 where p.event_type like :type and p.serialized_event like :command
                """).param("type", "%." + type).param("command", "%" + command + "%").query(Long.class).single();
    }

    @Test
    @DisplayName("#933 a future-dated transfer is entered pending and the due-date job makes it effective on its day, once, with EmployeeTransferred from the old unit")
    void dueJobExecutesAnOrdinaryCommand() throws Exception {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "CO-1");
        UUID target = PersonnelSeedSupport.orgUnit(jdbc, clock);
        LocalDate day = zone.today(clock).plusDays(3);
        String command = enter("{\"employeeId\":\"" + person.employee() + "\",\"commandKind\":\"TRANSFER\",\"effectiveDate\":\"" + day + "\",\"targetOrgUnitId\":\"" + target + "\"}");
        assertThat(statusOf(command)).isEqualTo("PENDING");

        dueJob.run();
        assertThat(statusOf(command)).as("not before its day").isEqualTo("PENDING");

        clock.advance(Duration.ofDays(4));
        dueJob.run();
        dueJob.run();

        assertThat(statusOf(command)).isEqualTo("EFFECTIVE");
        assertThat(events("EmployeeTransferred", command)).as("published once").isEqualTo(1);
        assertThat(jdbc.sql("""
                select max(p.serialized_event) from (select event_type, serialized_event from platform_integration.event_publication
                                                     union all
                                                     select event_type, serialized_event from platform_integration.event_publication_archive) p
                 where p.event_type like '%.EmployeeTransferred' and p.serialized_event like :c
                """).param("c", "%" + command + "%").query(String.class).single()).contains(person.orgUnit().toString()).contains(target.toString());
    }

    @Test
    @DisplayName("#933 a separation entered over HTTP takes effect and EmployeeSeparated is completed by an identity listener")
    void separationReachesIdentity() throws Exception {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "CO-2");
        String command = enter("{\"employeeId\":\"" + person.employee() + "\",\"commandKind\":\"SEPARATE\",\"lastWorkingDay\":\"" + zone.today(clock).minusDays(1) + "\"}");

        assertThat(statusOf(command)).isEqualTo("EFFECTIVE");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbc.sql("""
                select count(*) from (select listener_id, event_type, completion_date, serialized_event from platform_integration.event_publication
                                      union all
                                      select listener_id, event_type, completion_date, serialized_event from platform_integration.event_publication_archive) p
                 where p.listener_id like 'com.forcitis.one.platform.identity.%' and p.event_type like '%.EmployeeSeparated' and p.completion_date is not null
                   and p.serialized_event like :command
                """).param("command", "%" + command + "%").query(Long.class).single()).isPositive());
    }
}
