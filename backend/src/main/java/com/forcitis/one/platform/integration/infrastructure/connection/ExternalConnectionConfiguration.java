package com.forcitis.one.platform.integration.infrastructure.connection;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExternalConnectionProperties.class)
class ExternalConnectionConfiguration {

    @Bean(destroyMethod = "close")
    ExternalConnectionFactory externalConnectionFactory(ExternalConnectionProperties properties) {
        return new ExternalConnectionFactory(properties);
    }
}
