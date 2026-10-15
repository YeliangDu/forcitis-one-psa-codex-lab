package com.forcitis.one.platform.approval.infrastructure.legacy;

import com.forcitis.one.platform.approval.domain.LegacyInbox;
import com.forcitis.one.platform.integration.api.ExternalCallKind;
import com.forcitis.one.platform.integration.api.ExternalConnections;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The legacy DataSource is qualified 'legacy-approval' and not an autowire default, so the application's DataSource, Flowable and Flyway never pick it up. */
@Configuration
@EnableConfigurationProperties(LegacyProperties.class)
class LegacyInboxConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LegacyInboxConfiguration.class);

    @Bean(defaultCandidate = false)
    @Qualifier("legacy-approval")
    @ConditionalOnExpression("!'${forcitis.platform.approval.legacy.url:}'.isBlank()")
    DataSource legacyApprovalDataSource(LegacyProperties properties, ExternalConnections connections) {
        return connections.open("legacy-approval", properties.url(), properties.username(), properties.password(), ExternalCallKind.INTERACTIVE);
    }

    @Bean
    LegacyInbox legacyInbox(LegacyProperties properties, @Qualifier("legacy-approval") ObjectProvider<DataSource> source) {
        DataSource dataSource = source.getIfAvailable();
        if (dataSource == null) {
            log.warn("forcitis.platform.approval.legacy.url is not set: the merged inbox shows new tasks only");
            return user -> LegacyInbox.LegacyTodos.unavailable();
        }
        return new JdbcLegacyInbox(JdbcClient.create(dataSource), properties.viewName(), properties.detailUrlTemplate());
    }

    /** I-1: refuse to start when the read-only account can read anything but the inbox view. */
    @Bean
    SmartInitializingSingleton legacyAccessCheck(LegacyProperties properties, @Qualifier("legacy-approval") ObjectProvider<DataSource> source) {
        return () -> {
            DataSource dataSource = source.getIfAvailable();
            if (dataSource == null) {
                return;
            }
            try {
                new LegacyAccessGuard(JdbcClient.create(dataSource), properties.viewName()).verify();
            } catch (DataAccessException e) {
                log.error("Could not verify the grants of the legacy read-only account (database unreachable?)", e);
            }
        };
    }
}
