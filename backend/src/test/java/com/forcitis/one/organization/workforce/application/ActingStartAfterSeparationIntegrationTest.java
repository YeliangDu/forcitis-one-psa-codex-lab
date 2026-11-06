package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.Uuids;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * #1566: an acting start the due job meets only after an effective separation (the job had not run yet, or an earlier apply failed) takes effect and is
 * closed in the same transaction on the separation day, with Started before Ended; one dated after an earlier separation is withdrawn.
 */
class ActingStartAfterSeparationIntegrationTest extends WorkforceIntegrationSupport {
    @Autowired
    BusinessZone zone;

    @Autowired
    PersonnelCommandDueJob dueJob;

    private UUID command(PersonnelSeedSupport.Person person, String kind, LocalDate day, String status) {
        UUID command = Uuids.timeOrdered(clock);
        jdbc.sql("""
                insert into organization_workforce.personnel_commands (personnel_command_id, employment_relationship_id, employee_id, command_kind, effective_date, status, created_at, created_by, updated_at,
                  applied_at, target_org_unit_id, target_position_id, is_target_fee_earner, target_employment_phase_kind)
                values (:command, :relationship, :employee, :kind, :day, :status, now(), :by, now(), case when :status = 'EFFECTIVE' then now() end,
                  case when :kind = 'REINSTATE' then :unit end, case when :kind = 'REINSTATE' then :position end, case when :kind = 'REINSTATE' then false end,
                  case when :kind = 'REINSTATE' then 'REGULAR' end)""").param("unit", person.orgUnit()).param("position", person.position())
                .param("command", command)
                .param("relationship", EmploymentRelationshipSeed.relationship(jdbc, person.employee())).param("employee", person.employee()).param("kind", kind).param("day", day)
                .param("status", status).param("by", Uuids.timeOrdered(clock)).update();
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

    private List<Map<String, Object>> dutiesOf(UUID command) {
        return jdbc.sql("select * from organization_workforce.acting_duties where source_personnel_command_id = :id").param("id", command).query().listOfRows();
    }

    private long events(String type, UUID duty) {
        return jdbc.sql("""
                select count(distinct p.serialized_event) from (select event_type, serialized_event from platform_integration.event_publication
                                                                union all
                                                                select event_type, serialized_event from platform_integration.event_publication_archive) p
                 where p.event_type like :type and p.serialized_event like :duty
                """).param("type", "%." + type).param("duty", "%" + duty + "%").query(Long.class).single();
    }

    @Test
    @DisplayName("#1566 a start due on the separation day takes effect and is closed the same day: Started and Ended once each")
    void sameDayStartIsClosedBySeparation() {
        LocalDate today = zone.today(clock);
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "AC-1");
        command(person, "SEPARATE", today, "EFFECTIVE");
        UUID start = pendingStart(person, today);

        dueJob.run();
        dueJob.run();

        assertThat(statusOf(start)).isEqualTo("EFFECTIVE");
        Map<String, Object> duty = dutiesOf(start).getFirst();
        assertThat(duty).containsEntry("ended_on_date", java.sql.Date.valueOf(today)).containsEntry("end_cause_kind", "SEPARATION");
        UUID id = (UUID) duty.get("acting_duty_id");
        assertThat(events("EmployeeActingDutyStarted", id)).isEqualTo(1);
        assertThat(events("EmployeeActingDutyEnded", id)).isEqualTo(1);
    }

    @Test
    @DisplayName("#1566 an overdue start dated before the separation day runs and is closed on the separation day")
    void overdueStartIsClosedOnTheSeparationDay() {
        LocalDate today = zone.today(clock);
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "AC-2");
        command(person, "SEPARATE", today, "EFFECTIVE");
        UUID start = pendingStart(person, today.minusDays(2));

        dueJob.run();

        assertThat(statusOf(start)).isEqualTo("EFFECTIVE");
        Map<String, Object> duty = dutiesOf(start).getFirst();
        assertThat(duty).containsEntry("starts_on_date", java.sql.Date.valueOf(today.minusDays(2)))
                .containsEntry("ended_on_date", java.sql.Date.valueOf(today)).containsEntry("end_cause_kind", "SEPARATION");
        UUID id = (UUID) duty.get("acting_duty_id");
        assertThat(events("EmployeeActingDutyStarted", id)).isEqualTo(1);
        assertThat(events("EmployeeActingDutyEnded", id)).isEqualTo(1);
    }

    @Test
    @DisplayName("#1566 a start whose term ended before the separation day stays open and expires on its own end")
    void startEndedBeforeTheSeparationIsLeftToExpiry() {
        LocalDate today = zone.today(clock);
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "AC-3");
        UUID start = pendingStart(person, today.minusDays(30));
        command(person, "SEPARATE", today, "EFFECTIVE");

        dueJob.run();

        Map<String, Object> duty = dutiesOf(start).getFirst();
        assertThat(duty).containsEntry("ended_on_date", java.sql.Date.valueOf(today.minusDays(10))).containsEntry("end_cause_kind", "EXPIRED");
    }

    @Test
    @DisplayName("#1566 a start dated after an earlier effective separation is withdrawn and writes no duty")
    void startAfterTheSeparationIsWithdrawn() {
        LocalDate today = zone.today(clock);
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "AC-4");
        command(person, "SEPARATE", today.minusDays(3), "EFFECTIVE");
        UUID start = pendingStart(person, today);

        dueJob.run();

        assertThat(statusOf(start)).isEqualTo("WITHDRAWN");
        assertThat(dutiesOf(start)).isEmpty();
    }

    @Test
    @DisplayName("#1566 a late start is closed for a separation even when a reinstatement follows it: the reinstatement does not reopen the duty")
    void lateStartIsClosedAlthoughReinstatedAfterwards() {
        LocalDate today = zone.today(clock);
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "AC-5");
        command(person, "SEPARATE", today.minusDays(2), "EFFECTIVE");
        command(person, "REINSTATE", today.minusDays(1), "EFFECTIVE");
        UUID start = pendingStart(person, today.minusDays(3));

        dueJob.run();

        Map<String, Object> duty = dutiesOf(start).getFirst();
        assertThat(duty).containsEntry("ended_on_date", java.sql.Date.valueOf(today.minusDays(2))).containsEntry("end_cause_kind", "SEPARATION");
        UUID id = (UUID) duty.get("acting_duty_id");
        assertThat(events("EmployeeActingDutyStarted", id)).isEqualTo(1);
        assertThat(events("EmployeeActingDutyEnded", id)).isEqualTo(1);
    }

    @Test
    @DisplayName("#1566 a start dated after a separation that was reinstated before it takes effect and stays open")
    void startAfterReinstatementStaysOpen() {
        LocalDate today = zone.today(clock);
        PersonnelSeedSupport.Person person = PersonnelSeedSupport.employed(jdbc, clock, "AC-6");
        command(person, "SEPARATE", today.minusDays(5), "EFFECTIVE");
        command(person, "REINSTATE", today.minusDays(3), "EFFECTIVE");
        UUID start = pendingStart(person, today);

        dueJob.run();

        assertThat(statusOf(start)).isEqualTo("EFFECTIVE");
        Map<String, Object> duty = dutiesOf(start).getFirst();
        assertThat(duty.get("ended_on_date")).isNull();
        assertThat(events("EmployeeActingDutyEnded", (UUID) duty.get("acting_duty_id"))).isZero();
    }
}
