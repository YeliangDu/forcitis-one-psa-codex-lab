package com.forcitis.one.platform.identity.infrastructure.startup;

import com.forcitis.one.platform.audit.api.AuditLog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.ConfigurableEnvironment;

@Configuration
class SingleOperatorModeConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static SingleOperatorModeSettings singleOperatorModeSettings(ConfigurableEnvironment environment) {
        return new SingleOperatorModeSettings(SingleOperatorModeListener.resolve(environment));
    }

    @Bean
    static SingleOperatorModeStartup singleOperatorModeStartup(SingleOperatorModeSettings settings, ConfigurableEnvironment environment,
            ObjectProvider<AuditLog> audit) {
        return new SingleOperatorModeStartup(settings.enabled(), String.join(",", environment.getActiveProfiles()), audit);
    }
}
