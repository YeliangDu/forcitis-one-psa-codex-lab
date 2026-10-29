package com.forcitis.one.platform.integration.infrastructure.registry;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEventPublicationStatistics implements EventPublicationStatistics {
    private final JdbcClient jdbc;

    JdbcEventPublicationStatistics(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long exhausted(int maxAttempts) {
        return jdbc.sql("""
                        select count(*) from platform_integration.event_publication
                         where completion_date is null and coalesce(completion_attempts, 0) >= :max
                        """)
                .param("max", maxAttempts).query(Long.class).single();
    }

    @Override
    public long incompleteBefore(Instant cutoff) {
        return jdbc.sql("""
                        select count(*) from platform_integration.event_publication
                         where completion_date is null and publication_date < :cutoff
                        """)
                .param("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC)).query(Long.class).single();
    }
}
