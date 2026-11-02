package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.SeparationHoldOperationNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
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
 * #877 IMP-1030-PAR-B8 (ADR-0068 item 2.2, line 81): "运维命令放行" also takes {@code pg_advisory_xact_lock(hashtextextended(<key>, 0))} first, the same
 * lock as entering a separation, the single release and the withdrawal of a held command (see {@code SeparationHoldLockIT} for the technique:
 * hold the lock in a transaction of the test, start the operation on another thread, wait until pg_locks shows a backend waiting for exactly that
 * lock, check that the operation has not finished, release, check that it does).
 */
class SeparationHoldOperationLockIT extends SeparationHoldOperationSupport {

    static final TestDatabase postgres = TestPostgres.database(SeparationHoldOperationLockIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static String lockKey() {
        try {
            Object key = Class.forName(LOCK_KEY_CLASS).getField(LOCK_KEY_FIELD).get(null);
            assertThat(key).as("the lock key text is a String constant").isInstanceOf(String.class);
            return (String) key;
        } catch (ReflectiveOperationException missing) {
            throw new AssertionError("ADR-0068 item 2.2: no public static String " + LOCK_KEY_FIELD + " in " + LOCK_KEY_CLASS, missing);
        }
    }

    private boolean someoneWaitsFor(String key) throws SQLException {
        try (Connection monitor = schemaConnection("platform_integration"); PreparedStatement select = monitor.prepareStatement("""
                select count(*) from pg_locks l, (select hashtextextended(?, 0) as h) k
                 where l.locktype = 'advisory' and not l.granted
                   and l.database = (select oid from pg_database where datname = current_database())
                   and l.classid::bigint = ((k.h >> 32) & 4294967295) and l.objid::bigint = (k.h & 4294967295)""")) {
            select.setString(1, key);
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        }
    }

    @Test
    @DisplayName("#877 ADR-0068 第 2 条第 2 项（行 81「运维命令放行」）：运维命令放行先取同一把咨询锁：别的事务持锁期间命令一直等待，锁释放后完成，暂停记录 RELEASED、途径 OPERATIONS_COMMAND")
    void theCommandWaitsForTheLock() throws Exception {
        Held held = heldWithNoneRemaining();
        Pair pair = twoPeople();
        String key = lockKey();
        ExecutorService thread = Executors.newSingleThreadExecutor();
        Connection holder = schemaConnection("platform_integration");
        try {
            holder.setAutoCommit(false);
            try (PreparedStatement take = holder.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                take.setString(1, key);
                take.execute();
            }
            Future<Result> running = thread.submit(() -> release(held, pair, REASON_TEXT));
            Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(50)).until(() -> someoneWaitsFor(key) || running.isDone());
            if (running.isDone()) {
                running.get();
            }
            assertThat(running.isDone()).as("the command waits for the advisory lock instead of finishing without it").isFalse();
            holder.commit();
            Result result = running.get(30, TimeUnit.SECONDS);
            assertThat(result.exitCode()).as(result.text()).isEqualTo(DONE);
        } finally {
            holder.rollback();
            holder.close();
            thread.shutdownNow();
        }

        assertThat(holdStatus(held.hold())).isEqualTo(RELEASED);
        assertThat(hold(held.hold()).get("release_channel_kind")).isEqualTo(CHANNEL_OPERATIONS);
    }
}
