package com.forcitis.one.platform.audit;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.forcitis.one.TestIds;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.SystemActors;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvent;
import com.forcitis.one.kernel.event.EventDraft;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.events.LegalEntityRegistered;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.NodeKey;
import com.forcitis.one.platform.approval.events.TaskClosed;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.events.RoleCreated;
import com.forcitis.one.platform.identity.events.RoleDeleted;
import com.forcitis.one.platform.identity.events.RoleDisabled;
import com.forcitis.one.platform.identity.events.RoleEnabled;
import com.forcitis.one.platform.identity.events.RoleRevised;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000"})
class AuditDomainEventsIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    static final TestDatabase postgres = TestPostgres.database(AuditDomainEventsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

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

    private static EventEnvelope envelope(String type, UUID aggregateId, ActorType actorType, UUID actorId, UUID correlationId) {
        return new EventEnvelope(UUID.randomUUID(), type, 1, Instant.parse("2026-10-14T02:00:00Z"), "LegalEntity", aggregateId, 1,
                correlationId, null, actorType, actorId, new LegalEntityId(TestIds.uuid(1)), null, null, List.of());
    }

    private List<Map<String, Object>> entriesOf(UUID subject) {
        return jdbc.sql("select action, actor_id, detail, event_id, correlation_id, created_at from platform_audit.audit_entries where subject_kind = 'LEGAL_ENTITY' and subject_id = :subject")
                .param("subject", subject).query().listOfRows();
    }

    @Test
    @DisplayName("a published domain event is audited with its type, subject, user and correlation id; a redelivery writes nothing twice")
    void auditsAnEventOnceByItsEventId() {
        UUID correlation = UUID.randomUUID();
        LegalEntityRegistered event = new LegalEntityRegistered(envelope("organization.workforce.LegalEntityRegistered", TestIds.uuid(501), ActorType.USER, TestAccounts.account(9001).value(), correlation),
                new LegalEntityId(TestIds.uuid(501)), new LegalEntityCode("AUD"));

        tx.executeWithoutResult(status -> publisher.publishEvent(event));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(entriesOf(TestIds.uuid(501))).hasSize(1));
        tx.executeWithoutResult(status -> publisher.publishEvent(event));
        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(entriesOf(TestIds.uuid(501))).hasSize(1));

        Map<String, Object> entry = entriesOf(TestIds.uuid(501)).getFirst();
        assertThat(entry.get("action")).isEqualTo("organization.workforce.LegalEntityRegistered");
        assertThat(entry.get("actor_id")).isEqualTo(TestAccounts.account(9001).value());
        assertThat(entry.get("event_id")).isEqualTo(event.envelope().eventId());
        assertThat(entry.get("correlation_id")).isEqualTo(correlation);
        assertThat(jdbc.sql("select created_at from platform_audit.audit_entries where event_id = :id").param("id", event.envelope().eventId()).query(java.sql.Timestamp.class).single().toInstant())
                .as("DATA-REPAIR-timesheet-audit TB-07 the entry carries the moment of the event, which is what makes a redelivery land on the same key")
                .isEqualTo(event.envelope().occurredAt());
    }

    @Test
    @DisplayName("a system actor is recorded in the detail by its job name (looked up from its fixed UUID, C5), not as a user")
    void systemActorsAreNamedInTheDetail() {
        LegalEntityRegistered event = new LegalEntityRegistered(envelope("organization.workforce.LegalEntityRegistered", TestIds.uuid(502), ActorType.SYSTEM,
                SystemActors.idOf("organization.workforce.snapshot.reconcile"), UUID.randomUUID()), new LegalEntityId(TestIds.uuid(502)), new LegalEntityCode("SYS"));

        tx.executeWithoutResult(status -> publisher.publishEvent(event));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(entriesOf(TestIds.uuid(502))).hasSize(1));
        Map<String, Object> entry = entriesOf(TestIds.uuid(502)).getFirst();
        assertThat(entry.get("actor_id")).isNull();
        assertThat(entry.get("detail")).isEqualTo("by organization.workforce.snapshot.reconcile");
    }

    @Test
    @DisplayName("an event whose record says @NotAudited writes no entry, and its neighbour in the same minute still does")
    void notAuditedEventsAreSkipped() {
        TaskClosed skipped = new TaskClosed(envelope("platform.approval.TaskClosed", TestIds.uuid(601), ActorType.SYSTEM, SystemActors.idOf("platform.approval.deadline.enforce"), UUID.randomUUID()),
                new ApprovalTaskId(TestIds.uuid(601)), new ApprovalInstanceId(TestIds.uuid(602)),
                new DocumentReference(new DocumentTypeCode("TIMESHEET"), new DocumentId(TestIds.uuid(604))), new NodeKey("review"));
        LegalEntityRegistered audited = new LegalEntityRegistered(envelope("organization.workforce.LegalEntityRegistered", TestIds.uuid(603), ActorType.SYSTEM, SystemActors.idOf("platform.approval.deadline.enforce"), UUID.randomUUID()),
                new LegalEntityId(TestIds.uuid(603)), new LegalEntityCode("NEI"));

        tx.executeWithoutResult(status -> {
            publisher.publishEvent(skipped);
            publisher.publishEvent(audited);
        });

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(entriesOf(TestIds.uuid(603))).hasSize(1));
        assertThat(entriesOf(TestIds.uuid(601))).isEmpty();
    }

    @Test
    @DisplayName("ADR-0034-IMPL A-6 the five Role events write no entry of their own: role-version.opened/closed are written in the same transaction, so one change is audited once")
    void roleEventsAreNotAuditedTwice() {
        UUID actor = TestAccounts.account(9001).value();
        RoleId role = new RoleId(TestIds.uuid(801));
        RoleVersionId version = new RoleVersionId(TestIds.uuid(802));
        List<DomainEvent> roleEvents = List.of(
                new RoleCreated(envelope("platform.identity.RoleCreated", role.value(), ActorType.USER, actor, UUID.randomUUID()), role, version),
                new RoleRevised(envelope("platform.identity.RoleRevised", role.value(), ActorType.USER, actor, UUID.randomUUID()), role, version),
                new RoleEnabled(envelope("platform.identity.RoleEnabled", role.value(), ActorType.USER, actor, UUID.randomUUID()), role, version),
                new RoleDisabled(envelope("platform.identity.RoleDisabled", role.value(), ActorType.USER, actor, UUID.randomUUID()), role, version),
                new RoleDeleted(envelope("platform.identity.RoleDeleted", role.value(), ActorType.USER, actor, UUID.randomUUID()), role, version));
        LegalEntityRegistered neighbour = new LegalEntityRegistered(envelope("organization.workforce.LegalEntityRegistered", TestIds.uuid(803), ActorType.USER, actor, UUID.randomUUID()),
                new LegalEntityId(TestIds.uuid(803)), new LegalEntityCode("RLE"));

        tx.executeWithoutResult(status -> {
            roleEvents.forEach(publisher::publishEvent);
            publisher.publishEvent(neighbour);
        });

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(entriesOf(TestIds.uuid(803))).hasSize(1));
        for (DomainEvent event : roleEvents) {
            assertThat(jdbc.sql("select count(*) from platform_audit.audit_entries where event_id = :id").param("id", event.envelope().eventId())
                    .query(Long.class).single()).as("%s writes no audit entry of its own", event.getClass().getSimpleName()).isZero();
        }
    }

    @Test
    @DisplayName("one handling leaves one audit entry per event it raised, all with its correlation id, however often the registry redelivers them")
    void oneHandlingLeavesOneEntryPerEvent() {
        UUID correlation = UUID.randomUUID();
        LegalEntityRegistered first = new LegalEntityRegistered(envelope("organization.workforce.LegalEntityRegistered", TestIds.uuid(701), ActorType.USER, TestAccounts.account(9001).value(), correlation),
                new LegalEntityId(TestIds.uuid(701)), new LegalEntityCode("ONE"));
        LegalEntityRegistered second = new LegalEntityRegistered(envelope("organization.workforce.LegalEntityRegistered", TestIds.uuid(702), ActorType.USER, TestAccounts.account(9001).value(), correlation),
                new LegalEntityId(TestIds.uuid(702)), new LegalEntityCode("TWO"));

        tx.executeWithoutResult(status -> {
            publisher.publishEvent(first);
            publisher.publishEvent(second);
        });
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(entriesOf(TestIds.uuid(701))).hasSize(1);
            assertThat(entriesOf(TestIds.uuid(702))).hasSize(1);
        });
        for (int redelivery = 0; redelivery < 2; redelivery++) {
            tx.executeWithoutResult(status -> {
                publisher.publishEvent(first);
                publisher.publishEvent(second);
            });
        }
        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(entriesOf(TestIds.uuid(701))).hasSize(1);
            assertThat(entriesOf(TestIds.uuid(702))).hasSize(1);
        });
        assertThat(jdbc.sql("select count(*) from platform_audit.audit_entries where correlation_id = :c").param("c", correlation)
                .query(Long.class).single()).isEqualTo(2L);
    }
}
