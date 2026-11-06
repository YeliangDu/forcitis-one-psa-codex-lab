package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.ActingDutyNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** #1566 amended rule 6 / done-when 10: earlier separations close started duties and withdraw later starts. */
class ActingDutySeparationEarlyIT extends ActingDutySupport {
    static final TestDatabase postgres = TestPostgres.database(ActingDutySeparationEarlyIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private DataSource dataSource;

    @ParameterizedTest
    @CsvSource({"2, false", "2, true", "1, false"})
    @DisplayName("#1566 §8-L10b earlier separation closes every started duty at starts_on, including the preceding-day boundary")
    void earlierSeparationClosesStartedDuties(int daysEarlier, boolean secondDuty) throws Exception {
        UUID employee = employed(REGULAR);
        UUID first = dutyOf(startKeepingVersions(employee, newPosition(), TODAY));
        UUID second = secondDuty ? dutyOf(startKeepingVersions(employee, null, TODAY)) : null;
        assertOpen(first, TODAY, TODAY.plusDays(10));
        if (secondDuty) {
            assertOpen(second, TODAY, TODAY.plusDays(10));
        }
        LocalDate separationDay = TODAY.minusDays(daysEarlier);

        UUID separation = separate(employee, separationDay.minusDays(1));

        assertThat(command(separation).get("status")).isEqualTo(EFFECTIVE);
        assertSeparatedFrom(employee, separationDay);
        assertClosedAtStart(first);
        if (secondDuty) {
            assertClosedAtStart(second);
        }
        assertThat(duties(employee)).hasSize(secondDuty ? 2 : 1);
        assertNoActingEnd(employee);
        // Separation changes the person's versions, so no before/after equality is asserted here.
    }

    @Test
    @DisplayName("#1566 §8-L10b rolling back an earlier separation restores duties, commands, versions and published events")
    void earlierSeparationAndDutyEffectsRollBackTogether() throws Exception {
        UUID employee = employed(REGULAR);
        UUID first = dutyOf(startKeepingVersions(employee, newPosition(), TODAY));
        UUID second = dutyOf(startKeepingVersions(employee, null, TODAY));
        State before = stateOf(employee);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            UUID separation = separate(employee, TODAY.minusDays(3));
            assertThat(jdbc.queryForObject("select status from organization_workforce.personnel_commands where personnel_command_id = ?",
                    String.class, separation)).isEqualTo(EFFECTIVE);
            for (UUID duty : List.of(first, second)) {
                DutyEnd closed = jdbc.queryForObject("""
                        select starts_on_date, ended_on_date, end_cause_kind
                        from organization_workforce.acting_duties where acting_duty_id = ?
                        """, (rs, number) -> new DutyEnd(rs.getObject(1, LocalDate.class), rs.getObject(2, LocalDate.class), rs.getString(3)), duty);
                assertThat(closed).isEqualTo(new DutyEnd(TODAY, TODAY, "SEPARATION"));
            }
            status.setRollbackOnly();
        });

