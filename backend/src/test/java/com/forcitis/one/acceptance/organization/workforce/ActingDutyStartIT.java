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

class ActingDutyStartIT extends ActingDutySupport {
    static final TestDatabase postgres = TestPostgres.database(ActingDutyStartIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#1566 §8-L1 future start stays scheduled without a duty or Started event")
    void aFutureStartWaitsForItsDate() throws Exception {
        UUID employee = employed(REGULAR);
        LocalDate starts = TODAY.plusDays(3);
        UUID command = start(employee, newUnit(), newPosition(), starts, starts.plusDays(5));

        assertThat(command(command).get("status")).isEqualTo(PENDING);
        assertThat(duties(employee)).isEmpty();
        assertThat(eventsOf(EVENT_ACTING_STARTED, command)).isZero();
        advanceTo(starts.minusDays(1));
        runDueJob();
        assertThat(command(command).get("status")).isEqualTo(PENDING);
        assertThat(duties(employee)).isEmpty();
        // Before a duty id exists, the start command id also identifies its prospective Started event.
        assertThat(eventsOf(EVENT_ACTING_STARTED, command)).isZero();
    }

    @Test
    @DisplayName("#1566 §8-L2 due job starts once on starts_on and creates no expiry command")
    void theDueJobStartsExactlyOnceWithoutAnExpiryCommand() throws Exception {
        UUID employee = employed(REGULAR);
        LocalDate starts = TODAY.plusDays(2);
        LocalDate ends = starts.plusDays(5);
        long before = commandCount(employee);
        UUID command = start(employee, newUnit(), newPosition(), starts, ends);
        assertThat(commandCount(employee)).isEqualTo(before + 1);

        advanceTo(starts);
        runDueJob();

        UUID duty = dutyOf(command);
        assertOpen(duty, starts, ends);
        assertThat(command(command).get("status")).isEqualTo(EFFECTIVE);
        assertThat(eventsOf(EVENT_ACTING_STARTED, duty)).isEqualTo(1);
        assertThat(commandCount(employee)).as("expiry is not another personnel command").isEqualTo(before + 1);
        Map<String, Object> first = duty(duty);
        runDueJob();
        assertThat(duties(employee)).containsExactly(first);
        assertThat(eventsOf(EVENT_ACTING_STARTED, duty)).isEqualTo(1);
        assertThat(commandCount(employee)).isEqualTo(before + 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("#1566 §8-L3 equal and reversed period edges are refused")
    void theEndMustBeStrictlyAfterTheStart(int length) throws Exception {
        UUID employee = employed(REGULAR);
        UUID covered = newUnit();
        UUID position = newPosition();
        refused(employee, () -> start(employee, covered, position, TODAY, TODAY.plusDays(length)));
    }

    @Test
    @DisplayName("#1566 §8-L3 a one-day duty is accepted")
    void aOneDayDutyIsValid() throws Exception {
        UUID employee = employed(REGULAR);
        UUID command = start(employee, newUnit(), newPosition(), TODAY, TODAY.plusDays(1));

        assertThat(command(command).get("status")).isEqualTo(EFFECTIVE);
        assertOpen(dutyOf(command), TODAY, TODAY.plusDays(1));
    }

    @Test
    @DisplayName("#1566 A3 two people may cover the same unit and position during the same term")
    void differentPeopleCanCoverTheSamePost() throws Exception {
        UUID first = employed(REGULAR);
        UUID second = employed(REGULAR);
        UUID covered = newUnit();
        UUID position = newPosition();
        LocalDate ends = TODAY.plusDays(5);

        UUID firstDuty = dutyOf(start(first, covered, position, TODAY, ends));
        UUID secondDuty = dutyOf(start(second, covered, position, TODAY, ends));

        assertThat(firstDuty).isNotEqualTo(secondDuty);
        assertOpen(firstDuty, TODAY, ends);
        assertOpen(secondDuty, TODAY, ends);
        assertThat(duty(firstDuty)).containsEntry("employee_id", stored(first))
                .containsEntry("covered_org_unit_id", stored(covered)).containsEntry("covered_position_id", position);
        assertThat(duty(secondDuty)).containsEntry("employee_id", stored(second))
                .containsEntry("covered_org_unit_id", stored(covered)).containsEntry("covered_position_id", position);
        assertThat(duties(first)).hasSize(1);
        assertThat(duties(second)).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    @DisplayName("#1566 A5 immediate and future starts take effect without approval tasks or requests")
    void aStartNeedsNoApproval(int delay) throws Exception {
        UUID employee = employed(REGULAR);
        UUID covered = newUnit();
        UUID position = newPosition();
        LocalDate starts = TODAY.plusDays(delay);
        List<Map<String, Object>> before = approvalCounts();

        UUID command = start(employee, covered, position, starts, starts.plusDays(3));
        assertThat(approvalCounts()).isEqualTo(before);
        if (delay > 0) {
            advanceTo(starts);
            runDueJob();
        }

        UUID duty = dutyOf(command);
        assertThat(command(command).get("status")).isEqualTo(EFFECTIVE);
        assertThat(eventsOf(EVENT_ACTING_STARTED, duty)).isEqualTo(1);
        assertThat(approvalCounts()).as("no approval task or request when the start takes effect").isEqualTo(before);
    }
}
