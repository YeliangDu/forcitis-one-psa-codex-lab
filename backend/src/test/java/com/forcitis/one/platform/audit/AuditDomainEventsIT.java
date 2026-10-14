package com.forcitis.one.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.EventSource;
import com.forcitis.one.kernel.event.SourceSystem;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.SyncRunId;
import com.forcitis.one.organization.workforce.events.LegalEntityRegistered;
import com.forcitis.one.organization.workforce.events.SyncRunSucceeded;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000"})
@Testcontainers
class AuditDomainEventsIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18")
            .withInitScript("init-schemas.sql")
            .withUrlParam("currentSchema", "platform_approval");

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    private static EventEnvelope envelope(String type, String aggregateId, ActorType actorType, String actorId, UUID correlationId) {
        return new EventEnvelope(UUID.randomUUID(), type, 1, Instant.parse("2026-10-14T02:00:00Z"), "LegalEntity", aggregateId, 1,
                correlationId, null, actorType, actorId, new LegalEntityId(1), null, new EventSource(SourceSystem.ONE, null), null, List.of());
    }

    private List<Map<String, Object>> entriesOf(String subject) {
        return jdbc.sql("select action, actor_id, detail, event_id, correlation_id, created_at from platform_audit.audit_entries where subject_key = :subject")
                .param("subject", subject).query().listOfRows();
    }

    @Test
    @DisplayName("a published domain event is audited with its type, subject, user and correlation id; a redelivery writes nothing twice")
    void auditsAnEventOnceByItsEventId() {
        UUID correlation = UUID.randomUUID();
        LegalEntityRegistered event = new LegalEntityRegistered(envelope("organization.workforce.LegalEntityRegistered", "501", ActorType.USER, "9001", correlation),
                new LegalEntityId(501), new LegalEntityCode("AUD"));

        tx.executeWithoutResult(status -> publisher.publishEvent(event));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(entriesOf("LegalEntity:501")).hasSize(1));
        tx.executeWithoutResult(status -> publisher.publishEvent(event));
        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(entriesOf("LegalEntity:501")).hasSize(1));

        Map<String, Object> entry = entriesOf("LegalEntity:501").getFirst();
        assertThat(entry.get("action")).isEqualTo("organization.workforce.LegalEntityRegistered");
        assertThat(entry.get("actor_id")).isEqualTo(9001L);
        assertThat(entry.get("event_id")).isEqualTo(event.envelope().eventId());
        assertThat(entry.get("correlation_id")).isEqualTo(correlation);
    }

    @Test
    @DisplayName("a system actor is recorded in the detail, not as a user")
    void systemActorsAreNamedInTheDetail() {
        LegalEntityRegistered event = new LegalEntityRegistered(envelope("organization.workforce.LegalEntityRegistered", "502", ActorType.SYSTEM,
                "organization.workforce.snapshot.sync", UUID.randomUUID()), new LegalEntityId(502), new LegalEntityCode("SYS"));

        tx.executeWithoutResult(status -> publisher.publishEvent(event));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(entriesOf("LegalEntity:502")).hasSize(1));
        Map<String, Object> entry = entriesOf("LegalEntity:502").getFirst();
        assertThat(entry.get("actor_id")).isNull();
        assertThat(entry.get("detail")).isEqualTo("by organization.workforce.snapshot.sync");
    }

    @Test
    @DisplayName("an event whose record says @NotAudited writes no entry, and its neighbour in the same minute still does")
    void notAuditedEventsAreSkipped() {
        SyncRunSucceeded skipped = new SyncRunSucceeded(envelope("organization.workforce.SyncRunSucceeded", "601", ActorType.SYSTEM, "job", UUID.randomUUID()),
                new SyncRunId(601));
        LegalEntityRegistered audited = new LegalEntityRegistered(envelope("organization.workforce.LegalEntityRegistered", "603", ActorType.SYSTEM, "job", UUID.randomUUID()),
                new LegalEntityId(603), new LegalEntityCode("NEI"));

        tx.executeWithoutResult(status -> {
            publisher.publishEvent(skipped);
            publisher.publishEvent(audited);
        });

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(entriesOf("LegalEntity:603")).hasSize(1));
        assertThat(entriesOf("LegalEntity:601")).isEmpty();
    }
}
