package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.platform.integration.api.ExternalCallKind;
import com.forcitis.one.platform.integration.api.ExternalConnections;
import com.forcitis.one.platform.integration.api.OrgSource;
import com.forcitis.one.platform.integration.domain.ReadOnlyConnectionCheck;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Views connection is off by default; when on, the login check (connection factory) and the view check must pass, there is no way to skip them. */
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

        static final String INTERACTIVE = "integration-seeyon-interactive";
        static final String BACKGROUND = "integration-seeyon-background";

        /** Deliberately NOT a DataSource bean: that would make Spring Boot back off the application's own PostgreSQL DataSource. */
        @Bean
        SeeyonConnections seeyonConnections(ExternalConnections connections, SeeyonProperties properties) {
            SeeyonProperties.Views views = properties.views();
            return new SeeyonConnections(
                    JdbcClient.create(connections.open(INTERACTIVE, views.jdbcUrl(), views.username(), views.password(), ExternalCallKind.INTERACTIVE)),
                    JdbcClient.create(connections.open(BACKGROUND, views.jdbcUrl(), views.username(), views.password(), ExternalCallKind.BACKGROUND)));
        }

        @Bean
        OrgSource seeyonOrgSource(SeeyonConnections connections, SeeyonProperties properties, ReadOnlyConnectionCheck check) {
            return new SeeyonOrgSource(connections.interactive(), connections.background(), properties.views().mapping(), () -> check.recheck(BACKGROUND));
        }

        @Bean
        SeeyonSchemaInspector seeyonSchemaInspector(SeeyonConnections connections) {
            return new JdbcSeeyonSchemaInspector(connections.background());
        }

        /** A wrong view column or configured id stops the application. */
        @Bean
        SmartInitializingSingleton seeyonStartupCheck(SeeyonSchemaInspector inspector, SeeyonProperties properties) {
            return new SeeyonStartupCheck(inspector, properties.views().mapping());
        }
    }
}
