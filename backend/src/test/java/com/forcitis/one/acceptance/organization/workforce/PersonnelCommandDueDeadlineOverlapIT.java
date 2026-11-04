package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.CountersignNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.ClassUtils;

/**
 * #1184 (from #1146 / #1161): the personnel due job (00:05) and the countersign deadline job (#896, 00:10) take different ShedLocks, so a
 * catch-up run of one can overlap the other. On one countersigned PENDING personnel command whose withdrawal request set is still open, a
 * stale write of either job must not roll the command back or apply it twice. Both entry points are called directly on two threads; the
 * rounds repeat with fresh rows because concurrency is not deterministic, so a red run proves more than a green one. The command is built
 * through the real service flow (cross-entity transfer, both heads approve, the drafter withdraws), never by SQL.
 */
class PersonnelCommandDueDeadlineOverlapIT extends CountersignSupport {

    static final TestDatabase postgres = TestPostgres.database(PersonnelCommandDueDeadlineOverlapIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    private static final LocalDate DAY = TODAY.plusDays(10);
    private static final int ROUNDS = 5;
    private static final String DEADLINE_JOB_TYPE = "AttributionCountersignDeadlineJob";
    private static final String DEADLINE_JOB_METHOD = "runOnce";
    private static final String PACKAGE = "com.forcitis.one.organization.workforce";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    private ApplicationContext jobs;

    @Test
    @DisplayName("#1184 (#1146 完成标准 / #1161)：到日待生效的已会签人事命令带未决撤回请求，人事到日作业与 #896 截止作业同一天同时开跑（多轮、每轮新行）：两边都不失败，命令在当天 EFFECTIVE 而不是被撤回或回滚，版本仍在，未决撤回行 LAPSED，调动与法人变更事件各恰好一次")
    void thePersonnelDueJobAndTheDeadlineJobOverlappingOnOneCommand() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            Awaiting waiting = crossEntityTransfer(DAY);
            approveBoth(waiting.command());
            assertThat(commandStatus(waiting.command())).isEqualTo(PENDING);
            actIn(hr);
            commands().withdraw(waiting.command(), "synthetic: plan changed");
            assertThat(requests(waiting.command(), KIND_WITHDRAWAL)).hasSize(2).allSatisfy(row -> assertThat(row.get("decision_kind")).isNull());
            setToday(DAY);

            overlap(() -> run(DUE_JOB_TYPE, DUE_JOB_METHOD), () -> run(DEADLINE_JOB_TYPE, DEADLINE_JOB_METHOD));

            String where = "round " + round;
            assertThat(commandStatus(waiting.command())).as(where).isEqualTo(EFFECTIVE);
            assertThat(versionRowsWrittenBy(waiting.command())).as(where).isPositive();
            assertThat(requests(waiting.command(), KIND_WITHDRAWAL)).as(where).allSatisfy(row -> assertThat(row.get("decision_kind")).isEqualTo(LAPSED));
            assertThat(eventsOf(EVENT_TRANSFERRED, waiting.command())).as(where).isEqualTo(1);
            assertThat(eventsOf(EVENT_LEGAL_ENTITY_CHANGED, waiting.command())).as(where).isEqualTo(1);
            setToday(TODAY);
        }
    }

    /** Runs a job once with nobody signed in. A missing bean or method fails the test, it is never skipped. */
    private void run(String typeName, String methodName) {
        SecurityContextHolder.clearContext();
        for (String name : jobs.getBeanDefinitionNames()) {
            Class<?> type = jobs.getType(name);
            if (type == null) {
                continue;
            }
            Class<?> user = ClassUtils.getUserClass(type);
            if (!user.getSimpleName().equals(typeName) || !user.getName().startsWith(PACKAGE)) {
                continue;
            }
            try {
                Method method = user.getMethod(methodName);
                method.invoke(jobs.getBean(name));
            } catch (NoSuchMethodException missing) {
                throw new AssertionError(user.getName() + " has no public no-argument method " + methodName + "()", missing);
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(failure.getCause());
            } catch (IllegalAccessException broken) {
                throw new AssertionError("cannot call " + methodName, broken);
            }
            return;
        }
        throw new AssertionError("no bean of a type named " + typeName + " under " + PACKAGE);
    }

    /** Both bodies start together on two threads; the first failure of either is rethrown after both ended. */
    private void overlap(Runnable first, Runnable second) throws Exception {
        CyclicBarrier together = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> runs = new ArrayList<>();
            for (Runnable body : List.of(first, second)) {
                runs.add(pool.submit(() -> {
                    together.await(30, TimeUnit.SECONDS);
                    body.run();
                    return null;
                }));
            }
            Exception failure = null;
            for (Future<?> run : runs) {
                try {
                    run.get(120, TimeUnit.SECONDS);
                } catch (ExecutionException ended) {
                    failure = failure != null ? failure : ended;
                }
            }
            if (failure != null) {
                throw failure;
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
