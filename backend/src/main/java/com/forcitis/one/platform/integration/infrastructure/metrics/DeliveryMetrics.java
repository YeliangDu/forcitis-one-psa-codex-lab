package com.forcitis.one.platform.integration.infrastructure.metrics;

import com.forcitis.one.platform.integration.domain.OutboundDeliveryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class DeliveryMetrics {

    private static final Logger log = LoggerFactory.getLogger(DeliveryMetrics.class);
    static final Duration OVERDUE_AFTER = Duration.ofMinutes(10);

    private final OutboundDeliveryRepository deliveries;
    private final Clock clock;

    DeliveryMetrics(OutboundDeliveryRepository deliveries, MeterRegistry registry, ObjectProvider<Clock> clock) {
        this.deliveries = deliveries;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
        Gauge.builder("forcitis.platform.integration.deliveries.pending.overdue", this, DeliveryMetrics::pendingOverdue)
                .description("Deliveries still pending more than 10 minutes after they were queued")
                .register(registry);
        Gauge.builder("forcitis.platform.integration.deliveries.dead.letters", this, DeliveryMetrics::deadLetters)
                .description("Deliveries that used up their attempts and wait for an operator")
                .register(registry);
    }

    double pendingOverdue() {
        try {
            return deliveries.countPendingCreatedBefore(clock.instant().minus(OVERDUE_AFTER));
        } catch (RuntimeException unreadable) {
            log.warn("platform.integration overdue-delivery metric unreadable: {}", unreadable.toString());
            return Double.NaN;
        }
    }

    double deadLetters() {
        try {
            return deliveries.countDeadLetters();
        } catch (RuntimeException unreadable) {
            log.warn("platform.integration dead-letter metric unreadable: {}", unreadable.toString());
            return Double.NaN;
        }
    }
}
