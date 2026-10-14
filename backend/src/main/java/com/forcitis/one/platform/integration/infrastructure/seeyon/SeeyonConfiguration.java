package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.platform.integration.api.OrgSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

        @Bean(destroyMethod = "close")
        @Qualifier("seeyon")
        ReadOnlyConnection seeyonConnection(SeeyonProperties properties) {
            SeeyonProperties.Views views = properties.views();
            return ReadOnlyConnection.open("integration-seeyon", views.jdbcUrl(), views.username(), views.password());
        }

        @Bean
        OrgSource seeyonOrgSource(@Qualifier("seeyon") ReadOnlyConnection connection, SeeyonProperties properties) {
            return new SeeyonOrgSource(connection.jdbc(), properties.views().mapping());
        }

        @Bean
        SeeyonSchemaInspector seeyonSchemaInspector(@Qualifier("seeyon") ReadOnlyConnection connection) {
            return new JdbcSeeyonSchemaInspector(connection.jdbc());
        }

        /** Validate-on-startup: a wrong view, id or permission stops the application. */
        @Bean
        @ConditionalOnProperty(prefix = "forcitis.platform.integration.seeyon.views", name = "validate-on-startup", havingValue = "true")
        SmartInitializingSingleton seeyonStartupCheck(SeeyonSchemaInspector inspector, SeeyonProperties properties) {
            return new SeeyonStartupCheck(inspector, properties.views().mapping());
        }
    }
}
