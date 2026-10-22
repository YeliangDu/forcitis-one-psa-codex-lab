package com.forcitis.one.platform.integration.infrastructure.transport;

import java.util.Set;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** A static post-processor runs before any bean exists, so no connection is opened (and no password sent) before the check has passed. */
@Configuration(proxyBeanMethods = false)
class DatabaseTransportConfiguration {

    @Bean
    static BeanFactoryPostProcessor databaseTransportGuard(Environment environment) {
        return beanFactory -> {
            DatabaseTransportRequirement required = DatabaseTransportRequirement.forProfiles(Set.of(environment.getActiveProfiles()));
            var problems = DatabaseTransportCheck.problems(required, environment::getProperty);
            if (!problems.isEmpty()) {
                throw new IllegalStateException("Database transport requirement " + required + " is not met, refusing to start:\n  "
                        + String.join("\n  ", problems));
            }
        };
    }
}