        assertThat(stateOf(employee)).as("rollback restores every row and event, including both version histories").isEqualTo(before);
        assertThat(eventsOf(EVENT_ACTING_STARTED, first)).isEqualTo(1);
        assertThat(eventsOf(EVENT_ACTING_STARTED, second)).isEqualTo(1);
        assertThat(eventsOf(EVENT_ACTING_ENDED, first)).isZero();
        assertThat(eventsOf(EVENT_ACTING_ENDED, second)).isZero();
        assertNoActingEnd(employee);
    }

    @Test
    @DisplayName("#1566 §8-L10b one due-job run withdraws a start after an earlier PENDING separation")
    void dueStartAfterPendingEarlierSeparationIsWithdrawn() throws Exception {
        UUID employee = employed(REGULAR);
        LocalDate starts = TODAY.plusDays(5);
        UUID scheduled = startKeepingVersions(employee, newPosition(), starts);
        UUID separation = separate(employee, TODAY.plusDays(1));
        assertThat(command(scheduled).get("status")).isEqualTo(PENDING);
        assertThat(command(separation).get("status")).isEqualTo(PENDING);

        advanceTo(starts);
        runDueJob();

        assertThat(command(separation).get("status")).isEqualTo(EFFECTIVE);
        assertSeparatedFrom(employee, TODAY.plusDays(2));
        assertWithdrawnWithoutDutyEvents(employee, scheduled);
        // A pending separation writes its future versions when it is entered, so no version equality is asserted around the job.
    }

    @Test
    @DisplayName("#1566 §8-L10b a pending start is withdrawn after an earlier EFFECTIVE separation entered later")
    void dueStartAfterEffectiveEarlierSeparationIsWithdrawn() throws Exception {
        UUID employee = employed(REGULAR);
        LocalDate starts = TODAY.plusDays(5);
        UUID scheduled = startKeepingVersions(employee, newPosition(), starts);
        assertThat(command(scheduled).get("status")).isEqualTo(PENDING);
        UUID separation = separate(employee, TODAY.minusDays(1));
        assertThat(command(separation).get("status")).isEqualTo(EFFECTIVE);
        assertSeparatedFrom(employee, TODAY);
        Versions afterSeparation = versions(employee);

        advanceTo(starts);
        runDueJob();

        assertThat(command(separation).get("status")).isEqualTo(EFFECTIVE);
        assertWithdrawnWithoutDutyEvents(employee, scheduled);
        assertThat(versions(employee)).as("withdrawing the later start does not change the separated person's versions").isEqualTo(afterSeparation);
    }

    @Test
    @DisplayName("#1566 §8-L10b a start after an earlier HELD administrator separation is withdrawn while the hold remains")
    void dueStartAfterHeldEarlierSeparationIsWithdrawn() throws Exception {
        Staff leaver = administrator();
        UUID employee = leaver.employeeId();
        LocalDate starts = TODAY.plusDays(5);
        UUID scheduled = startKeepingVersions(employee, newPosition(), starts);
        assertThat(command(scheduled).get("status")).isEqualTo(PENDING);
        Held held = heldSeparationOn(leaver, TODAY.plusDays(2));
        Versions afterHold = versions(employee);

        advanceTo(starts);
        runDueJob();

        assertThat(command(held.command()).get("status")).isEqualTo(HELD);
        assertThat(holdStatus(held.hold())).isEqualTo(HELD);
        assertWithdrawnWithoutDutyEvents(employee, scheduled);
        assertThat(versions(employee)).as("withdrawing the start leaves both histories as the held separation left them").isEqualTo(afterHold);
    }

    private UUID startKeepingVersions(UUID employee, UUID position, LocalDate starts) throws Exception {
        Versions before = versions(employee);
        UUID command = start(employee, newUnit(), position, starts, starts.plusDays(10));
        assertThat(versions(employee)).as("acting duty entry does not rewrite the person's own versions").isEqualTo(before);
        return command;
    }

    private void assertClosedAtStart(UUID duty) throws Exception {
        assertThat(duty(duty).get("starts_on_date")).isEqualTo(TODAY);
        assertEnded(duty, TODAY, "SEPARATION");
        assertThat(eventsOf(EVENT_ACTING_STARTED, duty)).isEqualTo(1);
    }

    private void assertNoActingEnd(UUID employee) throws Exception {
        assertThat(rows("select * from " + COMMANDS + " where employee_id = ? and command_kind = ?", stored(employee), ACTING_END)).isEmpty();
    }

    private void assertWithdrawnWithoutDutyEvents(UUID employee, UUID command) throws Exception {
        assertThat(command(command).get("status")).isEqualTo(WITHDRAWN);
        assertThat(rows("select * from " + ACTING_DUTIES + " where source_personnel_command_id = ?", command)).isEmpty();
        assertThat(duties(employee)).isEmpty();
        for (UUID reference : List.of(employee, command)) {
            assertThat(eventsOf(EVENT_ACTING_STARTED, reference)).isZero();
            assertThat(eventsOf(EVENT_ACTING_ENDED, reference)).isZero();
        }
        assertNoActingEnd(employee);
    }

    private record DutyEnd(LocalDate starts, LocalDate ended, String cause) {
    }

    private record Versions(List<Map<String, Object>> employment, List<Map<String, Object>> assignments) {
    }

    private Versions versions(UUID employee) throws Exception {
        return new Versions(allVersions(EMPLOYEE_VERSIONS, employee), allVersions(ASSIGNMENT_VERSIONS, employee));
    }

    private record State(List<Map<String, Object>> duties, List<Map<String, Object>> commands, Versions versions, List<String> events) {
    }

    private State stateOf(UUID employee) throws Exception {
        return new State(duties(employee), rows("select * from " + COMMANDS + " where employee_id = ? order by personnel_command_id", stored(employee)),
                versions(employee), publishedEvents(employee));
    }

    /** Listener completion can move publications to the archive; compare distinct envelopes across both tables. */
    private List<String> publishedEvents(UUID employee) throws Exception {
        TreeSet<String> events = new TreeSet<>();
        try (Connection registry = schemaConnection("platform_integration")) {
            for (String table : List.of("event_publication", "event_publication_archive")) {
                for (Map<String, Object> row : query(registry, "select serialized_event from " + table + " where serialized_event like ?", "%" + text(employee) + "%")) {
                    events.add((String) row.get("serialized_event"));
                }
            }
        }
        return List.copyOf(events);
    }
}
