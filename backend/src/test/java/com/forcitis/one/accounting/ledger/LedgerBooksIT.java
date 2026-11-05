package com.forcitis.one.accounting.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import com.forcitis.one.TestIds;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.accounting.ledger.application.BackfillLedgers;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.RegisteredLegalEntities;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.events.LegalEntityRegistered;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #1440 G1: both books of a legal entity come from its registration event and, for entities that already exist, from the backfill. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
class LedgerBooksIT {
    @ServiceConnection
    static final PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static {
        postgres.start();
    }

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    RegisteredLegalEntities registered;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    IdGenerator ids;

    @Autowired
    BackfillLedgers backfill;

    private List<String> basesOf(UUID entity) {
        return jdbc.sql("select basis_kind from accounting_ledger.ledgers where legal_entity_id = :entity order by basis_kind").param("entity", entity).query(String.class).list();
    }

    private LegalEntityRegistered registered(UUID entity) {
        EventEnvelope envelope = new EventEnvelope(ids.next(), "organization.workforce.LegalEntityRegistered", 1, Instant.parse("2026-11-05T02:00:00Z"), "LegalEntity", entity, 1,
                ids.next(), null, ActorType.USER, TestIds.uuid(9001), new LegalEntityId(entity), null, null, List.of());
        return new LegalEntityRegistered(envelope, new LegalEntityId(entity), new LegalEntityCode("BKA"));
    }

    @Test
    @DisplayName("#1440 a registered legal entity gets an ACCRUAL and a CASH book, and a redelivery adds nothing")
    void registrationCreatesBothBooksOnce() {
        UUID entity = ids.next();
        LegalEntityRegistered event = registered(entity);
        tx.executeWithoutResult(status -> publisher.publishEvent(event));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(basesOf(entity)).containsExactly("ACCRUAL", "CASH"));
        tx.executeWithoutResult(status -> publisher.publishEvent(event));
        UUID other = ids.next();
        tx.executeWithoutResult(status -> publisher.publishEvent(registered(other)));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(basesOf(other)).containsExactly("ACCRUAL", "CASH"));
        assertThat(basesOf(entity)).containsExactly("ACCRUAL", "CASH");
    }

    @Test
    @DisplayName("#1440 the backfill creates only the missing books of the registered entities and a second run creates none")
    void backfillCreatesWhatIsMissing() {
        UUID none = ids.next();
        UUID half = ids.next();
        UUID full = ids.next();
        jdbc.sql("insert into accounting_ledger.ledgers (id, legal_entity_id, basis_kind, created_at, updated_at) values (:id, :entity, 'ACCRUAL', now(), now())")
                .param("id", ids.next()).param("entity", half).update();
        jdbc.sql("""
                insert into accounting_ledger.ledgers (id, legal_entity_id, basis_kind, created_at, updated_at)
                values (:a, :entity, 'ACCRUAL', now(), now()), (:c, :entity, 'CASH', now(), now())""").param("a", ids.next()).param("c", ids.next()).param("entity", full).update();
        when(registered.all()).thenReturn(List.of(new LegalEntityId(none), new LegalEntityId(half), new LegalEntityId(full)));

        assertThat(backfill.execute()).isEqualTo(3);
        assertThat(basesOf(none)).containsExactly("ACCRUAL", "CASH");
        assertThat(basesOf(half)).containsExactly("ACCRUAL", "CASH");
        assertThat(basesOf(full)).containsExactly("ACCRUAL", "CASH");
        assertThat(backfill.execute()).isZero();
    }
}
