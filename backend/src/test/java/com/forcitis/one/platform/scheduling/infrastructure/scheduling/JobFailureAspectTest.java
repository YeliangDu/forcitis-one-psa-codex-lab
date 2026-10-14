package com.forcitis.one.platform.scheduling.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

class JobFailureAspectTest {
    static class Jobs {
        @SchedulerLock(name = "platform.test.job.fails")
        public void fails() {
            throw new IllegalStateException("boom");
        }

        @SchedulerLock(name = "platform.test.job.works")
        public void works() {
        }
    }

    @Test
    @DisplayName("a job that fails is counted under its lock name and the failure still reaches the scheduler; a job that works is not counted")
    void failuresAreCounted() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AspectJProxyFactory factory = new AspectJProxyFactory(new Jobs());
        factory.addAspect(new JobFailureAspect(meters));
        Jobs jobs = factory.getProxy();

        jobs.works();
        assertThat(meters.find("forcitis.platform.scheduling.job.failures").counters()).isEmpty();
        assertThatThrownBy(jobs::fails).hasMessage("boom");
        assertThatThrownBy(jobs::fails).hasMessage("boom");

        assertThat(meters.get("forcitis.platform.scheduling.job.failures").tag("job", "platform.test.job.fails").counter().count()).isEqualTo(2.0);
    }
}
