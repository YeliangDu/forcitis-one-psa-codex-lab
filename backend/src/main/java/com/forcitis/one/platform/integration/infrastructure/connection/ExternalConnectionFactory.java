package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.api.ExternalCallKind;
import com.forcitis.one.platform.integration.api.ExternalConnections;
import com.forcitis.one.platform.integration.infrastructure.connection.ExternalConnectionProperties.Limits;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;

/**
 * The one place that builds pools to external databases. SQL Server waits for ever for a lock and its driver has no query or read limit
 * by default, so each pool sets queryTimeout, socketTimeout and LOCK_TIMEOUT; PostgreSQL (the stand-in in tests) gets the equivalent settings.
 * The socket limit is only the backstop for a dead network: the statement limits fire first so the server cancels the work and the connection is kept.
 */
final class ExternalConnectionFactory implements ExternalConnections, AutoCloseable {

    private static final int MAXIMUM_POOL_SIZE = 2;

    private final ExternalConnectionProperties properties;
    private final ReadOnlyAccounts accounts;
    private final List<HikariDataSource> pools = new CopyOnWriteArrayList<>();

    ExternalConnectionFactory(ExternalConnectionProperties properties, ReadOnlyAccounts accounts) {
        this.properties = properties;
        this.accounts = accounts;
    }

    /** Every external login is read-only (ADR-0037 item 4): the first pool of a login is only handed out after the login passed its check. */
    @Override
    public DataSource open(String poolName, String jdbcUrl, String username, String password, ExternalCallKind kind) {
        HikariDataSource pool = build(poolName, jdbcUrl, username, password, kind);
        accounts.attach(poolName, new ReadOnlyAccounts.Login(jdbcUrl, username), new ReadOnlyAccounts.Pool(pool, pool::close), () -> {
            HikariDataSource check = build(poolName + "-check", jdbcUrl, username, password, ExternalCallKind.BACKGROUND);
            return new ReadOnlyAccounts.Pool(check, check::close);
        });
        return pool;
    }

    HikariDataSource build(String poolName, String jdbcUrl, String username, String password, ExternalCallKind kind) {
        Limits limits = properties.limitsOf(kind);
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setReadOnly(true);
        config.setMaximumPoolSize(MAXIMUM_POOL_SIZE);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(limits.acquireTimeout().toMillis());
        config.setValidationTimeout(limits.acquireTimeout().toMillis());
        config.setInitializationFailTimeout(-1);
        limitStatementsAndWaits(config, jdbcUrl, limits);
        HikariDataSource pool = new HikariDataSource(config);
        pools.add(pool);
        return pool;
    }

    private static void limitStatementsAndWaits(HikariConfig config, String jdbcUrl, Limits limits) {
        Properties driver = new Properties();
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:sqlserver:")) {
            driver.setProperty("queryTimeout", String.valueOf(limits.queryTimeout().toSeconds()));
            driver.setProperty("socketTimeout", String.valueOf(limits.socketTimeout().toMillis()));
            config.setConnectionInitSql("SET LOCK_TIMEOUT " + limits.lockTimeout().toMillis());
        } else if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:")) {
            driver.setProperty("socketTimeout", String.valueOf(wholeSeconds(limits.socketTimeout())));
            driver.setProperty("options", "-c statement_timeout=" + limits.queryTimeout().toMillis() + " -c lock_timeout=" + limits.lockTimeout().toMillis());
        } else {
            throw new IllegalArgumentException("external connections support SQL Server and PostgreSQL URLs only");
        }
        config.setDataSourceProperties(driver);
    }

    private static long wholeSeconds(Duration limit) {
        return Math.max(1, (limit.toMillis() + 999) / 1000);
    }

    @Override
    public void close() {
        accounts.close();
        pools.forEach(HikariDataSource::close);
        pools.clear();
    }
}
