package com.forcitis.one.kernel;

import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class KernelConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(IdGenerator.class)
    IdGenerator idGenerator() {
        return UUID::randomUUID;
    }

    @Bean
    BusinessZone businessZone(@Value("${forcitis.kernel.business-zone:Asia/Shanghai}") String zone) {
        return new BusinessZone(ZoneId.of(zone));
    }
}
