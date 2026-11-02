package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.Uuids;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * #933: the due job executes a separation only when its hold was released; one released is never judged or held again, and an unreleased one is left
 * alone until its re-judgement exists (card #967).
 */
class DueSeparationReleasedIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    BusinessZone zone;

    @Autowired
    PersonnelCommandDueJob dueJob;

    @MockitoBean
    SeparationReview review;

    private UUID futureSeparation(PersonnelSeedSupport.Person person, String hold) {
        UUID command = Uuids.timeOrdered(clock);
        jdbc.sql("""
                insert into organization_workforce.personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at)
                values (:command, :employee, 'SEPARATE', :day, 'PENDING', now(), :by, now())""").param("command", command).param("employee", person.employee())
                .param("day", zone.today(clock).plusDays(2)).param("by", Uuids.timeOrdered(clock)).update();
        if (hold != null) {
            jdbc.sql("""
                    insert into organization_workforce.separation_holds (separation_hold_id, personnel_command_id, employee_id, status, released_at, released_by, release_reason,
                      release_channel_kind, created_at, updated_at) values (:hold, :command, :employee, 'RELEASED', now(), :by, 'synthetic', 'INTERFACE', now(), now())""")
                    .param("hold", Uuids.timeOrdered(clock)).param("command", command).param("employee", person.employee()).param("by", Uuids.timeOrdered(clock)).update();
        }
        return command;
    }

    private String statusOf(UUID command) {
        return jdbc.sql("select status from organization_workforce.personnel_commands where personnel_command_id = :id").param("id", command).query(String.class).single();
    }

    private long events(String type, UUID command) {
        return jdbc.sql("""
                select count(distinct p.serialized_event) from (select event_type, serialized_event from platform_integration.event_publication
                                                                union all
                                                                select event_type, serialized_event from platform_integration.event_publication_archive) p
                 where p.event_type like :type and p.serialized_event like :command
                """).param("type", "%." + type).param("command", "%" + command + "%").query(Long.class).single();
    }

    @Test
    @DisplayName("#933 ADR-0068 2.3 a released future separation is executed on its day even when a judgement would hold it now: effective, one hold row, no second SeparationHeld, one EmployeeSeparated")
    void releasedIsNotJudgedAgain() {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "RL-1");
        UUID command = futureSeparation(person, "RELEASED");
        when(review.review(any(), any(), any())).thenReturn(new SeparationReview.Verdict(Set.of(new EmployeeId(person.employee())), true, 1, "", "", Set.of()));

        clock.advance(Duration.ofDays(5));
        dueJob.run();
        dueJob.run();

        assertThat(statusOf(command)).isEqualTo("EFFECTIVE");
        assertThat(jdbc.sql("select count(*) from organization_workforce.separation_holds where personnel_command_id = :id").param("id", command).query(Long.class).single()).isEqualTo(1);
        assertThat(events("EmployeeSeparated", command)).isEqualTo(1);
        assertThat(events("SeparationHeld", command)).isZero();
    }

    @Test
    @DisplayName("#967 an unreleased future separation the job runs after its day is not judged: it takes effect as written")
    void unreleasedLateRunTakesEffectUnjudged() {
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "RL-2");
        UUID command = futureSeparation(person, null);

        clock.advance(Duration.ofDays(5));
        dueJob.run();

        assertThat(statusOf(command)).isEqualTo("EFFECTIVE");
        assertThat(events("EmployeeSeparated", command)).isEqualTo(1);
        assertThat(events("SeparationHeld", command)).isZero();
    }
}
