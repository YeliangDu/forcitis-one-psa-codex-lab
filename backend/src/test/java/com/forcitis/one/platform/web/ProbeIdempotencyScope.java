package com.forcitis.one.platform.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** For a test that sends Idempotency-Key to probe endpoints: the real list plus the two probe paths of IdempotencyIT. */
@TestConfiguration
public class ProbeIdempotencyScope {
    @Bean
    @Primary
    IdempotencyScope probeScope() {
        return new IdempotencyScope().plus("POST /api/v1/platform/idempotency-probe/orders", "POST /api/v1/platform/idempotency-probe/sized");
    }
}
