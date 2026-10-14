package com.forcitis.one.organization.workforce.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SyncPurgeKeepPeriodsTest {
    @Configuration
    @EnableConfigurationProperties(SyncPurgeJob.KeepPeriods.class)
    static class Enabled {
    }

    @Test
    @DisplayName("K7 a missing keep period stops the start-up")
    void keepPeriodsAreRequired() {
        var runner = new ApplicationContextRunner().withUserConfiguration(Enabled.class);
        runner.withPropertyValues("forcitis.organization.workforce.sync-purge.run-keep-period=P2Y",
                "forcitis.organization.workforce.sync-purge.diff-keep-period=P1Y").run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues("forcitis.organization.workforce.sync-purge.run-keep-period=P2Y").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("forcitis.organization.workforce.sync-purge.diff-keep-period=P1Y").run(context -> assertThat(context).hasFailed());
    }
}
