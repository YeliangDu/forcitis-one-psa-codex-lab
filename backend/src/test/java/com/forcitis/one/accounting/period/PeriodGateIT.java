package com.forcitis.one.accounting.period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.accounting.period.api.FiscalPeriodId;
import com.forcitis.one.accounting.period.api.PeriodGate;
import com.forcitis.one.accounting.period.api.PeriodStatus;
import com.forcitis.one.accounting.period.api.PostingPeriod;
import com.forcitis.one.kernel.AccountingPeriod;
import com.forcitis.one.kernel.LegalEntityId;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/** #1439 (accounting-spine.md section 11 items 2 to 4 and 9): PeriodGate answers, with the closed and closing periods put in place by SQL (C-6: no close command exists). */
class PeriodGateIT extends AcceptanceSupport {
    static final TestDatabase postgres = TestPostgres.database(PeriodGateIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    PeriodGate gate;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate tx;

    private static LegalEntityId newEntity() {
        return new LegalEntityId(UUID.randomUUID());
    }

    private FiscalPeriodId period(LegalEntityId entity, int year, int number, String status) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into accounting_period.fiscal_periods (id, legal_entity_id, fiscal_year_number, period_number, status, created_at, updated_at, version)
                values (:id, :e, :y, :n, :s, now(), now(), 0)
                """).param("id", id).param("e", entity.value()).param("y", year).param("n", number).param("s", status).update();
        return new FiscalPeriodId(id);
    }

    private PostingPeriod forPosting(LegalEntityId entity, LocalDate date) {
        return tx.execute(status -> gate.forPosting(entity, date));
    }

    private PeriodStatus periodStatus(LegalEntityId entity, int year, int number) {
        return tx.execute(status -> gate.periodStatus(entity, year, number));
    }

    @Test
    @DisplayName("#1439 forPosting: a date in an open period is OPEN with that period's id; the first and last day of the month map to the same period")
    void openPeriod() {
        LegalEntityId entity = newEntity();
        FiscalPeriodId march = period(entity, 2026, 3, "OPEN");
        assertThat(forPosting(entity, LocalDate.of(2026, 3, 1))).isEqualTo(new PostingPeriod.Open(march));
        assertThat(forPosting(entity, LocalDate.of(2026, 3, 31))).isEqualTo(new PostingPeriod.Open(march));
    }

    @Test
    @DisplayName("#1439 forPosting: a year without rows, a month without a row, another entity's period are NOT_OPENED")
    void notOpened() {
        LegalEntityId entity = newEntity();
        period(entity, 2026, 3, "OPEN");
        assertThat(forPosting(entity, LocalDate.of(2027, 3, 1))).isEqualTo(new PostingPeriod.NotOpened());
        assertThat(forPosting(entity, LocalDate.of(2026, 4, 1))).isEqualTo(new PostingPeriod.NotOpened());
        assertThat(forPosting(newEntity(), LocalDate.of(2026, 3, 1))).isEqualTo(new PostingPeriod.NotOpened());
    }

    @Test
    @DisplayName("#1439 forPosting: a CLOSING period answers Closing and writes nothing")
    void closingPeriod() {
        LegalEntityId entity = newEntity();
        period(entity, 2026, 3, "CLOSING");
        period(entity, 2026, 4, "OPEN");
        assertThat(forPosting(entity, LocalDate.of(2026, 3, 15))).isEqualTo(new PostingPeriod.Closing());
        assertThat(jdbc.sql("select count(*) from accounting_period.fiscal_periods where legal_entity_id = :e and status = 'CLOSING'").param("e", entity.value())
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("#1439 forPosting: a date in a CLOSED period routes to the lowest-numbered open period with the original period; the closed row is unchanged")
    void closedPeriodRoutesToTheCurrentOpenPeriod() {
        LegalEntityId entity = newEntity();
        period(entity, 2026, 1, "CLOSED");
        FiscalPeriodId closed = period(entity, 2026, 2, "CLOSED");
        period(entity, 2026, 4, "OPEN");
        FiscalPeriodId lowestOpen = period(entity, 2026, 3, "OPEN");
        String before = jdbc.sql("select to_jsonb(p)::text from accounting_period.fiscal_periods p where id = :id").param("id", closed.value()).query(String.class).single();

        PostingPeriod answer = forPosting(entity, LocalDate.of(2026, 2, 10));

        assertThat(answer).isEqualTo(new PostingPeriod.ClosedRouteTo(lowestOpen, AccountingPeriod.of(2026, 2)));
        assertThat(jdbc.sql("select to_jsonb(p)::text from accounting_period.fiscal_periods p where id = :id").param("id", closed.value()).query(String.class).single())
                .isEqualTo(before);
    }

    @Test
    @DisplayName("#1439 forPosting: the current open period may be in a later year")
    void routeCrossesTheYear() {
        LegalEntityId entity = newEntity();
        for (int number = 1; number <= 12; number++) {
            period(entity, 2026, number, "CLOSED");
        }
        FiscalPeriodId next = period(entity, 2027, 1, "OPEN");
        assertThat(forPosting(entity, LocalDate.of(2026, 6, 1))).isEqualTo(new PostingPeriod.ClosedRouteTo(next, AccountingPeriod.of(2026, 6)));
    }

    @Test
    @DisplayName("#1439 forPosting: a CLOSED period and no open period is NO_OPEN_PERIOD")
    void noOpenPeriod() {
        LegalEntityId entity = newEntity();
        period(entity, 2026, 1, "CLOSED");
        period(entity, 2026, 2, "CLOSING");
        assertThat(forPosting(entity, LocalDate.of(2026, 1, 20))).isEqualTo(new PostingPeriod.NoOpenPeriod());
    }

    @Test
    @DisplayName("#1439 periodStatus: the stored statuses, NOT_OPENED for a missing row, NOT_ENABLED for period 13 even when a row 13 exists")
    void periodStatuses() {
        LegalEntityId entity = newEntity();
        period(entity, 2026, 1, "OPEN");
        period(entity, 2026, 2, "CLOSING");
        period(entity, 2026, 3, "CLOSED");
        period(entity, 2026, 13, "OPEN");
        assertThat(periodStatus(entity, 2026, 1)).isEqualTo(PeriodStatus.OPEN);
        assertThat(periodStatus(entity, 2026, 2)).isEqualTo(PeriodStatus.CLOSING);
        assertThat(periodStatus(entity, 2026, 3)).isEqualTo(PeriodStatus.CLOSED);
        assertThat(periodStatus(entity, 2026, 4)).isEqualTo(PeriodStatus.NOT_OPENED);
        assertThat(periodStatus(entity, 2027, 1)).isEqualTo(PeriodStatus.NOT_OPENED);
        assertThat(periodStatus(entity, 2026, 13)).isEqualTo(PeriodStatus.NOT_ENABLED);
        assertThat(periodStatus(newEntity(), 2026, 13)).isEqualTo(PeriodStatus.NOT_ENABLED);
        assertThatThrownBy(() -> periodStatus(entity, 2026, 14)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> periodStatus(entity, 2026, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("#1439 INV-92 both reads refuse to run outside the posting transaction, where their shared lock would protect nothing")
    void needsATransaction() {
        LegalEntityId entity = newEntity();
        period(entity, 2026, 1, "OPEN");
        assertThatThrownBy(() -> gate.forPosting(entity, LocalDate.of(2026, 1, 1))).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> gate.periodStatus(entity, 2026, 1)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("#1439 INV-92 while a posting transaction holds the gate's answer, a close of that period cannot be written; after it ends the close goes through")
    void closeCannotSlipBetweenCheckAndWrite() throws Exception {
        LegalEntityId entity = newEntity();
        FiscalPeriodId march = period(entity, 2026, 3, "OPEN");
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<PostingPeriod> posting = pool.submit(() -> tx.execute(status -> {
                PostingPeriod answer = gate.forPosting(entity, LocalDate.of(2026, 3, 5));
                holding.countDown();
                try {
                    finish.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return answer;
            }));
            assertThat(holding.await(30, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                jdbc.sql("set local lock_timeout = '500ms'").update();
                jdbc.sql("update accounting_period.fiscal_periods set status = 'CLOSING', updated_at = now(), version = version + 1 where id = :id")
                        .param("id", march.value()).update();
            })).as("the close waits for the posting transaction and times out here").hasMessageContaining("lock");

            finish.countDown();
            assertThat(posting.get(30, TimeUnit.SECONDS)).isEqualTo(new PostingPeriod.Open(march));
            tx.executeWithoutResult(status -> jdbc.sql("update accounting_period.fiscal_periods set status = 'CLOSING', updated_at = now(), version = version + 1 where id = :id")
                    .param("id", march.value()).update());
            assertThat(forPosting(entity, LocalDate.of(2026, 3, 5))).isEqualTo(new PostingPeriod.Closing());
        } finally {
            finish.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("#1439 INV-92 the open period a closed date is routed to stays shared-locked too: its close waits for the posting transaction")
    void closeOfTheRoutedToPeriodCannotSlipBetweenCheckAndWrite() throws Exception {
        LegalEntityId entity = newEntity();
        period(entity, 2026, 2, "CLOSED");
        FiscalPeriodId march = period(entity, 2026, 3, "OPEN");
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<PostingPeriod> posting = pool.submit(() -> tx.execute(status -> {
                PostingPeriod answer = gate.forPosting(entity, LocalDate.of(2026, 2, 5));
                holding.countDown();
                try {
                    finish.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return answer;
            }));
            assertThat(holding.await(30, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                jdbc.sql("set local lock_timeout = '500ms'").update();
                jdbc.sql("update accounting_period.fiscal_periods set status = 'CLOSING', updated_at = now(), version = version + 1 where id = :id")
                        .param("id", march.value()).update();
            })).as("the close of the routed-to period waits for the posting transaction and times out here").hasMessageContaining("lock");

            finish.countDown();
            assertThat(posting.get(30, TimeUnit.SECONDS)).isEqualTo(new PostingPeriod.ClosedRouteTo(march, new AccountingPeriod(2026, 2)));
            tx.executeWithoutResult(status -> jdbc.sql("update accounting_period.fiscal_periods set status = 'CLOSING', updated_at = now(), version = version + 1 where id = :id")
                    .param("id", march.value()).update());
        } finally {
            finish.countDown();
            pool.shutdownNow();
        }
    }
}
