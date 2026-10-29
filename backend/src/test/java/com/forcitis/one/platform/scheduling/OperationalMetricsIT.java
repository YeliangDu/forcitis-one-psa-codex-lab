package com.forcitis.one.platform.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "flowable.database-schema-update=true",
        "spring.datasource.hikari.connection-timeout=2000"})
class OperationalMetricsIT {
    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    static final TestDatabase postgres = TestPostgres.database(OperationalMetricsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    MeterRegistry registry;

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("C-7 a scheduled job is wrapped next to its lock and starts at zero failures under its lock name")
    void scheduledJobFailuresAreCountedPerJob() {
        Object job = context.getBean("counterCapacityJob");

        assertThat(job).isInstanceOf(Advised.class);
        assertThat(((Advised) job).getAdvisors()[0]).as("outermost, ahead of the lock")
                .isSameAs(context.getBean("scheduledJobFailureAdvisor", Advisor.class));
        assertThat(registry.get("forcitis.platform.scheduling.job.failures").tag("job", "platform.numbering.counter.capacity.check").counter().count())
                .isZero();
    }
}
