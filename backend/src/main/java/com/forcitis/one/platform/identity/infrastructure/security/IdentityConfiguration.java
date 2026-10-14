package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.identity.application.SessionSettings;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

@Configuration
@EnableAsync
@ConfigurationPropertiesScan("com.forcitis.one.platform.identity")
class IdentityConfiguration {

    /** Boot 4 no longer maps server.servlet.session.cookie.* onto Spring Session's serializer, so the cookie is set here. */
    @Bean
    DefaultCookieSerializerCustomizer identityCookieSerializerCustomizer(SessionSettings settings) {
        return serializer -> {
            serializer.setCookieName("SESSION");
            serializer.setUseHttpOnlyCookie(true);
            serializer.setUseSecureCookie(settings.secureCookie());
            serializer.setSameSite(settings.sameSite());
        };
    }

    /** The absolute cap is enforced by SessionGuardFilter. */
    @Bean
    SessionRepositoryCustomizer<JdbcIndexedSessionRepository> identitySessionRepositoryCustomizer(SessionSettings settings) {
        return repository -> repository.setDefaultMaxInactiveInterval(settings.idleTimeout());
    }
}
