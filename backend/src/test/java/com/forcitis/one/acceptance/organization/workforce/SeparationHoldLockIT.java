package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
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
 * #873 IMP-1030-PAR-B4 (ADR-0068 item 2.2, lines 81 to 83): executing a separation (entry of a SEPARATE), a single release and the withdrawal
 * of a held command all first take {@code pg_advisory_xact_lock(hashtextextended(<key>, 0))}, the key being a kernel constant
 * ({@link PersonnelCommandNames#LOCK_KEY_CLASS}.{@link PersonnelCommandNames#LOCK_KEY_FIELD}, looked up at run time). The test holds that lock
 * in an open transaction of its own, starts the operation on another thread, waits until the database shows a backend waiting for exactly
 * that lock (pg_locks, no sleeping), checks that the operation has not finished, then commits and checks that it does. Under the ruling that
 * every separation of an administrator is held the result of two concurrent entries no longer depends on the order, so only the lock itself
 * can be observed this way.
 */
class SeparationHoldLockIT extends SeparationHoldSupport {

    static final TestDatabase postgres = TestPostgres.database(SeparationHoldLockIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

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

    /** True while some backend of this database waits for the advisory lock of the key (classid and objid are the halves of the 64-bit hash). */
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

    /** Holds the lock in an open transaction, runs the operation on another thread, and checks that it waits for the lock and then completes. */
    private <T> T runsOnlyWhenTheLockIsFree(Callable<T> operation) throws Exception {
        String key = lockKey();
        ExecutorService thread = Executors.newSingleThreadExecutor();
        Connection holder = schemaConnection("platform_integration");
        try {
            holder.setAutoCommit(false);
            try (PreparedStatement take = holder.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                take.setString(1, key);
                take.execute();
            }
            Future<T> running = thread.submit(() -> {
                actAsTheCallerOnThisThread();
                return operation.call();
            });
            Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(50))
                    .until(() -> someoneWaitsFor(key) || running.isDone());
            assertThat(running.isDone()).as("the operation waits for the advisory lock instead of finishing without it").isFalse();
            holder.commit();
            return running.get(30, TimeUnit.SECONDS);
        } finally {
            holder.rollback();
            holder.close();
            thread.shutdownNow();
        }
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 2 项（行 81、82）：录入管理员的离职命令先取咨询锁：别的事务持锁期间录入一直等待，锁释放后完成，命令转 HELD")
    void enteringASeparationWaitsForTheLock() throws Exception {
        Staff leaver = administrator();
        administrator();
        actIn(hr);
        PersonnelCommandsProbe probe = commands();
        Map<String, Object> fields = new HashMap<>();
        fields.put(EMPLOYEE_ID, leaver.employeeId());
        fields.put(COMMAND_KIND, SEPARATE);
        fields.put(LAST_WORKING_DAY, LAST_DAY);

        UUID command = runsOnlyWhenTheLockIsFree(() -> probe.enter(fields));

        assertThat(commandStatus(command)).isEqualTo(HELD);
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 2 项（行 81、82）、第 3 项：单条放行先取同一把咨询锁：持锁期间放行一直等待，锁释放后完成，暂停记录 RELEASED")
    void releasingWaitsForTheLock() throws Exception {
        Scenario scenario = heldWithRemaining(1);
        actIn(scenario.releaser());
        PersonnelCommandsProbe probe = commands();

        runsOnlyWhenTheLockIsFree(() -> {
            probe.release(scenario.held().hold(), REASON_TEXT);
            return null;
        });

        assertThat(holdStatus(scenario.held().hold())).isEqualTo(RELEASED);
    }

    @Test
    @DisplayName("#873 ADR-0068 第 2 条第 2 项（行 81、82）：撤回待放行命令先取同一把咨询锁：持锁期间撤回一直等待，锁释放后完成，命令与暂停记录都为 WITHDRAWN")
    void withdrawingAHeldCommandWaitsForTheLock() throws Exception {
        Staff leaver = administrator();
        administrator();
        Held held = heldSeparation(leaver);
        actIn(hr);
        PersonnelCommandsProbe probe = commands();

        runsOnlyWhenTheLockIsFree(() -> {
            probe.withdraw(held.command(), "synthetic: the person stays");
            return null;
        });

        assertThat(commandStatus(held.command())).isEqualTo(WITHDRAWN);
        assertThat(holdStatus(held.hold())).isEqualTo(WITHDRAWN);
    }
}
