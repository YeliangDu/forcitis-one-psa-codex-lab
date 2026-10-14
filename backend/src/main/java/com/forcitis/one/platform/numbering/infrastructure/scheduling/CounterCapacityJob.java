package com.forcitis.one.platform.numbering.infrastructure.scheduling;

import com.forcitis.one.platform.numbering.application.CheckCounterCapacity;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Job / lock / metric names are {subdomain}.{bc}.{name} (G46). */
@Component
class CounterCapacityJob {
    private final CheckCounterCapacity checkCounterCapacity;
    private final BigDecimal warningRatio;
    private final AtomicLong countersNearCapacity = new AtomicLong();

    CounterCapacityJob(CheckCounterCapacity checkCounterCapacity, MeterRegistry registry,
            @Value("${forcitis.platform.numbering.capacity-check.warning-ratio}") BigDecimal warningRatio) {
        this.checkCounterCapacity = checkCounterCapacity;
        this.warningRatio = warningRatio;
        Gauge.builder("forcitis.platform.numbering.counters.near.capacity", countersNearCapacity, AtomicLong::get)
                .description("Document types whose counter reached the warning ratio of the {seq:N} capacity")
                .register(registry);
    }

    @Scheduled(cron = "${forcitis.platform.numbering.capacity-check.cron}")
    @SchedulerLock(name = "platform.numbering.counter.capacity.check")
    void checkCounterCapacity() {
        countersNearCapacity.set(checkCounterCapacity.execute(warningRatio));
    }
}
