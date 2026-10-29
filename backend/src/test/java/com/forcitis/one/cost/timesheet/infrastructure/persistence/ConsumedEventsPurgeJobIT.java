package com.forcitis.one.cost.timesheet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000", "forcitis.cost.timesheet.consumed-events.enabled=true"})
class ConsumedEventsPurgeJobIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    static final TestDatabase postgres = TestPostgres.database(ConsumedEventsPurgeJobIT.class, TestPostgres.MIGRATED);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    ConsumedEventsPurgeJob job;

    @Autowired
    JdbcClient jdbc;

    private UUID consumedDaysAgo(int days) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into cost_timesheet.consumed_events (consumed_event_id, event_id, consumer_name, consumed_at) values (uuidv7(), :id, 'purge-test', now() - make_interval(days => :days))")
                .param("id", id).param("days", days).update();
        return id;
    }

    private boolean exists(UUID id) {
        return jdbc.sql("select count(*) from cost_timesheet.consumed_events where event_id = :id").param("id", id).query(Long.class).single() == 1;
    }

    @Test
    @DisplayName("T3 handled-event records older than 90 days are removed, newer ones stay")
    void removesOnlyWhatIsOlderThanNinetyDays() {
        UUID old = consumedDaysAgo(91);
        UUID edge = consumedDaysAgo(89);
        UUID fresh = consumedDaysAgo(0);

        job.purge();

        assertThat(exists(old)).isFalse();
        assertThat(exists(edge)).isTrue();
        assertThat(exists(fresh)).isTrue();
    }
}
