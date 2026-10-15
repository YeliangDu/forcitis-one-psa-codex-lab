package com.forcitis.one.platform.integration.infrastructure.legacy;

import com.forcitis.one.platform.integration.api.ExternalCallKind;
import com.forcitis.one.platform.integration.api.ExternalConnections;
import com.forcitis.one.platform.integration.api.OrgShortNames;
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

        @Bean
        Connection legacyConnection(ExternalConnections connections, LegacyProperties properties) {
            return new Connection(JdbcClient.create(
                    connections.open("integration-legacy", properties.jdbcUrl(), properties.username(), properties.password(), ExternalCallKind.BACKGROUND)));
        }

        @Bean
        OrgShortNames legacyOrgNames(Connection connection, LegacyProperties properties) {
            return new LegacyOrgNames(connection.jdbc(), properties.viewName());
        }

        @Bean
        @ConditionalOnProperty(prefix = "forcitis.platform.integration.legacy", name = "validate-on-startup", havingValue = "true")
        SmartInitializingSingleton legacyStartupCheck(Connection connection, LegacyProperties properties) {
            return () -> new SingleObjectGuard(connection.jdbc(), properties.viewName()).verify();
        }
    }

    record Connection(JdbcClient jdbc) {
    }
}
