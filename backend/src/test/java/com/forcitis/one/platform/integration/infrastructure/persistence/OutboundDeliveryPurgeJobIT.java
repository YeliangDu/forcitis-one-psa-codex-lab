package com.forcitis.one.platform.integration.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000", "forcitis.platform.integration.outbound-purge.enabled=true"})
@Testcontainers
class OutboundDeliveryPurgeJobIT {
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
    static PostgreSQLContainer postgres = TestPostgres.container();

    @Autowired
    OutboundDeliveryPurgeJob job;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void empty() {
        jdbc.sql("truncate table platform_integration.outbound_deliveries restart identity cascade").update();
    }

    private String delivery(String status, int daysAgo) {
        String key = java.util.UUID.randomUUID().toString();
        jdbc.sql("""
                insert into platform_integration.outbound_deliveries (id, subject_kind, subject_id, purpose_kind, recipient_codes, title, target_url, status, created_at, updated_at)
                values (uuidv7(), 'APPROVAL_TASK', cast(:key as uuid), 'TASK_ASSIGNED', array['alice'], 'title', 'https://one.example.test/x', :status, now() - make_interval(days => :days), now() - make_interval(days => :days))
                """).param("key", key).param("status", status).param("days", daysAgo).update();
        return key;
    }

    private boolean exists(String key) {
        return jdbc.sql("select count(*) from platform_integration.outbound_deliveries where subject_id = cast(:key as uuid)").param("key", key).query(Long.class).single() == 1;
    }

    @Test
    @DisplayName("K7 sent and skipped deliveries go after 90 days, failed and dead-letter ones after a year, pending ones never")
    void keepPeriodsFollowTheOutcome() {
        String sentOld = delivery("SENT", 91);
        String sentNew = delivery("SENT", 89);
        String skippedOld = delivery("SKIPPED", 100);
        String failedMiddle = delivery("FAILED", 200);
        String failedOld = delivery("FAILED", 400);
        String deadOld = delivery("DEAD_LETTER", 400);
        String deadMiddle = delivery("DEAD_LETTER", 300);
        String pendingOld = delivery("PENDING", 900);

        job.purge();

        assertThat(exists(sentOld)).isFalse();
        assertThat(exists(sentNew)).isTrue();
        assertThat(exists(skippedOld)).isFalse();
        assertThat(exists(failedMiddle)).isTrue();
        assertThat(exists(failedOld)).isFalse();
        assertThat(exists(deadOld)).isFalse();
        assertThat(exists(deadMiddle)).isTrue();
        assertThat(exists(pendingOld)).isTrue();
    }
}
