package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.Uuids;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** #1566: a pending acting start that the due job meets after a held separation dated before its start never takes effect; one on the separation day does. */
class ActingStartAfterSeparationIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    BusinessZone zone;

    @Autowired
    PersonnelCommandDueJob dueJob;

    private UUID heldSeparation(PersonnelSeedSupport.Person person, LocalDate day) {
        UUID command = Uuids.timeOrdered(clock);
        jdbc.sql("""
                insert into organization_workforce.personnel_commands (personnel_command_id, employment_relationship_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at)
                values (:command, :relationship, :employee, 'SEPARATE', :day, 'HELD', now(), :by, now())""").param("command", command)
                .param("relationship", EmploymentRelationshipSeed.relationship(jdbc, person.employee())).param("employee", person.employee()).param("day", day)
                .param("by", Uuids.timeOrdered(clock)).update();
        jdbc.sql("""
                insert into organization_workforce.separation_holds (separation_hold_id, personnel_command_id, employee_id, status, created_at, updated_at)
                values (:hold, :command, :employee, 'HELD', now(), now())""").param("hold", Uuids.timeOrdered(clock)).param("command", command).param("employee", person.employee()).update();
        return command;
    }

    private UUID pendingStart(PersonnelSeedSupport.Person person, LocalDate day) {
        UUID command = Uuids.timeOrdered(clock);
        jdbc.sql("""
                insert into organization_workforce.personnel_commands (personnel_command_id, employment_relationship_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at,
                  acting_covered_org_unit_id, acting_ends_before_date, reason)
                values (:command, :relationship, :employee, 'ACTING_START', :day, 'PENDING', now(), :by, now(), :unit, :ends, 'synthetic coverage')""").param("command", command)
                .param("relationship", EmploymentRelationshipSeed.relationship(jdbc, person.employee())).param("employee", person.employee()).param("day", day)
                .param("by", Uuids.timeOrdered(clock)).param("unit", PersonnelSeedSupport.orgUnit(jdbc, clock)).param("ends", day.plusDays(20)).update();
        return command;
    }

    private String statusOf(UUID command) {
        return jdbc.sql("select status from organization_workforce.personnel_commands where personnel_command_id = :id").param("id", command).query(String.class).single();
    }

    private long dutiesOf(UUID command) {
        return jdbc.sql("select count(*) from organization_workforce.acting_duties where source_personnel_command_id = :id").param("id", command).query(Long.class).single();
    }

    @Test
    @DisplayName("#1566 a start dated after a held separation is withdrawn by the due job and writes no duty")
    void startAfterHeldSeparationIsWithdrawn() {
        LocalDate today = zone.today(clock);
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "AS-1");
        heldSeparation(person, today.plusDays(1));
        UUID start = pendingStart(person, today.plusDays(3));

        clock.advance(Duration.ofDays(5));
        dueJob.run();

        assertThat(statusOf(start)).isEqualTo("WITHDRAWN");
        assertThat(dutiesOf(start)).isZero();
    }

    @Test
    @DisplayName("#1566 a start dated on the day of a held separation still takes effect")
    void startOnTheSeparationDayTakesEffect() {
        LocalDate today = zone.today(clock);
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "AS-2");
        heldSeparation(person, today.plusDays(1));
        UUID start = pendingStart(person, today.plusDays(1));

        clock.advance(Duration.ofDays(5));
        dueJob.run();

        assertThat(statusOf(start)).isEqualTo("EFFECTIVE");
        assertThat(dutiesOf(start)).isEqualTo(1);
    }
}
