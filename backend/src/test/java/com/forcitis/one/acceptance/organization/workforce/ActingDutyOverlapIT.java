package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.ActingDutyNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class ActingDutyOverlapIT extends ActingDutySupport {
    static final TestDatabase postgres = TestPostgres.database(ActingDutyOverlapIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("#1566 §8-L4 an active duty blocks overlapping coverage of the same position or unit head")
    void anActiveDutyBlocksTheSameCoveredPost(boolean unitHead) throws Exception {
        UUID employee = employed(REGULAR);
        UUID covered = newUnit();
        UUID position = unitHead ? null : newPosition();
        UUID duty = dutyOf(start(employee, covered, position, TODAY, TODAY.plusDays(5)));
        assertOpen(duty, TODAY, TODAY.plusDays(5));
        assertThat(duty(duty)).containsEntry("covered_position_id", position);

        refused(employee, () -> start(employee, covered, position, TODAY.plusDays(1), TODAY.plusDays(6)));

        assertThat(duties(employee)).hasSize(1);
        assertThat(eventsOf(EVENT_ACTING_STARTED, duty)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("#1566 §8-L4 touching half-open terms are accepted for a position and for a unit head")
    void backToBackDutiesAreAccepted(boolean unitHead) throws Exception {
        UUID employee = employed(REGULAR);
        UUID covered = newUnit();
        UUID position = unitHead ? null : newPosition();
        LocalDate boundary = TODAY.plusDays(3);
        UUID first = dutyOf(start(employee, covered, position, TODAY, boundary));

        UUID secondCommand = start(employee, covered, position, boundary, boundary.plusDays(3));
        assertThat(command(secondCommand).get("status")).isEqualTo(PENDING);
        advanceTo(boundary);
        runDueJob();

        UUID second = dutyOf(secondCommand);
        assertThat(second).isNotEqualTo(first);
        assertEnded(first, boundary, "EXPIRED");
        assertOpen(second, boundary, boundary.plusDays(3));
        assertThat(duties(employee)).hasSize(2);
    }

    @Test
    @DisplayName("#1566 §8-L5 covering one's own current unit and position is refused")
    void aDutyCannotRepeatThePersonsOwnPost() throws Exception {
        UUID position = newPosition();
        UUID employee = employedAs(unit, REGULAR, position);

        refused(employee, () -> start(employee, unit, position, TODAY, TODAY.plusDays(4)));
    }

    @Test
    @DisplayName("#1566 §8-L5 covering another unit preserves every primary assignment row")
    void coveringAnotherUnitLeavesTheOwnAssignmentUnchanged() throws Exception {
        UUID position = newPosition();
        UUID employee = employedAs(unit, REGULAR, position);
        UUID covered = newUnit();
        List<Map<String, Object>> versions = allVersions(ASSIGNMENT_VERSIONS, employee);
        List<Map<String, Object>> roots = rows("select * from employee_assignments where employee_id = ? order by assignment_id", stored(employee));
        assertThat(versions).isNotEmpty();
        assertThat(roots).hasSize(1);

        UUID duty = dutyOf(start(employee, covered, position, TODAY, TODAY.plusDays(4)));

        assertThat(duty(duty)).containsEntry("covered_org_unit_id", stored(covered));
        assertOpen(duty, TODAY, TODAY.plusDays(4));
        assertThat(allVersions(ASSIGNMENT_VERSIONS, employee)).isEqualTo(versions);
        assertThat(rows("select * from employee_assignments where employee_id = ? order by assignment_id", stored(employee))).isEqualTo(roots);
    }

    @Test
    @DisplayName("#1566 §8-L6 the covered employee cannot be the stand-in themself")
    void aPersonCannotCoverThemself() throws Exception {
        UUID employee = employed(REGULAR);
        UUID covered = newUnit();
        UUID position = newPosition();

        refused(employee, () -> commands().enter(startFields(employee, covered, position, employee, TODAY, TODAY.plusDays(4))));
    }
}
