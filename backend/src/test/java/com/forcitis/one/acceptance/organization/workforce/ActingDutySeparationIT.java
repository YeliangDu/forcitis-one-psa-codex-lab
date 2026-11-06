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
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ActingDutySeparationIT extends ActingDutySupport {
    static final TestDatabase postgres = TestPostgres.database(ActingDutySeparationIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("#1566 §8-L10 due separation closes every open duty and withdraws scheduled starts")
    void separationClosesOpenDutiesAndWithdrawsScheduledOnes() throws Exception {
        UUID employee = employed(REGULAR);
        UUID first = dutyOf(start(employee, newUnit(), newPosition(), TODAY, TODAY.plusDays(10)));
        UUID second = dutyOf(start(employee, newUnit(), null, TODAY, TODAY.plusDays(12)));
        UUID scheduled = start(employee, newUnit(), newPosition(), TODAY.plusDays(5), TODAY.plusDays(15));
        LocalDate separationDay = TODAY.plusDays(2);
        UUID separation = separate(employee, separationDay.minusDays(1));
        assertThat(command(separation).get("status")).isEqualTo(PENDING);
        assertThat(command(scheduled).get("status")).isEqualTo(PENDING);

        advanceTo(separationDay);
        runDueJob();

        assertThat(command(separation).get("status")).isEqualTo(EFFECTIVE);
        assertSeparatedFrom(employee, separationDay);
        assertEnded(first, separationDay, "SEPARATION");
        assertEnded(second, separationDay, "SEPARATION");
        assertThat(command(scheduled).get("status")).isEqualTo(WITHDRAWN);
        assertThat(rows("select * from " + ACTING_DUTIES + " where source_personnel_command_id = ?", scheduled)).isEmpty();
        advanceTo(TODAY.plusDays(5));
        runDueJob();
        assertThat(command(scheduled).get("status")).isEqualTo(WITHDRAWN);
        assertThat(duties(employee)).hasSize(2);
        assertThat(eventsOf(EVENT_ACTING_STARTED, scheduled)).isZero();
        assertThat(eventsOf(EVENT_ACTING_ENDED, first)).isEqualTo(1);
        assertThat(eventsOf(EVENT_ACTING_ENDED, second)).isEqualTo(1);
    }

    @Test
    @DisplayName("#1566 §8-L10 rolling back separation also rolls back duty closure and scheduled withdrawal")
    void separationAndItsDutyEffectsShareOneTransaction() throws Exception {
        UUID employee = employed(REGULAR);
        UUID duty = dutyOf(start(employee, newUnit(), newPosition(), TODAY, TODAY.plusDays(10)));
        UUID scheduled = start(employee, newUnit(), null, TODAY.plusDays(5), TODAY.plusDays(15));
        advanceTo(TODAY.plusDays(2));
        List<Map<String, Object>> beforeDuties = duties(employee);
        List<Map<String, Object>> beforeEmployment = allVersions(EMPLOYEE_VERSIONS, employee);
        List<Map<String, Object>> beforeAssignments = allVersions(ASSIGNMENT_VERSIONS, employee);
        Map<String, Object> beforeScheduled = command(scheduled);
        long beforeCommands = commandCount(employee);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            UUID separation = separate(employee, TODAY.plusDays(1));
            assertThat(jdbc.queryForObject("select status from organization_workforce.personnel_commands where personnel_command_id = ?",
                    String.class, separation)).isEqualTo(EFFECTIVE);
            assertThat(jdbc.queryForObject("select end_cause_kind from organization_workforce.acting_duties where acting_duty_id = ?",
                    String.class, duty)).isEqualTo("SEPARATION");
            LocalDate ended = jdbc.queryForObject("select ended_on_date from organization_workforce.acting_duties where acting_duty_id = ?",
                    (rs, number) -> rs.getObject(1, LocalDate.class), duty);
            assertThat(ended).isEqualTo(TODAY.plusDays(2));
            assertThat(jdbc.queryForObject("select status from organization_workforce.personnel_commands where personnel_command_id = ?",
                    String.class, scheduled)).isEqualTo(WITHDRAWN);
            status.setRollbackOnly();
        });

        assertThat(duties(employee)).isEqualTo(beforeDuties);
        assertThat(command(scheduled)).isEqualTo(beforeScheduled);
        assertThat(commandCount(employee)).isEqualTo(beforeCommands);
        assertThat(allVersions(EMPLOYEE_VERSIONS, employee)).isEqualTo(beforeEmployment);
        assertThat(allVersions(ASSIGNMENT_VERSIONS, employee)).isEqualTo(beforeAssignments);
        assertThat(eventsOf(EVENT_ACTING_ENDED, duty)).isZero();
    }

    @Test
    @DisplayName("#1566 §8-L10 a duty starting on separation day still ends that day and publishes Ended")
    void separationCanCloseADutyOnItsStartDay() throws Exception {
        UUID employee = employed(REGULAR);
        UUID duty = dutyOf(start(employee, newUnit(), newPosition(), TODAY, TODAY.plusDays(10)));

        UUID separation = separate(employee, TODAY.minusDays(1));

        assertThat(command(separation).get("status")).isEqualTo(EFFECTIVE);
        assertSeparatedFrom(employee, TODAY);
        assertThat(duty(duty).get("starts_on_date")).isEqualTo(TODAY);
        assertEnded(duty, TODAY, "SEPARATION");
        assertThat(eventsOf(EVENT_ACTING_STARTED, duty)).isEqualTo(1);
    }

    @Test
    @DisplayName("#1566 §8-L10 a scheduled start dated on the separation day starts and is closed that day with ended_on = starts_on")
    void aScheduledStartOnTheSeparationDayIsClosedTheSameDay() throws Exception {
        UUID employee = employed(REGULAR);
        LocalDate separationDay = TODAY.plusDays(2);
        UUID startCommand = start(employee, newUnit(), newPosition(), separationDay, separationDay.plusDays(6));
        UUID separation = separate(employee, separationDay.minusDays(1));
        assertThat(command(startCommand).get("status")).isEqualTo(PENDING);
        assertThat(command(separation).get("status")).isEqualTo(PENDING);

        advanceTo(separationDay);
        runDueJob();

        UUID duty = dutyOf(startCommand);
        assertSeparatedFrom(employee, separationDay);
        assertThat(duty(duty).get("starts_on_date")).isEqualTo(separationDay);
        assertEnded(duty, separationDay, "SEPARATION");
        assertThat(eventsOf(EVENT_ACTING_STARTED, duty)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    @DisplayName("#1566 §8-L10 start after a pending or effective separation date is refused at entry")
    void aStartAfterSeparationIsRefused(int delay) throws Exception {
        UUID employee = employed(REGULAR);
        LocalDate separationDay = TODAY.plusDays(delay);
        UUID separation = separate(employee, separationDay.minusDays(1));
        assertThat(command(separation).get("status")).isEqualTo(delay == 0 ? EFFECTIVE : PENDING);
        UUID covered = newUnit();
        UUID position = newPosition();

        refused(employee, () -> start(employee, covered, position, separationDay.plusDays(1), separationDay.plusDays(5)));
    }
}
