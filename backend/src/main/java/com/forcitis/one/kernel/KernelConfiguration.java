package com.forcitis.one.kernel;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class KernelConfiguration {

    @Bean
    BusinessZone businessZone(@Value("${forcitis.kernel.business-zone:Asia/Shanghai}") String zone) {
        return new BusinessZone(ZoneId.of(zone));
    }
}
