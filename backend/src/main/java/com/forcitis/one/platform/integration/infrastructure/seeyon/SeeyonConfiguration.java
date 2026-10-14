package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.platform.integration.api.OrgSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring of the Seeyon anti-corruption layer. The organisation views connection is qualified "seeyon"; it is off by
 * default (a disabled stand-in answers instead). Switching it on requires its connection settings (environment
 * variables) and, unless explicitly turned off, its start-up check must pass. The single-sign-on ticket check needs no
 * connection; it answers "unavailable" while its base URL is not configured.
 */
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

        /** Switchable (validate-on-startup, on by default in the shipped configuration): a wrong view, id or permission stops the application. */
        @Bean
        @ConditionalOnProperty(prefix = "forcitis.platform.integration.seeyon.views", name = "validate-on-startup", havingValue = "true")
        SmartInitializingSingleton seeyonStartupCheck(SeeyonSchemaInspector inspector, SeeyonProperties properties) {
            return new SeeyonStartupCheck(inspector, properties.views().mapping());
        }
    }
}
