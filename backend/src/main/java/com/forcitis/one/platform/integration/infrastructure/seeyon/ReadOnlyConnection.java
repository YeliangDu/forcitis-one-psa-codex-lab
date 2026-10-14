package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Deliberately NOT a Spring DataSource bean: that would make Spring Boot back off the application's own PostgreSQL DataSource. */
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
