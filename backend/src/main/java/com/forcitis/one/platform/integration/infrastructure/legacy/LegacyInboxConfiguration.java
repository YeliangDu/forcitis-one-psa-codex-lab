package com.forcitis.one.platform.integration.infrastructure.legacy;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.platform.integration.api.ExternalCallKind;
import com.forcitis.one.platform.integration.domain.ExternalConnections;
import com.forcitis.one.platform.integration.api.LegacyInbox;
import com.forcitis.one.platform.integration.application.SourceReferences;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The to-do connection is no DataSource bean, so the application's DataSource, Flowable and Flyway never pick it up. The connection
 * factory refuses the start-up when its login fails the read-only check (ADR-0037 item 4) and re-checks it daily. The keys are
 * forcitis.platform.integration.legacy.inbox.*.
 */
@Configuration
class LegacyInboxConfiguration {

    static final String CONNECTION = "integration-legacy-inbox";

    private static final Logger log = LoggerFactory.getLogger(LegacyInboxConfiguration.class);

    @Bean
    LegacyInboxSettings legacyInboxSettings(Environment environment) {
        return new LegacyInboxSettings(settings(environment));
    }

    static LegacyInboxProperties settings(Environment environment) {
        return Binder.get(environment).bind(LegacyInboxProperties.PREFIX, LegacyInboxProperties.class).orElseGet(LegacyInboxProperties::none);
    }

    @Bean
    @Conditional(Connected.class)
    InboxConnection legacyInboxConnection(LegacyInboxSettings settings, ExternalConnections connections) {
        LegacyInboxProperties properties = settings.properties();
        return new InboxConnection(JdbcClient.create(
                connections.open(CONNECTION, properties.url(), properties.username(), properties.password(), ExternalCallKind.INTERACTIVE)));
    }

    @Bean
    LegacyInbox legacyInbox(LegacyInboxSettings settings, ObjectProvider<InboxConnection> connection, SourceReferences references,
                            BusinessZone zone, Clock clock, MeterRegistry meters) {
        InboxConnection connected = connection.getIfAvailable();
        if (connected == null) {
            log.warn("{}.url is not set: the merged inbox shows new tasks only", LegacyInboxProperties.PREFIX);
            return person -> LegacyInbox.LegacyTodos.unavailable();
        }
        LegacyInboxProperties properties = settings.properties();
        return new JdbcLegacyInbox(connected.jdbc(), properties.viewName(), properties.detailUrlTemplate(), references, zone, clock, meters);
    }

    record LegacyInboxSettings(LegacyInboxProperties properties) {
    }

    record InboxConnection(JdbcClient jdbc) {
    }

    static final class Connected implements org.springframework.context.annotation.Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return settings(context.getEnvironment()).isConfigured();
        }
    }
}
