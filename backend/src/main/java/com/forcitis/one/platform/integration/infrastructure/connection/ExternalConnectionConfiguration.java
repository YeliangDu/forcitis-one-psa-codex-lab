package com.forcitis.one.platform.integration.infrastructure.connection;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
@EnableConfigurationProperties(ExternalConnectionProperties.class)
class ExternalConnectionConfiguration {

    @Bean(destroyMethod = "close")
    ReadOnlyAccounts readOnlyAccounts(Clock clock, MeterRegistry meters) {
        return new ReadOnlyAccounts(clock, source -> new JdbcReadOnlyCatalog(JdbcClient.create(source)), meters);
    }

    @Bean(destroyMethod = "close")
    ExternalConnectionFactory externalConnectionFactory(ExternalConnectionProperties properties, ReadOnlyAccounts accounts) {
        return new ExternalConnectionFactory(properties, accounts);
    }
}
