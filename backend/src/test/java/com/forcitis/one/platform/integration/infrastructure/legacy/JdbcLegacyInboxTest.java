package com.forcitis.one.platform.integration.infrastructure.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.fixture.doubles.MutableClock;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.LegacyInbox;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.application.InMemorySourceReferences;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcLegacyInboxTest {

    static final BusinessZone ZONE = new BusinessZone(ZoneId.of("Asia/Shanghai"));
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T02:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("ADR-0033 an employee without a LEGACY_SYSTEM EMPLOYEE reference: available = false with an ALERT, the old database is not asked")
    void withoutReferenceIsUnavailable() {
        InMemorySourceReferences references = new InMemorySourceReferences()
                .confirm(ExternalSource.LEGACY_SYSTEM, SourceObjectKind.EMPLOYEE, 2, TestAccounts.employee(2).value());
        JdbcClient neverAsked = JdbcClient.create(new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/none", "nobody", UUID.randomUUID().toString()));
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        Logger logger = (Logger) LoggerFactory.getLogger(JdbcLegacyInbox.class);
        logger.addAppender(logged);
        try {
            LegacyInbox.LegacyTodos result = new JdbcLegacyInbox(neverAsked, "dbo.v_psa_one_legacy_todo", null, references, ZONE, CLOCK, new SimpleMeterRegistry())
                    .readTodos(TestAccounts.employee(1));

            assertThat(result.available()).isFalse();
            assertThat(result.todos()).isEmpty();
            assertThat(logged.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).startsWith("ALERT ");
            });
            assertThat(references.allocations()).as("a lookup never allocates").isEmpty();
        } finally {
            logger.detachAppender(logged);
        }
    }

    @Test
    @DisplayName("RULING-P3B-14 the same employee three times in a business day: unavailable each time, counted 3 times, one ALERT and WARN after it; the next day alerts again")
    void unmappedEmployeeAlertsOncePerBusinessDay() {
        InMemorySourceReferences references = new InMemorySourceReferences();
        JdbcClient neverAsked = JdbcClient.create(new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:1/none", "nobody", UUID.randomUUID().toString()));
        MutableClock clock = new MutableClock(Instant.parse("2026-10-15T02:00:00Z"), ZoneOffset.UTC);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        JdbcLegacyInbox inbox = new JdbcLegacyInbox(neverAsked, "dbo.v_psa_one_legacy_todo", null, references, ZONE, clock, meters);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        Logger logger = (Logger) LoggerFactory.getLogger(JdbcLegacyInbox.class);
        logger.addAppender(logged);
        try {
            List<LegacyInbox.LegacyTodos> answers = List.of(inbox.readTodos(TestAccounts.employee(1)), inbox.readTodos(TestAccounts.employee(1)));
            clock.set(Instant.parse("2026-10-15T15:59:59Z"));
            LegacyInbox.LegacyTodos lastOfTheDay = inbox.readTodos(TestAccounts.employee(1));

            assertThat(answers).allSatisfy(answer -> assertThat(answer.available()).isFalse());
            assertThat(lastOfTheDay.available()).isFalse();
            assertThat(meters.counter("forcitis.platform.integration.legacy.inbox.unmapped").count()).isEqualTo(3.0);
            assertThat(logged.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.ERROR, Level.WARN, Level.WARN);
            assertThat(logged.list.getFirst().getFormattedMessage()).startsWith("ALERT ");
            assertThat(logged.list.subList(1, 3)).noneMatch(event -> event.getFormattedMessage().startsWith("ALERT"));

            inbox.readTodos(TestAccounts.employee(2));
            clock.set(Instant.parse("2026-10-15T16:00:00Z"));
            inbox.readTodos(TestAccounts.employee(1));

            assertThat(logged.list.subList(3, 5)).extracting(ILoggingEvent::getLevel).containsExactly(Level.ERROR, Level.ERROR);
            assertThat(meters.counter("forcitis.platform.integration.legacy.inbox.unmapped").count()).isEqualTo(5.0);
        } finally {
            logger.detachAppender(logged);
        }
    }
}
