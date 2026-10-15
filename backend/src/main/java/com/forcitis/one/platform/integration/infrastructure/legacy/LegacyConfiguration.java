package com.forcitis.one.platform.integration.infrastructure.legacy;

import com.forcitis.one.platform.integration.api.ExternalCallKind;
import com.forcitis.one.platform.integration.api.ExternalConnections;
import com.forcitis.one.platform.integration.api.OrgShortNames;
import com.forcitis.one.platform.integration.domain.ReadOnlyConnectionCheck;
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

        static final String CONNECTION = "integration-legacy";

        /** The connection factory checks the login before it hands out the pool (ADR-0037 item 4); there is no way to skip it. */
        @Bean
        Connection legacyConnection(ExternalConnections connections, LegacyProperties properties) {
            return new Connection(JdbcClient.create(
                    connections.open(CONNECTION, properties.jdbcUrl(), properties.username(), properties.password(), ExternalCallKind.BACKGROUND)));
        }

        @Bean
        OrgShortNames legacyOrgNames(Connection connection, LegacyProperties properties, ReadOnlyConnectionCheck check) {
            return new LegacyOrgNames(connection.jdbc(), properties.viewName(), () -> check.recheck(CONNECTION));
        }
    }

    record Connection(JdbcClient jdbc) {
    }
}
