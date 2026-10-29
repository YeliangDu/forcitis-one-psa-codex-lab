package com.forcitis.one.platform.approval.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.fixture.doubles.MutableClock;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.impl.runtime.Clock;
import org.flowable.common.engine.impl.util.DefaultClockImpl;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000"})
@Import(EngineClockIT.Doubles.class)
@DisplayName("FLW-1 引擎时间取注入 Clock，固定 Clock 下任务创建与到期时刻确定")
class EngineClockIT {
    static final Instant X = Instant.parse("2026-03-02T01:00:00Z");
    static final String PROBE = "engine_clock_probe";

    @TestConfiguration
    static class Doubles {
        @Bean
        @Primary
        MutableClock clock() {
            return new MutableClock(X, ZoneOffset.UTC);
        }
    }

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    static final TestDatabase postgres = TestPostgres.database(EngineClockIT.class, TestPostgres.MIGRATED);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    MutableClock clock;

    @Autowired
    ProcessEngine engine;

    @Autowired
    RepositoryService repository;

    @Autowired
    RuntimeService runtime;

    @Autowired
    TaskService tasks;

    @Autowired
    HistoryService history;

    @Autowired
    TaskAssignmentListener listener;

    @BeforeEach
    void deployProbe() {
        // The probe node carries no assignment rule: only the engine clock is under test, not approval task registration.
        engine.getProcessEngineConfiguration().getEventDispatcher().removeEventListener(listener);
        clock.set(X);
        if (repository.createProcessDefinitionQuery().processDefinitionKey(PROBE).count() == 0) {
            repository.createDeployment().addClasspathResource("engineclock/engine_clock_probe.bpmn20.xml").deploy();
        }
        runtime.createProcessInstanceQuery().processDefinitionKey(PROBE).list().forEach(i -> runtime.deleteProcessInstance(i.getId(), "test cleanup"));
        history.createHistoricProcessInstanceQuery().processDefinitionKey(PROBE).list().forEach(i -> history.deleteHistoricProcessInstance(i.getId()));
    }

    @AfterEach
    void restoreListener() {
        engine.getProcessEngineConfiguration().getEventDispatcher().addEventListener(listener, FlowableEngineEventType.TASK_CREATED);
    }

    private Task startProbe() {
        String instance = runtime.startProcessInstanceByKey(PROBE).getId();
        return tasks.createTaskQuery().processInstanceId(instance).singleResult();
    }

    private HistoricTaskInstance historic(String taskId) {
        return history.createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
    }

    @Test
    @DisplayName("FLW-1 引擎 clock 报出的当前时刻就是注入 Clock 的时刻")
    void engineClockReadsTheInjectedClock() {
        Clock engineClock = engine.getProcessEngineConfiguration().getClock();

        assertThat(engineClock.getCurrentTime().toInstant()).isEqualTo(X);
        clock.set(X.plus(Duration.ofMinutes(5)));
        assertThat(engineClock.getCurrentTime().toInstant()).isEqualTo(X.plus(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("FLW-1 固定 Clock 下任务创建时刻为 X、到期为 X+1h，历史任务开始时刻为 X")
    void taskCreationAndDueFollowTheInjectedClock() {
        Task task = startProbe();

        assertThat(task.getCreateTime().toInstant()).as("act_ru_task.create_time_").isEqualTo(X);
        assertThat(task.getDueDate().toInstant()).as("dueDate PT1H").isEqualTo(X.plus(Duration.ofHours(1)));
        assertThat(historic(task.getId()).getStartTime().toInstant()).as("act_hi_taskinst.start_time_").isEqualTo(X);
    }

    @Test
    @DisplayName("FLW-1 时钟前移 30 分钟后完成任务，历史任务结束时刻为 X+30m")
    void taskCompletionFollowsTheInjectedClock() {
        Task task = startProbe();
        clock.set(X.plus(Duration.ofMinutes(30)));

        tasks.complete(task.getId());

        assertThat(historic(task.getId()).getEndTime().toInstant()).as("act_hi_taskinst.end_time_").isEqualTo(X.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("FLW-1 引擎 clock 不能被改写：setCurrentTime、setCurrentCalendar、reset 都抛 UnsupportedOperationException")
    void engineClockRefusesToBeSet() {
        Clock engineClock = engine.getProcessEngineConfiguration().getClock();
        try {
            assertThatThrownBy(() -> engineClock.setCurrentTime(Date.from(X))).isInstanceOf(UnsupportedOperationException.class);
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));
            calendar.setTime(Date.from(X));
            assertThatThrownBy(() -> engineClock.setCurrentCalendar(calendar)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(engineClock::reset).isInstanceOf(UnsupportedOperationException.class);
        } finally {
            // DefaultClockImpl keeps the set time in a static field shared by every engine in this JVM.
            if (engineClock instanceof DefaultClockImpl) {
                engineClock.reset();
            }
        }
    }
}
