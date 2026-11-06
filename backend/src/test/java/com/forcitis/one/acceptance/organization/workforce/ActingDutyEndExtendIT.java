package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.ActingDutyNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.jayway.jsonpath.JsonPath;
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

class ActingDutyEndExtendIT extends ActingDutySupport {
    static final TestDatabase postgres = TestPostgres.database(ActingDutyEndExtendIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#1566 §8-L7 early end records its first non-holding day and publishes Ended exactly once")
    void earlyEndPublishesOnce() throws Exception {
        UUID employee = employed(REGULAR);
        UUID duty = dutyOf(start(employee, newUnit(), newPosition(), TODAY, TODAY.plusDays(10)));
        LocalDate ended = TODAY.plusDays(2);
        advanceTo(ended);

        UUID command = end(employee, duty, ended);

        assertThat(command(command).get("status")).isEqualTo(EFFECTIVE);
        assertEnded(duty, ended, "ENDED_EARLY");
        assertThat(eventsOf(EVENT_ACTING_STARTED, duty)).isEqualTo(1);
        Map<String, Object> first = duty(duty);
        runDueJob();
        runDueJob();
        assertThat(duty(duty)).isEqualTo(first);
        assertThat(eventsOf(EVENT_ACTING_ENDED, duty)).isEqualTo(1);
        advanceTo(TODAY.plusDays(10));
        runDueJob();
        assertThat(duty(duty)).isEqualTo(first);
        assertThat(eventsOf(EVENT_ACTING_ENDED, duty)).isEqualTo(1);
    }

    @Test
    @DisplayName("#1566 §8-L7 an end dated ahead waits PENDING and is applied by the due job on its date, ENDED_EARLY, Ended once")
    void aFutureDatedEndIsAppliedOnItsDate() throws Exception {
        UUID employee = employed(REGULAR);
        UUID duty = dutyOf(start(employee, newUnit(), newPosition(), TODAY, TODAY.plusDays(10)));
        LocalDate ended = TODAY.plusDays(3);

        UUID command = end(employee, duty, ended);

        assertThat(command(command).get("status")).isEqualTo(PENDING);
        assertOpen(duty, TODAY, TODAY.plusDays(10));
        assertThat(eventsOf(EVENT_ACTING_ENDED, duty)).isZero();
        advanceTo(ended.minusDays(1));
        runDueJob();
        assertOpen(duty, TODAY, TODAY.plusDays(10));
        advanceTo(ended);
        runDueJob();
        assertThat(command(command).get("status")).isEqualTo(EFFECTIVE);
        assertEnded(duty, ended, "ENDED_EARLY");
        Map<String, Object> first = duty(duty);
        runDueJob();
        assertThat(duty(duty)).isEqualTo(first);
        assertThat(eventsOf(EVENT_ACTING_ENDED, duty)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("#1566 §8-L7 end on or before starts_on is refused without a command or duty change")
    void endCannotBeOnOrBeforeTheStart(int offset) throws Exception {
        UUID employee = employed(REGULAR);
        UUID duty = dutyOf(start(employee, newUnit(), newPosition(), TODAY, TODAY.plusDays(10)));

        refused(employee, () -> end(employee, duty, TODAY.plusDays(offset)));

        assertThat(eventsOf(EVENT_ACTING_ENDED, duty)).isZero();
    }

    @Test
    @DisplayName("#1566 §8-L8 extending moves expiry without publishing Started or Ended, then expires at the new end")
    void extensionMovesTheExpiryDate() throws Exception {
        UUID employee = employed(REGULAR);
        LocalDate originalEnd = TODAY.plusDays(3);
        LocalDate newEnd = TODAY.plusDays(7);
        UUID duty = dutyOf(start(employee, newUnit(), newPosition(), TODAY, originalEnd));
        int started = eventsOf(EVENT_ACTING_STARTED, duty);
        int ended = eventsOf(EVENT_ACTING_ENDED, duty);
        int allEvents = eventsMentioning(duty);
        assertThat(started).isEqualTo(1);
        assertThat(ended).isZero();
        advanceTo(TODAY.plusDays(1));

        UUID command = extend(employee, duty, TODAY.plusDays(1), newEnd);

        assertThat(command(command).get("status")).isEqualTo(EFFECTIVE);
        assertOpen(duty, TODAY, newEnd);
        assertThat(eventsOf(EVENT_ACTING_STARTED, duty)).isEqualTo(started);
        assertThat(eventsOf(EVENT_ACTING_ENDED, duty)).isEqualTo(ended);
        assertThat(eventsMentioning(duty)).as("an extension publishes no event of any type").isEqualTo(allEvents);
        advanceTo(originalEnd);
        runDueJob();
        assertOpen(duty, TODAY, newEnd);
        assertThat(eventsOf(EVENT_ACTING_ENDED, duty)).isEqualTo(ended);
        advanceTo(newEnd.minusDays(1));
        runDueJob();
        assertOpen(duty, TODAY, newEnd);
        advanceTo(newEnd);
        runDueJob();
        assertEnded(duty, newEnd, "EXPIRED");
        assertThat(eventsOf(EVENT_ACTING_STARTED, duty)).isEqualTo(started);
    }

    @Test
    @DisplayName("#1566 §8-L8 an ended duty cannot be extended")
    void extensionCannotReopenAnEndedDuty() throws Exception {
        UUID employee = employed(REGULAR);
        LocalDate ends = TODAY.plusDays(2);
        UUID duty = dutyOf(start(employee, newUnit(), newPosition(), TODAY, ends));
        advanceTo(ends);
        runDueJob();
        assertEnded(duty, ends, "EXPIRED");

        refused(employee, () -> extend(employee, duty, ends, ends.plusDays(5)));

        assertThat(eventsOf(EVENT_ACTING_STARTED, duty)).isEqualTo(1);
        assertThat(eventsOf(EVENT_ACTING_ENDED, duty)).isEqualTo(1);
    }

    @Test
    @DisplayName("#1566 §8-L9 expiry has no personnel command id and is unchanged on a second daily run")
    void expiryIsACommandlessIdempotentEnd() throws Exception {
        UUID employee = employed(REGULAR);
        LocalDate ends = TODAY.plusDays(3);
        UUID duty = dutyOf(start(employee, newUnit(), newPosition(), TODAY, ends));
        long commands = commandCount(employee);
        advanceTo(ends.minusDays(1));
        runDueJob();
        assertOpen(duty, TODAY, ends);
        assertThat(eventsOf(EVENT_ACTING_ENDED, duty)).isZero();

        advanceTo(ends);
        runDueJob();

        assertEnded(duty, ends, "EXPIRED");
        assertExpiryHasNoCommand(duty);
        assertThat(commandCount(employee)).isEqualTo(commands);
        Map<String, Object> first = duty(duty);
        List<String> events = eventJsonOf(EVENT_ACTING_ENDED, duty);
        runDueJob();
        assertThat(duty(duty)).isEqualTo(first);
        assertThat(eventJsonOf(EVENT_ACTING_ENDED, duty)).containsExactlyInAnyOrderElementsOf(events);
        assertThat(eventsOf(EVENT_ACTING_ENDED, duty)).isEqualTo(1);
        assertThat(commandCount(employee)).isEqualTo(commands);
    }

    private void assertExpiryHasNoCommand(UUID duty) throws Exception {
        List<String> events = eventJsonOf(EVENT_ACTING_ENDED, duty);
        assertThat(events).isNotEmpty();
        for (String json : events) {
            Map<String, Object> payload = JsonPath.read(json, "$");
            assertThat(payload).as("the event payload identifies the acting duty").containsKey("actingDutyId");
            assertThat(payload.get("personnelCommandId")).as("expiry has no personnel command id, absent or null").isNull();
        }
    }
}
