package com.forcitis.one.platform.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** For a test that sends Idempotency-Key to probe endpoints: the real list plus the two probe paths of IdempotencyIT. */
@TestConfiguration
public class ProbeIdempotencyScope {
    @Bean
    @Primary
    IdempotencyScope probeScope() throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(Path.of("architecture/idempotent-endpoints.txt")));
        lines.add("POST /api/v1/platform/idempotency-probe/orders");
        lines.add("POST /api/v1/platform/idempotency-probe/sized");
        return new IdempotencyScope(lines);
    }
}
