package com.forcitis.one.kernel;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.SmartInitializingSingleton;
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
    SmartInitializingSingleton utcJvmCheck() {
        return () -> UtcJvm.require(ZoneId.systemDefault());
    }

    @Bean
    BusinessZone businessZone(@Value("${forcitis.kernel.business-zone:Asia/Shanghai}") String zone) {
        return new BusinessZone(ZoneId.of(zone));
    }
}
