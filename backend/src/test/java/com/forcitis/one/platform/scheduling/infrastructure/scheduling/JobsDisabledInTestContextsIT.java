package com.forcitis.one.platform.scheduling.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A test class that enables scheduling on a component-scanned configuration would start every job in every SpringBootTest context (FLAKY-SESS). */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000"})
@Testcontainers
class JobsDisabledInTestContextsIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18").withInitScript("init-schemas.sql")
            .withUrlParam("currentSchema", "platform_approval");

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("with jobs-enabled=false the application context of a SpringBootTest has no scheduling post-processor, so no job runs")
    void noJobIsScheduled() {
        assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
    }

    @Test
    @DisplayName("injection: the check sees a context that enables scheduling")
    void aContextThatEnablesSchedulingIsCaught() {
        new ApplicationContextRunner().withUserConfiguration(SchedulingSample.class)
                .run(enabled -> assertThat(enabled.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isNotEmpty());
    }
}

/** Top level, not nested in the test class: a nested test configuration would be picked up by the test's own context. */
@TestConfiguration
@EnableScheduling
class SchedulingSample {
}
