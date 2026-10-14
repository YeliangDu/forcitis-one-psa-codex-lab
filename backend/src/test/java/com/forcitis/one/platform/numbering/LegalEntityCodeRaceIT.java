package com.forcitis.one.platform.numbering;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.api.LegalEntityDirectory;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.application.CreateLegalEntity;
import com.forcitis.one.organization.workforce.application.UpdateLegalEntity;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Review H-2: issuing a number and correcting the legal entity's code take turns, so a number never carries a code that is
 * not (or no longer correctable to) the entity's current one. Two real transactions are interleaved.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000",
        "spring.datasource.hikari.maximum-pool-size=10"})
@Testcontainers
class LegalEntityCodeRaceIT {
    @MockitoBean
    AuditLog auditLog;

    @MockitoBean
    WecomGateway wecomGateway;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18")
            .withInitScript("init-schemas.sql")
            .withUrlParam("currentSchema", "platform_approval");

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    DocumentNumbers numbers;

    @Autowired
    CreateLegalEntity createLegalEntity;

    @Autowired
    UpdateLegalEntity updateLegalEntity;

    @Autowired
    LegalEntityDirectory directory;

    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

    private LegalEntityId legalEntity(String code) {
        try (WorkContext.Scope acting = WorkContext.enter(UUID.randomUUID(), null, "numbering.race.test")) {
            return new LegalEntityId(createLegalEntity.execute(code, "Synthetic " + code, null).legalEntityId());
        }
    }

    private void awaitBlockedStatement() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (jdbc.sql("select count(*) from pg_stat_activity where datname = current_database() and cardinality(pg_blocking_pids(pid)) > 0")
                .query(Long.class).single() == 0) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("nobody is waiting for a lock");
            }
            Thread.sleep(10);
        }
    }

    @Test
    @DisplayName("H-2 an issuer holds the legal entity first: the correction waits, then finds the number and is refused; the number keeps the old code")
    void issuerFirst() throws Exception {
        LegalEntityId le = legalEntity("RAA");
        CountDownLatch issued = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<String> issuer = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
            String number = numbers.next(DocumentType.QUOTE, le, DAY).value();
            issued.countDown();
            await(commit);
            return number;
        }));
        assertThat(issued.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Object> correction = CompletableFuture.supplyAsync(() -> {
            try (WorkContext.Scope acting = WorkContext.enter(UUID.randomUUID(), null, "numbering.race.test")) {
                return updateLegalEntity.execute(le.value(), com.forcitis.one.kernel.ExpectedVersion.of(1), "RAB", "Synthetic RAA", "ACTIVE", null);
            }
        });
        awaitBlockedStatement();
        commit.countDown();

        String number = issuer.get(10, TimeUnit.SECONDS);
        Throwable refused = org.assertj.core.api.Assertions.catchThrowable(() -> unwrap(correction));

        assertThat(number).startsWith("QT-RAA-");
        assertThat(refused).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_LOCKED));
        assertThat(directory.findCode(le).orElseThrow().value()).isEqualTo("RAA");
    }

    @Test
    @DisplayName("H-2 the correction holds the legal entity first: the issuer waits and numbers with the new code, never the old one")
    void correctionFirst() throws Throwable {
        LegalEntityId le = legalEntity("RBA");
        CountDownLatch corrected = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Object> correction = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
            try (WorkContext.Scope acting = WorkContext.enter(UUID.randomUUID(), null, "numbering.race.test")) {
                Object view = updateLegalEntity.execute(le.value(), com.forcitis.one.kernel.ExpectedVersion.of(1), "RBB", "Synthetic RBA", "ACTIVE", null);
                corrected.countDown();
                await(commit);
                return view;
            }
        }));
        assertThat(corrected.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<String> issuer = CompletableFuture.supplyAsync(() -> tx.execute(status -> numbers.next(DocumentType.QUOTE, le, DAY).value()));
        awaitBlockedStatement();
        commit.countDown();

        unwrap(correction);
        assertThat(issuer.get(10, TimeUnit.SECONDS)).startsWith("QT-RBB-");
    }

    @Test
    @DisplayName("H-2 a deactivation that holds the legal entity first makes the issuer wait and then refuse: no number is issued for a deactivated entity")
    void deactivationFirst() throws Throwable {
        LegalEntityId le = legalEntity("RCA");
        CountDownLatch deactivated = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        CompletableFuture<Object> deactivation = CompletableFuture.supplyAsync(() -> tx.execute(status -> {
            try (WorkContext.Scope acting = WorkContext.enter(UUID.randomUUID(), null, "numbering.race.test")) {
                Object view = updateLegalEntity.execute(le.value(), com.forcitis.one.kernel.ExpectedVersion.of(1), "RCA", "Synthetic RCA", "INACTIVE", null);
                deactivated.countDown();
                await(commit);
                return view;
            }
        }));
        assertThat(deactivated.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Object> issuer = CompletableFuture.supplyAsync(() -> tx.execute(status -> numbers.next(DocumentType.QUOTE, le, DAY).value()));
        awaitBlockedStatement();
        commit.countDown();

        unwrap(deactivation);
        Throwable refused = org.assertj.core.api.Assertions.catchThrowable(() -> unwrap(issuer));
        assertThat(refused).isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.errorCode()).isEqualTo(com.forcitis.one.platform.numbering.api.NumberingError.PLATFORM_NUMBERING_LEGAL_ENTITY_UNAVAILABLE));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static Object unwrap(CompletableFuture<Object> future) throws Throwable {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }
}
