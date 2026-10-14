package com.forcitis.one.cost.timesheet.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000", "forcitis.cost.timesheet.consumed-events.enabled=true"})
@Testcontainers
class ConsumedEventsPurgeJobIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired
    ConsumedEventsPurgeJob job;

    @Autowired
    JdbcClient jdbc;

    private UUID consumedDaysAgo(int days) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into cost_timesheet.consumed_events (event_id, consumer_name, consumed_at) values (:id, 'purge-test', now() - make_interval(days => :days))")
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
