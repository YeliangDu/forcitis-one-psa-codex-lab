package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.platform.integration.api.ExternalCallKind;
import com.forcitis.one.platform.integration.api.ExternalConnections;
import com.forcitis.one.platform.integration.api.OrgSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Views connection is off by default; when on, the start-up check must pass unless explicitly turned off. */
@Configuration
@EnableConfigurationProperties(SeeyonProperties.class)
class SeeyonConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "forcitis.platform.integration.seeyon.views", name = "enabled", havingValue = "false", matchIfMissing = true)
    OrgSource disabledOrgSource() {
        return new DisabledOrgSource();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "forcitis.platform.integration.seeyon.views", name = "enabled", havingValue = "true")
    static class ViewsEnabled {

        /** Deliberately NOT a DataSource bean: that would make Spring Boot back off the application's own PostgreSQL DataSource. */
        @Bean
        SeeyonConnections seeyonConnections(ExternalConnections connections, SeeyonProperties properties) {
            SeeyonProperties.Views views = properties.views();
            return new SeeyonConnections(
                    JdbcClient.create(connections.open("integration-seeyon-interactive", views.jdbcUrl(), views.username(), views.password(), ExternalCallKind.INTERACTIVE)),
                    JdbcClient.create(connections.open("integration-seeyon-background", views.jdbcUrl(), views.username(), views.password(), ExternalCallKind.BACKGROUND)));
        }

        @Bean
        OrgSource seeyonOrgSource(SeeyonConnections connections, SeeyonProperties properties) {
            return new SeeyonOrgSource(connections.interactive(), connections.background(), properties.views().mapping());
        }

        @Bean
        SeeyonSchemaInspector seeyonSchemaInspector(SeeyonConnections connections) {
            return new JdbcSeeyonSchemaInspector(connections.background());
        }

        /** Validate-on-startup: a wrong view, id or permission stops the application. */
        @Bean
        @ConditionalOnProperty(prefix = "forcitis.platform.integration.seeyon.views", name = "validate-on-startup", havingValue = "true")
        SmartInitializingSingleton seeyonStartupCheck(SeeyonSchemaInspector inspector, SeeyonProperties properties) {
            return new SeeyonStartupCheck(inspector, properties.views().mapping());
        }
    }
}
