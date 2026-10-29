package com.forcitis.one.platform.scheduling.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * FLAKY-SESS: Modulith's MomentsAutoConfiguration carries its own @EnableScheduling, which started every job in every context whatever jobs-enabled said.
 * The auto-configuration is excluded in application.properties; the runner cases pin the switch and the reason for the exclusion.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000"})
class JobsDisabledInTestContextsIT {

    static final TestDatabase postgres = TestPostgres.database(JobsDisabledInTestContextsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("with jobs-enabled=false the application context of a SpringBootTest has no scheduling post-processor, so no job runs")
    void noJobIsScheduled() {
        assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
    }

    private static ApplicationContextRunner switchRunner() {
        return new ApplicationContextRunner().withUserConfiguration(SchedulingConfiguration.class)
                .withBean(DataSource.class, () -> Mockito.mock(DataSource.class));
    }

    @Test
    @DisplayName("jobs-enabled=true puts the scheduling post-processor in the context")
    void theSwitchTurnsSchedulingOn() {
        switchRunner().withPropertyValues("forcitis.platform.scheduling.jobs-enabled=true")
                .run(on -> assertThat(on.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).as("the switch must enable scheduling").isNotEmpty());
    }

    @Test
    @DisplayName("jobs-enabled=false keeps the scheduling post-processor out of the context")
    void theSwitchTurnsSchedulingOff() {
        switchRunner().withPropertyValues("forcitis.platform.scheduling.jobs-enabled=false")
                .run(off -> assertThat(off.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class))
                        .as("exclude the auto-configuration that enables scheduling by itself, or keep @EnableScheduling in JobsActivation only").isEmpty());
    }

    @Test
    @DisplayName("why the exclusion stays: MomentsAutoConfiguration alone brings the scheduling post-processor (drop the exclusion when Modulith stops doing that)")
    void momentsEnablesSchedulingByItself() throws ClassNotFoundException {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(Class.forName(SpringAutoconfigureExcludeGuardTest.MOMENTS)))
                .run(moments -> assertThat(moments.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class))
                        .as("Modulith no longer enables scheduling in MomentsAutoConfiguration: the exclusion in application.properties can go").isNotEmpty());
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
