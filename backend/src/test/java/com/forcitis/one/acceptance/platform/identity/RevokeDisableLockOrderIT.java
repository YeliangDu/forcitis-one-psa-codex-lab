package com.forcitis.one.acceptance.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.SeparationReview;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
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
 * #732 part B, independent lock ruling (adr0068-22-ruling section 2(a) binding condition, 2(c) items 2 and 5): the paths that decide on the
 * available-super-administrator count (manual revoke, disable) take the separation advisory lock first, then the account row, then the role row.
 * Concurrent acts therefore give a serial outcome: with 2 available, of two revokes (or a revoke and a disable, or two disables) exactly one is
 * refused and the count never reaches 0; a hold release racing two revokes is always done and never leaves 0; no deadlock in either start order.
 * Every race runs 10 rounds, the start order alternating; each round has fresh people so that no request of an earlier round is in the way.
 */
class RevokeDisableLockOrderIT extends RevokeDisableFloorSupport {

    static final int ROUNDS = 10;

    static final TestDatabase postgres = TestPostgres.database(RevokeDisableLockOrderIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private void assertOneDoneOneRefused(Outcome first, Outcome second) {
        assertThat(List.of(first.done(), second.done())).as("exactly one act is done: " + first.status() + " " + first.body() + " / " + second.status() + " " + second.body())
                .containsExactlyInAnyOrder(true, false);
        assertRefusedByTheFloor(first.done() ? second : first);
    }

    // ---- serial outcomes under concurrency (ruling 2(c) item 5) ----

    @Test
    @DisplayName("#732B lock order: two concurrent manual revokes of different super_admins with 2 available: exactly one is refused, one remains, one card; no deadlock; 10 rounds, both start orders")
    void twoConcurrentRevokesNeverReachZero() throws Exception {
        Desk desk = desk();
        for (int round = 0; round < ROUNDS; round++) {
            Person x = admin();
            Person y = admin();
            forgetCards();

            List<Outcome> results = race(round % 2 == 1, List.<Callable<Outcome>>of(
                    () -> revoke(desk.requester(), desk.confirmer(), x, SUPER_ADMIN),
                    () -> revoke(desk.confirmer(), desk.requester(), y, SUPER_ADMIN)));

            assertOneDoneOneRefused(results.get(0), results.get(1));
            assertThat(availableAmong(x, y)).as("round " + round + ": one available super administrator remains").isEqualTo(1);
            assertThat(cardsAfterwards(1)).as("the done act left 1: one card").hasSize(1);
            retire(x, y);
        }
    }

    @Test
    @DisplayName("#732B lock order: a manual revoke racing a disable of another super_admin with 2 available: exactly one is refused, never 0; no deadlock; 10 rounds, both start orders")
    void aRevokeAndADisableNeverReachZero() throws Exception {
        Desk desk = desk();
        for (int round = 0; round < ROUNDS; round++) {
            Person x = admin();
            Person y = admin();

            List<Outcome> results = race(round % 2 == 1, List.<Callable<Outcome>>of(
                    () -> revoke(desk.requester(), desk.confirmer(), x, SUPER_ADMIN),
                    () -> disable(desk.confirmer(), y)));

            assertOneDoneOneRefused(results.get(0), results.get(1));
            assertThat(availableAmong(x, y)).as("round " + round).isEqualTo(1);
            retire(x, y);
        }
    }

    @Test
    @DisplayName("#732B lock order: two concurrent disables of different super_admins with 2 available: exactly one is refused, never 0; no deadlock; 10 rounds, both start orders")
    void twoConcurrentDisablesNeverReachZero() throws Exception {
        Desk desk = desk();
        for (int round = 0; round < ROUNDS; round++) {
            Person x = admin();
            Person y = admin();

            List<Outcome> results = race(round % 2 == 1, List.<Callable<Outcome>>of(
                    () -> disable(desk.requester(), x),
                    () -> disable(desk.confirmer(), y)));

            assertOneDoneOneRefused(results.get(0), results.get(1));
            assertThat(availableAmong(x, y)).as("round " + round).isEqualTo(1);
            retire(x, y);
        }
    }

    @Test
    @DisplayName("#732B lock order: a hold release racing two revokes of the 2 available super_admins: the release is always done, exactly one revoke is refused, never 0 left; no deadlock; 10 rounds, both start orders")
    void aHoldReleaseRacingRevokesGivesASerialOutcome() throws Exception {
        Desk desk = desk();
        Person releaser = auditor();
        for (int round = 0; round < ROUNDS; round++) {
            Person leaver = admin();
            UUID hold = hold(leaver);
            Person x = admin();
            Person y = admin();
            actIn(releaser);

            List<Object> results = race(round % 2 == 1, List.<Callable<Object>>of(
                    () -> {
                        release(releaser, hold);
                        return "released";
                    },
                    () -> revoke(desk.requester(), desk.confirmer(), x, SUPER_ADMIN),
                    () -> revoke(desk.confirmer(), desk.requester(), y, SUPER_ADMIN)));

            assertThat(results.get(0)).isEqualTo("released");
            assertThat(holdStatus(hold)).as("round " + round).isEqualTo("RELEASED");
            assertOneDoneOneRefused((Outcome) results.get(1), (Outcome) results.get(2));
            assertThat(availableAmong(x, y)).as("round " + round + ": never 0").isEqualTo(1);
            retire(x, y);
        }
    }

    // ---- the order of the locks (ruling 2(a) binding condition), seen from the database ----

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

    /** True while nothing holds the row: a no-wait row lock from the test's own connection is granted. */
    private boolean rowIsFree(String sql, Object id) throws SQLException {
        try (Connection probe = schemaConnection("platform_identity")) {
            probe.setAutoCommit(false);
            try (PreparedStatement lock = probe.prepareStatement(sql)) {
                lock.setObject(1, id);
                lock.execute();
                return true;
            } catch (SQLException locked) {
                if ("55P03".equals(locked.getSQLState())) {
                    return false;
                }
                throw locked;
            } finally {
                probe.rollback();
            }
        }
    }

    private <T> void theActWaitsForTheLockAndHoldsNoRowYet(Person target, Callable<T> act, String what) throws Exception {
        String key = SeparationReview.SEPARATION_LOCK_KEY;
        ExecutorService thread = Executors.newSingleThreadExecutor();
        Connection holder = schemaConnection("platform_integration");
        try {
            holder.setAutoCommit(false);
            try (PreparedStatement take = holder.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                take.setString(1, key);
                take.execute();
            }
            Future<T> running = thread.submit(act);
            Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(50)).until(() -> someoneWaitsFor(key) || running.isDone());
            if (running.isDone()) {
                running.get();
            }
            assertThat(running.isDone()).as(what + " waits for the separation advisory lock instead of finishing without it").isFalse();
            assertThat(rowIsFree("select 1 from user_accounts where user_id = ? for update nowait", target.userId()))
                    .as(what + " has not locked the account row while it waits for the advisory lock (advisory lock first)").isTrue();
            assertThat(rowIsFree("select 1 from account_role_grants where user_id = ? for update nowait", target.userId()))
                    .as(what + " has not touched the grants while it waits").isTrue();
            holder.commit();
            running.get(30, TimeUnit.SECONDS);
        } finally {
            holder.rollback();
            holder.close();
            thread.shutdownNow();
        }
    }

    @Test
    @DisplayName("#732B lock order: the applying step of a manual revoke waits for the separation advisory lock before it locks the account row (advisory lock, then account row, then role row), then completes")
    void theRevokeTakesTheAdvisoryLockFirst() throws Exception {
        Desk desk = desk();
        Person a = admin();
        admin();
        admin();
        UUID request = ask(desk.requester(), a, SUPER_ADMIN);

        theActWaitsForTheLockAndHoldsNoRowYet(a, () -> {
            Outcome outcome = confirm(desk.confirmer(), request);
            assertDone(outcome);
            return outcome;
        }, "the revoke");

        assertThat(holds(a, SUPER_ADMIN)).isFalse();
    }

    @Test
    @DisplayName("#732B lock order: a disable waits for the separation advisory lock before it locks the account row, then completes")
    void theDisableTakesTheAdvisoryLockFirst() throws Exception {
        Desk desk = desk();
        Person a = admin();
        admin();
        admin();

        theActWaitsForTheLockAndHoldsNoRowYet(a, () -> {
            Outcome outcome = disable(desk.requester(), a);
            assertDone(outcome);
            return outcome;
        }, "the disable");

        assertThat(accountStatus(a)).isEqualTo("DISABLED");
    }
}
