package com.forcitis.one.cost.timesheet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ConsumedEventsKeepPeriodConfigurationTest {
    @Configuration
    @EnableConfigurationProperties(ConsumedEventsPurgeJob.KeepPeriod.class)
    static class Enabled {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Enabled.class);

    @Test
    @DisplayName("T3 the handled-event keep period comes from forcitis.cost.timesheet.consumed-events.keep-period; without it the start-up fails")
    void keepPeriodIsRequired() {
        runner.withPropertyValues("forcitis.cost.timesheet.consumed-events.keep-period=P90D").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ConsumedEventsPurgeJob.KeepPeriod.class).keepPeriod().toDays()).isEqualTo(90);
        });
        runner.run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("forcitis.cost.timesheet.consumed-events.keep-period=PT0S").run(context -> assertThat(context).hasFailed());
    }
}
