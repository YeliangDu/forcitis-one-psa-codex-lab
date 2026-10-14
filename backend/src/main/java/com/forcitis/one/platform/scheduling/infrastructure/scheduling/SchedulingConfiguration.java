package com.forcitis.one.platform.scheduling.infrastructure.scheduling;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/** One lock table for every module's jobs; the lock name is the job name {subdomain}.{bc}.{name} (G46). */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class SchedulingConfiguration {

    /** The key has no default: a deployment says whether this instance runs the jobs, and the tests say no. */
    @Configuration
    @EnableScheduling
    @ConditionalOnExpression("${forcitis.platform.scheduling.jobs-enabled}")
    static class JobsActivation {
    }

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName("platform_scheduling.scheduler_locks")
                .withColumnNames(new JdbcTemplateLockProvider.ColumnNames("lock_name", "lock_until", "locked_at", "locked_by"))
                .usingDbTime()
                .build());
    }
}
