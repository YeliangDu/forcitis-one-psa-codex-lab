package com.forcitis.one.platform.scheduling.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

class ScheduledJobMetricsTest {
    static final String FAILURES = "forcitis.platform.scheduling.job.failures";

    @TestConfiguration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @EnableScheduling
    static class Jobs {
        @Bean
        MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        Nightly nightly() {
            return new Nightly();
        }
    }

    static class Nightly {
        @Scheduled(cron = "0 0 0 1 1 *")
        @SchedulerLock(name = "test.scheduling.breaks")
        void breaks() {
            throw new IllegalStateException("job failed");
        }

        @Scheduled(cron = "0 0 0 1 1 *")
        @SchedulerLock(name = "test.scheduling.works")
        void works() {
        }

        void helper() {
            throw new IllegalStateException("not a job");
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Jobs.class, ScheduledJobMetrics.class);

    private static double failures(MeterRegistry registry, String job) {
        return registry.get(FAILURES).tag("job", job).counter().count();
    }

    @Test
    @DisplayName("a job that throws is counted under its lock name and the exception still reaches the scheduler")
    void failingJobIsCountedAndRethrown() {
        runner.run(context -> {
            Nightly nightly = context.getBean(Nightly.class);
            MeterRegistry registry = context.getBean(MeterRegistry.class);

            assertThatThrownBy(nightly::breaks).isInstanceOf(IllegalStateException.class).hasMessage("job failed");
            assertThatThrownBy(nightly::breaks).isInstanceOf(IllegalStateException.class);

            assertThat(failures(registry, "test.scheduling.breaks")).isEqualTo(2.0);
        });
    }

    @Test
    @DisplayName("a job that succeeds is not counted, and a method that is not a locked job is never counted")
    void successAndPlainMethodsAreNotCounted() {
        runner.run(context -> {
            Nightly nightly = context.getBean(Nightly.class);
            MeterRegistry registry = context.getBean(MeterRegistry.class);

            nightly.works();
            assertThatThrownBy(nightly::helper).isInstanceOf(IllegalStateException.class);

            assertThat(failures(registry, "test.scheduling.works")).isZero();
            assertThat(registry.find(FAILURES).counters()).extracting(c -> c.getId().getTag("job"))
                    .containsExactlyInAnyOrder("test.scheduling.breaks", "test.scheduling.works");
        });
    }

    @Test
    @DisplayName("every scheduled job has its counter at zero from start, so the first failure is a visible increase")
    void everyJobStartsAtZero() {
        runner.run(context -> {
            MeterRegistry registry = context.getBean(MeterRegistry.class);

            assertThat(failures(registry, "test.scheduling.breaks")).isZero();
            assertThat(failures(registry, "test.scheduling.works")).isZero();
        });
    }
}
