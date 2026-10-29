package com.forcitis.one.platform.audit.infrastructure.archive;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000"})
class Nits1PartitionJobIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    static final TestDatabase postgres = TestPostgres.database(Nits1PartitionJobIT.class, TestPostgres.MIGRATED);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("NITS-1 months-ahead=23 时跨度为 24 个月，数据库函数接受：作业不报失败，最远一个月的分区已建")
    void theLargestMonthsAheadSpans24MonthsAndTheFunctionAccepts() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        Clock clock = Clock.systemUTC();
        AuditPartitionJob job = new AuditPartitionJob(jdbc, clock, new AuditPartitionJob.Policy(AuditPartitionJob.MAX_MONTHS_AHEAD), meters);

        job.ensurePartitions();

        assertThat(meters.get("forcitis.platform.audit.partitions.failures").counter().count()).isZero();
        String farthest = "platform_audit.audit_entries_p" + YearMonth.now(clock).plusMonths(23).toString().replace("-", "");
        assertThat(jdbc.sql("select to_regclass(:name) is not null").param("name", farthest).query(Boolean.class).single()).isTrue();
    }
}
