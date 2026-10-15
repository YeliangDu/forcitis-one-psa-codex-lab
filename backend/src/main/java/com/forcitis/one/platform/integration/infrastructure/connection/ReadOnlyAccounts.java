package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.ExternalFailure;
import com.forcitis.one.platform.integration.domain.ReadOnlyConnectionCheck;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Verdict;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every external pool belongs to one login. The login is checked against {@link ReadOnlyBaseline} when its first pool opens (anything unmet
 * refuses the start-up), again before each sync and whenever its last check is older than {@link #MAXIMUM_AGE}, so a login only read on
 * request (the merged inbox) is still checked every day. At a re-check (ADR-0037 item 4.4 as the user interpreted it, intent
 * 2026-10-15-120940) a violation closes every pool of the login for good; a check that cannot be determined makes only this read unavailable,
 * and {@link #UNDETERMINED_IN_A_ROW_STOPS} of them in a row stop the login for good as well; a determined pass clears the count.
 */
final class ReadOnlyAccounts implements ReadOnlyConnectionCheck, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyAccounts.class);

    /** With a tick of {@link #TICK} a login is re-checked at most 23 h 15 min after its last check: at least once a day (ADR-0037 item 4.4). */
    static final Duration MAXIMUM_AGE = Duration.ofHours(23);
    static final Duration TICK = Duration.ofMinutes(15);
    static final int UNDETERMINED_IN_A_ROW_STOPS = 3;

    record Login(String jdbcUrl, String username) {
    }

    record Pool(DataSource source, Runnable close) {
    }

    private final Clock clock;
    private final Function<DataSource, ReadOnlyCatalog> catalogs;
    private final MeterRegistry meters;
    private final Map<Login, Account> byLogin = new HashMap<>();
    private final Map<String, Account> byConnection = new HashMap<>();
    private ScheduledExecutorService ticker;

    ReadOnlyAccounts(Clock clock, Function<DataSource, ReadOnlyCatalog> catalogs, MeterRegistry meters) {
        this.clock = clock;
        this.catalogs = catalogs;
        this.meters = meters;
    }

    /** Throws IllegalStateException naming the unmet items when the login fails its first check; the pool is closed then. */
    synchronized void attach(String connectionName, Login login, Pool pool, Supplier<Pool> checkPool) {
        Account account = byLogin.get(login);
        if (account == null) {
            Pool check;
            try {
                check = checkPool.get();
            } catch (RuntimeException unbuildable) {
                pool.close().run();
                throw unbuildable;
            }
            account = new Account(check);
            account.add(connectionName, pool);
            Verdict verdict;
            try {
                verdict = account.check();
            } catch (RuntimeException unexpected) {
                account.closeAll();
                throw new IllegalStateException("The external connection " + connectionName + " is refused, the application does not start "
                        + "(ADR-0037 item 4): the read-only check failed (" + ExternalFailure.of(unexpected) + ")");
            }
            if (!verdict.passed()) {
                account.closeAll();
                throw new IllegalStateException("The external connection " + connectionName + " is refused, the application does not start "
                        + "(ADR-0037 item 4): " + String.join("; ", verdict.described()));
            }
            byLogin.put(login, account);
            Gauge.builder("forcitis.platform.integration.read.only.check.age.seconds", account, this::secondsSinceLastCheck).tag("connection", connectionName).register(meters);
            startTicking();
        } else if (account.stoppedBecause != null) {
            pool.close().run();
            throw new IllegalStateException("The external connection " + connectionName + " is refused: its login failed the read-only check: "
                    + account.stoppedBecause);
        } else {
            account.add(connectionName, pool);
        }
        byConnection.put(connectionName, account);
    }

    @Override
    public void recheck(String connectionName) {
        Account account;
        synchronized (this) {
            account = byConnection.get(connectionName);
        }
        if (account == null) {
            throw new IllegalStateException("no external connection named " + connectionName + " has been opened");
        }
        account.recheck(connectionName);
    }

    /** One tick: every running login whose last check is older than {@link #MAXIMUM_AGE} is checked again. */
    void recheckDue() {
        List<Account> accounts;
        synchronized (this) {
            accounts = new ArrayList<>(byLogin.values());
        }
        Instant due = clock.instant().minus(MAXIMUM_AGE);
        for (Account account : accounts) {
            if (account.stoppedBecause == null && !account.lastChecked.isAfter(due)) {
                try {
                    account.recheck(account.names.getFirst());
                } catch (ExternalSystemUnavailableException alreadyRaised) {
                    log.debug("the daily read-only check of {} did not pass", account.names);
                } catch (Throwable unexpected) {
                    account.stop("the daily read-only check could not run (" + ExternalFailure.of(unexpected) + ")", account.names.getFirst());
                }
            }
        }
    }

    private double secondsSinceLastCheck(Account account) {
        return Duration.between(account.lastChecked, clock.instant()).toSeconds();
    }

    private void startTicking() {
        if (ticker == null) {
            ticker = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "platform-integration-read-only-recheck");
                thread.setDaemon(true);
                return thread;
            });
            ticker.scheduleWithFixedDelay(this::recheckDueWithoutDying, TICK.toMinutes(), TICK.toMinutes(), TimeUnit.MINUTES);
        }
    }

    /** A scheduled task that throws is never run again, so nothing may escape a tick. */
    private void recheckDueWithoutDying() {
        try {
            recheckDue();
        } catch (Throwable unexpected) {
            log.error("ALERT platform.integration a tick of the daily read-only check failed ({})", ExternalFailure.of(unexpected));
        }
    }

    @Override
    public synchronized void close() {
        if (ticker != null) {
            ticker.shutdownNow();
            ticker = null;
        }
    }

    private final class Account {

        final Pool checkPool;
        final List<String> names = new CopyOnWriteArrayList<>();
        final List<Pool> pools = new CopyOnWriteArrayList<>();
        volatile Instant lastChecked;
        volatile String stoppedBecause;
        int undeterminedInARow;

        Account(Pool checkPool) {
            this.checkPool = checkPool;
        }

        void add(String connectionName, Pool pool) {
            names.add(connectionName);
            pools.add(pool);
        }

        Verdict check() {
            try {
                return ReadOnlyBaseline.check(catalogs.apply(checkPool.source()));
            } finally {
                lastChecked = clock.instant();
            }
        }

        synchronized void recheck(String connectionName) {
            if (stoppedBecause != null) {
                throw stopped(connectionName);
            }
            Verdict verdict;
            try {
                verdict = check();
            } catch (RuntimeException unexpected) {
                stop("the read-only check failed (" + ExternalFailure.of(unexpected) + ")", connectionName);
                throw stopped(connectionName);
            } catch (Error fatal) {
                stop("the read-only check failed (" + ExternalFailure.of(fatal) + ")", connectionName);
                throw fatal;
            }
            if (verdict.passed()) {
                undeterminedInARow = 0;
                return;
            }
            String unmet = String.join("; ", verdict.described());
            if (verdict.violated()) {
                stop("its login failed the read-only check (ADR-0037 item 4): " + unmet, connectionName);
                throw stopped(connectionName);
            }
            undeterminedInARow++;
            if (undeterminedInARow >= UNDETERMINED_IN_A_ROW_STOPS) {
                stop("the read-only check of its login could not be determined " + undeterminedInARow + " times in a row (ADR-0037 item 4): " + unmet,
                        connectionName);
                throw stopped(connectionName);
            }
            log.error("ALERT platform.integration external connection {} is unavailable for this read: the read-only check of its login {} could not "
                    + "be determined ({} of {} in a row before it stops): {}", connectionName, names, undeterminedInARow, UNDETERMINED_IN_A_ROW_STOPS, unmet);
            throw new ExternalSystemUnavailableException("the external connection " + connectionName
                    + " is unavailable for this read: the read-only check of its login could not be determined: " + unmet);
        }

        synchronized void stop(String because, String connectionName) {
            if (stoppedBecause != null) {
                return;
            }
            stoppedBecause = because;
            closeAll();
            log.error("ALERT platform.integration external connection {} stopped with every connection of its login {}, nothing is read until the next start: {}",
                    connectionName, names, stoppedBecause);
        }

        ExternalSystemUnavailableException stopped(String connectionName) {
            return new ExternalSystemUnavailableException("the external connection " + connectionName + " is stopped: " + stoppedBecause);
        }

        void closeAll() {
            pools.forEach(pool -> pool.close().run());
            checkPool.close().run();
        }
    }
}
