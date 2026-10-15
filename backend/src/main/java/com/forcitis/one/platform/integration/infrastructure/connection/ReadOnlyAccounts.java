package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.domain.ReadOnlyConnectionCheck;
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
 * Every external pool belongs to one login. The login is checked against {@link ReadOnlyBaseline} when its first pool opens (a failure refuses
 * the start-up), again before each sync and whenever its last check is older than {@link #MAXIMUM_AGE}, so a login only read on request (the
 * merged inbox) is still checked every day. A failed re-check closes every pool of the login: nothing is read until the next start.
 */
final class ReadOnlyAccounts implements ReadOnlyConnectionCheck, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyAccounts.class);

    /** With a tick of {@link #TICK} a login is re-checked at most 23 h 15 min after its last check: at least once a day (ADR-0037 item 4.4). */
    static final Duration MAXIMUM_AGE = Duration.ofHours(23);
    static final Duration TICK = Duration.ofMinutes(15);

    record Login(String jdbcUrl, String username) {
    }

    record Pool(DataSource source, Runnable close) {
    }

    private final Clock clock;
    private final Function<DataSource, ReadOnlyCatalog> catalogs;
    private final Map<Login, Account> byLogin = new HashMap<>();
    private final Map<String, Account> byConnection = new HashMap<>();
    private ScheduledExecutorService ticker;

    ReadOnlyAccounts(Clock clock, Function<DataSource, ReadOnlyCatalog> catalogs) {
        this.clock = clock;
        this.catalogs = catalogs;
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
            List<String> violations = account.check();
            if (!violations.isEmpty()) {
                account.closeAll();
                throw new IllegalStateException("The external connection " + connectionName + " is refused, the application does not start "
                        + "(ADR-0037 item 4): " + String.join("; ", violations));
            }
            byLogin.put(login, account);
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
                    log.debug("the daily read-only check of {} failed", account.names);
                } catch (RuntimeException unexpected) {
                    log.error("ALERT platform.integration the daily read-only check of {} could not run ({})", account.names,
                            unexpected.getClass().getSimpleName());
                }
            }
        }
    }

    private void startTicking() {
        if (ticker == null) {
            ticker = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "platform-integration-read-only-recheck");
                thread.setDaemon(true);
                return thread;
            });
            ticker.scheduleWithFixedDelay(this::recheckDue, TICK.toMinutes(), TICK.toMinutes(), TimeUnit.MINUTES);
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

        Account(Pool checkPool) {
            this.checkPool = checkPool;
        }

        void add(String connectionName, Pool pool) {
            names.add(connectionName);
            pools.add(pool);
        }

        List<String> check() {
            List<String> violations = ReadOnlyBaseline.violations(catalogs.apply(checkPool.source()));
            lastChecked = clock.instant();
            return violations;
        }

        synchronized void recheck(String connectionName) {
            if (stoppedBecause != null) {
                throw new ExternalSystemUnavailableException("the external connection " + connectionName + " is stopped: " + stoppedBecause);
            }
            List<String> violations = check();
            if (violations.isEmpty()) {
                return;
            }
            stoppedBecause = "its login failed the read-only check (ADR-0037 item 4): " + String.join("; ", violations);
            closeAll();
            log.error("ALERT platform.integration external connection {} stopped with every connection of its login {}, nothing is read until the next start: {}",
                    connectionName, names, stoppedBecause);
            throw new ExternalSystemUnavailableException("the external connection " + connectionName + " is stopped: " + stoppedBecause);
        }

        void closeAll() {
            pools.forEach(pool -> pool.close().run());
            checkPool.close().run();
        }
    }
}
