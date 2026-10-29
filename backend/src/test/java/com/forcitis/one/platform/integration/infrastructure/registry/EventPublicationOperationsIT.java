package com.forcitis.one.platform.integration.infrastructure.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestIds;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.events.LegalEntityRegistered;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import com.forcitis.one.kernel.SystemActors;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "forcitis.platform.integration.event-publication.jobs-enabled=true",
        "forcitis.platform.integration.event-publication.republish-interval=PT6H",
        "spring.datasource.hikari.connection-timeout=2000"})
class EventPublicationOperationsIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    static final TestDatabase postgres = TestPostgres.database(EventPublicationOperationsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EventPublicationJobs jobs;

    @Autowired
    EventPublicationStatistics statistics;

    private int count(String table, String condition) {
        return jdbc.sql("select count(*) from platform_integration." + table + " where " + condition).query(Integer.class).single();
    }

    @Test
    @DisplayName("a completed publication moves to the archive table; the cleanup job removes archived rows older than 30 days and keeps newer ones")
    void completedPublicationsAreArchivedThenCleaned() {
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), "organization.workforce.LegalEntityRegistered", 1, Instant.now(), "LegalEntity",
                TestIds.uuid(701), 1, UUID.randomUUID(), null, ActorType.SYSTEM, SystemActors.idOf("test.envelope"), new LegalEntityId(TestIds.uuid(701)), null,
                null, List.of());

        tx.executeWithoutResult(status -> publisher.publishEvent(new LegalEntityRegistered(envelope, new LegalEntityId(TestIds.uuid(701)), new LegalEntityCode("ARC"))));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(count("event_publication_archive", "serialized_event like '%\"ARC\"%'")).isEqualTo(1));
        assertThat(count("event_publication", "serialized_event like '%\"ARC\"%'")).as("moved, not copied").isZero();

        jdbc.sql("""
                insert into platform_integration.event_publication_archive (id, listener_id, event_type, serialized_event, publication_date, completion_date, status)
                values (:id, 'old.Listener', 'x.Old', '{}', now() - interval '40 days', now() - interval '31 days', 'COMPLETED')
                """).param("id", UUID.randomUUID()).update();

        jobs.cleanup();

        assertThat(count("event_publication_archive", "listener_id = 'old.Listener'")).isZero();
        assertThat(count("event_publication_archive", "serialized_event like '%\"ARC\"%'")).as("today's row is within the retention").isEqualTo(1);
    }

    @Test
    @DisplayName("the statistics count incomplete publications that used up their attempts and those stuck for too long")
    void statisticsCountWhatTheAlertsNeed() {
        jdbc.sql("delete from platform_integration.event_publication where listener_id like 'stat.%'").update();
        jdbc.sql("""
                insert into platform_integration.event_publication (id, listener_id, event_type, serialized_event, publication_date, completion_attempts, status)
                values (:a, 'stat.A', 'x.A', '{}', now() - interval '2 hours', 8, 'FAILED'),
                       (:b, 'stat.B', 'x.B', '{}', now() - interval '2 hours', 3, 'FAILED'),
                       (:c, 'stat.C', 'x.C', '{}', now() - interval '1 minute', 0, 'PUBLISHED')
                """).param("a", UUID.randomUUID()).param("b", UUID.randomUUID()).param("c", UUID.randomUUID()).update();
        try {
            assertThat(statistics.exhausted(8)).isGreaterThanOrEqualTo(1);
            long beforeAdding = statistics.incompleteBefore(Instant.now().minus(Duration.ofMinutes(10)));
            assertThat(beforeAdding).isGreaterThanOrEqualTo(2);
            assertThat(statistics.incompleteBefore(Instant.now().minus(Duration.ofHours(3)))).isLessThan(beforeAdding);
        } finally {
            jdbc.sql("delete from platform_integration.event_publication where listener_id like 'stat.%'").update();
        }
    }
}
