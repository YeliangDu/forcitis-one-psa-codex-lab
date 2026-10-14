package com.forcitis.one.organization.workforce.infrastructure.external;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A small read-only connection pool to an external system (A8 views, or the old system's department table).
 * It is deliberately NOT a Spring DataSource bean: a second DataSource bean would make Spring Boot back off the
 * application's own PostgreSQL DataSource (Flyway, sessions, locks). The beans are qualified "a8" and "legacy" and
 * only organization.workforce.infrastructure may use them (ArchUnit: A8DataSourceIsolationTest). Credentials come
 * from the environment through configuration; they are never logged.
 */
final class ReadOnlyConnection implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final JdbcClient jdbc;

    private ReadOnlyConnection(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = JdbcClient.create(dataSource);
    }

    static ReadOnlyConnection open(String poolName, String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setReadOnly(true);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5_000);
        config.setValidationTimeout(3_000);
        config.setInitializationFailTimeout(-1);
        return new ReadOnlyConnection(new HikariDataSource(config));
    }

    JdbcClient jdbc() {
        return jdbc;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
