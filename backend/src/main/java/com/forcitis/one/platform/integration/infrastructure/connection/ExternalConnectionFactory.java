package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.api.ExternalCallKind;
import com.forcitis.one.platform.integration.api.ExternalConnections;
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
 */
final class ExternalConnectionFactory implements ExternalConnections, AutoCloseable {

    private static final int MAXIMUM_POOL_SIZE = 2;

    private final ExternalConnectionProperties properties;
    private final List<HikariDataSource> pools = new CopyOnWriteArrayList<>();

    ExternalConnectionFactory(ExternalConnectionProperties properties) {
        this.properties = properties;
    }

    @Override
    public DataSource open(String poolName, String jdbcUrl, String username, String password, ExternalCallKind kind) {
        Duration limit = properties.timeoutOf(kind);
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setReadOnly(true);
        config.setMaximumPoolSize(MAXIMUM_POOL_SIZE);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(limit.toMillis());
        config.setValidationTimeout(limit.toMillis());
        config.setInitializationFailTimeout(-1);
        limitStatementsAndWaits(config, jdbcUrl, limit);
        HikariDataSource pool = new HikariDataSource(config);
        pools.add(pool);
        return pool;
    }

    private static void limitStatementsAndWaits(HikariConfig config, String jdbcUrl, Duration limit) {
        Properties driver = new Properties();
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:sqlserver:")) {
            driver.setProperty("queryTimeout", String.valueOf(wholeSeconds(limit)));
            driver.setProperty("socketTimeout", String.valueOf(limit.toMillis()));
            config.setConnectionInitSql("SET LOCK_TIMEOUT " + limit.toMillis());
        } else if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:")) {
            driver.setProperty("socketTimeout", String.valueOf(wholeSeconds(limit)));
            driver.setProperty("options", "-c statement_timeout=" + limit.toMillis() + " -c lock_timeout=" + limit.toMillis());
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
        pools.forEach(HikariDataSource::close);
        pools.clear();
    }
}
