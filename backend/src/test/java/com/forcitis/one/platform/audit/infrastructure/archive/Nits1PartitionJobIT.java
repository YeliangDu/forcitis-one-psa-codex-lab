package com.forcitis.one.platform.audit.infrastructure.archive;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
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

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000"})
@Testcontainers
class Nits1PartitionJobIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = TestPostgres.container();

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("NITS-1 months-ahead=23 时跨度为 24 个月，数据库函数接受：作业不报失败，最远一个月的分区已建")
    void theLargestMonthsAheadSpans24MonthsAndTheFunctionAccepts() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AuditPartitionJob job = new AuditPartitionJob(jdbc, Clock.systemUTC(), new AuditPartitionJob.Policy(AuditPartitionJob.MAX_MONTHS_AHEAD), meters);

        job.ensurePartitions();

        assertThat(meters.get("forcitis.platform.audit.partitions.failures").counter().count()).isZero();
        String farthest = "platform_audit.audit_entries_p" + YearMonth.now(ZoneOffset.UTC).plusMonths(23).toString().replace("-", "");
        assertThat(jdbc.sql("select to_regclass(:name) is not null").param("name", farthest).query(Boolean.class).single()).isTrue();
    }
}
