package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.platform.identity.application.SessionSettings;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

/**
 * Wiring for platform.identity: its settings records, the shared Clock (time is always injected), asynchronous module
 * listeners (@ApplicationModuleListener needs @EnableAsync), and the idle timeout of the session repository.
 */
@Configuration
@EnableAsync
@ConfigurationPropertiesScan("com.forcitis.one.platform.identity")
class IdentityConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock identityClock() {
        return Clock.systemUTC();
    }

    /**
     * The session cookie: SESSION, HttpOnly, Secure and SameSite=Lax. Boot 4 no longer maps server.servlet.session.cookie.* onto
     * Spring Session's serializer, so it is set here (WecomLoginIntegrationTest guards the attributes).
     */
    @Bean
    DefaultCookieSerializerCustomizer identityCookieSerializerCustomizer(SessionSettings settings) {
        return serializer -> {
            serializer.setCookieName("SESSION");
            serializer.setUseHttpOnlyCookie(true);
            serializer.setUseSecureCookie(settings.secureCookie());
            serializer.setSameSite(settings.sameSite());
        };
    }

    /** Idle timeout of the session: 10 h by default, configurable (RULE-332). The absolute cap is enforced by SessionGuardFilter. */
    @Bean
    SessionRepositoryCustomizer<JdbcIndexedSessionRepository> identitySessionRepositoryCustomizer(SessionSettings settings) {
        return repository -> repository.setDefaultMaxInactiveInterval(settings.idleTimeout());
    }
}
