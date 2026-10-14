package com.forcitis.one.platform.integration.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OutboundDeliveryPurgeKeepPeriodsTest {
    @Configuration
    @EnableConfigurationProperties(OutboundDeliveryPurgeJob.KeepPeriods.class)
    static class Enabled {
    }

    @Test
    @DisplayName("K7 a missing keep period stops the start-up")
    void keepPeriodsAreRequired() {
        var runner = new ApplicationContextRunner().withUserConfiguration(Enabled.class);
        runner.withPropertyValues("forcitis.platform.integration.outbound-purge.delivered-keep-period=P90D",
                "forcitis.platform.integration.outbound-purge.failed-keep-period=P1Y").run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues("forcitis.platform.integration.outbound-purge.delivered-keep-period=P90D").run(context -> assertThat(context).hasFailed());
        runner.run(context -> assertThat(context).hasFailed());
    }
}
