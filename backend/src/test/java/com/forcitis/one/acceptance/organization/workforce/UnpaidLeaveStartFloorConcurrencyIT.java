package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.SeparationReview;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #943 (ADR-0068 section 2.2): the count of available super administrators is taken under the separation advisory lock
 * ({@link SeparationReview#SEPARATION_LOCK_KEY}), so two concurrent starts for the last two available super administrators end with exactly one
 * accepted and one refused.
 */
class UnpaidLeaveStartFloorConcurrencyIT extends UnpaidLeaveStartFloorSupport {

    static final TestDatabase postgres = TestPostgres.database(UnpaidLeaveStartFloorConcurrencyIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final int ROUNDS = 8;

    @Test
    @DisplayName("#943 并发：最后两名可用超管同时开始停薪（重复 8 轮，每轮新的一对）：每轮恰一个被接受、一个被拒（422 + 约定码），最终恰一人在停薪")
    void theLastTwoAdministratorsStartingTogetherLeaveExactlyOne() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Staff a = administrator();
                Staff b = administrator();
                actIn(hr);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Throwable>> results = new ArrayList<>();
                for (Staff who : List.of(a, b)) {
                    results.add(pool.submit(() -> {
                        actAsTheCallerOnThisThread();
                        go.await();
                        try {
                            enter(who.employeeId(), UNPAID_LEAVE_START, TODAY);
                            return null;
                        } catch (Throwable refused) {
                            return refused;
                        }
                    }));
                }
                go.countDown();
                List<Throwable> outcomes = new ArrayList<>();
                for (Future<Throwable> result : results) {
                    outcomes.add(result.get(60, TimeUnit.SECONDS));
                }

                assertThat(outcomes.stream().filter(java.util.Objects::isNull).count()).as("round " + round + ": exactly one accepted: " + outcomes).isEqualTo(1);
                Throwable refused = outcomes.stream().filter(java.util.Objects::nonNull).findFirst().orElseThrow();
                assertFloorRefusal(refused);
                int onLeave = (onUnpaidLeaveOn(a.employeeId(), TODAY) ? 1 : 0) + (onUnpaidLeaveOn(b.employeeId(), TODAY) ? 1 : 0);
                assertThat(onLeave).as("round " + round + ": exactly one of the two is on leave").isEqualTo(1);
                Staff accepted = onUnpaidLeaveOn(a.employeeId(), TODAY) ? a : b;
                assertThat(reviewsAbout(accepted.userId())).as("round " + round + ": the accepted start opened its sensitive role review").isNotEmpty();
                // The pair leaves the pool, so that the next round counts only its own two.
                disableAccount(a);
                disableAccount(b);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean someoneWaitsForTheLock() {
        try (Connection monitor = schemaConnection("platform_integration"); PreparedStatement select = monitor.prepareStatement("""
                select count(*) from pg_locks l, (select hashtextextended(?, 0) as h) k
                 where l.locktype = 'advisory' and not l.granted
                   and l.database = (select oid from pg_database where datname = current_database())
                   and l.classid::bigint = ((k.h >> 32) & 4294967295) and l.objid::bigint = (k.h & 4294967295)""")) {
            select.setString(1, SeparationReview.SEPARATION_LOCK_KEY);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        } catch (SQLException broken) {
            throw new IllegalStateException(broken);
        }
    }

    @Test
    @DisplayName("#943 锁：别的事务持有分离咨询锁时，超管开始停薪（另有两名可用，本应被接受）要等锁：pg_locks 显示在等该锁、命令未完成；锁放开后完成并被接受")
    void theStartWaitsForTheSeparationLock() throws Exception {
        Staff leaving = administrator();
        administrator();
        administrator();
        actIn(hr);
        ExecutorService thread = Executors.newSingleThreadExecutor();
        Connection holder = schemaConnection("platform_integration");
        try {
            holder.setAutoCommit(false);
            try (PreparedStatement take = holder.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                take.setString(1, SeparationReview.SEPARATION_LOCK_KEY);
                take.execute();
            }
            Callable<UUID> start = () -> {
                actAsTheCallerOnThisThread();
                return enter(leaving.employeeId(), UNPAID_LEAVE_START, TODAY);
            };
            Future<UUID> pending = thread.submit(start);
            Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> someoneWaitsForTheLock() || pending.isDone());

            assertThat(pending.isDone()).as("the start finished without waiting for the separation lock").isFalse();
            assertThat(someoneWaitsForTheLock()).isTrue();
            holder.commit();
            UUID command = pending.get(30, TimeUnit.SECONDS);
            assertThat(commandStatus(command)).isEqualTo(EFFECTIVE);
            assertThat(reviewsAbout(leaving.userId())).as("the accepted start opened its sensitive role review").isNotEmpty();
        } finally {
            holder.close();
            thread.shutdownNow();
        }
    }
}
