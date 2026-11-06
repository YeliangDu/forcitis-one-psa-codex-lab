package com.forcitis.one.kernel;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
class KernelConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    @Profile("!local") // #1478: under the profile local the settable clock below is the Clock
    Clock systemClock() {
        return Clock.systemUTC();
    }

    /** Card #1478: profile local only. Starts at the real time, or at forcitis.kernel.local-clock-start when given; the local-only endpoint moves it. */
    @Bean
    @Profile("local")
    SettableClock settableClock(@Value("${forcitis.kernel.local-clock-start:}") String start) {
        SettableClock clock = new SettableClock(Clock.systemUTC());
        if (!start.isBlank()) {
            clock.set(Instant.parse(start.strip()));
        }
        return clock;
    }

    @Bean
    @ConditionalOnMissingBean(IdGenerator.class)
    IdGenerator idGenerator(Clock clock) {
        return new TimeOrderedIds(clock);
    }

    @Bean
    SmartInitializingSingleton utcJvmCheck() {
        return () -> UtcJvm.require(ZoneId.systemDefault());
    }

    @Bean
    BusinessZone businessZone(@Value("${forcitis.kernel.business-zone:Asia/Shanghai}") String zone) {
        return new BusinessZone(ZoneId.of(zone));
    }
}
