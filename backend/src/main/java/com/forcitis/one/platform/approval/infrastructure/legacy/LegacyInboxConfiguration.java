package com.forcitis.one.platform.approval.infrastructure.legacy;

import com.forcitis.one.platform.approval.domain.LegacyInbox;
import com.zaxxer.hikari.HikariDataSource;
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

/**
 * Wires the read-only connection to the old system. The DataSource exists only when forcitis.platform.approval.legacy.url
 * is set, is qualified "legacy-approval" and is NOT an autowire default (so the application's own DataSource, Flowable and
 * Flyway never pick it up). Read-only, two connections, no write path (I-1, REIMAGINED_ARCHITECTURE §3.6).
 */
@Configuration
@EnableConfigurationProperties(LegacyProperties.class)
class LegacyInboxConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LegacyInboxConfiguration.class);

    @Bean(defaultCandidate = false)
    @Qualifier("legacy-approval")
    @ConditionalOnExpression("!'${forcitis.platform.approval.legacy.url:}'.isBlank()")
    DataSource legacyApprovalDataSource(LegacyProperties properties) {
        HikariDataSource source = new HikariDataSource();
        source.setPoolName("legacy-approval");
        source.setJdbcUrl(properties.url());
        source.setUsername(properties.username());
        source.setPassword(properties.password());
        source.setReadOnly(true);
        source.setMaximumPoolSize(2);
        source.setConnectionTimeout(5_000);
        source.setInitializationFailTimeout(-1);
        return source;
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
