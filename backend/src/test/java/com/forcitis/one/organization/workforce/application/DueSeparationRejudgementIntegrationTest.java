package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.Uuids;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** #933, ADR-0068 item 2.2: a separation that was not released is judged once more on its day; one released before is not. */
class DueSeparationRejudgementIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    MockMvc mvc;

    @Autowired
    BusinessZone zone;

    @Autowired
    PersonnelCommandDueJob dueJob;

    @MockitoBean
    SeparationReview review;

    private String enterFutureSeparation(PersonnelSeedSupport.Person person) throws Exception {
        when(review.review(any(), any(), any())).thenReturn(new SeparationReview.Verdict(Set.of(), true, 2, "", "", Set.of()));
        String answer = mvc.perform(post("/api/v1/organization/personnel-commands").with(csrf())
                .with(user(Uuids.timeOrdered(Clock.systemUTC()).toString()).authorities(() -> "organization.workforce:personnel-command:create"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"employeeId\":\"" + person.employee() + "\",\"commandKind\":\"SEPARATE\",\"lastWorkingDay\":\""
                        + zone.today(clock).plusDays(2) + "\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
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
    @DisplayName("#933 ADR-0068 2.2 an unreleased future separation whose leaver has become an available administrator goes back to HELD on its day: hold row, SeparationHeld, future version voided, no EmployeeSeparated")
    void heldAgainOnItsDay() throws Exception {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "RJ-1");
        String command = enterFutureSeparation(person);
        assertThat(statusOf(command)).isEqualTo("PENDING");

        when(review.review(any(), any(), any())).thenReturn(new SeparationReview.Verdict(Set.of(new EmployeeId(person.employee())), true, 1, "", "", Set.of()));
        clock.advance(Duration.ofDays(5));
        dueJob.run();
        dueJob.run();

        assertThat(statusOf(command)).isEqualTo("HELD");
        assertThat(jdbc.sql("select count(*) from organization_workforce.separation_holds where personnel_command_id = :id and status = 'HELD'").param("id", UUID.fromString(command))
                .query(Long.class).single()).isEqualTo(1);
        assertThat(events("SeparationHeld", command)).isEqualTo(1);
        assertThat(events("EmployeeSeparated", command)).isZero();
        assertThat(jdbc.sql("select count(*) from organization_workforce.employee_versions where employee_id = :e and superseded_at is null and employment_status = 'SEPARATED'")
                .param("e", person.employee()).query(Long.class).single()).as("the future SEPARATED version is voided").isZero();
        assertThat(jdbc.sql("select count(*) from organization_workforce.assignment_versions where employee_id = :e and superseded_at is null and effective_to_date is not null")
                .param("e", person.employee()).query(Long.class).single()).as("the assignment is open again").isZero();
    }

    @Test
    @DisplayName("#933 an unreleased future separation with no administrator involved becomes EFFECTIVE on its day, once, with EmployeeSeparated")
    void effectiveWhenNoAdministrator() throws Exception {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "RJ-2");
        String command = enterFutureSeparation(person);

        dueJob.run();
        assertThat(statusOf(command)).as("not before its day").isEqualTo("PENDING");
        clock.advance(Duration.ofDays(5));
        dueJob.run();
        dueJob.run();

        assertThat(statusOf(command)).isEqualTo("EFFECTIVE");
        assertThat(events("EmployeeSeparated", command)).isEqualTo(1);
        assertThat(events("SeparationHeld", command)).isZero();
    }
}
