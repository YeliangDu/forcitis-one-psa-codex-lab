package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #961 (user 2026-10-30 「先补前提再停同步」): with the settings production runs with — jobs on (forcitis.platform.scheduling.jobs-enabled)
 * and the switch production sets today (WORKFORCE_SYNC_ENABLED=true) — no scheduled task runs the organisation snapshot sync any more,
 * while the other workforce jobs are still scheduled. A task counts as the sync when its class is OrgSnapshotSyncJob or holds the sync
 * use case (SyncOrgSnapshot), whatever it is renamed to.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000",
        "forcitis.platform.scheduling.jobs-enabled=true", "forcitis.organization.workforce.sync.enabled=true"})
class SnapshotSyncNotScheduledIT {

    static final String SYNC_USE_CASE = "com.forcitis.one.organization.workforce.application.SyncOrgSnapshot";

    static final TestDatabase postgres = TestPostgres.database(SnapshotSyncNotScheduledIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    private ApplicationContext context;

    /** The scheduled methods of the context, as "declaring.Class.method". */
    private List<String> scheduledMethods() {
        List<String> methods = new ArrayList<>();
        for (ScheduledTaskHolder holder : context.getBeansOfType(ScheduledTaskHolder.class).values()) {
            for (ScheduledTask task : holder.getScheduledTasks()) {
                methods.add(task.getTask().toString());
            }
        }
        return methods;
    }

    private static boolean runsTheSync(String method) {
        String className = method.substring(0, method.lastIndexOf('.'));
        if (className.endsWith(".OrgSnapshotSyncJob")) {
            return true;
        }
        try {
            Class<?> type = Class.forName(className);
            return Arrays.stream(type.getDeclaredFields()).map(Field::getType).map(Class::getName).anyMatch(SYNC_USE_CASE::equals);
        } catch (ClassNotFoundException notAClass) {
            throw new AssertionError("cannot read the scheduled task " + method, notAClass);
        }
    }

    @Test
    @DisplayName("#961 停旧同步：按生产设置（作业开、WORKFORCE_SYNC_ENABLED=true）启动，没有任何定时任务再跑组织快照同步；人事命令到期作业照常在排")
    void theSnapshotSyncIsNoLongerScheduled() {
        List<String> methods = scheduledMethods();

        assertThat(methods).as("premise: scheduling is on in this context and the other workforce jobs are scheduled")
                .anyMatch(method -> method.contains("PersonnelCommandDueJob"));
        assertThat(methods).as("no scheduled task runs the organisation snapshot sync").noneMatch(SnapshotSyncNotScheduledIT::runsTheSync);
    }
}
