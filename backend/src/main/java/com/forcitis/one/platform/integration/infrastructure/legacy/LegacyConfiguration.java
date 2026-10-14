package com.forcitis.one.platform.integration.infrastructure.legacy;

import com.forcitis.one.platform.integration.api.OrgShortNames;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
@EnableConfigurationProperties(LegacyProperties.class)
class LegacyConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "forcitis.platform.integration.legacy", name = "enabled", havingValue = "false", matchIfMissing = true)
    OrgShortNames disabledOrgNames() {
        return new DisabledOrgNames();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "forcitis.platform.integration.legacy", name = "enabled", havingValue = "true")
    static class Enabled {

        @Bean(destroyMethod = "close")
        Connection legacyConnection(LegacyProperties properties) {
            return Connection.open(properties);
        }

        @Bean
        OrgShortNames legacyOrgNames(Connection connection, LegacyProperties properties) {
            return new LegacyOrgNames(connection.jdbc, properties.viewName());
        }

        @Bean
        @ConditionalOnProperty(prefix = "forcitis.platform.integration.legacy", name = "validate-on-startup", havingValue = "true")
        SmartInitializingSingleton legacyStartupCheck(Connection connection, LegacyProperties properties) {
            return () -> new SingleObjectGuard(connection.jdbc, properties.viewName()).verify();
        }
    }

    static final class Connection implements AutoCloseable {

        private final HikariDataSource dataSource;
        final JdbcClient jdbc;

        private Connection(HikariDataSource dataSource) {
            this.dataSource = dataSource;
            this.jdbc = JdbcClient.create(dataSource);
        }

        static Connection open(LegacyProperties properties) {
            HikariConfig config = new HikariConfig();
            config.setPoolName("integration-legacy");
            config.setJdbcUrl(properties.jdbcUrl());
            config.setUsername(properties.username());
            config.setPassword(properties.password());
            config.setReadOnly(true);
            config.setMaximumPoolSize(2);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(5_000);
            config.setValidationTimeout(3_000);
            config.setInitializationFailTimeout(-1);
            return new Connection(new HikariDataSource(config));
        }

        @Override
        public void close() {
            dataSource.close();
        }
    }
}
