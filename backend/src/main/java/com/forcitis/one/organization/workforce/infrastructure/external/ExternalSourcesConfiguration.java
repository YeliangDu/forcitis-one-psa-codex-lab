package com.forcitis.one.organization.workforce.infrastructure.external;

import com.forcitis.one.organization.workforce.domain.OrgSource;
import com.forcitis.one.organization.workforce.domain.ShortNameSource;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wiring of the two external read-only sources. The A8 views connection is qualified "a8", the old-system connection
 * "legacy". Both are off by default (disabled stand-ins answer instead); switching one on requires its connection
 * settings (environment variables) and, unless explicitly turned off, its start-up check must pass.
 */
@Configuration
@EnableConfigurationProperties(LegacyProperties.class)
class ExternalSourcesConfiguration {

    @Bean
    A8Properties a8Properties(Environment environment) {
        return A8Properties.bind(environment);
    }

    @Bean
    @ConditionalOnProperty(prefix = "forcitis.organization.workforce.a8", name = "enabled", havingValue = "false", matchIfMissing = true)
    OrgSource disabledOrgSource() {
        return new DisabledOrgSource();
    }

    @Bean
    @ConditionalOnProperty(prefix = "forcitis.organization.workforce.legacy", name = "enabled", havingValue = "false", matchIfMissing = true)
    ShortNameSource disabledShortNameSource() {
        return new DisabledShortNameSource();
    }

    // ---------------------------------------------------------------- A8 views

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "forcitis.organization.workforce.a8", name = "enabled", havingValue = "true")
    static class A8Enabled {

        @Bean(destroyMethod = "close")
        @Qualifier("a8")
        ReadOnlyConnection a8Connection(A8Properties properties) {
            return ReadOnlyConnection.open("workforce-a8", properties.jdbcUrl(), properties.username(), properties.password());
        }

        @Bean
        OrgSource a8OrgSource(@Qualifier("a8") ReadOnlyConnection connection, A8Properties properties) {
            return new A8OrgSource(connection.jdbc(), properties.mapping());
        }

        @Bean
        A8SchemaInspector a8SchemaInspector(@Qualifier("a8") ReadOnlyConnection connection) {
            return new JdbcA8SchemaInspector(connection.jdbc());
        }

        /** Switchable (validate-on-startup, on by default): a wrong view, id or permission stops the application. */
        @Bean
        @ConditionalOnProperty(prefix = "forcitis.organization.workforce.a8", name = "validate-on-startup", havingValue = "true")
        SmartInitializingSingleton a8StartupCheck(A8SchemaInspector inspector, A8Properties properties) {
            return new A8StartupCheck(inspector, properties.mapping());
        }
    }

    // ---------------------------------------------------------------- old system (short names)

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "forcitis.organization.workforce.legacy", name = "enabled", havingValue = "true")
    static class LegacyEnabled {

        @Bean(destroyMethod = "close")
        @Qualifier("legacy")
        ReadOnlyConnection legacyConnection(LegacyProperties properties) {
            return ReadOnlyConnection.open("workforce-legacy", properties.jdbcUrl(), properties.username(), properties.password());
        }

        @Bean
        ShortNameSource legacyShortNameSource(@Qualifier("legacy") ReadOnlyConnection connection) {
            return new LegacyShortNameSource(connection.jdbc());
        }

        /** The old-system account must be read-only too: no write, execute or database-wide read permission. */
        @Bean
        @ConditionalOnProperty(prefix = "forcitis.organization.workforce.legacy", name = "validate-on-startup", havingValue = "true")
        SmartInitializingSingleton legacyStartupCheck(@Qualifier("legacy") ReadOnlyConnection connection) {
            return () -> {
                List<String> excessive = ReadOnlyGuard.excessiveDatabasePermissions(connection.jdbc());
                if (!excessive.isEmpty()) {
                    throw new IllegalStateException("the old-system account holds database-level permissions a read-only account "
                            + "must not have: " + excessive);
                }
            };
        }
    }
}
